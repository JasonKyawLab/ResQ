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
- Privacy policy draft covering the app's on-device-only data handling.

### Fixed
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
