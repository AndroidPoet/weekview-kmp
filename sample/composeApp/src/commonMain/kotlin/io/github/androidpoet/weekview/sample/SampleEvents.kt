package io.github.androidpoet.weekview.sample

import io.github.androidpoet.weekview.WeekViewEvent
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus

private const val BLUE = 0xFF2563EB.toInt()
private const val TEAL = 0xFF0D9488.toInt()
private const val VIOLET = 0xFF7C3AED.toInt()
private const val AMBER = 0xFFF59E0B.toInt()
private const val SLATE = 0xFF475569.toInt()
private const val GREEN = 0xFF16A34A.toInt()
private const val SKY = 0xFF0284C7.toInt()

/** A schedule built around [today] that exercises every overlap case the layout engine handles. */
fun sampleEvents(today: LocalDate): List<WeekViewEvent> {
    fun at(
        dayOffset: Int,
        hour: Int,
        minute: Int = 0,
    ) = LocalDateTime(today.plus(dayOffset, DateTimeUnit.DAY), LocalTime(hour, minute))

    return listOf(
        // Three-way overlap; "Deep work" then grows into the columns the short ones free up.
        WeekViewEvent("review", "Design review", at(0, 9), at(0, 12), BLUE, "Room 4"),
        WeekViewEvent("standup", "Standup", at(0, 9), at(0, 9, 45), TEAL),
        WeekViewEvent("one-on-one", "1:1", at(0, 9), at(0, 10), VIOLET),
        WeekViewEvent("deep-work", "Deep work", at(0, 10, 30), at(0, 11, 30), SLATE),
        WeekViewEvent("lunch", "Lunch", at(0, 12, 30), at(0, 13, 30), GREEN, "Canteen"),
        // A chain: first and last do not overlap each other, so they share a column.
        WeekViewEvent("planning", "Planning", at(0, 14), at(0, 15, 30), SKY),
        WeekViewEvent("interview", "Interview", at(0, 15), at(0, 16, 30), AMBER),
        WeekViewEvent("retro", "Retro", at(0, 16), at(0, 17), VIOLET),
        // Short events still get a visible, tappable block.
        WeekViewEvent("ping", "Ping", at(0, 17, 30), at(0, 17, 35), TEAL),
        WeekViewEvent("call", "Call", at(0, 17, 40), at(0, 17, 50), BLUE),
        // Spans three dates; its continuation must share tomorrow with "Maintenance".
        WeekViewEvent("deploy", "Night deploy", at(0, 22), at(2, 2), SLATE, "Ops"),
        WeekViewEvent("maintenance", "Maintenance", at(1, 1), at(1, 3), AMBER),
        WeekViewEvent("workshop", "Workshop", at(1, 9), at(1, 11), GREEN),
        // Ends exactly at midnight.
        WeekViewEvent("late-show", "Late show", at(1, 22), at(2, 0), SKY),
        // Text handling: a long title that must wrap at spaces, one unbreakable word, no title at all.
        WeekViewEvent("roadmap", "Quarterly planning and roadmap review with leadership", at(2, 9), at(2, 11, 30), BLUE, "Boardroom, 4th floor"),
        WeekViewEvent("i18n", "Internationalization", at(2, 12), at(2, 13), TEAL, "Remote"),
        WeekViewEvent("untitled", "", at(2, 13, 30), at(2, 14, 30), SLATE),
        WeekViewEvent("sync-a", "Sync", at(2, 15), at(2, 15, 30), VIOLET),
        WeekViewEvent("sync-b", "Handover notes", at(2, 15), at(2, 15, 30), AMBER),
        WeekViewEvent("sync-c", "Deployment checklist", at(2, 15), at(2, 18), GREEN, "Ops room"),
        WeekViewEvent("conference", "Conference", at(0, 0), at(2, 0), VIOLET, isAllDay = true),
        WeekViewEvent("birthday", "Birthday", at(1, 0), at(1, 0), TEAL, isAllDay = true),
    )
}

private val addedColors = listOf(AMBER, VIOLET, SKY, TEAL, GREEN, BLUE)
private const val FIRST_ADDED_START_HOUR = 6
private const val ADDED_STAGGER_HOURS = 5
private const val ADDED_DURATION_HOURS = 30

/**
 * The [index]-th event the "Add" button creates: 30 hours long, each starting 5 hours after the
 * previous one, so every press adds a tall event that crosses midnight and overlaps the earlier
 * ones on more than one day.
 */
fun addedEvent(
    today: LocalDate,
    index: Int,
): WeekViewEvent {
    fun atHour(hoursFromToday: Int) = LocalDateTime(today.plus(hoursFromToday / 24, DateTimeUnit.DAY), LocalTime(hoursFromToday % 24, 0))

    val startHour = FIRST_ADDED_START_HOUR + index * ADDED_STAGGER_HOURS
    return WeekViewEvent(
        id = "added-$index",
        title = "Long ${index + 1}",
        start = atHour(startHour),
        end = atHour(startHour + ADDED_DURATION_HOURS),
        color = addedColors[index % addedColors.size],
    )
}
