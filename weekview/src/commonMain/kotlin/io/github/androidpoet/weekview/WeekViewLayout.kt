package io.github.androidpoet.weekview

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.minus

/**
 * Where a timed event sits inside one day column.
 *
 * Vertical position is [startMinute]..[endMinute] of the day (0..1440). Horizontal position is a
 * fraction of the day width: the event starts at [left] and is [width] wide.
 *
 * @param column zero-based column the event was packed into, counted from the left.
 * @param startsBeforeDay the event began on an earlier date and continues into this one.
 * @param endsAfterDay the event continues past midnight into a later date.
 */
public data class TimedSlot(
    val event: WeekViewEvent,
    val date: LocalDate,
    val startMinute: Int,
    val endMinute: Int,
    val left: Float,
    val width: Float,
    val column: Int,
    val startsBeforeDay: Boolean,
    val endsAfterDay: Boolean,
)

/**
 * Where an all-day event sits in the all-day row of one date.
 *
 * @param lane zero-based row. A multi-day event keeps the same lane on every date it covers.
 */
public data class AllDaySlot(
    val event: WeekViewEvent,
    val date: LocalDate,
    val lane: Int,
    val startsBeforeDay: Boolean,
    val endsAfterDay: Boolean,
)

/**
 * The positions of a set of events, per date. Pure data: both the Compose and the View week view
 * draw from it, so they cannot disagree about overlaps.
 *
 * Guarantee: two timed slots of the same date whose minute ranges intersect never share a column,
 * so events are never drawn on top of each other.
 *
 * A date is laid out the first time it is asked for and then kept, so an event spanning years
 * costs nothing for the dates nobody looks at. Read one instance from one thread.
 */
public class WeekViewLayout private constructor(
    private val timedEvents: List<WeekViewEvent>,
    private val allDayEvents: List<LanedEvent>,
    private val minEventMinutes: Int,
) {
    private val timedByDate = HashMap<LocalDate, List<TimedSlot>>()
    private val allDayByDate = HashMap<LocalDate, List<AllDaySlot>>()

    /** Timed slots of [date], ordered by start minute. */
    public fun timedSlots(date: LocalDate): List<TimedSlot> {
        if (timedEvents.isEmpty()) return emptyList()
        if (timedByDate.size >= MAX_CACHED_DATES) timedByDate.clear()
        return timedByDate.getOrPut(date) { layoutDay(timedEvents.mapNotNull { timedSegment(it, date, minEventMinutes) }) }
    }

    /** All-day slots of [date], ordered by lane. */
    public fun allDaySlots(date: LocalDate): List<AllDaySlot> {
        if (allDayEvents.isEmpty()) return emptyList()
        if (allDayByDate.size >= MAX_CACHED_DATES) allDayByDate.clear()
        return allDayByDate.getOrPut(date) {
            allDayEvents
                .filter { date >= it.firstDate && date <= it.lastDate }
                .map { AllDaySlot(it.event, date, it.lane, startsBeforeDay = date > it.firstDate, endsAfterDay = date < it.lastDate) }
        }
    }

    /**
     * The all-day slots of [date] to draw when only [maxRows] rows fit. A date that needs more
     * keeps its last row free for a "+N" marker, where N is the size of [allDaySlots] minus the
     * size of this list.
     */
    public fun visibleAllDaySlots(
        date: LocalDate,
        maxRows: Int,
    ): List<AllDaySlot> {
        require(maxRows >= 1) { "maxRows must be at least 1" }
        val slots = allDaySlots(date)
        return if (allDayLaneCount(date) <= maxRows) slots else slots.filter { it.lane < maxRows - 1 }
    }

    /** How many all-day rows [date] needs. */
    public fun allDayLaneCount(date: LocalDate): Int = allDaySlots(date).maxOfOrNull { it.lane + 1 } ?: 0

    public companion object {
        public const val MINUTES_PER_DAY: Int = 1440
        private const val MAX_CACHED_DATES = 512

        /** The layout of no events. */
        public val Empty: WeekViewLayout = WeekViewLayout(emptyList(), emptyList(), minEventMinutes = 1)

        /**
         * Lays out [events]. Input order does not matter. Events whose end is before their start
         * are ignored.
         *
         * Positions have minute precision: a start is rounded down and an end up, so events that
         * overlap by seconds still get separate columns.
         *
         * @param minEventMinutes the shortest span an event is given, so a 5-minute or zero-length
         * event stays visible and tappable. Overlaps are computed on this visible span.
         */
        public fun of(
            events: List<WeekViewEvent>,
            minEventMinutes: Int = 20,
        ): WeekViewLayout {
            require(minEventMinutes in 1..MINUTES_PER_DAY) { "minEventMinutes must be in 1..$MINUTES_PER_DAY" }
            val valid = events.filter { it.end >= it.start }
            if (valid.isEmpty()) return Empty
            return WeekViewLayout(
                timedEvents = valid.filterNot { it.isAllDay },
                allDayEvents = assignAllDayLanes(valid.filter { it.isAllDay }),
                minEventMinutes = minEventMinutes,
            )
        }
    }
}

