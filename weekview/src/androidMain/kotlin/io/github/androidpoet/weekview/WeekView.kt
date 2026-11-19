package io.github.androidpoet.weekview

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import android.os.Parcelable
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.text.format.DateFormat
import android.text.style.StyleSpan
import android.util.AttributeSet
import android.util.TypedValue
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.SoundEffectConstants
import android.view.View
import android.widget.OverScroller
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.properties.Delegates
import kotlin.properties.ReadWriteProperty

/**
 * A day / multi-day / week calendar grid for the Android View system.
 *
 * Days scroll horizontally without end and snap to a column; hours scroll vertically; pinch changes
 * the hour height. Overlapping events are placed side by side by [WeekViewLayout], the same engine
 * the Compose `WeekView` uses.
 */
public class WeekView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : View(context, attrs, defStyleAttr) {
        /** The events to show. Order does not matter. */
        public var events: List<WeekViewEvent> = emptyList()
            set(value) {
                field = value
                eventLayout = WeekViewLayout.of(value)
                invalidate()
            }

        /** How many day columns fit the width: 1 for a day view, 7 for a week. */
        public var visibleDays: Int = 3
            set(value) {
                require(value >= 1) { "visibleDays must be at least 1" }
                field = value
                stopScrolling()
                scrollDays = scrollDays.roundToInt().toFloat()
                invalidate()
            }

        /** Height of one hour in pixels. Never drawn smaller than what fills the view with 24 hours. */
        public var hourHeight: Float = dp(60f)
            set(value) {
                field = value.coerceIn(minHourHeight, maxHourHeight)
                invalidate()
            }
        public var minHourHeight: Float = dp(28f)
        public var maxHourHeight: Float = dp(180f)

        public var onEventClick: ((WeekViewEvent) -> Unit)? = null
        public var onEventLongClick: ((WeekViewEvent) -> Unit)? = null
        public var onEmptyClick: ((LocalDateTime) -> Unit)? = null
        public var onEmptyLongClick: ((LocalDateTime) -> Unit)? = null
        public var onFirstVisibleDateChanged: ((LocalDate) -> Unit)? = null

        /**
         * The current time: where the "now" line is drawn, which date is highlighted and where
         * [goToToday] goes. Defaults to the device's clock and time zone; supply your own when the
         * events are in another zone, or return `null` to hide the line and the highlight.
         */
        public var currentTime: () -> LocalDateTime? by redrawing(::currentLocalDateTime)

        /**
         * The most all-day rows shown. A date with more keeps the last row for a "+N" marker, so
         * all-day events can never push the hour grid off the view.
         */
        public var maxAllDayRows: Int = 3
            set(value) {
                require(value >= 1) { "maxAllDayRows must be at least 1" }
                field = value
                invalidate()
            }

        /** Small first line of a column header; the day of the month is drawn under it. */
        public var weekdayLabel: (LocalDate) -> String by redrawing(::localizedWeekdayLabel)

        /**
         * Text of an hour label (0..23) in the left column, which grows to fit the widest one.
         * By default the device's language and its 12/24-hour setting decide: `9 AM` or `09:00`.
         */
        public var timeLabel: (hour: Int) -> String = { localizedHourLabel(it, DateFormat.is24HourFormat(context)) }
            set(value) {
                field = value
                refreshHourLabels()
            }

        private val isNight = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        public var gridBackgroundColor: Int by redrawing(if (isNight) 0xFF0B1220.toInt() else Color.WHITE)
        public var headerColor: Int by redrawing(if (isNight) 0xFF111A2B.toInt() else 0xFFF8FAFC.toInt())
        public var lineColor: Int by redrawing(if (isNight) 0xFF1F2A3D.toInt() else 0xFFE2E8F0.toInt())
        public var todayColor: Int by redrawing(if (isNight) 0x1F60A5FA else 0x0F2563EB)
        public var accentColor: Int by redrawing(if (isNight) 0xFF60A5FA.toInt() else 0xFF2563EB.toInt())
        public var nowColor: Int by redrawing(0xFFEA580C.toInt())
        public var headerTextColor: Int by redrawing(if (isNight) 0xFFE2E8F0.toInt() else 0xFF0F172A.toInt())
        public var timeTextColor: Int by redrawing(if (isNight) 0xFF94A3B8.toInt() else 0xFF64748B.toInt())

