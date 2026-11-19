package io.github.androidpoet.weekview.sample

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.androidpoet.weekview.WeekViewEvent
import io.github.androidpoet.weekview.compose.WeekView
import io.github.androidpoet.weekview.compose.rememberWeekViewState
import io.github.androidpoet.weekview.currentLocalDateTime

/**
 * Draws the same events with a platform's non-Compose week view, so both renderers can be compared.
 *
 * @param todayRequests increments each time "Today" is pressed.
 */
typealias ViewRenderer = @Composable (
    events: List<WeekViewEvent>,
    visibleDays: Int,
    todayRequests: Int,
    onMessage: (String) -> Unit,
    modifier: Modifier,
) -> Unit

@Composable
fun SampleApp(
    startWithViewRenderer: Boolean = false,
    initialVisibleDays: Int = 3,
    showWholeDay: Boolean = false,
    viewRenderer: ViewRenderer? = null,
) {
    val today = remember { currentLocalDateTime().date }
    var events by remember(today) { mutableStateOf(sampleEvents(today)) }
    var addedCount by remember { mutableIntStateOf(0) }
    var useViewRenderer by remember { mutableStateOf(startWithViewRenderer && viewRenderer != null) }
    var visibleDays by remember { mutableIntStateOf(initialVisibleDays) }
    var todayRequests by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("Tap an event or an empty slot") }
    // The smallest hour height is stretched to fill the viewport, so it shows all 24 hours at once.
    val state =
        rememberWeekViewState(
            initialDate = today,
            initialHour = if (showWholeDay) 0f else 8f,
            initialHourHeight = if (showWholeDay) 28.dp else 60.dp,
        )

    LaunchedEffect(todayRequests) {
        if (todayRequests > 0) state.animateScrollToDate(today)
    }

    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (viewRenderer != null) {
                        FilterChip(selected = !useViewRenderer, onClick = { useViewRenderer = false }, label = { Text("Compose") })
                        FilterChip(selected = useViewRenderer, onClick = { useViewRenderer = true }, label = { Text("View") })
                    }
                    Spacer(Modifier.weight(1f))
                    listOf(1, 3, 7).forEach { days ->
                        FilterChip(selected = visibleDays == days, onClick = { visibleDays = days }, label = { Text("$days") })
                    }
                }
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            val event = addedEvent(today, addedCount++)
                            events = events + event
                            message = "${event.title}: ${event.start} to ${event.end}"
                        },
                    ) { Text("Add", maxLines = 1) }
                    TextButton(onClick = { todayRequests++ }) { Text("Today", maxLines = 1) }
                }
                if (useViewRenderer && viewRenderer != null) {
                    viewRenderer(events, visibleDays, todayRequests, { message = it }, Modifier.fillMaxSize())
                } else {
                    WeekView(
                        events = events,
                        state = state,
                        visibleDays = visibleDays,
                        onEventClick = { message = "Clicked ${it.title}" },
                        onEventLongClick = { message = "Long-pressed ${it.title}" },
                        onEmptyClick = { message = "Empty slot $it" },
                        onEmptyLongClick = { message = "Long-pressed empty slot $it" },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
