package io.github.androidpoet.weekview

import kotlinx.datetime.LocalDate

/**
 * An hour label for the time column in the device's language and clock convention: `9 AM` where a
 * 12-hour clock is used, `09:00` where a 24-hour clock is.
 *
 * Formatting is not free; a week view calls this once per hour and keeps the result.
 *
 * @param hour 0..23.
 * @param use24HourClock forces the convention, for a user setting that overrides the locale;
 * `null` follows the locale.
 */
public expect fun localizedHourLabel(
    hour: Int,
    use24HourClock: Boolean? = null,
): String

/** The short weekday name of [date] in the device's language, such as `Mon`. */
public expect fun localizedWeekdayLabel(date: LocalDate): String

/** The day of the month of [date] in the device's digits, such as `19` or `١٩`. */
public expect fun localizedDayOfMonth(date: LocalDate): String

/**
 * Whether a date-time [pattern] (Unicode / `SimpleDateFormat` letters) shows a 12-hour clock.
 * Quoted text is skipped, so the literal in `HH 'h' mm` is not mistaken for an hour field.
 */
internal fun patternUses12HourClock(pattern: String): Boolean {
    var quoted = false
    for (char in pattern) {
        if (char == '\'') quoted = !quoted else if (!quoted && (char == 'h' || char == 'K')) return true
    }
    return false
}

internal fun requireHour(hour: Int) {
    require(hour in 0..23) { "hour must be in 0..23, was $hour" }
}
