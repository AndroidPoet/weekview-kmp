# Changelog

## 0.2.0 (unreleased)

Changed

- Hour labels, weekday names and day numbers follow the device's language and 12/24-hour clock
  instead of always showing `09:00` and `MON`. New public functions: `localizedHourLabel`,
  `localizedWeekdayLabel`, `localizedDayOfMonth`; Compose default is `WeekViewDefaults.rememberTimeLabel()`.
- The hour column grows to fit its widest label; headers and all-day rows grow with the font scale.
  `WeekViewStyle.timeColumnWidth`, `headerHeight` and `allDayRowHeight` are now minimums.
- At most `maxAllDayRows` all-day rows are shown (3 by default), with a "+N" marker for the rest, so
  all-day events can no longer push the hour grid off the screen.
- View: `firstVisibleDate` and `onFirstVisibleDateChanged` report the first column that is at least
  partly visible, not the nearest one.
- `WeekViewLayout` lays out a date when it is first asked for. An event spanning years no longer
  costs memory for every date it covers.

Added

- View: right-to-left layouts, and `currentTime` to supply or hide "now".
- `WeekViewLayout.visibleAllDaySlots(date, maxRows)`.

Fixed

- Thousands of simultaneous events froze the layout (8,000 in one hour: 5.3 s, now 0.2 s).
- Events overlapping by less than a minute were drawn on top of each other.
- Compose: adding to a `mutableStateListOf` did not show the new event; replacing the
  `WeekViewState` kept the old dates; pinch-zoom jumped while the grid was still filling the
  viewport; state remembered inside `eventContent` could move to another event.
- View: resizing during a scroll animation showed the wrong date; the text cache was rebuilt on
  every frame once more than 256 titles were visible.
- wasmJs: weekday names for years 0 to 99.

## 0.1.0

First release.

- `weekview`: `WeekViewLayout`, the overlap layout engine (Kotlin Multiplatform), and `WeekView`, an Android `View`.
- `weekview-compose`: `WeekView` for Compose Multiplatform, with `rememberWeekViewState` and `WeekViewStyle`.
