package io.github.mmaarij123.v2componentfix

import com.discord.api.botuikit.ButtonComponent
import com.discord.api.botuikit.Component
import com.discord.api.botuikit.ComponentType
import com.discord.api.botuikit.ContainerComponent
import com.discord.api.botuikit.FileComponent
import com.discord.api.botuikit.MediaGalleryComponent
import com.discord.api.botuikit.SectionComponent
import com.discord.api.botuikit.TextDisplayComponent
import com.discord.api.botuikit.ThumbnailComponent
import com.discord.api.botuikit.UnfurledMediaItem
import com.discord.api.message.MessageFlags
import com.discord.api.message.MessageSnapshot
import com.discord.api.message.attachment.MessageAttachment
import com.discord.models.message.Message
import java.net.URLConnection
import java.net.URLDecoder

private const val ATTACHMENT_SCHEME = "attachment://"
private const val MAX_COMPONENT_DEPTH = 40

internal fun Message.repairComponentsV2(): Message {
    val repairedComponents = repairComponents(flags, components, attachments)
    val repairedSnapshots = repairSnapshots(messageSnapshots)
    return if (repairedComponents === components && repairedSnapshots === messageSnapshots) this
    else copy(components = repairedComponents, messageSnapshots = repairedSnapshots)
}

private fun repairSnapshots(snapshots: List<MessageSnapshot>?): List<MessageSnapshot>? =
    snapshots?.mapChanged { snapshot ->
        val message = snapshot.message
        val components = repairComponents(message.flags, message.components, message.attachments)
        if (components === message.components) snapshot
        else snapshot.copy(message = message.copy(components = components))
    }

private fun repairComponents(
    flags: Long?,
    components: List<Component>?,
    attachments: List<MessageAttachment>?,
): List<Component>? {
    if ((flags ?: 0L) and MessageFlags.IS_COMPONENTS_V2 == 0L || components.isNullOrEmpty()) {
        return components
    }
    return ComponentRepairs(attachments.orEmpty()).repair(components)
}

private class ComponentRepairs(private val attachments: List<MessageAttachment>) {
    fun repair(components: List<Component>, depth: Int = 0): List<Component> =
        components.mapChanged { repair(it, depth) }

    private fun repair(component: Component, depth: Int): Component {
        if (depth >= MAX_COMPONENT_DEPTH) return component
        return when (component) {
            is ContainerComponent -> {
                val children = repair(component.components, depth + 1)
                if (children === component.components) component else component.copy(components = children)
            }
            is SectionComponent -> {
                val children = repair(component.components, depth + 1)
                val accessory = component.accessory?.let { repair(it, depth + 1) }
                val hasText = children.any { it is TextDisplayComponent }
                if ((accessory is ButtonComponent || accessory is ThumbnailComponent) && hasText) {
                    if (children === component.components && accessory === component.accessory) component
                    else component.copy(components = children, accessory = accessory)
                } else if (hasText || accessory is ButtonComponent || accessory is ThumbnailComponent) {
                    // A section with an incomplete layout is dropped by Microcord. Keeping the
                    // wrapper and the accessory slot preserves the host's interaction indices.
                    ContainerComponent(
                        type = ComponentType.CONTAINER,
                        id = component.id,
                        components = children + listOfNotNull(accessory),
                        accentColor = null,
                        spoiler = false,
                    )
                } else {
                    component
                }
            }
            is ThumbnailComponent -> {
                val attachment = findAttachment(component.media) ?: return component
                val media = component.media.withAttachment(attachment)
                val spoiler = component.spoiler.withAttachment(attachment)
                if (media == component.media && spoiler == component.spoiler) component
                else component.copy(media = media, spoiler = spoiler)
            }
            is MediaGalleryComponent -> {
                val items = component.items.mapChanged { item ->
                    val attachment = findAttachment(item.media) ?: return@mapChanged item
                    val media = item.media.withAttachment(attachment)
                    val spoiler = item.spoiler.withAttachment(attachment)
                    if (media == item.media && spoiler == item.spoiler) item
                    else item.copy(media = media, spoiler = spoiler)
                }
                if (items === component.items) component else component.copy(items = items)
            }
            is FileComponent -> {
                val attachment = findAttachment(component.file) ?: return component
                val updated = component.copy(
                    file = component.file.withAttachment(attachment),
                    name = component.name?.takeIf(String::isNotBlank) ?: attachment.filename,
                    size = component.size ?: attachment.size,
                    spoiler = component.spoiler.withAttachment(attachment),
                )
                if (updated == component) component else updated
            }
            else -> component
        }
    }

    private fun findAttachment(media: UnfurledMediaItem): MessageAttachment? {
        media.attachmentId?.let { id -> attachments.firstOrNull { it.id == id } }?.let { return it }
        if (media.url.startsWith(ATTACHMENT_SCHEME)) {
            val name = media.url.removePrefix(ATTACHMENT_SCHEME)
            attachments.singleOrNull { it.filename == name }?.let { return it }
            val decoded = runCatching { URLDecoder.decode(name.replace("+", "%2B"), "UTF-8") }.getOrNull()
            return attachments.singleOrNull { it.filename == decoded }
        }
        if (media.url.isBlank()) return null
        return attachments.firstOrNull { media.url == it.url || media.url == it.proxyUrl }
    }
}

private fun Boolean?.withAttachment(attachment: MessageAttachment): Boolean? =
    if (attachment.isSpoiler) true else this

private fun UnfurledMediaItem.withAttachment(attachment: MessageAttachment): UnfurledMediaItem = copy(
    url = if (url.isBlank() || url.startsWith(ATTACHMENT_SCHEME)) attachment.url else url,
    proxyUrl = proxyUrl?.takeIf { it.isNotBlank() && !it.startsWith(ATTACHMENT_SCHEME) }
        ?: attachment.proxyUrl.takeIf(String::isNotBlank),
    width = width?.takeIf { it > 0 } ?: attachment.width,
    height = height?.takeIf { it > 0 } ?: attachment.height,
    contentType = contentType?.takeIf(String::isNotBlank)
        ?: attachment.contentType?.takeIf(String::isNotBlank)
        ?: URLConnection.guessContentTypeFromName(attachment.filename),
    attachmentId = attachment.id,
    flags = if (attachment.isAnimated) (flags ?: 0) or UnfurledMediaItem.FLAG_IS_ANIMATED else flags,
)

// Preserve the original list and unmodified branch instances when there is no repair.
private inline fun <T> List<T>.mapChanged(transform: (T) -> T): List<T> {
    var changes: MutableList<T>? = null
    forEachIndexed { index, original ->
        val updated = transform(original)
        if (updated !== original) {
            val result = changes ?: toMutableList().also { changes = it }
            result[index] = updated
        }
    }
    return changes ?: this
}
