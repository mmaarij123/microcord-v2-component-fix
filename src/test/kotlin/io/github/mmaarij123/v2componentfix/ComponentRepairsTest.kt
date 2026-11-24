package io.github.mmaarij123.v2componentfix

import com.discord.api.botuikit.ButtonComponent
import com.discord.api.botuikit.ButtonStyle
import com.discord.api.botuikit.Component
import com.discord.api.botuikit.ComponentType
import com.discord.api.botuikit.ContainerComponent
import com.discord.api.botuikit.FileComponent
import com.discord.api.botuikit.MediaGalleryComponent
import com.discord.api.botuikit.MediaGalleryItem
import com.discord.api.botuikit.SectionComponent
import com.discord.api.botuikit.TextDisplayComponent
import com.discord.api.botuikit.ThumbnailComponent
import com.discord.api.botuikit.UnfurledMediaItem
import com.discord.api.botuikit.UnknownComponent
import com.discord.api.message.Message as ApiMessage
import com.discord.api.message.MessageFlags
import com.discord.api.message.MessageSnapshot
import com.discord.api.message.attachment.MessageAttachment
import com.discord.models.message.Message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ComponentRepairsTest {
    @Test
    fun legacyMessageAndCompleteV2TreeKeepTheirIdentity() {
        val incomplete = SectionComponent(ComponentType.SECTION, 1, listOf(text), null)
        val legacy = message(incomplete).copy(flags = 0L)
        val complete = message(SectionComponent(ComponentType.SECTION, 1, listOf(text), button))

        assertSame(legacy, legacy.repairComponentsV2())
        assertSame(complete, complete.repairComponentsV2())
    }

    @Test
    fun incompleteSectionsKeepTextAndComponentPositions() {
        val unknown = UnknownComponent()
        val section = SectionComponent(ComponentType.SECTION, 1, listOf(text), unknown)
        val original = message(section, button)

        val repaired = original.repairComponentsV2()
        val fallback = repaired.components!![0] as ContainerComponent

        assertEquals(1, fallback.id)
        assertEquals(listOf(text, unknown), fallback.components)
        assertSame(button, repaired.components!![1])
        assertSame(repaired, repaired.repairComponentsV2())
    }

    @Test
    fun sectionWithoutTextKeepsItsButton() {
        val original = message(SectionComponent(ComponentType.SECTION, 1, emptyList(), button))

        val fallback = original.repairComponentsV2().components!!.single() as ContainerComponent

        assertEquals(listOf(button), fallback.components)
    }

    @Test
    fun galleryAndSectionThumbnailGetMetadataFromTheSameMessage() {
        val attachment = attachment().copy(
            filename = "chart + data.webp",
            flags = MessageAttachment.FLAG_IS_ANIMATED or MessageAttachment.FLAG_IS_SPOILER,
        )
        val thumbnail = ThumbnailComponent(ComponentType.THUMBNAIL, 3, media("attachment://chart%20+%20data.webp"), "Chart", null)
        val gallery = MediaGalleryComponent(
            ComponentType.MEDIA_GALLERY, 4,
            listOf(MediaGalleryItem(media(attachment.url, attachment.id), null, false)),
        )
        val section = SectionComponent(ComponentType.SECTION, 2, listOf(text), thumbnail)
        val original = message(ContainerComponent(ComponentType.CONTAINER, 1, listOf(section, gallery), 0x556677, true))
            .copy(attachments = listOf(attachment))

        val repaired = original.repairComponentsV2()
        val container = repaired.components!!.single() as ContainerComponent
        val fixedThumbnail = (container.components[0] as SectionComponent).accessory as ThumbnailComponent
        val fixedGallery = (container.components[1] as MediaGalleryComponent).items.single()

        assertEquals(attachment.url, fixedThumbnail.media.url)
        assertEquals(attachment.proxyUrl, fixedThumbnail.media.proxyUrl)
        assertEquals(attachment.id, fixedThumbnail.media.attachmentId)
        assertEquals(640, fixedThumbnail.media.width)
        assertEquals(480, fixedThumbnail.media.height)
        assertEquals("image/webp", fixedThumbnail.media.contentType)
        assertTrue(fixedThumbnail.media.isAnimated)
        assertEquals(true, fixedThumbnail.spoiler)
        assertEquals(fixedThumbnail.media, fixedGallery.media)
        assertEquals(true, fixedGallery.spoiler)
        assertEquals(true, container.spoiler)
        assertEquals(0x556677, container.accentColor)
        assertSame(original.attachments, repaired.attachments)
        assertSame(repaired, repaired.repairComponentsV2())
    }

    @Test
    fun fileUsesAttachmentNameAndSizeAndKeepsExplicitMetadata() {
        val attachment = attachment()
        val missing = FileComponent(ComponentType.FILE, 1, media("attachment://chart.webp"), null, null, null)
        val explicit = missing.copy(name = "Display name", size = 0L)
        val original = message(missing, explicit).copy(attachments = listOf(attachment))

        val (first, second) = original.repairComponentsV2().components!!.filterIsInstance<FileComponent>()

        assertEquals(attachment.filename, first.name)
        assertEquals(attachment.size, first.size)
        assertEquals(attachment.url, first.file.url)
        assertEquals("Display name", second.name)
        assertEquals(0L, second.size)
    }

    @Test
    fun unrelatedAndAmbiguousMediaStayUnresolved() {
        val first = attachment()
        val second = first.copy(id = 22L, url = "${first.url}?different")
        val ambiguous = ThumbnailComponent(ComponentType.THUMBNAIL, 1, media("attachment://chart.webp"), null, null)
        val unrelated = ambiguous.copy(media = media("https://example.com/chart.webp"))
        val original = message(ambiguous, unrelated).copy(attachments = listOf(first, second))

        assertSame(original, original.repairComponentsV2())
    }

    @Test
    fun forwardedSnapshotUsesItsOwnAttachments() {
        val attachment = attachment()
        val file = FileComponent(ComponentType.FILE, 1, media("attachment://chart.webp"), null, null, null)
        val snapshot = MessageSnapshot(
            ApiMessage(
                id = 3L,
                channelId = 1L,
                flags = MessageFlags.IS_COMPONENTS_V2,
                attachments = listOf(attachment),
                components = listOf(file),
            ),
        )
        val original = Message(id = 2L, messageSnapshots = listOf(snapshot))

        val repaired = original.repairComponentsV2()
        val repairedFile = repaired.messageSnapshots!!.single().message.components!!.single() as FileComponent

        assertNull(repaired.components)
        assertEquals(attachment.url, repairedFile.file.url)
        assertSame(repaired, repaired.repairComponentsV2())
    }

    private fun message(vararg components: Component) =
        Message(id = 2L, components = components.toList(), flags = MessageFlags.IS_COMPONENTS_V2)

    private fun media(url: String, attachmentId: Long? = null) =
        UnfurledMediaItem(url, null, null, null, null, attachmentId)

    private fun attachment() = MessageAttachment(
        url = "https://cdn.discordapp.com/attachments/1/11/chart.webp",
        size = 500L,
        id = 11L,
        proxyUrl = "https://media.discordapp.net/attachments/1/11/chart.webp",
        filename = "chart.webp",
        width = 640,
        height = 480,
        contentType = "image/webp",
    )

    private val text = TextDisplayComponent(ComponentType.TEXT_DISPLAY, 2, "**Hello**")
    private val button = ButtonComponent(ComponentType.BUTTON, "details", "Details", ButtonStyle.PRIMARY, false, null, null)
}
