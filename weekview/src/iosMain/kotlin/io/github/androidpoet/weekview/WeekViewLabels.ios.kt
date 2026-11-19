package io.github.androidpoet.weekview

import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarIdentifierGregorian
import platform.Foundation.NSDateComponents
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterNoStyle
import platform.Foundation.NSTimeZone
import platform.Foundation.currentLocale
import platform.Foundation.timeZoneWithAbbreviation

// Labels are formatted in UTC so a daylight-saving gap can never swallow an hour.
private fun format(
    pattern: String,
    year: Int,
    month: Int,
    day: Int,
    hour: Int,
): String {
    val utc = NSTimeZone.timeZoneWithAbbreviation("UTC")!!
    val calendar = NSCalendar(calendarIdentifier = NSCalendarIdentifierGregorian).apply { timeZone = utc }
    val components =
        NSDateComponents().apply {
            this.year = year.toLong()
            this.month = month.toLong()
            this.day = day.toLong()
            this.hour = hour.toLong()
        }
    val formatter =
        NSDateFormatter().apply {
            locale = NSLocale.currentLocale
            timeZone = utc
            dateFormat = pattern
        }
    return calendar.dateFromComponents(components)?.let(formatter::stringFromDate).orEmpty()
}

/** `NSLocale.currentLocale` already carries the user's 24-hour switch. */
public actual fun localizedHourLabel(
    hour: Int,
    use24HourClock: Boolean?,
): String {
    requireHour(hour)
    val locale = NSLocale.currentLocale
    val is24Hour = use24HourClock ?: !patternUses12HourClock(NSDateFormatter.dateFormatFromTemplate("j", 0u, locale).orEmpty())
    return if (is24Hour) {
        format("HH:mm", 2000, 1, 1, hour)
    } else {
        format(NSDateFormatter.dateFormatFromTemplate("ha", 0u, locale)?.takeIf(::patternUses12HourClock) ?: "h a", 2000, 1, 1, hour)
    }
}

public actual fun localizedDayOfMonth(date: LocalDate): String =
    NSNumberFormatter.localizedStringFromNumber(NSNumber(int = date.day), NSNumberFormatterNoStyle)

public actual fun localizedWeekdayLabel(date: LocalDate): String = format("EEE", date.year, date.month.number, date.day, 12)
