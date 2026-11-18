# Changelog

All notable changes to ResQ are documented here. Format follows
[Keep a Changelog](https://keepachangelog.com/en/1.0.0/); versioning
follows [Semantic Versioning](https://semver.org/) — see the
[README](README.md#technical-decisions) for the versionCode/versionName
scheme used for each release.

## [Unreleased]

Everything below is pre-launch work on the way to the first Play Store
submission (versionCode 1, versionName 1.0.0). Move this section's
content under a new dated `## [1.0.0] - YYYY-MM-DD` heading the day
that build is actually published.

### Added
- Sender screen: editable SOS message, GPS auto-attached per broadcast
  cycle, Start/Stop toggle, live status (broadcasting/idle, send
  frequency, last coordinate, last sent, battery).
- Receiver screen: live log of nearby SOS beacons (sender ID, message,
  coordinates, signal strength, last-seen time), with automatic
  multi-hop relay while scanning.
- Settings screen: send-frequency picker, battery saver (forces the
  longest interval), per-permission status (Bluetooth adapter/scan/
  advertise/connect, location permission/services, notifications) with
  inline fix actions, sender ID display.
- Offline-only BLE mesh: a compact always-on advertisement carries
  sender ID + GPS to any scanner with zero connection required; a GATT
  pull fetches the full message when available. No Play Services
  dependency, no network permission anywhere in the app.
- Release signing and `.aab` build pipeline, opt-in via a gitignored
  `keystore.properties`.
- Custom SOS-morse-mark launcher icon and in-app logo mark.
- Privacy policy covering the app's on-device-only data handling, hosted at
  https://resq-privacy.pages.dev.
- "My location" setting (room / floor / building): saved once, sent ahead
  of every SOS message so it still goes out when the user can barely type.
- Quick-phrase chips (Trapped, Injured, Need medical help, ...) in a strip
  above the keyboard while typing the message.
- Live byte counter on the message box and a hard cap at the 180-byte
  broadcast limit; truncation never splits a character or emoji.
- Copy button for an SOS entry's coordinates on the Scan screen.

### Changed
- compileSdk/targetSdk raised from 35 to 36: Google Play requires new
  apps and updates to target Android 16 (API 36) from Aug 31, 2026.

### Fixed
- Message box felt stuck: typing round-tripped through storage and lagged,
  and the keyboard could squash the screen on older Android versions (the
  app now draws edge-to-edge everywhere).
- Scan card ran the coordinates and time together on large system fonts;
  now stacked, and "Seen" is clearer as "Last heard".
- Crash when stopping the foreground service on Android 6.0 (API 23):
  `stopForeground(int)` only exists from API 24; now uses
  `ServiceCompat.stopForeground`. Found by release lint (NewApi).
- Settings screen overflow that made the Permissions section
  unreachable once it grew past screen height.
- Permission-row buttons clipping off-screen on long labels.
- Bluetooth/location/permission status going stale until a full
  navigate-away-and-back, instead of updating live when toggled from
  Quick Settings or the system Settings app.
- Send loop waiting out the full user-configured interval (up to an
  hour) after a failed GPS fix instead of retrying promptly once
  permission/Bluetooth/location changed, or on a short backoff while
  no fix has ever been obtained.
- Location fetch only ever trying one provider (GPS or network, never
  both), missing a usable fix when the first one was slow or disabled.