        /** The date in the first column that is at least partly visible. */
        public val firstVisibleDate: LocalDate get() = dateAt(floor(scrollDays + SNAP_EPSILON).toInt())

        private var eventLayout = WeekViewLayout.Empty
        private var anchorDate = currentLocalDateTime().date

        /** Horizontal position in days from [anchorDate]; fractional while dragging. */
        private var scrollDays = 0f

        /** Minute of the day at the top edge of the grid. */
        private var topMinute = DEFAULT_TOP_MINUTE
        private var visibleAllDayLanes = 0
        private var lastNotifiedDate: LocalDate? = null

        private var hourLabels: List<String> = emptyList()
        private var timeColumnWidth = dp(MIN_TIME_COLUMN_DP)
        private val eventGap = dp(2f)
        private val eventRadius = dp(6f)
        private val eventPaddingX = dp(5f)
        private val eventPaddingY = dp(2f)

        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val linePaint = Paint().apply { strokeWidth = dp(1f) }
        private val nowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = dp(2f) }
        private val headerTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = sp(16f) }
        private val timeTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = sp(11f) }
        private val eventTextSize = sp(12f)
        private val eventTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = eventTextSize }

        // Header and all-day rows are sized from the fonts, so a large font scale cannot clip them.
        private val dayBadgeRadius = maxOf(dp(14f), headerTextPaint.fontSpacing * 0.6f)
        private val headerContentHeight = timeTextPaint.fontSpacing + dp(2f) + dayBadgeRadius * 2
        private val headerHeight = maxOf(dp(56f), headerContentHeight + dp(8f))
        private val allDayRowHeight = maxOf(dp(24f), eventTextPaint.fontSpacing + eventGap + dp(2f))
        private val eventTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
        private val rect = RectF()

        private class HitTarget(
            val bounds: RectF,
            val event: WeekViewEvent,
        )

        private data class TextKey(
            val title: String,
            val location: String?,
            val width: Int,
            val maxLines: Int,
        )

        /** Rebuilt on every draw: the visible part of each drawn event, topmost last. */
        private val hitTargets = ArrayList<HitTarget>()

        /** Least recently used layouts go first, one at a time. */
        private val textLayouts =
            object : LinkedHashMap<TextKey, StaticLayout>(64, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<TextKey, StaticLayout>?): Boolean = size > MAX_CACHED_TEXT_LAYOUTS
            }
        private val longestWordWidths = HashMap<String, Float>()

        private enum class Direction { NONE, HORIZONTAL, VERTICAL }

        private var direction = Direction.NONE
        private var isScaling = false
        private var didFling = false
        private var snapTarget: Int? = null
        private val xScroller = OverScroller(context)
        private val yScroller = OverScroller(context)

        private val dayWidth: Float get() = (width - timeColumnWidth) / visibleDays
        private val bodyTop: Float get() = headerHeight + visibleAllDayLanes * allDayRowHeight
        private val isRtl: Boolean get() = layoutDirection == LAYOUT_DIRECTION_RTL

        /** The grid (everything but the time column) spans these x; the time column takes the rest. */
        private val gridLeft: Float get() = if (isRtl) 0f else timeColumnWidth
        private val gridRight: Float get() = if (isRtl) width - timeColumnWidth else width.toFloat()
        private val bodyHeight: Float get() = (height - bodyTop).coerceAtLeast(1f)
        private val pxPerMinute: Float get() = maxOf(hourHeight, bodyHeight / HOURS_PER_DAY) / 60f

        private val nowTicker =
            object : Runnable {
                override fun run() {
                    invalidate()
                    postDelayed(this, NOW_REFRESH_MILLIS)
                }
            }

        init {
            if (attrs != null) {
                val array = context.obtainStyledAttributes(attrs, R.styleable.WeekView, defStyleAttr, 0)
                visibleDays = array.getInt(R.styleable.WeekView_visibleDays, visibleDays)
                hourHeight = array.getDimension(R.styleable.WeekView_hourHeight, hourHeight)
                array.recycle()
            }
            refreshHourLabels()
        }

        /** Re-reads the hour labels and fits the time column to the widest one. */
        private fun refreshHourLabels() {
            hourLabels = List(HOURS_PER_DAY) { timeLabel(it) }
            timeColumnWidth = maxOf(dp(MIN_TIME_COLUMN_DP), hourLabels.maxOf { timeTextPaint.measureText(it) } + 2 * dp(TIME_LABEL_PADDING_DP))
            settleScroll()
            invalidate()
        }

        override fun onSizeChanged(
            w: Int,
            h: Int,
            oldw: Int,
            oldh: Int,
        ) {
            super.onSizeChanged(w, h, oldw, oldh)
            settleScroll()
        }

        override fun onRtlPropertiesChanged(layoutDirection: Int) {
            super.onRtlPropertiesChanged(layoutDirection)
            invalidate()
        }

        /**
         * The scrollers count pixels, which mean other days once the column width changes; land
         * a running animation on the day it was heading for.
         */
        private fun settleScroll() {
            if (xScroller.isFinished && snapTarget == null) return
            val target = snapTarget ?: scrollDays.roundToInt()
            stopScrolling()
            scrollDays = target.toFloat()
            notifyDateChanged()
        }

        /** Scrolls so [date] is the first visible column. */
        public fun goToDate(
            date: LocalDate,
            animate: Boolean = true,
        ) {
            val target = anchorDate.daysUntil(date)
            stopScrolling()
            if (animate && width > 0) {
                animateToDay(target)
            } else {
                scrollDays = target.toFloat()
                notifyDateChanged()
                invalidate()
            }
        }

        public fun goToToday(animate: Boolean = true): Unit = goToDate((currentTime() ?: currentLocalDateTime()).date, animate)

        /** Scrolls so [hour] (e.g. `8.5f` for 08:30) is at the top of the grid. */
        public fun goToHour(hour: Float) {
            yScroller.forceFinished(true)
            topMinute = hour * 60f
            invalidate()
        }

        private fun dateAt(dayIndex: Int): LocalDate = anchorDate.plus(dayIndex, DateTimeUnit.DAY)

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            postDelayed(nowTicker, NOW_REFRESH_MILLIS)
        }

        override fun onDetachedFromWindow() {
            removeCallbacks(nowTicker)
            super.onDetachedFromWindow()
        }

        override fun onWindowVisibilityChanged(visibility: Int) {
            super.onWindowVisibilityChanged(visibility)
            // The language or the 24-hour setting may have changed while the view was away.
            if (visibility == VISIBLE) refreshHourLabels()
        }

        // region Drawing

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            hitTargets.clear()
            if (longestWordWidths.size > MAX_CACHED_TEXT_LAYOUTS) longestWordWidths.clear()

            val firstDay = floor(scrollDays).toInt()
            // One extra column covers the day sliding in while dragging.
            val days = (firstDay..firstDay + visibleDays).filter { (it - scrollDays) * dayWidth < gridRight - gridLeft - 0.5f }
            visibleAllDayLanes = (days.maxOfOrNull { eventLayout.allDayLaneCount(dateAt(it)) } ?: 0).coerceAtMost(maxAllDayRows)
            topMinute = topMinute.coerceIn(0f, maxTopMinute())
            val now = currentTime()

            // Not drawColor: a host that does not clip its children (Compose's AndroidView) would
            // let that flood everything around the view.
            fillPaint.color = gridBackgroundColor
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fillPaint)
            drawBody(canvas, days, now)
            drawHeader(canvas, days, now?.date)
            drawTimeColumn(canvas)
        }

        /** Left edge of a day column on screen; days run right to left in an RTL layout. */
        private fun dayLeft(dayIndex: Int): Float {
            val offset = (dayIndex - scrollDays) * dayWidth
            return if (isRtl) gridRight - offset - dayWidth else gridLeft + offset
        }

        private fun minuteY(minute: Float): Float = bodyTop + (minute - topMinute) * pxPerMinute

        private fun drawBody(
            canvas: Canvas,
            days: List<Int>,
            now: LocalDateTime?,
        ) {
            val bodyBounds = RectF(gridLeft, bodyTop, gridRight, height.toFloat())
            canvas.save()
            canvas.clipRect(bodyBounds)
            for (day in days) {
                val date = dateAt(day)
                val left = dayLeft(day)
                val right = left + dayWidth
                // The edge a day starts from: where its divider and the "now" dot sit.
                val startEdge = if (isRtl) right else left
                if (date == now?.date) {
                    fillPaint.color = todayColor
                    canvas.drawRect(left, bodyTop, right, height.toFloat(), fillPaint)
                }
                linePaint.color = lineColor
                for (hour in 0..HOURS_PER_DAY) {
                    val y = minuteY(hour * 60f)
                    if (y >= bodyTop && y <= height) canvas.drawLine(left, y, right, y, linePaint)
                }
                canvas.drawLine(startEdge, bodyTop, startEdge, height.toFloat(), linePaint)

                for (slot in eventLayout.timedSlots(date)) {
                    rect.set(
                        left + slot.left * dayWidth + eventGap / 2,
                        minuteY(slot.startMinute.toFloat()),
                        left + (slot.left + slot.width) * dayWidth - eventGap / 2,
                        minuteY(slot.endMinute.toFloat()) - eventGap,
                    )
                    if (rect.bottom < bodyTop || rect.top > height) continue
                    drawEvent(canvas, slot.event, bodyBounds)
                }

                if (now != null && date == now.date) {
                    val y = minuteY(now.hour * 60f + now.minute)
                    nowPaint.color = nowColor
                    canvas.drawLine(left, y, right, y, nowPaint)
                    canvas.drawCircle(startEdge, y, dp(4f), nowPaint)
                }
            }
            canvas.restore()
        }

        private fun drawHeader(
            canvas: Canvas,
            days: List<Int>,
            today: LocalDate?,
        ) {
            val headerBounds = RectF(gridLeft, 0f, gridRight, bodyTop)
            val contentTop = (headerHeight - headerContentHeight) / 2
            canvas.save()
            canvas.clipRect(headerBounds)
            fillPaint.color = headerColor
            canvas.drawRect(headerBounds, fillPaint)
            for (day in days) {
                val date = dateAt(day)
                val left = dayLeft(day)
                val isToday = date == today
                val centerX = left + dayWidth / 2
                timeTextPaint.color = if (isToday) accentColor else timeTextColor
                timeTextPaint.textAlign = Paint.Align.CENTER
                val weekday = TextUtils.ellipsize(weekdayLabel(date), timeTextPaint, dayWidth - eventGap * 2, TextUtils.TruncateAt.END)
                canvas.drawText(weekday, 0, weekday.length, centerX, contentTop - timeTextPaint.ascent(), timeTextPaint)

                val numberCenterY = contentTop + headerContentHeight - dayBadgeRadius
                if (isToday) {
                    fillPaint.color = accentColor
                    canvas.drawCircle(centerX, numberCenterY, dayBadgeRadius, fillPaint)
                }
                headerTextPaint.color = if (!isToday) headerTextColor else if (prefersDarkText(accentColor)) DARK_TEXT else Color.WHITE
                headerTextPaint.textAlign = Paint.Align.CENTER
                val baseline = numberCenterY - (headerTextPaint.ascent() + headerTextPaint.descent()) / 2
                canvas.drawText(localizedDayOfMonth(date), centerX, baseline, headerTextPaint)

                val allDaySlots = eventLayout.visibleAllDaySlots(date, maxAllDayRows)
                for (slot in allDaySlots) {
                    val top = headerHeight + slot.lane * allDayRowHeight
                    rect.set(left + eventGap / 2, top + eventGap / 2, left + dayWidth - eventGap / 2, top + allDayRowHeight - eventGap / 2)
                    drawEvent(canvas, slot.event, headerBounds)
                }
                val hidden = eventLayout.allDaySlots(date).size - allDaySlots.size
                if (hidden > 0) {
                    val rowCenterY = headerHeight + (maxAllDayRows - 0.5f) * allDayRowHeight
                    timeTextPaint.color = timeTextColor
                    canvas.drawText("+$hidden", centerX, rowCenterY - (timeTextPaint.ascent() + timeTextPaint.descent()) / 2, timeTextPaint)
                }
            }
            canvas.restore()
            linePaint.color = lineColor
            canvas.drawLine(0f, bodyTop, width.toFloat(), bodyTop, linePaint)
        }

        private fun drawTimeColumn(canvas: Canvas) {
            // The column sits before the grid: on the left, or on the right in an RTL layout.
            val left = if (isRtl) gridRight else 0f
            val right = left + timeColumnWidth
            canvas.save()
            canvas.clipRect(left, bodyTop, right, height.toFloat())
            fillPaint.color = gridBackgroundColor
            canvas.drawRect(left, bodyTop, right, height.toFloat(), fillPaint)
            timeTextPaint.color = timeTextColor
            // Labels hug the grid.
            timeTextPaint.textAlign = if (isRtl) Paint.Align.LEFT else Paint.Align.RIGHT
            val labelX = if (isRtl) left + dp(TIME_LABEL_PADDING_DP) else right - dp(TIME_LABEL_PADDING_DP)
            val centerOffset = (timeTextPaint.ascent() + timeTextPaint.descent()) / 2
            for (hour in 1 until HOURS_PER_DAY) {
                canvas.drawText(hourLabels[hour], labelX, minuteY(hour * 60f) - centerOffset, timeTextPaint)
            }
            canvas.restore()
            fillPaint.color = headerColor
            canvas.drawRect(left, 0f, right, bodyTop, fillPaint)
        }

        /** Draws [event] into [rect] and records the part of it inside [clip] as tappable. */
        private fun drawEvent(
            canvas: Canvas,
            event: WeekViewEvent,
            clip: RectF,
        ) {
            if (rect.width() <= 0f || rect.height() <= 0f) return
            fillPaint.color = event.color
            canvas.drawRoundRect(rect, eventRadius, eventRadius, fillPaint)
            drawEventText(canvas, event, clip.top)
            val visible = RectF(rect)
            if (visible.intersect(clip)) hitTargets += HitTarget(visible, event)
        }

        /**
         * Draws the title (and location) of [event] inside [rect]. Text never breaks inside a word:
         * it wraps at spaces, falls back to one ellipsized line, runs down the block when the block
         * is narrow and tall, or is left out when nothing readable fits.
         *
         * @param clipTop the text is pinned here when the event's own top is scrolled above it.
         */
        private fun drawEventText(
            canvas: Canvas,
            event: WeekViewEvent,
            clipTop: Float,
        ) {
            val textColor = if (prefersDarkText(event.color)) DARK_TEXT else Color.WHITE
            eventTextPaint.color = textColor
            eventTextPaint.textSize = eventTextSize
            eventTitlePaint.color = textColor
            eventTitlePaint.textSize = eventTextSize
            val lineHeight = eventTextPaint.fontSpacing
            val textTop = maxOf(rect.top, clipTop)
            val textHeight = rect.bottom - textTop
            val textWidth = rect.width() - eventPaddingX * 2
            val longestWord = longestWordWidths.getOrPut(event.title) { event.title.split(' ').maxOf { eventTitlePaint.measureText(it) } }

            canvas.save()
            canvas.clipRect(rect)
            when (eventTextMode(rect.width(), textHeight, lineHeight, longestWord, eventPaddingX)) {
                EventTextMode.Hidden -> Unit
                EventTextMode.Wrapped -> {
                    val maxLines = ((textHeight - eventPaddingY * 2) / lineHeight).toInt().coerceAtLeast(1)
                    val textLayout =
                        textLayouts.getOrPut(TextKey(event.title, event.location, textWidth.toInt(), maxLines)) {
                            // The title wraps at spaces; the location stays on one ellipsized line.
                            val location = event.location?.let { TextUtils.ellipsize(it, eventTextPaint, textWidth, TextUtils.TruncateAt.END) }
                            val text = SpannableString(listOfNotNull(event.title, location).joinToString("\n"))
                            text.setSpan(StyleSpan(Typeface.BOLD), 0, event.title.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            StaticLayout.Builder
                                .obtain(text, 0, text.length, eventTextPaint, textWidth.toInt())
                                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                                .setMaxLines(maxLines)
                                .setEllipsize(TextUtils.TruncateAt.END)
                                .setIncludePad(false)
                                .build()
                        }
                    // A slot barely one line tall centers its text instead of pushing it out with padding.
                    canvas.translate(rect.left + eventPaddingX, textTop + minOf(eventPaddingY, (textHeight - lineHeight) / 2))
                    textLayout.draw(canvas)
                }
                EventTextMode.SingleLine -> {
                    val top = textTop + minOf(eventPaddingY, (textHeight - lineHeight) / 2).coerceAtLeast(0f)
                    val title = TextUtils.ellipsize(event.title, eventTitlePaint, textWidth, TextUtils.TruncateAt.END)
                    canvas.drawText(title, 0, title.length, rect.left + eventPaddingX, top - eventTitlePaint.ascent(), eventTitlePaint)
                    val location = event.location
                    if (location != null && textHeight - eventPaddingY * 2 >= lineHeight * 2) {
                        val line = TextUtils.ellipsize(location, eventTextPaint, textWidth, TextUtils.TruncateAt.END)
                        canvas.drawText(line, 0, line.length, rect.left + eventPaddingX, top + lineHeight - eventTextPaint.ascent(), eventTextPaint)
                    }
                }
                EventTextMode.Vertical -> {
                    eventTitlePaint.textSize = eventTextSize * verticalTextScale(rect.width(), lineHeight)
                    val text = listOfNotNull(event.title, event.location).joinToString("  ")
                    val line = TextUtils.ellipsize(text, eventTitlePaint, textHeight - eventPaddingX * 2, TextUtils.TruncateAt.END)
                    // Turned a quarter clockwise around the block's top-left corner: x now runs
                    // down the block, and a negative y moves the baseline right, centering the line.
                    canvas.translate(rect.left, textTop)
                    canvas.rotate(QUARTER_TURN)
                    val baseline = -rect.width() / 2 - (eventTitlePaint.ascent() + eventTitlePaint.descent()) / 2
                    canvas.drawText(line, 0, line.length, eventPaddingX, baseline, eventTitlePaint)
                }
            }
            canvas.restore()
        }

        // endregion

        // region Touch

        private val gestureDetector =
            GestureDetector(
                context,
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onDown(e: MotionEvent): Boolean {
                        stopScrolling()
                        direction = Direction.NONE
                        didFling = false
                        return true
                    }

                    override fun onScroll(
                        e1: MotionEvent?,
                        e2: MotionEvent,
                        distanceX: Float,
                        distanceY: Float,
                    ): Boolean {
                        if (isScaling) return true
                        if (direction == Direction.NONE) {
                            // Lock to one axis per gesture so a diagonal drag cannot move both.
                            direction = if (abs(distanceX) > abs(distanceY)) Direction.HORIZONTAL else Direction.VERTICAL
                            parent?.requestDisallowInterceptTouchEvent(true)
                        }
                        if (direction == Direction.HORIZONTAL) {
                            scrollDays += (if (isRtl) -distanceX else distanceX) / dayWidth
                            notifyDateChanged()
                        } else {
                            topMinute += distanceY / pxPerMinute
                        }
                        invalidate()
                        return true
                    }

                    override fun onFling(
                        e1: MotionEvent?,
                        e2: MotionEvent,
                        velocityX: Float,
                        velocityY: Float,
                    ): Boolean {
                        if (isScaling) return true
                        didFling = true
                        if (direction == Direction.HORIZONTAL) {
                            flingToDay(if (isRtl) -velocityX else velocityX)
                        } else if (direction == Direction.VERTICAL) {
                            val maxY = (maxTopMinute() * pxPerMinute).toInt()
                            yScroller.fling(0, (topMinute * pxPerMinute).toInt(), 0, -velocityY.toInt(), 0, 0, 0, maxY)
                            postInvalidateOnAnimation()
                        }
                        return true
                    }

                    override fun onSingleTapUp(e: MotionEvent): Boolean {
                        if (dispatchTap(e.x, e.y, onEventClick, onEmptyClick)) playSoundEffect(SoundEffectConstants.CLICK)
                        return true
                    }

                    override fun onLongPress(e: MotionEvent) {
                        if (dispatchTap(e.x, e.y, onEventLongClick, onEmptyLongClick)) {
                            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        }
                    }
                },
            )

        private val scaleDetector =
            ScaleGestureDetector(
                context,
                object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                        isScaling = true
                        stopScrolling()
                        return true
                    }

                    override fun onScale(detector: ScaleGestureDetector): Boolean {
                        // Keep the minute under the fingers where it is while the grid stretches.
                        val focusY = detector.focusY - bodyTop
                        val focusMinute = topMinute + focusY / pxPerMinute
                        hourHeight *= detector.scaleFactor
                        topMinute = focusMinute - focusY / pxPerMinute
                        invalidate()
                        return true
                    }

                    override fun onScaleEnd(detector: ScaleGestureDetector) {
                        isScaling = false
                    }
                },
            )

        @Suppress("ClickableViewAccessibility") // Taps address individual events, not the view as a whole.
        override fun onTouchEvent(event: MotionEvent): Boolean {
            scaleDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                if (direction == Direction.HORIZONTAL && !didFling) animateToDay(scrollDays.roundToInt())
                direction = Direction.NONE
            }
            return true
        }

        /** @return true when a listener was called. */
        private fun dispatchTap(
            x: Float,
            y: Float,
            onEvent: ((WeekViewEvent) -> Unit)?,
            onEmpty: ((LocalDateTime) -> Unit)?,
        ): Boolean {
            val hit = hitTargets.lastOrNull { it.bounds.contains(x, y) }
            if (hit != null) {
                onEvent?.invoke(hit.event)
                return onEvent != null
            }
            if (x < gridLeft || x >= gridRight || y < bodyTop || onEmpty == null) return false
            val daysFromStart = (if (isRtl) gridRight - x else x - gridLeft) / dayWidth
            val date = dateAt(floor(scrollDays + daysFromStart).toInt())
            val minute = (topMinute + (y - bodyTop) / pxPerMinute).toInt().coerceIn(0, WeekViewLayout.MINUTES_PER_DAY - 1)
            onEmpty(LocalDateTime(date, LocalTime(minute / 60, minute % 60)))
            return true
        }

        // endregion

        // region Scrolling

        private fun maxTopMinute(): Float = (WeekViewLayout.MINUTES_PER_DAY - bodyHeight / pxPerMinute).coerceAtLeast(0f)

        private fun stopScrolling() {
            xScroller.forceFinished(true)
            yScroller.forceFinished(true)
            snapTarget = null
        }

        /** Lands a horizontal fling on a whole day, at least one day away in the fling direction. */
        private fun flingToDay(velocityX: Float) {
            xScroller.fling((scrollDays * dayWidth).toInt(), 0, -velocityX.toInt(), 0, Int.MIN_VALUE, Int.MAX_VALUE, 0, 0)
            val natural = (xScroller.finalX / dayWidth).roundToInt()
            xScroller.forceFinished(true)
            animateToDay(if (velocityX < 0) maxOf(natural, ceil(scrollDays).toInt()) else minOf(natural, floor(scrollDays).toInt()))
        }

        private fun animateToDay(day: Int) {
            val startX = (scrollDays * dayWidth).roundToInt()
            val distance = (day * dayWidth).roundToInt() - startX
            val duration = (abs(distance) / dayWidth * SNAP_MILLIS_PER_DAY).toInt().coerceIn(MIN_SNAP_MILLIS, MAX_SNAP_MILLIS)
            snapTarget = day
            xScroller.startScroll(startX, 0, distance, 0, duration)
            postInvalidateOnAnimation()
        }

        override fun computeScroll() {
            var animating = false
            if (xScroller.computeScrollOffset()) {
                scrollDays = xScroller.currX / dayWidth
                animating = true
            } else {
                // The scroller works in whole pixels; finish exactly on the day it was aimed at.
                snapTarget?.let {
                    scrollDays = it.toFloat()
                    snapTarget = null
                    animating = true
                }
            }
            if (yScroller.computeScrollOffset()) {
                topMinute = yScroller.currY / pxPerMinute
                animating = true
            }
            if (animating) {
                notifyDateChanged()
                postInvalidateOnAnimation()
            }
        }

        private fun notifyDateChanged() {
            val date = firstVisibleDate
            if (date != lastNotifiedDate) {
                lastNotifiedDate = date
                onFirstVisibleDateChanged?.invoke(date)
            }
        }

        // endregion

        override fun onSaveInstanceState(): Parcelable =
            Bundle().apply {
                putParcelable(STATE_SUPER, super.onSaveInstanceState())
                // Mid-animation, save the day being scrolled to.
                putString(STATE_DATE, dateAt(snapTarget ?: scrollDays.roundToInt()).toString())
                putFloat(STATE_TOP_MINUTE, topMinute)
                putFloat(STATE_HOUR_HEIGHT, hourHeight)
            }

        override fun onRestoreInstanceState(state: Parcelable?) {
            if (state !is Bundle) return super.onRestoreInstanceState(state)
            @Suppress("DEPRECATION") // The typed overload needs API 33.
            super.onRestoreInstanceState(state.getParcelable(STATE_SUPER))
            anchorDate = LocalDate.parse(state.getString(STATE_DATE) ?: return)
            scrollDays = 0f
            topMinute = state.getFloat(STATE_TOP_MINUTE, DEFAULT_TOP_MINUTE)
            hourHeight = state.getFloat(STATE_HOUR_HEIGHT, hourHeight)
        }

        private fun <T> redrawing(initial: T): ReadWriteProperty<Any?, T> = Delegates.observable(initial) { _, _, _ -> invalidate() }

        private fun dp(value: Float): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

        private fun sp(value: Float): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

        private companion object {
            const val HOURS_PER_DAY = 24
            const val MIN_TIME_COLUMN_DP = 52f
            const val TIME_LABEL_PADDING_DP = 8f
            const val DEFAULT_TOP_MINUTE = 8 * 60f
            const val NOW_REFRESH_MILLIS = 30_000L
            const val SNAP_MILLIS_PER_DAY = 220f
            const val MIN_SNAP_MILLIS = 180
            const val MAX_SNAP_MILLIS = 600
            const val MAX_CACHED_TEXT_LAYOUTS = 512

            /** Absorbs float error so a day that has just snapped into place counts as reached. */
            const val SNAP_EPSILON = 0.001f
            const val QUARTER_TURN = 90f
            const val DARK_TEXT = 0xFF0F172A.toInt()
            const val STATE_SUPER = "super"
            const val STATE_DATE = "date"
            const val STATE_TOP_MINUTE = "topMinute"
            const val STATE_HOUR_HEIGHT = "hourHeight"
        }
    }
