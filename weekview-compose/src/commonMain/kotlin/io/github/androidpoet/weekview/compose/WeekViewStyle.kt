package io.github.androidpoet.weekview.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import io.github.androidpoet.weekview.EventTextMode
import io.github.androidpoet.weekview.eventTextMode
import io.github.androidpoet.weekview.localizedDayOfMonth
import io.github.androidpoet.weekview.localizedHourLabel
import io.github.androidpoet.weekview.localizedWeekdayLabel
import io.github.androidpoet.weekview.prefersDarkText
import io.github.androidpoet.weekview.verticalTextScale
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.androidpoet.weekview.WeekViewEvent
import kotlinx.datetime.LocalDate

/**
 * Colors, text styles and sizes of a [WeekView]. Start from [WeekViewDefaults.style] and `copy`.
 *
 * @param timeColumnWidth the least width of the hour column; it grows to fit the widest hour label.
 * @param headerHeight the least height of the day headers; it grows with the font scale.
 * @param allDayRowHeight the least height of an all-day row; it grows with the font scale.
 * @param maxAllDayRows the most all-day rows shown. A date with more keeps the last row for a
 * "+N" marker, so all-day events can never push the hour grid off the screen.
 */
@Immutable
public data class WeekViewStyle(
    val backgroundColor: Color,
    val headerColor: Color,
    val hourLineColor: Color,
    val dividerColor: Color,
    val todayColor: Color,
    val accentColor: Color,
    val nowColor: Color,
    val headerTextStyle: TextStyle,
    val timeTextStyle: TextStyle,
    val eventTextStyle: TextStyle,
    val timeColumnWidth: Dp = 52.dp,
    val headerHeight: Dp = 56.dp,
    val allDayRowHeight: Dp = 24.dp,
    val eventGap: Dp = 2.dp,
    val eventCornerRadius: Dp = 6.dp,
    val maxAllDayRows: Int = 3,
) {
    init {
        require(maxAllDayRows >= 1) { "maxAllDayRows must be at least 1" }
    }
}

/** Diameter of the circle behind the day number: at least 28dp, more at large font scales. */
internal fun WeekViewStyle.dayBadgeSize(density: Density): Dp = with(density) { maxOf(28.dp, headerTextStyle.fontSize.toDp() * 1.75f) }

/** Height of the default day header: weekday line, a gap, the day badge. */
internal fun WeekViewStyle.dayHeaderContentHeight(density: Density): Dp =
    with(density) { timeTextStyle.fontSize.toDp() * 1.4f } + 2.dp + dayBadgeSize(density)

public object WeekViewDefaults {
    /** The default style, following the system light/dark setting. */
    @Composable
    public fun style(darkTheme: Boolean = isSystemInDarkTheme()): WeekViewStyle = if (darkTheme) darkStyle() else lightStyle()

    public fun lightStyle(): WeekViewStyle =
        style(
            background = Color(0xFFFFFFFF),
            header = Color(0xFFF8FAFC),
            line = Color(0xFFE2E8F0),
            today = Color(0x0F2563EB),
            accent = Color(0xFF2563EB),
            text = Color(0xFF0F172A),
            mutedText = Color(0xFF64748B),
        )

    public fun darkStyle(): WeekViewStyle =
        style(
            background = Color(0xFF0B1220),
            header = Color(0xFF111A2B),
            line = Color(0xFF1F2A3D),
            today = Color(0x1F60A5FA),
            accent = Color(0xFF60A5FA),
            text = Color(0xFFE2E8F0),
            mutedText = Color(0xFF94A3B8),
        )

    private fun style(
        background: Color,
        header: Color,
        line: Color,
        today: Color,
        accent: Color,
        text: Color,
        mutedText: Color,
    ) = WeekViewStyle(
        backgroundColor = background,
        headerColor = header,
        hourLineColor = line,
        dividerColor = line,
        todayColor = today,
        accentColor = accent,
        nowColor = Color(0xFFEA580C),
        headerTextStyle = TextStyle(color = text, fontSize = 16.sp, fontWeight = FontWeight.Medium),
        timeTextStyle = TextStyle(color = mutedText, fontSize = 11.sp),
        eventTextStyle = TextStyle(fontSize = 12.sp, lineHeight = 15.sp),
    )

    /**
     * Hour labels in the device's language and clock convention: `9 AM` or `09:00`. On Android
     * the user's 24-hour setting wins over the locale.
     */
    @Composable
    public fun rememberTimeLabel(): (hour: Int) -> String {
        val use24HourClock = deviceUses24HourClock()
        // Keyed by locale as well: a language change must produce new labels.
        return remember(use24HourClock, Locale.current) { { hour -> localizedHourLabel(hour, use24HourClock) } }
    }

    /** Always `09:00`, whatever the locale. */
    public fun timeLabel(hour: Int): String = "${hour.toString().padStart(2, '0')}:00"

