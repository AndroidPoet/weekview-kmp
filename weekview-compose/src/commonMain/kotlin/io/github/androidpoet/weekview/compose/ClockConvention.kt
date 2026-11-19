package io.github.androidpoet.weekview.compose

import androidx.compose.runtime.Composable

/**
 * The user's explicit 12/24-hour choice where the platform keeps one apart from the locale
 * (Android); `null` where the locale already says it all.
 */
@Composable
internal expect fun deviceUses24HourClock(): Boolean?
