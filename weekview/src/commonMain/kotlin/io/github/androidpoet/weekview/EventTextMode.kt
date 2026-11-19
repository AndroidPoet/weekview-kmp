package io.github.androidpoet.weekview

/** How an event's title is drawn inside its block. Shared so the View and Compose renderers agree. */
public enum class EventTextMode {
    /** The block is too small for any readable text. */
    Hidden,

    /** Every word fits the width: wrap at spaces over as many lines as the height allows. */
    Wrapped,

    /** A word is wider than the block: one ellipsized line, never a word broken across lines. */
    SingleLine,

    /** A narrow, tall block: one line rotated to run down the block. */
    Vertical,
}

/**
 * Picks the [EventTextMode] for a block of [width] x [height] pixels.
 *
 * @param lineHeight height of one line of the event text.
 * @param longestWordWidth width of the widest word of the title.
 * @param horizontalPadding space kept free on the left and right of horizontal text.
 */
public fun eventTextMode(
    width: Float,
    height: Float,
    lineHeight: Float,
    longestWordWidth: Float,
    horizontalPadding: Float,
): EventTextMode {
    val innerWidth = width - 2 * horizontalPadding
    return when {
        height < lineHeight * MIN_LINE_FRACTION -> EventTextMode.Hidden
        longestWordWidth <= innerWidth -> EventTextMode.Wrapped
        innerWidth >= lineHeight * COMFORTABLE_WIDTH_LINES -> EventTextMode.SingleLine
        height >= lineHeight * VERTICAL_MIN_HEIGHT_LINES && width >= lineHeight * VERTICAL_MIN_WIDTH_LINES -> EventTextMode.Vertical
        innerWidth >= lineHeight -> EventTextMode.SingleLine
        else -> EventTextMode.Hidden
    }
}

/** Font scale for [EventTextMode.Vertical] so one rotated line fits a block [width] pixels wide. */
public fun verticalTextScale(
    width: Float,
    lineHeight: Float,
): Float = (width * VERTICAL_FILL / lineHeight).coerceAtMost(1f)

private const val MIN_LINE_FRACTION = 0.8f
private const val COMFORTABLE_WIDTH_LINES = 2.5f
private const val VERTICAL_MIN_HEIGHT_LINES = 3f
private const val VERTICAL_MIN_WIDTH_LINES = 0.75f
private const val VERTICAL_FILL = 0.9f

/**
 * Whether dark text reads better than white on the ARGB [color], by perceived brightness.
 * Shared so both renderers pick the same text color for the same event.
 */
public fun prefersDarkText(color: Int): Boolean {
    val red = color shr RED_SHIFT and CHANNEL_MASK
    val green = color shr GREEN_SHIFT and CHANNEL_MASK
    val blue = color and CHANNEL_MASK
    return (LUMA_RED * red + LUMA_GREEN * green + LUMA_BLUE * blue) / CHANNEL_MASK > LIGHT_THRESHOLD
}

private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val CHANNEL_MASK = 0xFF
private const val LUMA_RED = 0.299f
private const val LUMA_GREEN = 0.587f
private const val LUMA_BLUE = 0.114f
private const val LIGHT_THRESHOLD = 0.6f
