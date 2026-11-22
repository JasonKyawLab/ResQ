# ResQ

<img src="branding/icon-preview.svg" width="96" height="96" alt="ResQ logo — an SOS morse mark in red on black">

Offline-only Bluetooth emergency beacon for Android. No internet permission,
no backend, no account. A phone broadcasts its location over Bluetooth Low
Energy, nearby phones running ResQ see it, and every phone in range relays it
onward.

Disasters take out cell towers and Wi-Fi before they take out a phone's
Bluetooth radio, so ResQ assumes no connectivity at all: the manifest has no
`INTERNET` permission and there is no Google Play Services dependency.

## Screens

| Screen | Purpose |
|---|---|
| **Send** | Write your SOS message, start or stop broadcasting |
| **Scan** | Live list of SOS heard nearby; live ones are relayed automatically |
| **Settings** | Send interval, battery saver, permissions, clear received SOS, guide |

## How it works

- **Beacon:** a 17-byte BLE advertisement (sender ID, position, flags, hop count,
  update counter) that any scanner reads without connecting.
- **Full message:** a scanner connects over GATT and reads the full record
  (message, battery, position time, interval).
- **Relay:** scanning phones re-advertise live SOS with a higher hop count
  (capped at 6), so an SOS travels past the sender's own range.
- **Lifecycle:** received entries go Live, Signal lost, Marked safe, Archived and
  are kept 72 hours. A new sender ID is made at every Start; a Start within 30
  minutes of Stop continues the same SOS.

The exact byte format and the rules for changing it are in
[ROADMAP.md](ROADMAP.md).

## Code layout

```
app/src/main/java/com/resq/app/
├── core/   ble, location, battery, permissions, data (Room + DataStore),
│           service (foreground service), di (manual ServiceLocator), util
└── ui/     theme (OLED black), navigation (3-tab state switch), components,
            guide, sender, receiver, settings
```

Choices worth knowing: Jetpack Compose with no XML layouts, no Hilt (a small
manual `ServiceLocator`), raw `LocationManager` and `BluetoothManager` instead
of Play Services, GPS read once per send interval to save battery, and a
foreground service rather than a kill-proof background service.

## Build

Needs the Android SDK (`local.properties` with `sdk.dir=...`, not committed)
and JDK 17.

```bash
./gradlew test               # unit tests, no phone needed
./gradlew assembleDebug      # debug APK
./gradlew bundleRelease      # .aab for Play (signed if keystore.properties exists)
```

Release signing reads a gitignored `keystore.properties`; see
`keystore.properties.example`. Keep the keystore and its passwords outside this
repo.

## More

- [Privacy policy](https://resq-privacy.pages.dev) (source in `store/privacy/`)
- [Roadmap](ROADMAP.md), [Changelog](CHANGELOG.md)

## License

[MIT](LICENSE)
