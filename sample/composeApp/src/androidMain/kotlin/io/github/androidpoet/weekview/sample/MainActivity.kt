package io.github.androidpoet.weekview.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.viewinterop.AndroidView
import io.github.androidpoet.weekview.WeekView

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // adb shell am start -n <package>/.MainActivity --es renderer view --ei days 7 --ez wholeDay true
        val startWithView = intent.getStringExtra("renderer") == "view"
        val days = intent.getIntExtra("days", 3)
        val wholeDay = intent.getBooleanExtra("wholeDay", false)
        setContent {
            SampleApp(startWithViewRenderer = startWithView, initialVisibleDays = days, showWholeDay = wholeDay) { events, visibleDays, todayRequests, onMessage, modifier ->
                var weekView by remember { mutableStateOf<WeekView?>(null) }
                LaunchedEffect(todayRequests) {
                    if (todayRequests > 0) weekView?.goToToday()
                }
                AndroidView(
                    factory = { context ->
                        WeekView(context).apply {
                            onEventClick = { onMessage("Clicked ${it.title}") }
                            onEventLongClick = { onMessage("Long-pressed ${it.title}") }
                            onEmptyClick = { onMessage("Empty slot $it") }
                            onEmptyLongClick = { onMessage("Long-pressed empty slot $it") }
                            if (wholeDay) {
                                hourHeight = minHourHeight
                                goToHour(0f)
                            }
                            weekView = this
                        }
                    },
                    update = {
                        it.events = events
                        if (it.visibleDays != visibleDays) it.visibleDays = visibleDays
                    },
                    modifier = modifier,
                )
            }
        }
    }
}
