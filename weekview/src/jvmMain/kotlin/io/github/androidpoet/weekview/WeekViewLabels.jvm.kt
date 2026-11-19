package io.github.androidpoet.weekview

import kotlinx.datetime.LocalDate
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

public actual fun localizedHourLabel(
    hour: Int,
    use24HourClock: Boolean?,
): String {
    requireHour(hour)
    val locale = Locale.getDefault(Locale.Category.FORMAT)
    val is24Hour =
        use24HourClock
            ?: !patternUses12HourClock(
                DateTimeFormatterBuilder.getLocalizedDateTimePattern(null, FormatStyle.SHORT, IsoChronology.INSTANCE, locale),
            )
    return DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h a", locale).format(LocalTime.of(hour, 0))
}

public actual fun localizedDayOfMonth(date: LocalDate): String = String.format(Locale.getDefault(Locale.Category.FORMAT), "%d", date.day)

public actual fun localizedWeekdayLabel(date: LocalDate): String =
    DayOfWeek.of(date.dayOfWeek.ordinal + 1).getDisplayName(TextStyle.SHORT, Locale.getDefault(Locale.Category.FORMAT))
