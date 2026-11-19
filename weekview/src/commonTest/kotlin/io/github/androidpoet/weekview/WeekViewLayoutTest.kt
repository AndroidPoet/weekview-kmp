package io.github.androidpoet.weekview

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WeekViewLayoutTest {
    private val monday = LocalDate(2026, 3, 2)
    private val tuesday = LocalDate(2026, 3, 3)
    private val wednesday = LocalDate(2026, 3, 4)

    private fun at(
        date: LocalDate,
        hour: Int,
        minute: Int = 0,
    ) = LocalDateTime(date.year, date.month, date.day, hour, minute)

    private fun event(
        id: String,
        start: LocalDateTime,
        end: LocalDateTime,
        isAllDay: Boolean = false,
    ) = WeekViewEvent(id = id, title = id, start = start, end = end, isAllDay = isAllDay)

    private fun WeekViewLayout.slot(
        date: LocalDate,
        id: String,
    ) = timedSlots(date).single { it.event.id == id }

    @Test
    fun test_of_twoOverlappingEvents_placesThemSideBySide() {
        val events = listOf(event("a", at(monday, 9), at(monday, 11)), event("b", at(monday, 10), at(monday, 12)))

        val layout = WeekViewLayout.of(events)

        assertEquals(0f, layout.slot(monday, "a").left)
        assertEquals(0.5f, layout.slot(monday, "a").width)
        assertEquals(0.5f, layout.slot(monday, "b").left)
        assertEquals(0.5f, layout.slot(monday, "b").width)
    }

    @Test
    fun test_of_eventsThatOnlyTouch_keepFullWidth() {
        val events = listOf(event("a", at(monday, 9), at(monday, 10)), event("b", at(monday, 10), at(monday, 11)))

        val layout = WeekViewLayout.of(events)

        assertEquals(1f, layout.slot(monday, "a").width)
        assertEquals(1f, layout.slot(monday, "b").width)
    }

    @Test
    fun test_of_overlapChain_reusesFirstColumnForNonOverlappingEnds() {
        val events =
            listOf(
                event("a", at(monday, 9), at(monday, 10, 30)),
                event("b", at(monday, 10), at(monday, 12)),
                event("c", at(monday, 11), at(monday, 13)),
            )

        val layout = WeekViewLayout.of(events)

        assertEquals(0, layout.slot(monday, "a").column)
        assertEquals(1, layout.slot(monday, "b").column)
        assertEquals(0, layout.slot(monday, "c").column)
        assertTrue(layout.timedSlots(monday).all { it.width == 0.5f })
    }

    @Test
    fun test_of_freeColumnToTheRight_expandsEventIntoIt() {
        val events =
            listOf(
                event("a", at(monday, 9), at(monday, 12)),
                event("b", at(monday, 9), at(monday, 10)),
                event("c", at(monday, 9), at(monday, 10)),
                event("d", at(monday, 10, 30), at(monday, 11, 30)),
            )

        val layout = WeekViewLayout.of(events)

        val expanded = layout.slot(monday, "d")
        assertEquals(1, expanded.column)
        assertEquals(1f / 3, expanded.left, 1e-4f)
        assertEquals(2f / 3, expanded.width, 1e-4f)
        assertEquals(1f / 3, layout.slot(monday, "a").width, 1e-4f)
    }

    @Test
    fun test_of_dayLongEventBesideQuietMorning_sizesMorningByLocalCrowding() {
        val events =
            listOf(
                event("all-day-long", at(monday, 0), at(monday, 23, 59)),
                event("morning", at(monday, 9), at(monday, 10)),
                event("evening-1", at(monday, 18), at(monday, 20)),
                event("evening-2", at(monday, 18), at(monday, 20)),
                event("evening-3", at(monday, 18), at(monday, 20)),
            )

        val layout = WeekViewLayout.of(events)

        // Four events overlap in the evening, but only two in the morning.
        assertEquals(0.25f, layout.slot(monday, "all-day-long").width, 1e-4f)
        assertEquals(0.75f, layout.slot(monday, "morning").width, 1e-4f)
        assertEquals(0.25f, layout.slot(monday, "evening-3").width, 1e-4f)
    }

    @Test
    fun test_of_shuffledInput_producesSameLayout() {
        val events =
            listOf(
                event("a", at(monday, 9), at(monday, 12)),
                event("b", at(monday, 9, 30), at(monday, 10)),
                event("c", at(monday, 11), at(monday, 14)),
                event("d", at(monday, 13), at(monday, 15)),
            )

        val ordered = WeekViewLayout.of(events).timedSlots(monday)
        val shuffled = WeekViewLayout.of(events.reversed()).timedSlots(monday)

        assertEquals(ordered, shuffled)
    }

    @Test
    fun test_of_multiDayEvent_splitsPerDateAndCollidesOnContinuationDay() {
        val events =
            listOf(
                event("morning", at(tuesday, 1), at(tuesday, 3)),
                event("trip", at(monday, 22), at(wednesday, 2)),
            )

        val layout = WeekViewLayout.of(events)

        assertEquals(1320 to 1440, layout.slot(monday, "trip").let { it.startMinute to it.endMinute })
        assertEquals(0 to 1440, layout.slot(tuesday, "trip").let { it.startMinute to it.endMinute })
        assertEquals(0 to 120, layout.slot(wednesday, "trip").let { it.startMinute to it.endMinute })
        assertTrue(layout.slot(tuesday, "trip").startsBeforeDay && layout.slot(tuesday, "trip").endsAfterDay)
        assertEquals(0, layout.slot(tuesday, "trip").column)
        assertEquals(1, layout.slot(tuesday, "morning").column)
    }

    @Test
    fun test_of_eventEndingAtMidnight_staysOnStartDateWithFullHeight() {
        val events = listOf(event("late", at(monday, 22), at(tuesday, 0)))

        val layout = WeekViewLayout.of(events)

        assertEquals(1320 to 1440, layout.slot(monday, "late").let { it.startMinute to it.endMinute })
        assertFalse(layout.slot(monday, "late").endsAfterDay)
        assertTrue(layout.timedSlots(tuesday).isEmpty())
    }

    @Test
    fun test_of_zeroLengthEvent_getsMinimumSpan() {
        val events = listOf(event("ping", at(monday, 9), at(monday, 9)), event("last", at(monday, 23, 55), at(monday, 23, 58)))

        val layout = WeekViewLayout.of(events, minEventMinutes = 15)

        assertEquals(540 to 555, layout.slot(monday, "ping").let { it.startMinute to it.endMinute })
        assertEquals(1425 to 1440, layout.slot(monday, "last").let { it.startMinute to it.endMinute })
    }

    @Test
    fun test_of_endBeforeStart_ignoresEvent() {
        val events = listOf(event("broken", at(monday, 12), at(monday, 9)))

        val layout = WeekViewLayout.of(events)

        assertTrue(layout.timedSlots(monday).isEmpty())
    }

    @Test
    fun test_of_shortEventsWhoseVisibleSpansCollide_getSeparateColumns() {
        val events = listOf(event("a", at(monday, 9), at(monday, 9, 5)), event("b", at(monday, 9, 10), at(monday, 9, 20)))

        val layout = WeekViewLayout.of(events, minEventMinutes = 15)

        assertEquals(0, layout.slot(monday, "a").column)
        assertEquals(1, layout.slot(monday, "b").column)
    }

    @Test
    fun test_of_multiDayAllDayEvent_keepsOneLaneAcrossDates() {
        val events =
            listOf(
                event("one", at(monday, 0), at(monday, 0), isAllDay = true),
                event("conference", at(monday, 0), at(wednesday, 0), isAllDay = true),
                event("timed", at(monday, 9), at(monday, 10)),
            )

        val layout = WeekViewLayout.of(events)

        assertEquals(listOf(0, 0), listOf(monday, tuesday).map { date -> layout.allDaySlots(date).single { it.event.id == "conference" }.lane })
        assertEquals(1, layout.allDaySlots(monday).single { it.event.id == "one" }.lane)
        assertEquals(2, layout.allDayLaneCount(monday))
        assertEquals(1, layout.allDayLaneCount(tuesday))
        assertTrue(layout.allDaySlots(wednesday).isEmpty())
        assertEquals(listOf("timed"), layout.timedSlots(monday).map { it.event.id })
    }

    @Test
    fun test_visibleAllDaySlots_moreLanesThanRows_keepsLastRowFree() {
        val events = List(5) { event("e$it", at(monday, 0), at(monday, 0), isAllDay = true) } +
            event("tuesday-only", at(tuesday, 0), at(tuesday, 0), isAllDay = true)

        val layout = WeekViewLayout.of(events)

        assertEquals(listOf(0, 1), layout.visibleAllDaySlots(monday, maxRows = 3).map { it.lane })
        assertEquals(5, layout.visibleAllDaySlots(monday, maxRows = 5).size)
        assertEquals(1, layout.visibleAllDaySlots(tuesday, maxRows = 3).size)
    }

    @Test
    fun test_of_eventsOverlappingBySeconds_getSeparateColumns() {
        val events =
            listOf(
                event("a", at(monday, 9), LocalDateTime(2026, 3, 2, 10, 0, 59)),
                event("b", LocalDateTime(2026, 3, 2, 10, 0, 30), at(monday, 11)),
            )

        val layout = WeekViewLayout.of(events)

        assertEquals(601, layout.slot(monday, "a").endMinute)
        assertEquals(0.5f, layout.slot(monday, "a").width)
        assertEquals(1, layout.slot(monday, "b").column)
    }

    @Test
    fun test_of_eventsSpanningMillennia_laysOutOnlyTheDatesAsked() {
        val events =
            listOf(
                event("timed", LocalDateTime(1, 1, 1, 0, 0), LocalDateTime(9999, 12, 31, 23, 0)),
                event("all-day", LocalDateTime(1, 1, 1, 0, 0), LocalDateTime(9999, 12, 31, 0, 0), isAllDay = true),
            )

        val layout = WeekViewLayout.of(events)

        assertEquals(0 to 1440, layout.slot(monday, "timed").let { it.startMinute to it.endMinute })
        assertEquals(1, layout.allDayLaneCount(monday))
        assertEquals(1380, layout.slot(LocalDate(9999, 12, 31), "timed").endMinute)
        assertTrue(layout.allDaySlots(LocalDate(9999, 12, 30)).single().startsBeforeDay)
    }

    @Test
    fun test_of_thousandsOfSimultaneousEvents_givesEachItsOwnColumn() {
        val events = List(8000) { event("e$it", at(monday, 9), at(monday, 10)) }

        val slots = WeekViewLayout.of(events).timedSlots(monday)

        assertEquals(8000, slots.map { it.column }.distinct().size)
        assertTrue(slots.all { it.width > 0f && it.left >= 0f && it.left + it.width <= 1f + 1e-4f })
    }

    @Test
    fun test_of_randomEvents_neverOverlapInsideADay() {
        val random = Random(seed = 42)

        repeat(5000) { round ->
            // Alternate sparse and crowded days, short and day-long events.
            val count = random.nextInt(1, if (round % 2 == 0) 8 else 30)
            val events =
                List(count) { index ->
                    val start = random.nextInt(0, 1380)
                    val end = start + random.nextInt(0, if (index % 4 == 0) 1500 else 240)
                    val endDate = if (end >= 1440) tuesday else monday
                    event("e$index", at(monday, start / 60, start % 60), at(endDate, end % 1440 / 60, end % 60))
                }

            val layout = WeekViewLayout.of(events)

            for (date in listOf(monday, tuesday)) {
                val slots = layout.timedSlots(date)
                slots.forEach {
                    assertTrue(it.left >= -1e-4f && it.left + it.width <= 1 + 1e-4f && it.width > 0f, "outside the day: $it")
                    assertTrue(it.startMinute < it.endMinute)
                }
                for (first in slots) {
                    for (second in slots) {
                        if (first === second) continue
                        val sameTime = first.startMinute < second.endMinute && second.startMinute < first.endMinute
                        val sameSpace = first.left < second.left + second.width - 1e-3f && second.left < first.left + first.width - 1e-3f
                        assertFalse(sameTime && sameSpace, "overlap: $first vs $second")
                    }
                }
            }
        }
    }
}
