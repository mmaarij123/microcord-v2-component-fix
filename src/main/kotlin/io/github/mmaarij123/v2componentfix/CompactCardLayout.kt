package io.github.mmaarij123.v2componentfix

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.text.Layout
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.discord.R
import com.discord.databinding.WidgetBotUiContainerComponentBinding
import com.discord.models.botuikit.ContainerMessageComponent
import com.discord.models.botuikit.MediaGalleryMessageComponent
import com.discord.models.botuikit.MessageComponent
import com.discord.models.botuikit.SeparatorMessageComponent
import com.discord.models.botuikit.TextDisplayMessageComponent
import com.discord.stores.StoreStream
import com.discord.utilities.lifecycle.ActivityProvider
import com.discord.widgets.botuikit.views.v2.ContainerComponentView
import com.discord.widgets.botuikit.views.v2.MediaGalleryComponentView
import com.discord.widgets.botuikit.views.v2.MediaGalleryLayout
import com.discord.widgets.botuikit.views.v2.TextDisplayComponentView
import com.discord.widgets.chat.list.adapter.WidgetChatListAdapterItemBotComponentRow
import java.util.WeakHashMap
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Runs on the main thread after Microcord binds the visible component rows. */
internal class CompactCardLayout : Application.ActivityLifecycleCallbacks {
    private var application: Application? = null
    private val windows = mutableMapOf<Activity, ChatWindow>()
    private val originalWidths = WeakHashMap<View, Int>()
    private val measurements = WeakHashMap<ContainerComponentView, CardMeasurement>()

    fun start(application: Application) {
        if (this.application != null) return
        this.application = application
        application.registerActivityLifecycleCallbacks(this)
        ActivityProvider.getActivity()?.let(::onActivityStarted)
    }

    fun stop() {
        application?.unregisterActivityLifecycleCallbacks(this)
        application = null
        windows.values.forEach(ChatWindow::close)
        windows.clear()
        for ((view, width) in originalWidths) view.setWidth(width)
        originalWidths.clear()
        measurements.clear()
    }

    override fun onActivityStarted(activity: Activity) {
        if (activity in windows) return
        val decor = activity.window.decorView
        windows[activity] = ChatWindow(decor)
        decor.invalidate()
    }

    override fun onActivityStopped(activity: Activity) {
        windows.remove(activity)?.close()
    }

    override fun onActivityDestroyed(activity: Activity) = onActivityStopped(activity)
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    private inner class ChatWindow(private val decor: View) :
        ViewTreeObserver.OnGlobalLayoutListener, ViewTreeObserver.OnPreDrawListener {
        private var chat: RecyclerView? = null
        private var search: RecyclerView? = null
        private val playback = ComponentGifPlayback()

        init {
            decor.viewTreeObserver.addOnGlobalLayoutListener(this)
            decor.viewTreeObserver.addOnPreDrawListener(this)
            onGlobalLayout()
        }

        fun close() {
            playback.stop()
            val observer = decor.viewTreeObserver
            if (observer.isAlive) {
                observer.removeOnGlobalLayoutListener(this)
                observer.removeOnPreDrawListener(this)
            }
        }

        override fun onGlobalLayout() {
            chat = decor.findViewById(R.id.chat_list_recycler)
            search = decor.findViewById(R.id.search_results_list)
        }

        override fun onPreDraw(): Boolean {
            val autoPlay = (chat?.isShown == true || search?.isShown == true) &&
                StoreStream.getUserSettings().getIsAutoPlayGifsEnabled()
            val chatChanged = fitList(chat, autoPlay)
            val searchChanged = fitList(search, autoPlay)
            playback.finishFrame()
            return !chatChanged && !searchChanged
        }

        private fun fitList(list: RecyclerView?, autoPlay: Boolean): Boolean {
            if (list == null || !list.isShown) return false
            var changed = false
            for (index in 0 until list.childCount) {
                val row = list.getChildAt(index)
                val holder = list.getChildViewHolder(row)
                if (holder is WidgetChatListAdapterItemBotComponentRow) {
                    if (fitRow(row, holder, autoPlay)) changed = true
                    continue
                }
                val accessories = row.findViewById<ViewGroup>(R.id.chat_list_adapter_item_attachments) ?: continue
                for (accessoryIndex in 0 until accessories.childCount) {
                    val accessory = accessories.getChildAt(accessoryIndex)
                    val hosted = accessory.getTag(R.id.chat_list_adapter_item_attachments) as? WidgetChatListAdapterItemBotComponentRow
                        ?: continue
                    if (fitRow(accessory, hosted, autoPlay)) changed = true
                }
            }
            return changed
        }

        private fun fitRow(view: View, holder: WidgetChatListAdapterItemBotComponentRow, autoPlay: Boolean): Boolean {
            val root = view.findViewById<ViewGroup>(R.id.chat_list_adapter_item_component_root) ?: return false
            if (autoPlay) playback.scan(root)
            val components = holder.componentEntry.messageComponents
            if (root.childCount != components.size) return false
            var changed = false
            for ((index, component) in components.withIndex()) {
                val card = root.getChildAt(index) as? ContainerComponentView ?: continue
                if (component is ContainerMessageComponent && fitCard(card, root, component)) changed = true
            }
            return changed
        }
    }

