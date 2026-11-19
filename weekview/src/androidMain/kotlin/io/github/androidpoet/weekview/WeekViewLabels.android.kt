package io.github.androidpoet.weekview

import android.text.format.DateFormat
import kotlinx.datetime.LocalDate
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

// Labels are formatted in UTC so a daylight-saving gap can never swallow an hour.
private val utc = TimeZone.getTimeZone("UTC")

/**
 * On Android the locale alone does not know the user's 24-hour switch: pass
 * `DateFormat.is24HourFormat(context)` as [use24HourClock] to honour it.
 */
public actual fun localizedHourLabel(
    hour: Int,
    use24HourClock: Boolean?,
): String {
    requireHour(hour)
    val locale = Locale.getDefault()
    val is24Hour = use24HourClock ?: !patternUses12HourClock(DateFormat.getBestDateTimePattern(locale, "j"))
    val pattern = DateFormat.getBestDateTimePattern(locale, if (is24Hour) "Hm" else "ha")
    val time = Calendar.getInstance(utc).apply { clear(); set(2000, Calendar.JANUARY, 1, hour, 0) }.time
    return SimpleDateFormat(pattern, locale).apply { timeZone = utc }.format(time)
}

/** The header texts of one locale. Headers ask for them on every frame, so they are made once. */
private class HeaderTexts(
    val locale: Locale,
) {
    val weekdays: List<String> =
        SimpleDateFormat("EEE", locale).apply { timeZone = utc }.let { format ->
            // 2001-01-01 was a Monday.
            List(7) { format.format(Calendar.getInstance(utc).apply { clear(); set(2001, Calendar.JANUARY, 1 + it) }.time) }
        }
    val daysOfMonth: List<String> = List(31) { String.format(locale, "%d", it + 1) }
}

private var headerTexts: HeaderTexts? = null

private fun headerTexts(): HeaderTexts {
    val locale = Locale.getDefault()
    return headerTexts?.takeIf { it.locale == locale } ?: HeaderTexts(locale).also { headerTexts = it }
}

public actual fun localizedWeekdayLabel(date: LocalDate): String = headerTexts().weekdays[date.dayOfWeek.ordinal]

public actual fun localizedDayOfMonth(date: LocalDate): String = headerTexts().daysOfMonth[date.day - 1]
