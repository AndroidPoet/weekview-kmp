package io.github.androidpoet.weekview.compose

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.androidpoet.weekview.TimedSlot
import io.github.androidpoet.weekview.WeekViewEvent
import io.github.androidpoet.weekview.WeekViewLayout
import io.github.androidpoet.weekview.currentLocalDateTime
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.math.roundToInt

/**
 * A day / multi-day / week calendar grid.
 *
 * Days scroll horizontally without end and snap to a column; hours scroll vertically; a two-finger
 * pinch changes the hour height. Overlapping events are placed side by side by [WeekViewLayout],
 * never on top of each other.
 *
 * @param visibleDays how many day columns fit the width: 1 for a day view, 7 for a week.
 * @param now where the "now" line is drawn and which date is highlighted; `null` hides both.
 * @param timeLabel text of the hour labels in the left column.
 * @param dayHeader content of a column header.
 * @param eventContent content of one event block; it is given the exact size of the event's slot.
 */
@Composable
public fun WeekView(
    events: List<WeekViewEvent>,
    modifier: Modifier = Modifier,
    state: WeekViewState = rememberWeekViewState(),
    visibleDays: Int = 3,
    style: WeekViewStyle = WeekViewDefaults.style(),
    now: LocalDateTime? = rememberCurrentDateTime(),
    onEventClick: (WeekViewEvent) -> Unit = {},
    onEventLongClick: (WeekViewEvent) -> Unit = {},
    onEmptyClick: (LocalDateTime) -> Unit = {},
    onEmptyLongClick: (LocalDateTime) -> Unit = {},
    timeLabel: (hour: Int) -> String = WeekViewDefaults::timeLabel,
    dayHeader: @Composable (date: LocalDate, isToday: Boolean) -> Unit = { date, isToday ->
        WeekViewDefaults.DayHeader(date, isToday, style)
    },
    eventContent: @Composable (event: WeekViewEvent) -> Unit = { WeekViewDefaults.EventContent(it, style) },
) {
    require(visibleDays >= 1) { "visibleDays must be at least 1" }
    val layout = remember(events) { WeekViewLayout.of(events) }
    val density = LocalDensity.current
    // An hour is never shorter than 1/24 of the viewport, so the grid always fills it.
    val pxPerMinute =
        remember(state, density) {
            { maxOf(with(density) { state.hourHeight.toPx() }, state.bodyHeightPx / HOURS_PER_DAY.toFloat()) / 60f }
        }

    val allDayLanes by remember(layout, state) {
        derivedStateOf {
            state.listState.layoutInfo.visibleItemsInfo
                .maxOfOrNull { layout.allDayLaneCount(state.dateAt(it.index)) } ?: 0
        }
    }
    val allDayHeight by animateDpAsState(style.allDayRowHeight * allDayLanes)
    val headerHeightPx by rememberUpdatedState(with(density) { (style.headerHeight + allDayHeight).toPx() })
    val verticalScroll = rememberScrollableState { delta -> state.dispatchScroll(delta, pxPerMinute()) }

    Row(modifier.background(style.backgroundColor).clipToBounds()) {
        Column(Modifier.width(style.timeColumnWidth).fillMaxHeight()) {
            Spacer(Modifier.fillMaxWidth().height(style.headerHeight + allDayHeight).background(style.headerColor))
            HourLabels(
                state = state,
                style = style,
                pxPerMinute = pxPerMinute,
                timeLabel = timeLabel,
                modifier = Modifier.weight(1f).fillMaxWidth().onSizeChanged { state.bodyHeightPx = it.height },
            )
        }
        LazyRow(
            state = state.listState,
            flingBehavior = rememberSnapFlingBehavior(state.listState, SnapPosition.Start),
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .scrollable(verticalScroll, Orientation.Vertical)
                    .pinchToZoom { zoom, centroidY -> state.zoomBy(zoom, centroidY - headerHeightPx, pxPerMinute()) },
        ) {
            items(count = Int.MAX_VALUE, key = { it }) { index ->
                val date = remember(index) { state.dateAt(index) }
                val isToday = date == now?.date
                Column(Modifier.fillParentMaxWidth(1f / visibleDays).fillParentMaxHeight()) {
                    Box(Modifier.fillMaxWidth().height(style.headerHeight).background(style.headerColor), Alignment.Center) {
                        dayHeader(date, isToday)
                    }
                    Box(Modifier.fillMaxWidth().height(allDayHeight).background(style.headerColor).clipToBounds()) {
                        layout.allDaySlots(date).forEach { slot ->
                            EventBox(
                                event = slot.event,
                                shape = eventShape(style.eventCornerRadius, slot.startsBeforeDay, slot.endsAfterDay, horizontal = true),
                                onClick = onEventClick,
                                onLongClick = onEventLongClick,
                                content = eventContent,
                                modifier =
                                    Modifier
                                        .offset(y = style.allDayRowHeight * slot.lane)
                                        .fillMaxWidth()
                                        .height(style.allDayRowHeight)
                                        .padding(horizontal = style.eventGap / 2, vertical = style.eventGap / 2),
                            )
                        }
                    }
                    DayBody(
                        date = date,
                        slots = layout.timedSlots(date),
                        nowMinute = if (isToday) now?.let { it.hour * 60 + it.minute } else null,
                        state = state,
                        style = style,
                        pxPerMinute = pxPerMinute,
                        onEventClick = onEventClick,
                        onEventLongClick = onEventLongClick,
                        onEmptyClick = onEmptyClick,
                        onEmptyLongClick = onEmptyLongClick,
                        eventContent = eventContent,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** The current wall-clock time, refreshed twice a minute so the "now" line keeps moving. */
@Composable
public fun rememberCurrentDateTime(): LocalDateTime {
    var now by remember { mutableStateOf(currentLocalDateTime()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(NOW_REFRESH_MILLIS)
            now = currentLocalDateTime()
        }
    }
    return now
}

@Composable
private fun HourLabels(
    state: WeekViewState,
    style: WeekViewStyle,
    pxPerMinute: () -> Float,
    timeLabel: (Int) -> String,
    modifier: Modifier = Modifier,
) {
    Layout(
        content = { for (hour in 1 until HOURS_PER_DAY) BasicText(timeLabel(hour), style = style.timeTextStyle) },
        modifier = modifier.clipToBounds(),
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val endPadding = LABEL_END_PADDING.roundToPx()
        layout(constraints.maxWidth, constraints.maxHeight) {
            val scale = pxPerMinute()
            val scroll = state.scrollPx(scale)
            placeables.forEachIndexed { index, placeable ->
                val lineY = (index + 1) * 60 * scale - scroll
                placeable.place(constraints.maxWidth - placeable.width - endPadding, (lineY - placeable.height / 2f).roundToInt())
            }
        }
    }
}

@Composable
private fun DayBody(
    date: LocalDate,
    slots: List<TimedSlot>,
    nowMinute: Int?,
    state: WeekViewState,
    style: WeekViewStyle,
    pxPerMinute: () -> Float,
    onEventClick: (WeekViewEvent) -> Unit,
    onEventLongClick: (WeekViewEvent) -> Unit,
    onEmptyClick: (LocalDateTime) -> Unit,
    onEmptyLongClick: (LocalDateTime) -> Unit,
    eventContent: @Composable (WeekViewEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnEmptyClick by rememberUpdatedState(onEmptyClick)
    val currentOnEmptyLongClick by rememberUpdatedState(onEmptyLongClick)

    fun dateTimeAt(y: Float): LocalDateTime {
        val scale = pxPerMinute()
        val minute = ((y + state.scrollPx(scale)) / scale).toInt().coerceIn(0, WeekViewLayout.MINUTES_PER_DAY - 1)
        return LocalDateTime(date, LocalTime(minute / 60, minute % 60))
    }

    Layout(
        content = {
            slots.forEach { slot ->
                EventBox(
                    event = slot.event,
                    shape = eventShape(style.eventCornerRadius, slot.startsBeforeDay, slot.endsAfterDay, horizontal = false),
                    onClick = onEventClick,
                    onLongClick = onEventLongClick,
                    content = eventContent,
                )
            }
        },
        modifier =
            modifier
                .clipToBounds()
                .drawWithContent {
                    val scale = pxPerMinute()
                    val scroll = state.scrollPx(scale)
                    val hairline = 1.dp.toPx()
                    if (nowMinute != null) drawRect(style.todayColor)
                    for (hour in 0..HOURS_PER_DAY) {
                        val y = hour * 60 * scale - scroll
                        if (y in 0f..size.height) drawLine(style.hourLineColor, Offset(0f, y), Offset(size.width, y), hairline)
                    }
                    drawLine(style.dividerColor, Offset(0f, 0f), Offset(0f, size.height), hairline)
                    drawContent()
                    if (nowMinute != null) {
                        val y = nowMinute * scale - scroll
                        drawLine(style.nowColor, Offset(0f, y), Offset(size.width, y), 2.dp.toPx())
                        drawCircle(style.nowColor, 4.dp.toPx(), Offset(0f, y))
                    }
                }.pointerInput(date) {
                    detectTapGestures(
                        onTap = { currentOnEmptyClick(dateTimeAt(it.y)) },
                        onLongPress = { currentOnEmptyLongClick(dateTimeAt(it.y)) },
                    )
                },
    ) { measurables, constraints ->
        val scale = pxPerMinute()
        val scroll = state.scrollPx(scale)
        val gap = style.eventGap.roundToPx()
        val dayWidth = constraints.maxWidth
        val tops = IntArray(slots.size)
        val placeables =
            measurables.mapIndexed { index, measurable ->
                val slot = slots[index]
                val left = (slot.left * dayWidth).roundToInt()
                val right = ((slot.left + slot.width) * dayWidth).roundToInt()
                // Height comes from the unscrolled span, so scrolling keeps the same constraints
                // and Compose skips remeasuring the block.
                val height = (((slot.endMinute - slot.startMinute) * scale).roundToInt() - gap).coerceAtLeast(0)
                val top = (slot.startMinute * scale - scroll).roundToInt()
                // A block scrolled partly above the grid is pinned to the top edge and shortened,
                // so the title of a long event stays readable.
                val isCutByTop = top < 0 && top + height > 0
                tops[index] = if (isCutByTop) 0 else top
                measurable.measure(Constraints.fixed((right - left - gap).coerceAtLeast(0), if (isCutByTop) top + height else height))
            }
        layout(dayWidth, constraints.maxHeight) {
            placeables.forEachIndexed { index, placeable ->
                placeable.place((slots[index].left * dayWidth).roundToInt() + gap / 2, tops[index])
            }
        }
    }
}

@Composable
private fun EventBox(
    event: WeekViewEvent,
    shape: Shape,
    onClick: (WeekViewEvent) -> Unit,
    onLongClick: (WeekViewEvent) -> Unit,
    content: @Composable (WeekViewEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.clip(shape).combinedClickable(onClick = { onClick(event) }, onLongClick = { onLongClick(event) })) {
        content(event)
    }
}

/** Rounded, except on the edge where the event continues into the previous or next day. */
private fun eventShape(
    radius: Dp,
    continuesFromBefore: Boolean,
    continuesAfter: Boolean,
    horizontal: Boolean,
): Shape {
    val before = if (continuesFromBefore) 0.dp else radius
    val after = if (continuesAfter) 0.dp else radius
    return if (horizontal) {
        RoundedCornerShape(topStart = before, bottomStart = before, topEnd = after, bottomEnd = after)
    } else {
        RoundedCornerShape(topStart = before, topEnd = before, bottomStart = after, bottomEnd = after)
    }
}

/**
 * Reports two-finger pinches. Runs on the initial pass and consumes only while two fingers are
 * down, so single-finger scrolling and taps underneath keep working.
 */
private fun Modifier.pinchToZoom(onZoom: (zoom: Float, centroidY: Float) -> Unit): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.count { it.pressed } >= 2) {
                    val zoom = event.calculateZoom()
                    if (zoom != 1f) onZoom(zoom, event.calculateCentroid(useCurrent = true).y)
                    event.changes.forEach { it.consume() }
                }
            } while (event.changes.any { it.pressed })
        }
    }

private const val HOURS_PER_DAY = 24
private const val NOW_REFRESH_MILLIS = 30_000L
private val LABEL_END_PADDING = 8.dp
