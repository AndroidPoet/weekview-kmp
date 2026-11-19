package io.github.androidpoet.weekview.compose

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.androidpoet.weekview.WeekViewLayout
import io.github.androidpoet.weekview.currentLocalDateTime
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus

/**
 * Scroll and zoom position of a [WeekView].
 *
 * @param initialDate the date shown in the first column.
 * @param initialHour the hour at the top of the grid, e.g. `8.5f` for 08:30.
 * @param initialHourHeight height of one hour; pinch-zoom moves it between [minHourHeight] and [maxHourHeight].
 */
@Stable
public class WeekViewState(
    initialDate: LocalDate = currentLocalDateTime().date,
    initialHour: Float = 8f,
    initialHourHeight: Dp = 60.dp,
    public val minHourHeight: Dp = 28.dp,
    public val maxHourHeight: Dp = 180.dp,
) {
    private val anchorDate = initialDate
    internal val listState = LazyListState(firstVisibleItemIndex = ANCHOR_INDEX)

    /** Height of one hour. Never drawn smaller than what fills the viewport with 24 hours. */
    public var hourHeight: Dp by mutableStateOf(initialHourHeight.coerceIn(minHourHeight, maxHourHeight))
        private set

    /** Minute of the day at the top edge of the grid, before clamping to the viewport. */
    private var topMinute by mutableFloatStateOf(initialHour * 60f)
    internal var bodyHeightPx by mutableIntStateOf(0)

    /** The date in the first visible column. Observable. */
    public val firstVisibleDate: LocalDate get() = dateAt(listState.firstVisibleItemIndex)

    /** The hour at the top edge of the grid, as last requested or scrolled to. Observable. */
    public val firstVisibleHour: Float get() = topMinute / 60f

    public suspend fun scrollToDate(date: LocalDate) {
        listState.scrollToItem(indexOf(date))
    }

    public suspend fun animateScrollToDate(date: LocalDate) {
        listState.animateScrollToItem(indexOf(date))
    }

    public fun scrollToHour(hour: Float) {
        topMinute = (hour * 60f).coerceIn(0f, WeekViewLayout.MINUTES_PER_DAY.toFloat())
    }

    internal fun dateAt(index: Int): LocalDate = anchorDate.plus(index - ANCHOR_INDEX, DateTimeUnit.DAY)

    private fun indexOf(date: LocalDate): Int = ANCHOR_INDEX + anchorDate.daysUntil(date)

    private fun maxTopMinute(pxPerMinute: Float): Float = (WeekViewLayout.MINUTES_PER_DAY - bodyHeightPx / pxPerMinute).coerceAtLeast(0f)

    /** Vertical scroll offset in pixels, clamped to the content. */
    internal fun scrollPx(pxPerMinute: Float): Float = topMinute.coerceIn(0f, maxTopMinute(pxPerMinute)) * pxPerMinute

    /** Applies a drag [deltaPx] (positive = finger down) and returns how much of it was consumed. */
    internal fun dispatchScroll(
        deltaPx: Float,
        pxPerMinute: Float,
    ): Float {
        val max = maxTopMinute(pxPerMinute)
        val old = topMinute.coerceIn(0f, max)
        val new = (old - deltaPx / pxPerMinute).coerceIn(0f, max)
        topMinute = new
        return (old - new) * pxPerMinute
    }

    /** Scales the hour height by [factor], keeping the minute under [focusY] (pixels from the grid top) in place. */
    internal fun zoomBy(
        factor: Float,
        focusY: Float,
        pxPerMinute: Float,
    ) {
        val old = hourHeight
        val new = (old * factor).coerceIn(minHourHeight, maxHourHeight)
        if (new == old) return
        val focusMinute = scrollPx(pxPerMinute) / pxPerMinute + focusY / pxPerMinute
        hourHeight = new
        topMinute = focusMinute - focusY / (pxPerMinute * (new / old))
    }

    internal companion object {
        const val ANCHOR_INDEX = Int.MAX_VALUE / 2

        fun saver(
            minHourHeight: Dp,
            maxHourHeight: Dp,
        ): Saver<WeekViewState, Any> =
            listSaver(
                save = { listOf(it.firstVisibleDate.toString(), it.firstVisibleHour, it.hourHeight.value) },
                restore = {
                    WeekViewState(
                        initialDate = LocalDate.parse(it[0] as String),
                        initialHour = it[1] as Float,
                        initialHourHeight = (it[2] as Float).dp,
                        minHourHeight = minHourHeight,
                        maxHourHeight = maxHourHeight,
                    )
                },
            )
    }
}

/** Creates a [WeekViewState] that survives configuration changes. */
@Composable
public fun rememberWeekViewState(
    initialDate: LocalDate = currentLocalDateTime().date,
    initialHour: Float = 8f,
    initialHourHeight: Dp = 60.dp,
    minHourHeight: Dp = 28.dp,
    maxHourHeight: Dp = 180.dp,
): WeekViewState =
    rememberSaveable(saver = WeekViewState.saver(minHourHeight, maxHourHeight)) {
        WeekViewState(initialDate, initialHour, initialHourHeight, minHourHeight, maxHourHeight)
    }
