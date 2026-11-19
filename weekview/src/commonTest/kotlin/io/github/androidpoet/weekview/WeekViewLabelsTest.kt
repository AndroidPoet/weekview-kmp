package io.github.androidpoet.weekview

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WeekViewLabelsTest {
    @Test
    fun test_patternUses12HourClock_hourFieldH_returnsTrue() {
        val patterns = listOf("h:mm a", "a h時", "K:mm a")

        val results = patterns.map(::patternUses12HourClock)

        assertTrue(results.all { it })
    }

    @Test
    fun test_patternUses12HourClock_24HourFields_returnsFalse() {
        val patterns = listOf("HH:mm", "H:mm", "k:mm")

        val results = patterns.map(::patternUses12HourClock)

        assertTrue(results.none { it })
    }

    @Test
    fun test_patternUses12HourClock_quotedLetterH_isNotAnHourField() {
        val canadianFrench = "HH 'h' mm"

        val result = patternUses12HourClock(canadianFrench)

        assertFalse(result)
    }
}
