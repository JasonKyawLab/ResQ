# Changelog

All notable changes to ResQ are documented here. Format follows
[Keep a Changelog](https://keepachangelog.com/en/1.0.0/); versioning
follows [Semantic Versioning](https://semver.org/) — see the
[README](README.md#technical-decisions) for the versionCode/versionName
scheme used for each release.

## [Unreleased]

Work on the way to the first Play Store release (versionName 1.0.0; the closed
test is versionCode 2). On the day 1.0.0 is published, put this content under a
dated `## [1.0.0] - YYYY-MM-DD` heading.

### Added
- Send screen: editable SOS message (180-byte cap with live counter), preset
  cards after Start, quick-phrase chips, "My location" line, Start/Stop, live
  status (send frequency, last position, last sent, battery).
- Scan screen: live list of nearby SOS with stages (Live, Signal lost, Marked
  safe, Archived), position age, signal strength, hop count, Copy coordinates,
  Dismiss, and automatic relay of live entries.
- Settings: send frequency, battery saver, permission and radio status with fix
  actions, battery-optimisation row, Clear all received SOS, "How ResQ works".
- First-launch screen and guide, shown once.
- Offline BLE mesh: a 17-byte beacon (marker, version, sender ID, position,
  flags, hop, update counter) plus a GATT record with extras (position time,
  SOS started, interval). No internet permission, no Play Services.
- New sender ID at every Start; Start within 30 minutes of Stop continues the
  same SOS. "Cancelled" beacon on Stop, shown as "Marked safe (unverified)".
- Resume of a running SOS or scan after the app is killed (up to 12 hours).
- Broadcast with no GPS fix as "position unknown", with a heartbeat, and repeat
  of the last position when GPS is lost.
- Release signing, `.aab` build, new application id `com.jasonkyawlab.resq`.
- Custom launcher icon and in-app logo; privacy policy at
  https://resq-privacy.pages.dev; Play Store graphics.
- 106 unit tests for the protocol, update rules, lifecycle and session logic.

### Changed
- compileSdk/targetSdk 36 (Google Play requirement from 31 Aug 2026).
- Tabs switch instantly with a plain state switch; the navigation library was
  removed.
- Hourly removed from the send choices (Battery saver already forces hourly).

### Fixed
- Relayed SOS that kept appearing live after the sender stopped ("zombie"
  relay): only a new counter or a direct hearing refreshes the live clock.
- Crash in the location listener on Android 10 and below.
- Crash stopping the foreground service on Android 6 (`stopForeground`).
- Message box lag and keyboard layout; Scan card layout on large fonts;
  Settings overflow; stale Bluetooth/location status; send loop waiting out the
  full interval after a failed fix; location fetch trying only one provider.
