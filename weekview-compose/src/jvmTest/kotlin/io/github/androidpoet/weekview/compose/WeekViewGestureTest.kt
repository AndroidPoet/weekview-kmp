package io.github.androidpoet.weekview.compose

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import io.github.androidpoet.weekview.WeekViewEvent
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class WeekViewGestureTest {
    private val monday = LocalDate(2026, 3, 2)
    private val lunch = WeekViewEvent("lunch", "Lunch", LocalDateTime(2026, 3, 2, 9, 0), LocalDateTime(2026, 3, 2, 11, 0))

    private fun ComposeUiTest.setWeekView(
        state: WeekViewState,
        onEventClick: (WeekViewEvent) -> Unit = {},
        onEventLongClick: (WeekViewEvent) -> Unit = {},
        onEmptyClick: (LocalDateTime) -> Unit = {},
    ) = setContent {
        WeekView(
            events = listOf(lunch),
            state = state,
            now = null,
            onEventClick = onEventClick,
            onEventLongClick = onEventLongClick,
            onEmptyClick = onEmptyClick,
            modifier = Modifier.size(400.dp, 600.dp).testTag("week"),
        )
    }

    @Test
    fun test_pinch_fingersSpreading_increasesHourHeight() =
        runComposeUiTest {
            val state = WeekViewState(initialDate = monday)
            setWeekView(state)
            val before = state.hourHeight

            onNodeWithTag("week").performTouchInput {
                pinch(Offset(200f, 300f), Offset(200f, 180f), Offset(200f, 340f), Offset(200f, 520f))
            }

            assertTrue(state.hourHeight > before, "hour height stayed ${state.hourHeight}")
        }

    @Test
    fun test_pinch_fingersClosing_decreasesHourHeight() =
        runComposeUiTest {
            val state = WeekViewState(initialDate = monday)
            setWeekView(state)
            val before = state.hourHeight

            onNodeWithTag("week").performTouchInput {
                pinch(Offset(200f, 180f), Offset(200f, 300f), Offset(200f, 520f), Offset(200f, 340f))
            }

            assertTrue(state.hourHeight < before, "hour height stayed ${state.hourHeight}")
        }

    @Test
    fun test_swipeUp_scrollsToLaterHours() =
        runComposeUiTest {
            val state = WeekViewState(initialDate = monday, initialHour = 8f)
            setWeekView(state)

            onNodeWithTag("week").performTouchInput { swipeUp() }
            waitForIdle()

            assertTrue(state.firstVisibleHour > 8f, "first visible hour stayed ${state.firstVisibleHour}")
        }

    @Test
    fun test_swipeLeft_snapsToALaterDate() =
        runComposeUiTest {
            val state = WeekViewState(initialDate = monday)
            setWeekView(state)

            onNodeWithTag("week").performTouchInput { swipeLeft() }
            waitForIdle()

            assertTrue(state.firstVisibleDate > monday, "first visible date stayed ${state.firstVisibleDate}")
            assertEquals(0, state.listState.firstVisibleItemScrollOffset)
        }

    @Test
    fun test_click_onEvent_reportsThatEvent() =
        runComposeUiTest {
            val clicked = ArrayList<String>()
            setWeekView(WeekViewState(initialDate = monday), onEventClick = { clicked += it.id })

            onNodeWithText("Lunch").performClick()

            assertEquals(listOf("lunch"), clicked)
        }

    @Test
    fun test_longClick_onEvent_reportsThatEvent() =
        runComposeUiTest {
            val pressed = ArrayList<String>()
            setWeekView(WeekViewState(initialDate = monday), onEventLongClick = { pressed += it.id })

            onNodeWithText("Lunch").performTouchInput { longClick() }

            assertEquals(listOf("lunch"), pressed)
        }

    @Test
    fun test_click_onEmptySlot_reportsDateAndTime() =
        runComposeUiTest {
            val slots = ArrayList<LocalDateTime>()
            setWeekView(WeekViewState(initialDate = monday, initialHour = 8f), onEmptyClick = { slots += it })

            // Third column (Wednesday), well below the header.
            onNodeWithTag("week").performTouchInput {
                down(Offset(width - 20f, height - 20f))
                up()
            }
            waitForIdle()

            assertEquals(LocalDate(2026, 3, 4), slots.single().date)
            assertTrue(slots.single().hour >= 8)
        }
}
