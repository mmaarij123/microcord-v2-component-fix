package io.github.mmaarij123.v2componentfix

import android.graphics.Rect
import android.graphics.drawable.Animatable
import android.view.View
import android.view.ViewGroup
import com.discord.R
import com.discord.widgets.botuikit.views.v2.ContainerComponentView
import com.discord.widgets.botuikit.views.v2.ThumbnailComponentView
import com.facebook.fresco.vito.view.VitoView

internal class ComponentGifPlayback {
    private val visibleBounds = Rect()
    private val animations = VisibleAnimations()

    fun scan(view: View) {
        if (view.visibility != View.VISIBLE) return
        if (view is ContainerComponentView && view.hasVisibleSpoiler(R.id.container_spoiler)) return
        if (view is ThumbnailComponentView) {
            if (view.hasVisibleSpoiler(R.id.media_spoiler)) return
            val image = view.findViewById<View>(R.id.image) ?: return
            if (!image.isShown || !image.getGlobalVisibleRect(visibleBounds)) return
            // The native component calls setImageURI without requesting Vito autoplay.
            val animation = VitoView.getDrawable(image)?.actualImageDrawable as? Animatable ?: return
            animations.markVisible(animation)
            return
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) scan(view.getChildAt(index))
        }
    }

    fun finishFrame() = animations.finishFrame()
    fun stop() = animations.stop()

    private fun ViewGroup.hasVisibleSpoiler(id: Int): Boolean {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.id == id) return child.visibility == View.VISIBLE
        }
        return false
    }
}

internal class VisibleAnimations {
    private val started = mutableSetOf<Animatable>()
    private val visible = mutableSetOf<Animatable>()

    fun markVisible(animation: Animatable) {
        visible += animation
        if (animation !in started && !animation.isRunning) {
            animation.start()
            started += animation
        }
    }

    fun finishFrame() {
        val iterator = started.iterator()
        while (iterator.hasNext()) {
            val animation = iterator.next()
            if (animation !in visible) {
                animation.stop()
                iterator.remove()
            }
        }
        visible.clear()
    }

    fun stop() {
        started.forEach(Animatable::stop)
        started.clear()
        visible.clear()
    }
}