/** An all-day event with the lane it keeps on every date from [firstDate] to [lastDate]. */
private class LanedEvent(
    val event: WeekViewEvent,
    val firstDate: LocalDate,
    val lastDate: LocalDate,
    val lane: Int,
)

/** The part of a timed event that falls on one date, already widened to the minimum span. */
private class Segment(
    val event: WeekViewEvent,
    val date: LocalDate,
    val start: Int,
    val end: Int,
    val startsBeforeDay: Boolean,
    val endsAfterDay: Boolean,
) {
    fun overlaps(other: Segment): Boolean = start < other.end && other.start < end
}

private val LocalDateTime.minuteOfDay: Int get() = hour * 60 + minute

/** Like [minuteOfDay], but a minute that has begun counts in full. */
private val LocalDateTime.minuteOfDayRoundedUp: Int get() = minuteOfDay + if (second > 0 || nanosecond > 0) 1 else 0

/**
 * The last date an event touches. An end of exactly midnight is exclusive: the event stops at the
 * end of the previous date instead of leaking a zero-height sliver into the next one.
 */
private fun WeekViewEvent.lastDate(): LocalDate {
    val endsAtMidnight = end.time.toSecondOfDay() == 0 && end.time.nanosecond == 0
    return if (endsAtMidnight && end.date > start.date) end.date.minus(1, DateTimeUnit.DAY) else end.date
}

/** The segment of [event] on [date], or `null` when the event does not touch that date. */
private fun timedSegment(
    event: WeekViewEvent,
    date: LocalDate,
    minEventMinutes: Int,
): Segment? {
    val firstDate = event.start.date
    if (date < firstDate) return null
    val lastDate = event.lastDate()
    if (date > lastDate) return null
    val rawStart = if (date == firstDate) event.start.minuteOfDay else 0
    val rawEnd = if (date == event.end.date) event.end.minuteOfDayRoundedUp else WeekViewLayout.MINUTES_PER_DAY
    // Widen to the minimum span; near midnight grow upwards instead of past the day.
    val end = maxOf(rawEnd, rawStart + minEventMinutes).coerceAtMost(WeekViewLayout.MINUTES_PER_DAY)
    val start = minOf(rawStart, end - minEventMinutes)
    return Segment(event, date, start, end, startsBeforeDay = date > firstDate, endsAfterDay = date < lastDate)
}

/**
 * Splits one day into clusters of transitively overlapping segments and lays out each cluster.
 * Sorting happens here, on the per-day segments, so a multi-day event's continuation (which starts
 * at 00:00) is ordered by where it actually sits on this date.
 */
private fun layoutDay(segments: List<Segment>): List<TimedSlot> {
    val sorted = segments.sortedWith(compareBy<Segment> { it.start }.thenByDescending { it.end }.thenBy { it.event.id })
    val slots = ArrayList<TimedSlot>(sorted.size)
    var cluster = ArrayList<Segment>()
    var clusterEnd = 0
    for (segment in sorted) {
        if (cluster.isNotEmpty() && segment.start >= clusterEnd) {
            slots += layoutCluster(cluster)
            cluster = ArrayList()
            clusterEnd = 0
        }
        cluster += segment
        clusterEnd = maxOf(clusterEnd, segment.end)
    }
    if (cluster.isNotEmpty()) slots += layoutCluster(cluster)
    return slots
}

/** A segment while its cluster is being laid out. */
private class Node(
    val segment: Segment,
    val column: Int,
) {
    /** Nodes in columns further right that overlap this one in time. */
    val forward = ArrayList<Node>()

    /** Length of the longest chain of mutually constraining nodes to the right of this one. */
    var pressure = -1
    var left = Float.NaN
    var right = Float.NaN
}

/**
 * Lays out a cluster (sorted by start).
 *
 * Segments are packed into the fewest columns, then each one is sized by how crowded it is where
 * it actually sits: its width is the space left of the right edge divided by the longest chain of
 * overlapping segments it is part of. A day-long event therefore does not squeeze a quiet morning
 * down to the column count of a crowded evening.
 */
