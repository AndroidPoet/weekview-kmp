package io.github.androidpoet.weekview.compose

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.androidpoet.weekview.WeekViewEvent
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class WeekViewContentTest {
    private val monday = LocalDate(2026, 3, 2)
    private val size = Modifier.size(400.dp, 600.dp)

    private fun event(
        id: String,
        isAllDay: Boolean = false,
    ) = WeekViewEvent(id, id, LocalDateTime(2026, 3, 2, 9, 0), LocalDateTime(2026, 3, 2, 11, 0), isAllDay = isAllDay)

    @Test
    fun test_events_addedToSnapshotStateList_showsTheNewEvent() =
        runComposeUiTest {
            val events = mutableStateListOf(event("Lunch"))
            setContent { WeekView(events, size, WeekViewState(monday), now = null) }

            events += event("Review")

            onNodeWithText("Review").assertIsDisplayed()
        }

    @Test
    fun test_state_replaced_showsAndReportsTheNewStatesDate() =
        runComposeUiTest {
            var state by mutableStateOf(WeekViewState(initialDate = LocalDate(2026, 4, 1)))
            setContent { WeekView(listOf(event("Lunch")), size, state, visibleDays = 1, now = null) }

            state = WeekViewState(initialDate = monday)

            onNodeWithText("Lunch").assertIsDisplayed()
            onNodeWithText("2").assertIsDisplayed()
        }

    @Test
    fun test_allDayEvents_moreThanMaxRows_showsMarkerInsteadOfTheRest() =
        runComposeUiTest {
            val events = List(5) { event("Holiday $it", isAllDay = true) }
            setContent { WeekView(events, size, WeekViewState(monday), visibleDays = 1, now = null) }

            onNodeWithText("Holiday 1").assertIsDisplayed()
            onNodeWithText("+3").assertIsDisplayed()
            onNodeWithText("Holiday 2").assertDoesNotExist()
        }

    @Test
    fun test_zoomBy_whileViewportFillStillDecidesTheScale_keepsFocusedMinuteInPlace() {
        // 28dp hours would not fill a 1000px body, so the drawn hour is 1000 / 24 px, before and after.
        val density = Density(1f)
        val state = WeekViewState(monday, initialHour = 0f, initialHourHeight = 28.dp, minHourHeight = 28.dp)
        state.bodyHeightPx = 1000

        state.zoomBy(factor = 1.2f, focusY = 400f, density = density)

        assertEquals(0f, state.scrollPx(state.pxPerMinute(density)), 0.01f)
    }

    @Test
    fun test_zoomBy_pastTheViewportFill_keepsFocusedMinuteInPlace() {
        val density = Density(1f)
        val state = WeekViewState(monday, initialHour = 0f, initialHourHeight = 28.dp, minHourHeight = 28.dp)
        state.bodyHeightPx = 1000
        val focusY = 400f
        val before = (state.scrollPx(state.pxPerMinute(density)) + focusY) / state.pxPerMinute(density)

        state.zoomBy(factor = 2f, focusY = focusY, density = density)

        val after = (state.scrollPx(state.pxPerMinute(density)) + focusY) / state.pxPerMinute(density)
        assertEquals(before, after, 0.01f)
    }
}
