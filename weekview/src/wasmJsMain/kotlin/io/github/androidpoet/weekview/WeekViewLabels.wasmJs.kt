package io.github.androidpoet.weekview

import kotlinx.datetime.LocalDate

private fun localeUses12HourClock(): Boolean =
    js("new Intl.DateTimeFormat(undefined, { hour: 'numeric' }).resolvedOptions().hour12 === true")

// Labels are formatted in UTC so a daylight-saving gap can never swallow an hour.
private fun formatHour(
    hour: Int,
    hour12: Boolean,
): String =
    js(
        """new Intl.DateTimeFormat(undefined, hour12
            ? { hour: 'numeric', hour12: true, timeZone: 'UTC' }
            : { hour: '2-digit', minute: '2-digit', hourCycle: 'h23', timeZone: 'UTC' }
        ).format(new Date(Date.UTC(2000, 0, 1, hour)))""",
    )

// 2001-01-01 was a Monday. A fixed week avoids Date.UTC reading the years 0..99 as 1900..1999.
private fun formatWeekday(isoDayNumber: Int): String =
    js("new Intl.DateTimeFormat(undefined, { weekday: 'short', timeZone: 'UTC' }).format(new Date(Date.UTC(2001, 0, isoDayNumber)))")

public actual fun localizedHourLabel(
    hour: Int,
    use24HourClock: Boolean?,
): String {
    requireHour(hour)
    return formatHour(hour, hour12 = !(use24HourClock ?: !localeUses12HourClock()))
}

private fun formatNumber(value: Int): String = js("new Intl.NumberFormat().format(value)")

public actual fun localizedDayOfMonth(date: LocalDate): String = formatNumber(date.day)

public actual fun localizedWeekdayLabel(date: LocalDate): String = formatWeekday(date.dayOfWeek.ordinal + 1)
