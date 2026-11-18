# ResQ

<img src="branding/icon-preview.svg" width="96" height="96" alt="ResQ logo — an SOS morse mark in red on black">

Offline-only BLE emergency beacon for Android. No internet permission,
no backend, no account — a phone broadcasts its location over Bluetooth
Low Energy, nearby phones running the same app see it, and every phone
in range relays it onward.

## Why offline-only

Disasters take out cell towers and Wi-Fi before they take out a
phone's battery or its Bluetooth radio. ResQ assumes zero connectivity
by design: no network calls exist in the codebase (the manifest
doesn't even request `INTERNET`), and it has no Google Play Services
dependency, so it works on a phone that's never seen a SIM or a Play
Store update.

## The three screens

| Screen | Purpose |
|---|---|
| **Send** | Edit your SOS message, see your last GPS fix and battery, start/stop broadcasting |
| **Scan** | Live log of every SOS beacon heard nearby — relays them automatically while scanning |
| **Settings** | Send/GPS interval, battery saver, permission and radio status |

## How the mesh works

Every beacon is a small (16-byte) BLE advertisement containing a
sender ID, a fixed-point GPS coordinate, and a flag telling scanners
whether a fuller free-text message is available. Any phone scanning
sees that location *without connecting to anything* — advertisements
are the one thing on BLE that reaches every nearby device, broadcast
or not. If the flag says a message is available, a scanner opportunistically
connects over GATT and reads the full `SosRecord` (message, exact
coordinates, battery, hop count).

Every phone that's scanning also **relays**: it keeps the freshest
sighting it's heard from someone else and periodically re-advertises
it with an incremented hop count (capped at `MAX_HOP_COUNT = 6`), so
an SOS can hop phone-to-phone past the sender's own BLE range. A
device's own SOS always wins the advertising slot over relaying someone
else's — see the class doc on `ResQForegroundService` for the full
reasoning and the v2 extension point (concurrent multi-advertiser relay).

## Architecture

```
app/src/main/java/com/resq/app/
├── core/                     # Everything with no UI dependency
│   ├── ble/                  # Wire protocol, advertiser, scanner, GATT client/server,
│   │                         # BluetoothStateObserver (reacts to radio on/off)
│   ├── location/             # Platform LocationManager wrapper (no Play Services),
│   │                         # LocationCapability + LocationProviderObserver
│   ├── battery/               # Battery level snapshot
│   ├── permissions/           # PermissionManager, tri-state PermissionStatus,
│   │                          # ReadinessRefreshTrigger (see Technical decisions below)
│   ├── data/
│   │   ├── model/             # BroadcastInterval, SignalStrength — shared value types
│   │   ├── settings/           # DataStore-backed user settings + device identity
│   │   ├── db/                 # Room: the receiver's SOS log
│   │   └── repository/         # Bridges BLE detections ↔ the log
│   ├── service/                # ResQForegroundService + the shared SessionController state
│   ├── di/                     # ServiceLocator — manual DI, see its doc for why
│   └── util/                   # Logger (compiles out in release builds)
└── ui/
    ├── theme/                  # Pure OLED-black Compose theme
    ├── navigation/              # 3-tab NavHost
    ├── components/               # Shared composables (toggle button, status/action
    │                             # banners, AppMark logo, badges)
    ├── sender/, receiver/, settings/   # One screen + ViewModel + UiState per package

branding/                     # Non-build preview copies of the launcher icon/mark —
                              # this README's logo, and visual review outside Android
                              # Studio. Not referenced by the app itself; the actual
                              # assets are app/src/main/res/drawable/ic_app_mark.xml
                              # and ic_launcher_foreground.xml
```

**Adding a fourth screen**: add a package under `ui/`, a `ResQDestination`
entry, and a `composable(...)` block in `ResQNavHost`. Nothing else
needs to change — routing, theming and DI are all already generic.

**Changing the wire protocol**: bump `PROTOCOL_VERSION` in
`core/ble/BlePayload.kt`, add a new `encodeVX`/`decodeVX` branch, and
leave the old version decodable so mixed-version devices on the same
mesh don't silently drop each other. The codec already returns `null`
(not a crash) on an unrecognised version.

## Technical decisions

- **minSdk 23 / targetSdk 35** — BLE central+peripheral roles and the
  modern permission model are reliable from API 23 on; API 18-22 would
  add real complexity for devices that are a rounding error of current
  installs.
- **Jetpack Compose + Material3**, no XML layouts — smaller, more
  maintainable, and the custom OLED theme is simpler to express as
  Compose tokens than as a style hierarchy.
- **No Hilt/Dagger** — a flat `ServiceLocator` covers ~8 singletons in
  a single-process app; an annotation-processor DI framework is a
  tax with no payoff at this size (see its class doc).
- **No Google Play Services** — raw `LocationManager`/`BluetoothManager`
  only, so the app works on a phone without GMS installed or reachable.
- **GATT connect-and-pull over BLE5 Extended Advertising** — Extended
  Advertising needs BLE5 hardware and isn't universal; GATT has been
  reliable since API 21. The legacy advertisement alone (location +
  sender ID) already reaches every scanner with zero connection
  required; GATT is strictly a best-effort upgrade for the message.
- **Foreground service, not a kill-proof background service** — matches
  the actual use case (open the app, it keeps running with the screen
  off) without the cross-OEM flakiness of trying to survive a force-kill.
- **GPS fetched once per broadcast interval**, never continuously — the
  single biggest battery lever in the app.
- **Room for the structured SOS log, DataStore for settings** — each
  used for what it's actually good at, not a single catch-all.
- **Reactive readiness, not polling** — `ReadinessRefreshTrigger`
  merges real OS broadcasts (`BluetoothAdapter.ACTION_STATE_CHANGED`,
  `LocationManager.PROVIDERS_CHANGED_ACTION`) with a tick bumped after
  permission-dialog results, so every screen's Bluetooth/location/
  permission status updates live — toggling Bluetooth from Quick
  Settings while a screen is open reflects immediately, no navigate-
  away-and-back required.
- **Every enabled location provider gets tried, not just the first** —
  `LocationProvider` falls through GPS → network before giving up on a
  cycle; with no Play Services fused location to smooth over a slow or
  signal-starved GPS cold start, trying only one provider left real
  fixes on the table. A failed cycle also retries on a short backoff
  (30s) rather than waiting out the full user-configured interval,
  which could otherwise make the very first fix after starting
  broadcast seem to never arrive.

## Versioning

Two separate fields in `app/build.gradle.kts`, both bumped on every
release:

- `versionCode` — an integer Google Play uses internally; must
  strictly increase on every upload. Just `+1` every time, no encoded
  meaning — simplest scheme, nothing to get wrong.
- `versionName` — the human-readable string users see, following
  [semver](https://semver.org/): patch for bug-fix-only releases,
  minor for backward-compatible features, major for breaking/large
  changes (e.g. the v2 chat feature below).

Tag the release commit to match (`git tag v1.1.0`) so any build on
Play Store traces back to an exact commit, and record what shipped in
[CHANGELOG.md](CHANGELOG.md).

## Building

Requires a local Android SDK (`local.properties` with `sdk.dir=...`,
not committed) and JDK 17.

```bash
./gradlew assembleDebug      # debug APK
./gradlew assembleRelease    # minified + resource-shrunk release APK (unsigned until you set up signing)
./gradlew bundleRelease      # the .aab Play Store actually wants
```

## Play Store release signing

Release builds are unsigned until a `keystore.properties` file exists
at the project root — see `keystore.properties.example` for the exact
`keytool` command and the fields to fill in. That file (and the `.jks`
it points to) are gitignored; once it's in place, `bundleRelease`
signs automatically with zero code changes. Keep the `.jks` and its
passwords somewhere durable outside this repo — losing them means you
can never ship an update to the same Play Store listing again.

## Privacy policy

The policy is hosted at <https://resq-privacy.pages.dev> (a Cloudflare
Pages project; the page itself lives in `store/privacy/index.html`). It
covers exactly what the app's own code already guarantees: no network
calls, nothing sent to the developer, data only ever leaving a device via
local Bluetooth broadcast to other nearby devices running ResQ. That URL
is what goes into Play Console. If the policy changes, edit that file and
upload it again to the same Pages project; the URL stays the same.

## Roadmap

What is decided for v1, planned for v1.1 and v2, and the known limits
are tracked in [ROADMAP.md](ROADMAP.md). The headline v2 feature is
two-way chat between a sender and a specific helper, matched by a
`check_id` per SOS session. That needs bidirectional BLE messaging and
session-matching logic that is substantial enough to scope once v1's
broadcast/relay path is proven in the field; see `ResQForegroundService`'s
class doc for where the relay loop would need to grow to support it.
