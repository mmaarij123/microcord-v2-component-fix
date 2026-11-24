package io.github.mmaarij123.v2componentfix

import android.graphics.drawable.Animatable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisibleAnimationsTest {
    @Test
    fun visibleAnimationStartsOnceAcrossDraws() {
        val playback = VisibleAnimations()
        val gif = Animation()

        repeat(3) {
            playback.markVisible(gif)
            playback.finishFrame()
        }

        assertTrue(gif.isRunning)
        assertEquals(1, gif.starts)
    }

    @Test
    fun hiddenAnimationStopsAndCanStartWhenItReturns() {
        val playback = VisibleAnimations()
        val gif = Animation()

        playback.markVisible(gif)
        playback.finishFrame()
        playback.finishFrame()
        assertFalse(gif.isRunning)
        assertEquals(1, gif.stops)

        playback.markVisible(gif)
        playback.finishFrame()
        assertEquals(2, gif.starts)
    }

    @Test
    fun finishedFiniteAnimationStaysFinishedWhileVisible() {
        val playback = VisibleAnimations()
        val gif = Animation()
        playback.markVisible(gif)
        playback.finishFrame()
        gif.running = false

        playback.markVisible(gif)
        playback.finishFrame()

        assertEquals(1, gif.starts)
        assertFalse(gif.isRunning)
    }

    @Test
    fun cleanupStopsOnlyAnimationsStartedByThisPlugin() {
        val playback = VisibleAnimations()
        val gif = Animation()
        val alreadyPlaying = Animation().apply { running = true }

        playback.markVisible(gif)
        playback.markVisible(alreadyPlaying)
        playback.finishFrame()
        playback.stop()

        assertFalse(gif.isRunning)
        assertTrue(alreadyPlaying.isRunning)
    }

    private class Animation : Animatable {
        var running = false
        var starts = 0
        var stops = 0

        override fun isRunning() = running

        override fun start() {
            starts++
            running = true
        }

        override fun stop() {
            stops++
            running = false
        }
    }
}
