package io.github.mmaarij123.v2componentfix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompactCardLayoutTest {
    @Test
    fun shortTextUsesItsTextWidthAndContainerPadding() {
        assertEquals(186, fitCardWidth(318, 24, listOf(162)))
    }

    @Test
    fun avatarCardUsesTheImageWidthWhenItIsWiderThanTheTitleAndFooter() {
        assertEquals(234, fitCardWidth(318, 24, listOf(104, 210, 152)))
    }

    @Test
    fun longTextAndFullWidthMediaKeepTheAvailableWidth() {
        assertNull(fitCardWidth(318, 24, listOf(400)))
        assertNull(fitCardWidth(200, 24, listOf(104, 176, 152)))
    }

    @Test
    fun unknownOrEmptyContentKeepsTheNativeWidth() {
        assertNull(fitCardWidth(318, 24, listOf(104, null)))
        assertNull(fitCardWidth(318, 24, listOf(0)))
        assertNull(fitCardWidth(318, 24, emptyList()))
    }
}