private fun layoutCluster(cluster: List<Segment>): List<TimedSlot> {
    val columns = ArrayList<MutableList<Node>>()
    val nodes =
        cluster.map { segment ->
            var column = columns.indexOfFirst { it.last().segment.end <= segment.start }
            if (column == -1) {
                columns += ArrayList<Node>()
                column = columns.lastIndex
            }
            Node(segment, column).also { columns[column] += it }
        }
    if (columns.size > MAX_SIZED_COLUMNS) {
        // Hundreds of simultaneous events: slivers either way, so skip the sizing passes, whose
        // cost and recursion depth grow with the column count.
        nodes.forEach { node ->
            node.left = node.column.toFloat() / columns.size
            node.right = (node.column + 1).toFloat() / columns.size
        }
    } else {
        for (node in nodes) {
            for (column in node.column + 1 until columns.size) {
                columns[column].filterTo(node.forward) { it.segment.overlaps(node.segment) }
            }
        }
        columns.first().forEach { computePressure(it) }
        columns.first().forEach { computeEdges(it, chainLength = 0, chainLeft = 0f) }

        // The sizing above is a heuristic; the column grid is the proof. If any two overlapping
        // segments would share space, fall back to plain equal columns for this cluster.
        if (nodes.any { it.left.isNaN() } || hasCollision(nodes)) {
            nodes.forEach { node ->
                var span = 1
                while (node.column + span < columns.size && columns[node.column + span].none { it.segment.overlaps(node.segment) }) span++
                node.left = node.column.toFloat() / columns.size
                node.right = (node.column + span).toFloat() / columns.size
            }
        }
    }
    return nodes.map { node ->
        TimedSlot(
            event = node.segment.event,
            date = node.segment.date,
            startMinute = node.segment.start,
            endMinute = node.segment.end,
            left = node.left,
            width = node.right - node.left,
            column = node.column,
            startsBeforeDay = node.segment.startsBeforeDay,
            endsAfterDay = node.segment.endsAfterDay,
        )
    }
}

private fun computePressure(node: Node): Int {
    if (node.pressure == -1) node.pressure = node.forward.maxOfOrNull { computePressure(it) + 1 } ?: 0
    return node.pressure
}

/**
 * Fixes the left and right edge of [node] and of everything to its right.
 *
 * @param chainLength how many nodes to the left share the space ending at this node's right edge.
 * @param chainLeft where that shared space begins.
 */
private fun computeEdges(
    node: Node,
    chainLength: Int,
    chainLeft: Float,
) {
    if (!node.right.isNaN()) return
    // Follow the most crowded neighbour first: it decides how much room this node can have.
    val forward = node.forward.sortedByDescending { it.pressure }
    node.right =
        if (forward.isEmpty()) {
            1f
        } else {
            computeEdges(forward.first(), chainLength + 1, chainLeft)
            forward.first().left
        }
    node.left = node.right - (node.right - chainLeft) / (chainLength + 1)
    forward.forEach { computeEdges(it, chainLength = 0, chainLeft = node.right) }
}

private fun hasCollision(nodes: List<Node>): Boolean {
    for (i in nodes.indices) {
        for (j in i + 1 until nodes.size) {
            val a = nodes[i]
            val b = nodes[j]
            if (a.segment.overlaps(b.segment) && a.left < b.right - EDGE_TOLERANCE && b.left < a.right - EDGE_TOLERANCE) return true
        }
    }
    return false
}

private const val EDGE_TOLERANCE = 1e-4f
private const val MAX_SIZED_COLUMNS = 24

/** Gives every all-day event one lane for its whole date range, reusing lanes as they free up. */
private fun assignAllDayLanes(events: List<WeekViewEvent>): List<LanedEvent> {
    val sorted = events.sortedWith(compareBy<WeekViewEvent> { it.start.date }.thenByDescending { it.lastDate() }.thenBy { it.id })
    val laneEnds = ArrayList<LocalDate>()
    return sorted
        .map { event ->
            val firstDate = event.start.date
            val lastDate = event.lastDate()
            var lane = laneEnds.indexOfFirst { it < firstDate }
            if (lane == -1) {
                laneEnds += lastDate
                lane = laneEnds.lastIndex
            } else {
                laneEnds[lane] = lastDate
            }
            LanedEvent(event, firstDate, lastDate, lane)
        }.sortedBy { it.lane }
}
