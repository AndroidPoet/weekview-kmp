package io.github.androidpoet.weekview

import kotlinx.datetime.LocalDate
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LocalizedLabelsJvmTest {
    private val original = Locale.getDefault()

    @AfterTest
    fun restoreLocale() = Locale.setDefault(original)

    @Test
    fun test_localizedHourLabel_unitedStates_uses12HourClock() {
        Locale.setDefault(Locale.US)

        val labels = listOf(0, 9, 12, 23).map { localizedHourLabel(it) }

        assertEquals(listOf("12 AM", "9 AM", "12 PM", "11 PM"), labels)
    }

    @Test
    fun test_localizedHourLabel_germany_uses24HourClock() {
        Locale.setDefault(Locale.GERMANY)

        val labels = listOf(0, 9, 23).map { localizedHourLabel(it) }

        assertEquals(listOf("00:00", "09:00", "23:00"), labels)
    }

    @Test
    fun test_localizedHourLabel_forcedConvention_overridesLocale() {
        Locale.setDefault(Locale.US)

        val forced24 = localizedHourLabel(15, use24HourClock = true)
        Locale.setDefault(Locale.GERMANY)
        val forced12 = localizedHourLabel(15, use24HourClock = false)

        assertEquals("15:00", forced24)
        assertEquals("3 PM", forced12)
    }

    @Test
    fun test_localizedHourLabel_everyLocale_formatsAll24Hours() {
        val locales = Locale.getAvailableLocales()

        val blank =
            locales.filter { locale ->
                Locale.setDefault(locale)
                (0..23).any { localizedHourLabel(it).isBlank() }
            }

        assertEquals(emptyList(), blank)
    }

    @Test
    fun test_localizedHourLabel_hourOutOfRange_throws() {
        val hour = 24

        assertFailsWith<IllegalArgumentException> { localizedHourLabel(hour) }
    }

    @Test
    fun test_localizedDayOfMonth_arabicEgypt_usesArabicIndicDigits() {
        val date = LocalDate(2026, 3, 19)

        Locale.setDefault(Locale.US)
        val latin = localizedDayOfMonth(date)
        Locale.setDefault(Locale.forLanguageTag("ar-EG"))
        val arabic = localizedDayOfMonth(date)

        assertEquals("19", latin)
        assertEquals("\u0661\u0669", arabic)
    }

    @Test
    fun test_localizedWeekdayLabel_followsLocale() {
        val monday = LocalDate(2026, 3, 2)

        Locale.setDefault(Locale.US)
        val english = localizedWeekdayLabel(monday)
        Locale.setDefault(Locale.FRANCE)
        val french = localizedWeekdayLabel(monday)

        assertEquals("Mon", english)
        assertEquals("lun.", french)
    }
}
