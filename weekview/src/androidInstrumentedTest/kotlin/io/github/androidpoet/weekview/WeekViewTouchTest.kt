package io.github.androidpoet.weekview

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class WeekViewTouchTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val today = currentLocalDateTime().date
    private val lunch = WeekViewEvent("lunch", "Lunch", LocalDateTime(today, LocalTime(9, 0)), LocalDateTime(today, LocalTime(11, 0)))

    /** A laid-out 1080x1920 view showing 08:00 at the top; touches are sent on the main thread. */
    private fun onWeekView(
        layoutDirection: Int = View.LAYOUT_DIRECTION_LTR,
        block: (WeekView) -> Unit,
    ) = instrumentation.runOnMainSync {
            val view = WeekView(instrumentation.targetContext)
            view.layoutDirection = layoutDirection
            // Fixed labels keep the time column at its 52dp minimum whatever the device's locale.
            view.timeLabel = { "$it:00" }
            view.events = listOf(lunch)
            view.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, WIDTH, HEIGHT)
            block(view)
        }

    private var clock = SystemClock.uptimeMillis()

    private fun WeekView.touch(
        action: Int,
        vararg points: Pair<Float, Float>,
        downTime: Long,
    ) {
        clock += STEP_MILLIS
        val properties =
            Array(points.size) { index ->
                MotionEvent.PointerProperties().apply {
                    id = index
                    toolType = MotionEvent.TOOL_TYPE_FINGER
                }
            }
        val coords =
            Array(points.size) { index ->
                MotionEvent.PointerCoords().apply {
                    x = points[index].first
                    y = points[index].second
                    pressure = 1f
                    size = 1f
                }
            }
        val event = MotionEvent.obtain(downTime, clock, action, points.size, properties, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        onTouchEvent(event)
        event.recycle()
    }

    /** Two fingers on a vertical line around y=1000, moving from [fromSpan] apart to [toSpan] apart. */
    private fun WeekView.pinch(
        fromSpan: Float,
        toSpan: Float,
    ) {
        val downTime = clock
        val secondPointerDown = MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        val secondPointerUp = MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)

        fun fingers(span: Float) = arrayOf(600f to 1000f - span / 2, 600f to 1000f + span / 2)
        touch(MotionEvent.ACTION_DOWN, fingers(fromSpan)[0], downTime = downTime)
        touch(secondPointerDown, *fingers(fromSpan), downTime = downTime)
        for (step in 1..PINCH_STEPS) {
            touch(MotionEvent.ACTION_MOVE, *fingers(fromSpan + (toSpan - fromSpan) * step / PINCH_STEPS), downTime = downTime)
        }
        touch(secondPointerUp, *fingers(toSpan), downTime = downTime)
        touch(MotionEvent.ACTION_UP, fingers(toSpan)[0], downTime = downTime)
    }

    private fun WeekView.drag(
        from: Pair<Float, Float>,
        to: Pair<Float, Float>,
    ) {
        val downTime = clock
        touch(MotionEvent.ACTION_DOWN, from, downTime = downTime)
        for (step in 1..DRAG_STEPS) {
            val fraction = step.toFloat() / DRAG_STEPS
            touch(
                MotionEvent.ACTION_MOVE,
                from.first + (to.first - from.first) * fraction to from.second + (to.second - from.second) * fraction,
                downTime = downTime,
            )
        }
        // Hold still before lifting so the gesture ends as a drag, not a fling.
        repeat(HOLD_STEPS) { touch(MotionEvent.ACTION_MOVE, to, downTime = downTime) }
        touch(MotionEvent.ACTION_UP, to, downTime = downTime)
    }

    private fun WeekView.tap(point: Pair<Float, Float>) {
        val downTime = clock
        touch(MotionEvent.ACTION_DOWN, point, downTime = downTime)
        touch(MotionEvent.ACTION_UP, point, downTime = downTime)
    }

    private fun WeekView.drawOnce() = draw(android.graphics.Canvas(android.graphics.Bitmap.createBitmap(WIDTH, HEIGHT, android.graphics.Bitmap.Config.ARGB_8888)))

    @Test
    fun test_pinch_fingersSpreading_increasesHourHeight() =
        onWeekView { view ->
            val before = view.hourHeight

            view.pinch(fromSpan = 300f, toSpan = 900f)

            assertTrue(view.hourHeight > before, "hour height stayed ${view.hourHeight}")
        }

    @Test
    fun test_pinch_fingersClosing_decreasesHourHeight() =
        onWeekView { view ->
            val before = view.hourHeight

            view.pinch(fromSpan = 900f, toSpan = 300f)

            assertTrue(view.hourHeight < before, "hour height stayed ${view.hourHeight}")
        }

    @Test
    fun test_pinch_beyondLimits_staysWithinMinAndMaxHourHeight() =
        onWeekView { view ->
            repeat(6) { view.pinch(fromSpan = 200f, toSpan = 1400f) }

            assertEquals(view.maxHourHeight, view.hourHeight)
        }

    @Test
    fun test_drag_oneDayToTheLeft_showsTheNextDate() =
        onWeekView { view ->
            val dayWidth = (WIDTH - 52 * view.resources.displayMetrics.density) / view.visibleDays

            view.drag(900f to 1000f, 900f - dayWidth to 1000f)

            assertEquals(today.plus(1, DateTimeUnit.DAY), view.firstVisibleDate)
        }

    @Test
    fun test_tap_onEventAndEmptySlot_reportsEach() =
        onWeekView { view ->
            val clicked = ArrayList<String>()
            val slots = ArrayList<LocalDateTime>()
            view.onEventClick = { clicked += it.id }
            view.onEmptyClick = { slots += it }
            view.drawOnce()
            val density = view.resources.displayMetrics.density
            // 08:00 is at the top of the grid and an hour is 60dp, so 10:00 sits 120dp below the header.
            val insideLunch = 52 * density + 40f to 56 * density + 120 * density

            view.tap(insideLunch)
            view.tap(WIDTH - 20f to HEIGHT - 20f)

            assertEquals(listOf("lunch"), clicked)
            assertEquals(today.plus(2, DateTimeUnit.DAY), slots.single().date)
        }

    @Test
    fun test_tap_inRtlLayout_countsDaysFromTheRightEdgeOfTheGrid() =
        onWeekView(View.LAYOUT_DIRECTION_RTL) { view ->
            val slots = ArrayList<LocalDateTime>()
            view.onEmptyClick = { slots += it }
            view.drawOnce()
            val timeColumn = 52 * view.resources.displayMetrics.density

            view.tap(WIDTH - timeColumn - 20f to HEIGHT - 20f)
            view.tap(20f to HEIGHT - 20f)
            view.tap(WIDTH - 20f to HEIGHT - 20f)

            assertEquals(listOf(today, today.plus(2, DateTimeUnit.DAY)), slots.map { it.date })
        }

    @Test
    fun test_drag_inRtlLayout_oneDayToTheRight_showsTheNextDate() =
        onWeekView(View.LAYOUT_DIRECTION_RTL) { view ->
            val dayWidth = (WIDTH - 52 * view.resources.displayMetrics.density) / view.visibleDays

            view.drag(200f to 1000f, 200f + dayWidth to 1000f)

            assertEquals(today.plus(1, DateTimeUnit.DAY), view.firstVisibleDate)
        }

    @Test
    fun test_allDayEvents_moreThanMaxRows_keepTheHourGridTappable() =
        onWeekView { view ->
            val slots = ArrayList<LocalDateTime>()
            view.onEmptyClick = { slots += it }
            view.events = List(40) { WeekViewEvent("all-day-$it", "Holiday", LocalDateTime(today, LocalTime(0, 0)), LocalDateTime(today, LocalTime(0, 0)), isAllDay = true) }
            view.drawOnce()

            view.tap(WIDTH - 20f to HEIGHT / 2f)

            assertEquals(today.plus(2, DateTimeUnit.DAY), slots.single().date)
        }

    @Test
    fun test_resize_duringSnapAnimation_landsOnTheDayItWasHeadingFor() =
        onWeekView { view ->
            view.goToDate(today.plus(5, DateTimeUnit.DAY), animate = true)

            view.layout(0, 0, WIDTH / 2, HEIGHT)

            assertEquals(today.plus(5, DateTimeUnit.DAY), view.firstVisibleDate)
        }

    private companion object {
        const val WIDTH = 1080
        const val HEIGHT = 1920
        const val STEP_MILLIS = 16L
        const val PINCH_STEPS = 12
        const val DRAG_STEPS = 12
        const val HOLD_STEPS = 8
    }
}
