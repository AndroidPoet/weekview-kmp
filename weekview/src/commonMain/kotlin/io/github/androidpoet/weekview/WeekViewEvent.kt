package io.github.androidpoet.weekview

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * One calendar entry, in wall-clock time.
 *
 * Times are [LocalDateTime] on purpose: a week grid is a wall-clock picture, every day is 24 rows
 * tall, and converting from instants is the caller's decision (which zone, which calendar).
 *
 * @param id stable identity, used only for deterministic ordering of otherwise equal events.
 * @param end exclusive. An event ending at 00:00 belongs entirely to the previous day.
 * @param color ARGB, e.g. `0xFF2563EB.toInt()`.
 * @param isAllDay drawn in the all-day row for every date from `start.date` to `end.date`.
 */
public data class WeekViewEvent(
    val id: String,
    val title: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val color: Int = DEFAULT_COLOR,
    val location: String? = null,
    val isAllDay: Boolean = false,
) {
    public companion object {
        public const val DEFAULT_COLOR: Int = 0xFF2563EB.toInt()
    }
}

/** The current wall-clock time in the system time zone. */
@OptIn(ExperimentalTime::class)
public fun currentLocalDateTime(): LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
