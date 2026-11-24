package io.github.mmaarij123.v2componentfix

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import com.discord.api.botuikit.ComponentType
import com.discord.api.botuikit.SelectItem
import com.discord.models.botuikit.MessageComponent
import com.discord.models.botuikit.withInteractionsDisabled
import com.discord.models.message.Message
import com.discord.stores.StoreStream
import com.discord.widgets.botuikit.BotComponentExperiments
import com.discord.widgets.botuikit.ComponentProvider
import com.discord.widgets.botuikit.ComponentStateMapper
import com.discord.widgets.botuikit.views.ComponentActionListener
import com.discord.widgets.chat.list.entries.BotUiComponentEntry
import com.discord.widgets.chat.list.entries.ForwardedMessageEntry
import kotlin.math.roundToInt

internal fun createRepairedPreview(context: Context, original: Message, repaired: Message): View {
    val provider = ComponentProvider()
    val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val padding = (16 * resources.displayMetrics.density).roundToInt()
        setPadding(padding, padding, padding, padding)
    }
    content.addRepairs(provider, original, repaired)
    return ScrollView(context).apply {
        addView(content)
    }
}

private fun LinearLayout.addRepairs(provider: ComponentProvider, original: Message, repaired: Message) {
    if (original.components !== repaired.components) addComponentTree(provider, repaired)
    if (original.messageSnapshots === repaired.messageSnapshots) return
    for (forward in ForwardedMessageEntry.resolve(repaired)) {
        val index = forward.snapshotIndex
        val old = original.messageSnapshots?.getOrNull(index)
        val new = repaired.messageSnapshots?.getOrNull(index)
        val message = forward.message
        if (message != null && old !== new) addComponentTree(provider, message)
    }
}

private fun LinearLayout.addComponentTree(provider: ComponentProvider, message: Message) {
    val components = ComponentStateMapper.processComponentsToMessageComponents(
        message.components.orEmpty(),
        componentStoreState = null,
        componentExperiments = BotComponentExperiments,
        animateEmojis = false,
    ).map(MessageComponent::withInteractionsDisabled)
    if (components.isEmpty()) return

    val channel = StoreStream.getChannels().getChannel(message.channelId)
    val guildId = message.guildId ?: channel?.guildId
    val entry = BotUiComponentEntry(
        message = message,
        applicationId = message.applicationId ?: message.author?.id ?: 0L,
        guildId = guildId,
        messageComponents = components,
        channel = channel,
        guildMembers = guildId?.let { StoreStream.getGuilds().getMembers()[it] }.orEmpty(),
        guildRoles = guildId?.let { StoreStream.getGuilds().getRoles()[it] }.orEmpty(),
    )
    val listener = PreviewActions(entry)
    val group = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    addView(group)
    for (component in components) {
        val view = provider.getConfiguredComponentView(listener, component, group, group.childCount) as? View
        if (view != null) group.addView(view)
    }
}

private class PreviewActions(override val componentEntry: BotUiComponentEntry) : ComponentActionListener {
    override fun onButtonComponentClick(componentIndex: Int, customId: String) = Unit

    override fun onSelectComponentClick(
        componentIndex: Int,
        componentType: ComponentType,
        customId: String,
        placeholder: String?,
        options: List<SelectItem>,
        selectedItems: List<SelectItem>,
        minOptionsToSelect: Int,
        maxOptionsToSelect: Int,
        emojiAnimationsEnabled: Boolean,
        defaultValues: List<String>,
    ) = Unit
}
