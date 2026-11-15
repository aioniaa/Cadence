# Cadence 2.5.0

## Highlights

- **In-app updates**: on launch, Cadence checks the latest GitHub Release, shows a Mochi dialog, downloads the APK inside the app, and hands it to the Android package installer.
- **Countdown progress semantics**:
  - before work: the semicircle fills up as the start countdown decreases;
  - during work: the semicircle drains as the off-work countdown decreases;
  - rest days: shows a countdown to the next shift and fills with the same waiting logic.
- Round-dot separators between hours, minutes and seconds.
- Single-letter **C** icon with black/charcoal gradient and a bold connected script mark.
- Mochi glass presentation and the 2.4.x UI refinements remain.

## Update note

Android still requires the one-time “install unknown apps” permission for the package installer. The download itself happens inside Cadence; it does not open the GitHub release page.

## Assets

- `Cadence-2.5.0-Android.apk`
- `Cadence-2.5.0-source.zip`
- `SHA256SUMS.txt`
- macOS 1.4.1 DMG/ZIP

## Install

Android 8.0+; install over the existing app to keep data. macOS 1.4.1 remains unchanged.