    private fun fitCard(card: ContainerComponentView, parent: ViewGroup, model: ContainerMessageComponent): Boolean {
        val available = parent.width - parent.paddingLeft - parent.paddingRight - card.horizontalMargins()
        if (available <= 0) return false
        val binding = WidgetBotUiContainerComponentBinding.bind(card)
        val content = binding.components
        val cached = measurements[card]
        val fit = if (cached?.model === model && cached.available == available) cached else {
            val accent = if (binding.accent.visibility == View.GONE) 0 else binding.accent.layoutParams.width
            val chrome = card.horizontalPadding() + content.horizontalPadding() + accent
            val width = measureCard(model.children, content, available, chrome)
            CardMeasurement(model, available, width, chrome).also { measurements[card] = it }
        }
        var changed = if (fit.width == null) restoreWidth(card) else constrainWidth(card, fit.width)
        for (index in 0 until content.childCount) {
            val gallery = content.getChildAt(index) as? MediaGalleryComponentView ?: continue
            val innerWidth = fit.width?.minus(fit.chrome)
            val galleryChanged = if (innerWidth == null) restoreWidth(gallery)
            else constrainWidth(gallery, innerWidth - gallery.horizontalMargins(), ViewGroup.LayoutParams.MATCH_PARENT)
            if (galleryChanged) changed = true
        }
        return changed
    }

    private fun measureCard(children: List<MessageComponent>, content: ViewGroup, available: Int, chrome: Int): Int? {
        if (children.size != content.childCount || available <= chrome) return null
        return fitCardWidth(available, chrome, children.mapIndexed { index, component ->
            val view = content.getChildAt(index)
            val width = when (component) {
                is TextDisplayMessageComponent -> (view as? TextDisplayComponentView)?.naturalTextWidth()
                is MediaGalleryMessageComponent -> component.singleCellWidth(view, available - chrome)
                is SeparatorMessageComponent -> 0
                else -> null
            }
            width?.plus(view.horizontalMargins())
        })
    }

    private fun constrainWidth(view: View, width: Int, resetWidth: Int = view.layoutParams.width): Boolean {
        if (width <= 0 || view.layoutParams.width == width) return false
        originalWidths.getOrPut(view) { resetWidth }
        return view.setWidth(width)
    }

    private fun restoreWidth(view: View): Boolean {
        val original = originalWidths.remove(view) ?: return false
        return view.setWidth(original)
    }

    private data class CardMeasurement(
        val model: ContainerMessageComponent,
        val available: Int,
        val width: Int?,
        val chrome: Int,
    )
}

internal fun fitCardWidth(available: Int, chrome: Int, childWidths: List<Int?>): Int? {
    var content = 0
    for (width in childWidths) content = maxOf(content, width ?: return null)
    return if (content > 0 && content < available - chrome) content + chrome else null
}

private fun TextDisplayComponentView.naturalTextWidth(): Int? {
    val text = getChildAt(0) as? TextView ?: return null
    val displayed = text.layout?.text ?: text.text
    return ceil(Layout.getDesiredWidth(displayed, text.paint)).toInt() + text.compoundPaddingLeft +
        text.compoundPaddingRight + horizontalPadding() + 1
}

private fun MediaGalleryMessageComponent.singleCellWidth(view: View, available: Int): Int? {
    if (view !is MediaGalleryComponentView) return null
    val media = items.singleOrNull()?.media ?: return null
    val width = media.width?.takeIf { it > 0 } ?: return null
    val height = media.height?.takeIf { it > 0 } ?: return null
    val density = view.resources.displayMetrics.density
    return MediaGalleryLayout.layout(
        count = 1,
        width = available,
        gap = (MediaGalleryLayout.GAP_DP * density).roundToInt(),
        singleMaxHeight = (MediaGalleryLayout.SINGLE_MAX_HEIGHT_DP * density).roundToInt(),
        sourceWidth = (width * density).roundToInt(),
        sourceHeight = (height * density).roundToInt(),
    ).cells.singleOrNull()?.width
}

private fun View.setWidth(width: Int): Boolean {
    val params = layoutParams ?: return false
    if (params.width == width) return false
    params.width = width
    layoutParams = params
    return true
}

private fun View.horizontalMargins(): Int {
    val params = layoutParams as? ViewGroup.MarginLayoutParams ?: return 0
    return params.leftMargin + params.rightMargin
}

private fun View.horizontalPadding(): Int = paddingLeft + paddingRight
