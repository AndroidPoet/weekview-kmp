package io.github.androidpoet.weekview.compose

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun deviceUses24HourClock(): Boolean? {
    // Read so a locale change recomposes this.
    LocalConfiguration.current
    return DateFormat.is24HourFormat(LocalContext.current)
}