    /** Weekday over day-of-month; today's number sits in an accent circle. */
    @Composable
    public fun DayHeader(
        date: LocalDate,
        isToday: Boolean,
        style: WeekViewStyle,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BasicText(
                text = remember(date, Locale.current) { localizedWeekdayLabel(date) },
                style = style.timeTextStyle.copy(color = if (isToday) style.accentColor else style.timeTextStyle.color),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                modifier =
                    Modifier
                        .padding(top = 2.dp)
                        .size(style.dayBadgeSize(LocalDensity.current))
                        .clip(CircleShape)
                        .background(if (isToday) style.accentColor else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = remember(date, Locale.current) { localizedDayOfMonth(date) },
                    style = style.headerTextStyle.copy(color = if (isToday) readableOn(style.accentColor) else style.headerTextStyle.color),
                )
            }
        }
    }

    /**
     * A filled block in the event's color with its title and, when there is room, its location.
     *
     * Text never breaks inside a word: it wraps at spaces, falls back to one ellipsized line, runs
     * down the block when the block is narrow and tall, or is left out when nothing readable fits.
     */
    @Composable
    public fun EventContent(
        event: WeekViewEvent,
        style: WeekViewStyle,
    ) {
        val measurer = rememberTextMeasurer()
        val background = Color(event.color)
        // Content direction: a title aligns by its own script, not by the layout around it.
        val textStyle = style.eventTextStyle.copy(color = readableOn(background), textDirection = TextDirection.Content)
        val titleStyle = textStyle.copy(fontWeight = FontWeight.SemiBold)
        Spacer(
            Modifier
                .fillMaxSize()
                .background(background)
                .semantics { text = AnnotatedString(listOfNotNull(event.title, event.location).joinToString(", ")) }
                .drawWithCache {
                    val paddingX = EVENT_PADDING_X.toPx()
                    val paddingY = EVENT_PADDING_Y.toPx()
                    val lineHeight = measurer.measure("Ag", titleStyle).size.height.toFloat()
                    val longestWord =
                        event.title.split(' ').maxOf { measurer.measure(it, titleStyle, softWrap = false, maxLines = 1).size.width }
                    val oneLine = Constraints(maxWidth = (size.width - 2 * paddingX).toInt().coerceAtLeast(0))

                    when (val mode = eventTextMode(size.width, size.height, lineHeight, longestWord.toFloat(), paddingX)) {
                        EventTextMode.Hidden -> onDrawBehind {}
                        EventTextMode.Wrapped, EventTextMode.SingleLine -> {
                            val wraps = mode == EventTextMode.Wrapped
                            val maxLines = if (wraps) ((size.height - 2 * paddingY) / lineHeight).toInt().coerceAtLeast(1) else 1
                            val title = measurer.measure(event.title, titleStyle, TextOverflow.Ellipsis, wraps, maxLines, oneLine)
                            val location =
                                event.location
                                    ?.takeIf { size.height - 2 * paddingY - title.size.height >= lineHeight }
                                    ?.let { measurer.measure(it, textStyle, TextOverflow.Ellipsis, softWrap = false, maxLines = 1, oneLine) }
                            // A block barely one line tall centers its text instead of padding it out.
                            val top = minOf(paddingY, (size.height - lineHeight) / 2).coerceAtLeast(0f)
                            onDrawBehind {
                                drawText(title, topLeft = Offset(paddingX, top))
                                location?.let { drawText(it, topLeft = Offset(paddingX, top + title.size.height)) }
                            }
                        }
                        EventTextMode.Vertical -> {
                            val scale = verticalTextScale(size.width, lineHeight)
                            val rotatedStyle = titleStyle.copy(fontSize = titleStyle.fontSize * scale, lineHeight = TextUnit.Unspecified)
                            val line =
                                measurer.measure(
                                    text = listOfNotNull(event.title, event.location).joinToString("  "),
                                    style = rotatedStyle,
                                    overflow = TextOverflow.Ellipsis,
                                    softWrap = false,
                                    maxLines = 1,
                                    constraints = Constraints(maxWidth = (size.height - 2 * paddingX).toInt().coerceAtLeast(0)),
                                )
                            onDrawBehind {
                                // Turned a quarter clockwise around the top-left corner: the line's
                                // x runs down the block, and a negative y moves it right, centered.
                                rotate(degrees = 90f, pivot = Offset.Zero) {
                                    drawText(line, topLeft = Offset(paddingX, -(size.width + line.size.height) / 2f))
                                }
                            }
                        }
                    }
                },
        )
    }

    private val EVENT_PADDING_X = 5.dp
    private val EVENT_PADDING_Y = 1.dp

    private fun readableOn(background: Color): Color = if (prefersDarkText(background.toArgb())) Color(0xFF0F172A) else Color.White
}
