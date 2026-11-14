# Cadence 2.4.20

## Highlights

- New single-letter **C** app icon: black-to-charcoal gradient with a bold connected script mark.
- Home countdown moved slightly below the semicircle's visual centre.
- Round-dot colon between hours, minutes and seconds.
- Automatic rotation segments now use lighter, container-matched backgrounds in both light and dark mode.
- Selected cycle-day tiles are white; unselected tiles are light grey.
- Restored the original system typeface with tabular numbers (`tnum`) so digits stay aligned.
- Glass buttons throughout the app.
- Leave flow uses `MochiListDisclosure`; the date picker expands inline, only applies after confirmation, and supports cancelling leave.
- Xiaomi HyperOS focus-notification payload (`miui.focus.param`) for the Super Island countdown.

## Xiaomi Super Island note

The app now emits the official focus-notification payload and timer data. Xiaomi's Super Island is still controlled by the system. The app must be granted focus-notification permission by Xiaomi; without it, Android shows the ongoing fallback notification instead.

## Assets

- `Cadence-2.4.20-Android.apk`
- `Cadence-2.4.20-source.zip`
- `SHA256SUMS.txt`
- macOS 1.4.1 DMG/ZIP

## Install

Android 8.0+; install over the existing app to keep data. macOS 1.4.1 remains unchanged.
