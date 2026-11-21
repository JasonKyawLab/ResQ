# ResQ

ResQ is an offline SOS beacon for Android. A person in trouble (for example
trapped under a collapsed building) taps one button, and their phone
broadcasts their GPS position and a short message over Bluetooth Low Energy.
Any other phone running ResQ that is nearby picks it up, shows it to its
owner, and relays it onward so it can travel further than one phone's range.
There is no account, no server and no internet permission: everything
happens between phones, and nothing is ever sent to the developer.

This file is the working plan. Each release has its own section: what must be
built, and what must be checked before it ships. Update it as items are
finished, and move finished work into [CHANGELOG.md](CHANGELOG.md).

Status key: `[ ]` to do, `[x]` done, `[?]` still undecided.

Current state: **v1.0.0 is not released.** The first Play Store build is
still being finished, so the Bluetooth format below can still change freely
until the day it ships (see "Format rules" at the bottom).

---

## How it works

Two messages travel over Bluetooth. The numbers are made-up examples, written
in hex (two characters per byte), biggest part first. "Built" means the code
does it today; "planned" means it is a v1.0.0 item below.

### 1. The beacon (always on, no connection needed)

Sent about once a second to every phone in range. 17 bytes planned (16 today).

```
A1 | 00 ED 28 99 | 00 21 89 15 | 00 92 96 E8 | 01 | 00 | 00 2A
```

| Part | Bytes | Example | What it means | Status |
|---|---|---|---|---|
| Marker + version | 1 | `A1` | The first half (`A`) is a fixed ResQ marker and the second half (`1`) is the format version. A phone drops any packet whose marker is not `A`, which filters out most accidental matches from other Bluetooth devices, and ignores a version it does not know. Today the byte is plain `01`. | planned |
| Sender ID | 4 | `00 ED 28 99` | A random number, shown as `#ED2899`. Not linked to a name or phone number. A **new one is made every time the user taps Start**, so nobody can follow one phone across days. Today it is made once on first install. | planned |
| Latitude | 4 | `00 21 89 15` | Degrees x 100,000: 2,197,781 is **21.97781** (about 1 m). South is negative. | built |
| Longitude | 4 | `00 92 96 E8` | Same idea: 9,606,888 is **96.06888**. West is negative. | built |
| Flags | 1 | `01` | Switches added together: 1 = full message waiting, 2 = relayed copy, 4 = battery critical, 8 = cancelled (planned), 16 = position unknown (planned). Bits 32, 64 and 128 are reserved. | first three built |
| Hop count | 1 | `00` | Phones that have relayed it. 0 = heard directly from the sender. Capped at 6. | built |
| Update counter | 2 | `00 2A` | 42. Starts at 0 with each new sender ID, then goes up only on a new GPS fix or a message edit. Lets a phone tell "same update" from "newer update" without connecting. Today it is 1 byte taken from the clock, which changes every second and cannot do this job. | planned |

### 2. The full record (fetched by connecting to the sender)

A phone connects when the beacon says a message is waiting, reads this once,
and disconnects. Core part first, then a list of optional extras.

```
A1 | 00 ED 28 99 | 00 21 89 15 | 00 92 96 E8 | 00 1D | 52 6D 20 33 ... 72 65 64 | 27 | 00
    | 01 04 6A FC 22 A1 | 02 04 6A FC 1A 60 | 03 01 05
```

| Part | Bytes | Example | What it means | Status |
|---|---|---|---|---|
| Marker + version | 1 | `A1` | Same as the beacon. | planned |
| Sender ID | 4 | `00 ED 28 99` | Same as the beacon. | built |
| Latitude, longitude | 4 + 4 | `00 21 89 15`, `00 92 96 E8` | Same as the beacon. | built |
| Message length | 2 | `00 1D` | 29, the number of message bytes that follow. | built |
| Message | up to 180 | `52 6D 20 33 ...` | UTF-8 text: "Rm 305, 3F \| Trapped. Injured". The saved "My location" line, then ` \| `, then the typed message. Both share the 180-byte limit. Burmese and Thai use 3 bytes per character. | built |
| Battery | 1 | `27` | 39 percent. | built |
| Hop count | 1 | `00` | Same as the beacon. | built |
| Extras | any | see below | A list of `type, length, value` items (TLV) until the end of the record. | planned |

Extras planned for v1.0.0 (each is `type`, `length`, then the value):

| Type | Length | Example | What it means |
|---|---|---|---|
| `01` position time | 4 | `01 04 6A FC 22 A1` | The sender's phone clock when the fix was taken, in seconds since 1 Jan 1970 UTC: 1,794,908,833 is **17 Nov 2026, 09:47:13 UTC**. Time zones do not matter. It is only as right as the sender's clock. |
| `02` SOS started | 4 | `02 04 6A FC 1A 60` | Same kind of number: 09:12:00 UTC, when the person pressed Start. Tells a rescuer how long they have been in trouble. |
| `03` send interval | 1 | `03 01 05` | Minutes between updates (5). Lets a receiver tell "slow sender" from "gone silent". |

Reader rules for extras:
1. A type the phone does not know is skipped, using the length.
2. If an item's length runs past the end of the record, stop there and keep
   everything read so far.
3. Type `00` is reserved.
4. If a type appears twice, the first one wins.

Size: at most 197 bytes today, at most 212 planned. One packet carries 244, so
it is still a single read.

### How the two fit together

- The beacon answers: who, roughly where, is there more, and which update is
  this. The record answers everything else.
- A relayed copy keeps the original sender's ID, position, message and times.
  Only the hop count goes up and the "relayed" flag is set.
- A phone compares sender ID + update counter from the beacon. Same or older:
  it does nothing. Newer: it connects and reads the record.
- Nothing is encrypted. Anyone with a Bluetooth scanner, or the app itself, can
  read all of this. That is deliberate: an SOS only works if strangers can read
  it with no setup. The app says so before first use.

---

# **v1.0.0**

First Play Store release (`versionCode 1`, `versionName 1.0.0`). Not released.
Everything in "must do" has to be built, and everything in "must check" has to
pass, before the build is signed and uploaded.

## Done so far

- [x] Sender, Receiver and Settings screens; BLE send, scan, GATT pull and relay
- [x] Release signing and `.aab` build, API 36, 16 KB alignment checked
- [x] Message box fixed (typing lag, keyboard layout, 180-byte cap with counter)
- [x] "My location" line in Settings, sent ahead of every message
- [x] Quick-phrase chips in a strip above the keyboard while typing
- [x] Copy button for coordinates on the Scan screen
- [x] MTU: the client already asks for 247 on every connection, and the server
      already answers reads by offset, so long values are not truncated
- [x] Privacy policy hosted: https://resq-privacy.pages.dev (source in
      `store/privacy/index.html`)
- [x] Two-phone test: beacon and full message received on a second phone

## Decided in review (still to build)

- [x] **Marker `A1`** in the first byte of the beacon and the record (see above).
      It filters accidental matches. It does not save battery: the phone's own
      scan filter on service ID `0xFFF1` already drops most other devices before
      the app sees them.
- [x] **New sender ID at every Start.** Also:
  - The saved "SOS is active" state keeps the same ID, counter and start time,
    so an SOS resumed after a kill is still the same SOS.
  - The phone remembers its recent own IDs (about 72 hours), so a relay that
    echoes an old session back is still recognised as its own.
  - **Start within 30 minutes of Stop continues the same SOS** (same ID, counter
    carried on, original start time kept). A panicking user who taps Stop and
    Start repeatedly leaves one entry on the rescuers' phones, not a pile. A newer
    non-cancelled update must clear a "cancelled" mark. After 30 minutes a Start is a
    new SOS with a new ID. Built, with unit tests.
  - **Editing the message while sending** sends an update about 2 seconds after
    typing stops (counter goes up, last known fix reused, its own position time still
    sent). Stop cancels a pending edit so nothing goes out after the SOS ends.
    Built; the timing needs a real-phone check.
  - The sender ID shown in Settings is now "Last SOS ID" (built).
  - The privacy policy and the Play data safety answer say the ID changes at
    each Start.
- [ ] **"Clear all received SOS"** in Settings, with a confirmation dialog.
- [ ] **Own echo shown on the Send screen.** When a relay of your own SOS is
      heard, show "Another phone has passed on your SOS". It does not mean help
      is coming, so never word it that way. If none is heard, say nothing
      alarming ("No relay heard yet" is fine, never "nobody heard you"). This
      needs the phone to scan while it is sending (a low-power scan, small
      battery cost). Decision: keep this small echo-check scan running while
      sending even when the user's Scan switch is off, so the indicator is not
      misleading; or grey the indicator out. Never show "nobody around" just
      because the scanner is off.
- [x] **A cancel shows as "Marked safe by sender (unverified)"**, dimmed and
      sorted lower, never silently deleted. Dismiss removes it.
- [x] **No checksum in the beacon.** Bluetooth already checks for corruption. The
      marker, the range checks and our own 128-bit connection ID cover
      identification. The spare bytes stay free.

## Must do

### 1. Format (first, it cannot be changed after release)
- [x] Record extras as `type, length, value` (types `01`, `02`, `03` above) with
      the four reader rules.
- [x] Beacon flags: 8 = cancelled, 16 = position unknown. Bits 32 to 128 stay
      reserved (write 0, ignore on read).
- [x] Marker + version byte `A1` in the beacon and the record; drop packets with a
      different marker; ignore an unknown version.
- [x] New random sender ID at every Start.
- [x] Update counter: 2 bytes, starts at 0 with each new sender ID, saved while the
      SOS is active, only goes up. Changes only on a new GPS fix or a message edit.
- [x] Do not spend the spare bytes in the advertisement (about 3 left after the
      17-byte beacon). Keep them as headroom.
- [x] Room database migration for the new columns, so phones that already have
      the app do not crash on update.

### 2. Keep the SOS running
- [x] Save "an SOS is active" (with its sender ID, update counter and start time)
      on the phone, clear it on Stop, and resume it when Android restarts the service
      and when the app is opened (only if saved less than 12 hours ago). **Bug found
      in the code:** when Android kills the process and restarts the service, it
      came back with no instruction, saw nothing running, and stopped itself, so
      the SOS silently ended. Fixed and checked on the Samsung.
- [x] Also save "scanning is on" and resume it the same way. Built: switching Scan on
      saves it (refreshed every 10 min while alive); only switching it off clears it,
      a kill does not. After a kill or when the app is opened it resumes if the last
      sign of life was under 12 hours ago and Bluetooth is ready; an older flag is
      cleared. One resume action (`ACTION_RESUME`) brings back an SOS, scanning or both.
      Debug line in Settings: "Scanning saved". Still to test on the phone.
- [x] Make sure the restarted service shows its notification straight away.
- [x] Battery optimisation row in Settings (under Permissions). Changed after the phone test: an
      app can only see Android's own "don't optimise" list, not Samsung's Never sleeping apps or
      other makers' lists, and the stock "Allow background usage" switch is the default Optimized
      state, so a red "Not set" was often wrong. Now it is green "Not restricted" only when
      Android confirms the exemption, and otherwise a plain row with an Open button (no red).
      Open goes to Android's battery optimisation page, or ResQ's own settings page if missing.
      Maker-specific steps (Samsung, Xiaomi, Oppo, Huawei) were tried and removed: too much text
      on the screen. The row sits under the Set / Not set / Unavailable legend. No special
      permission is used. Still to test on a second brand.
- [x] Check that advertising keeps running after another phone connects and
      disconnects; restart it if it does not.

### 3. Connections
- [x] Wait a random 0.5 to 3 seconds before connecting to a sender, so a crowd
      of helpers does not all connect at once.
- [x] Back off after a failed connection (for example status 133).
- [x] One connection per update (see the rules below). Today nothing limits it, so
      a phone in range of a flagged beacon can reconnect over and over.
- [x] On the sender: disconnect a helper after the read or a time limit, and cap
      simultaneous connections, so one slow helper cannot hold a slot.
- [x] If the larger MTU request returns false, carry on instead of waiting for the
      8 second timeout.

### 4. Once-only handling and relay rules
When a phone hears sender X, update number u, hop h:

| Case | Action |
|---|---|
| X is new | Save, fetch the full record, relay later |
| u newer | Save, fetch, relay later (replaces the old data) |
| u same | No fetch, no relay. Refresh "heard", keep the lowest hop |
| u older | Ignore |

- [x] Compare update numbers with wraparound.
- [x] Keep the lowest hop count ever heard. Hop is only a safety fuse (cap 6); it
      stops ping-pong inflation between nearby relays.
- [x] Decided: keep the hop count ("relayed ×N") instead of Nearby / Relayed words, so
      a rescuer can work out distance. The hop number is explained in "How ResQ works"
      (see section 8): 0 = heard straight from the sender's phone, so they are within
      Bluetooth range (tens of metres); each extra hop is one more phone that passed it
      on, so the sender may be further away, and a higher number means an older, more
      indirect copy. Use the coordinates and "Position X old" for where they are.

### 5. Time
- [x] Position time and SOS started use the phone's system clock (UTC seconds),
      sent as extras `01` and `02`.
- [x] "Heard X ago" on the Receiver uses `SystemClock.elapsedRealtime()`, which a
      changed clock cannot affect. Elapsed time restarts at reboot while entries
      are saved, so fall back to the wall clock for entries saved before a reboot.
- [ ] If testing shows clock problems, v1.1.0 adds an age field as a new extra.

### 6. Ending an SOS
- [x] The Stop button sends "cancelled" (flag 8, newer update number) for a short
      while. It is never sent automatically when the app or service is killed: a
      kill is not a rescue.
- [x] Keep stale entries instead of deleting them after an hour. Stages: Live,
      then Signal lost ("last known position, 3 h old", about twice the sender's
      interval), then archived at about 48 to 72 hours. A dead phone's last known
      position is the most valuable thing a rescuer has.
- [x] Dismiss button on the Receiver (clears an entry on that phone only).
- [x] A received cancel shows as "Marked safe by sender (unverified)", dimmed and
      sorted lower. It is relayed for a short while like any update.
      Built: Stop broadcasts "cancelled" for 60 seconds (nothing is sent if no
      broadcast ever went out); entries are kept 72 hours (Live, Signal lost, Marked
      safe, Archived); only live ones, and a fresh "marked safe" for 15 minutes, are
      relayed; Dismiss hides an entry until a newer update arrives.
- [x] **Zombie risk, fixed without a new column.** Only a new update or a direct (hop 0)
      hearing restarts "Live / Signal lost" and "Heard X ago" (`UpdateRules.refreshesLiveClock`);
      a relayed repeat of the update we hold does not, and reading the full record is not a
      sign of life either. Two helpers passing a dead SOS to each other now let it go stale
      and stop relaying it. Heartbeat: a sender with no GPS fix re-broadcasts "position
      unknown" with a newer counter once per send interval, so one reachable only through
      relays does not look "Signal lost". The same when GPS is lost after a fix: the last
      position is repeated once per interval with its original time (receivers still see its
      real age). Cost: every counter bump makes a helper fetch the
      record once, as any update does. Trade-off: a living sender behind slow relays can show
      "Signal lost" a little early, the safe direction. 6 new tests.

### 7. Sending
- [x] Broadcast even with no GPS fix, marked "position unknown" (flag 16). Today a
      failed fix means nothing is broadcast, which fails exactly the person under
      rubble. The message, saved location and signal strength still help.
- [x] A stale last-known fix shows its real age (today it is sent as if fresh).
      Built: the fix time is sent in the record, and the Receiver shows "Position X
      old". With no fix at all the SOS goes out once as "position unknown" (flag 16,
      coordinates 0), not again on every retry, and is sent again if the message
      changes. If an earlier position exists this session, that beacon keeps going.
      Instant first broadcast: with no position known yet, Start sends "position
      unknown" at once and the first real fix follows as the next update, so waiting
      for GPS (up to about 40 s) never delays the first sign of the SOS. Skipped if
      this app run already holds a fix from an earlier SOS.

### 8. Screens and help text
- [x] **Preset cards** on the Send screen. Pressing Start with an empty message starts
      at once (an SOS never waits on a choice) and six cards appear ("Your SOS is on
      the air. Tap to tell helpers what you need"). A tap fills the message and the
      edit goes out after the 2 s pause. Cards hide once there is text or when
      stopped. Chips and cards share one list (`SosPhrases.kt`). The no-GPS tip is one
      amber line. Screen order, as chosen: header, message box and location note,
      cards, Start button, amber line, status line, status card. The Start button sits
      low on purpose; revisit if it is hard to reach in the phone test.
      Still to test on the phone.
- [ ] ~~Send screen echo line~~ moved to v1.1.0 (see there): not needed to send or
      receive, and it adds scanning while sending that is not battery-tested.
- [x] Settings: "Clear all received SOS" with a confirmation dialog. Built as a "Received SOS"
      section with a red-outlined button; the dialog says it cannot be undone.
- [x] Receiver cards: "Position X min old" and "Heard X min ago", in minutes, with
      a clock time or date once older than a day. Built as "Heard 3 h ago (20:59)"
      and "Heard 2 days ago (Nov 19, 21:59)"; the stage and the text use the same age.
      Position age comes from the sender's own clock, so it is hidden when that clock
      is ahead of ours; a sender clock far behind would show a too-old position.
- [x] **First-launch screen**, shown once: what is sent (location and message, to
      anyone nearby with ResQ), that it is not encrypted, that it only works if
      another phone is in range, that it does not contact emergency services, and
      three short steps. Button: "I understand".
      Built as the full guide in `ui/guide/GuideScreen.kt`, shown once (saved flag `intro_seen`),
      button "I understand". Still to test on the phone.
- [x] **Settings, "How ResQ works"** (must explain the hop count, see section 4): the full guide, reopening the first-launch
      text. Sections: if you need help, if you want to help, and "get ready before
      an emergency" (set "My location", turn off battery optimisation, allow
      permissions).
      Built: Settings section "How ResQ works" opens the same guide over the screens (button Back,
      system Back works); it explains what is sent, limits, three steps, help, helper, the hop count
      ("relayed ×N"), how often ResQ updates (send frequency, battery saver, the message-edit
      delay and when others show Signal lost), and getting ready. Sections are collapsed to their titles (tap to open); on first launch the two safety
      sections ("What ResQ sends", "What it cannot do") start open.
- [x] **Empty-state hints**, one line each where it applies. For example on the
      Scan screen with nothing found: "Nothing nearby yet. ResQ listens for SOS
      from nearby phones and passes them on."

### 9. Tooling and docs
      Built on Scan: "Nothing nearby yet. ResQ listens for SOS from nearby phones and passes them on."
      while scanning, and "Scan is off. Press Start scan to listen for SOS from nearby phones." when off.
- [x] Debug-only demo helper that fills the Scan list with fake entries, for
      checking the Copy button and layout and for a Receiver screenshot with fake
      coordinates. Debug build only, never in the release.
- [x] Move all UI text into `strings.xml` (needed for the new screens, and for any
      translation later).
      Done for every screen, banners, dialogs, buttons, phrases, intervals and signal labels. Left in
      Kotlin on purpose: the age wording in `EntryLifecycle` ("Heard 3 h ago") because its unit tests pin it,
      and the debug-only rows. Do those when a second language is added.
- [x] Update the privacy policy (`store/privacy/index.html`, dated 21 November 2026): a new
      sender ID at each Start (kept for 30 minutes after Stop) and recent own IDs remembered for 72
      hours, received entries kept up to 72 hours with Dismiss and Clear all, resume of an SOS or
      scanning for up to 12 hours, no Android backup, "position unknown" and repeated positions,
      battery level and other details sent, relaying of other people's SOS, the battery settings
      page. **Still to do on your side: upload it again to the same Cloudflare Pages project
      (`resq-privacy`); the URL stays the same.**
- [x] Store description (`store/listing/en-US.md`: name 24/30, short 74/80, full 3074/4000, with the "not encrypted" and "does not replace emergency services" lines near the top and no "secure" or "private") with a "How it works" paragraph that says the broadcast is
      not encrypted and that ResQ does not replace emergency services. No words
      like "secure" or "private".

## Must check

### Automated tests (`./gradlew test`, no phones needed)
The decision rules become plain Kotlin so these run on a Mac:
1. Unknown sender: save, fetch, relay later
2. Same update via two relays: no fetch, no re-relay, data unchanged
3. Two relays echoing each other: hop stays 1
4. Newer update: accepted and relayed
5. Late older relay: ignored
6. First heard at hop 2, then direct at hop 0: hop becomes 0
7. Counter wraps 65535 to 0: treated as newer
8. Stop and Start again: the new SOS is accepted everywhere
9. Hop 6 entry: not relayed
10. Own SOS echoed back: handled as decided above
11. Three saved SOS, one advertising slot: relays rotate
12. B, C and D all heard A: already-covered relays are skipped
13. Record round trip with extras, and an old record without them
14. 180-byte message with emoji; southern and western coordinates
15. Position age text: seconds, minutes, hours, date
16. Record with an unknown extra type: skipped, rest still read
17. Beacon with unknown flag bits: read without error
18. Extra type appearing twice: the first one wins
19. Extra whose length runs past the end: earlier extras are kept
20. Server reply by offset: correct at MTU 23, 185 and 247
21. "Heard X ago": uses elapsed time, and falls back after a reboot
22. Resume: a saved active SOS is restored on restart and cleared on Stop
23. Adding a phrase: whole phrase or nothing, within the byte budget
24. Beacon with a wrong marker: dropped; unknown version: ignored
25. Every Start makes a new sender ID and a counter at 0; a resumed SOS keeps
    its ID, counter and start time
26. An echo of the current or a recent own ID is recognised and not listed as
    someone else's SOS
27. A cancel is shown as unverified and not deleted
28. Clear all removes every received entry

### On real phones
- [ ] Database migration from the current version opens without a crash.
- [ ] The 17-byte beacon still starts advertising (no "data too large" error).
- [ ] Two-phone test again with the final build: beacon, full message, Copy.
- [ ] Kill the app while sending (swipe away, then force stop): the SOS resumes.
- [ ] Screen-off test: sender in a locked pocket for 30 minutes, then 2 hours or
      more, on different phone brands. Watch it from a second phone (or an iPhone
      scanner) to confirm the beacon never stops.
- [ ] Skewed clock test: change the receiver's clock by 2 hours. "Heard X ago"
      must stay right.
- [ ] Advertising keeps running after another phone connects and disconnects
      (an iPhone scanner can confirm).
- [ ] GATT read of a full 180-byte message plus extras on an older budget phone,
      or through a closed tester with an Android 6 to 8 device.
- [ ] Multi-client stress test: 3 or 4 helper phones read from one sender at the
      same moment. Failed connections must not stop the sender's advertising. Do
      this with closed testers.
- [ ] Battery row opens the system battery page on a Samsung and one other brand.
- [ ] Start, Stop, Start again: the second SOS has a different ID. Another phone
      shows the first as cancelled (unverified) and the second as new.
- [ ] Preset cards: appear only after Start with an empty message; one tap fills it
      and the update reaches another phone.
- [ ] First-launch screen appears once; "How ResQ works" reopens it.
- [ ] Scanning resume: Scan on, crash the app (`am crash`), it keeps scanning; Scan off,
      crash it, it stays off.
- [ ] Release APK (minified) smoke test on at least two phone brands.
- [ ] Android 6 (API 23), a mid version, and the newest version.
- [ ] Large system font and a small screen: no clipped text.
- [ ] Send with no GPS fix (indoors): still broadcasts, marked position unknown.
- [ ] Press Stop: other phones show the cancel. Kill the app: they do not.
- [ ] Release APK contains no demo helper.

### Play Console and release hygiene
- [x] Package name: Play Console says `com.resq.app` is already taken by another app, so the public
      identity is now `com.jasonkyawlab.resq` (debug build `com.jasonkyawlab.resq.debug`). Only the
      `applicationId` changed; the code keeps its `com.resq.app` packages and namespace. **It can never
      be changed once a build is uploaded to Play.** For adb use the new id, and uninstall the old
      `com.resq.app.debug` from test phones: it is a separate app now.
- [ ] Back up `~/.resq-signing/resq-upload.jks` and its passwords somewhere safe.
      Losing them means no updates to the listing, ever.
- [ ] 512x512 icon and 1024x500 feature graphic.
- [ ] At least 2 phone screenshots with no real coordinates (blur them).
- [x] Short (80 characters) and full descriptions (written, see `store/listing/en-US.md`), including that ResQ is not a
      replacement for emergency services.
- [ ] App content forms: privacy policy URL, ads (none), target audience (18+),
      content rating, data safety (precise location and messages shared with
      nearby users, **not encrypted in transit**), foreground service declarations
      (`connectedDevice`, `location`) with a demo video.
- [ ] Closed test: 12 testers opted in for 14 days (personal account).
- [ ] Decide the Play contact email (currently askdeskbyjkl@gmail.com).
- [ ] Move [CHANGELOG.md](CHANGELOG.md) "Unreleased" under a dated `1.0.0`
      heading, build the `.aab`, tag `v1.0.0`.

---

# **v1.1.0**

Small additions that do not change the Bluetooth format (they use the extras
and reserved flag bits set up in v1.0.0).

- [ ] Send screen: "Another phone has passed on your SOS (last at 14:05)" when a
      beacon with the current session ID and the relayed flag is heard. Needs low-power
      scanning while sending (extra battery, measure it first) and two phones to test.
      A finished version is saved in git stash "echo line (moved to v1.1)"; it adds
      `EchoRule` (4 tests), echo fields in the session state, a send-job listener and
      the green line. Test: phone A sends, phone B scans and relays, A shows the line.

- [ ] Resume broadcasting after the phone reboots (`BOOT_COMPLETED` receiver, on
      top of the saved SOS state from v1.0.0). Limits: it only runs once the phone
      is unlocked, it cannot read a fresh GPS fix from the background without
      background location permission (so it sends the last known position, with
      its age), and Android 14 and 15 restrictions on starting foreground services
      from boot must be tested.
- [ ] Add an age extra (position age in seconds) if clock problems show up in
      testing, so ages no longer depend on any phone's clock.
- [ ] Relay jitter: a random delay before relaying, skip a relay if another phone
      already relayed that update, and rotate across saved SOS entries.
- [ ] Revisit the battery exemption permission once Play's policy for it is checked.
- [ ] "Last gasp": one final update at about 3 percent battery, marked "battery
      dying".
- [ ] Slow the send interval automatically below about 20 percent battery.
- [ ] Settings switch: "Help relay others".
- [ ] Open in Maps button on each entry (Copy already exists).
- [ ] Search mode for rescuers: fast scanning, screen on, live signal-strength
      meter (works like an avalanche beacon).
- [ ] Translations (Thai, Burmese), reviewed by a native speaker. Watch length:
      Burmese and Thai use 3 bytes per character, so the 180-byte limit is about
      60 characters. Check Burmese rendering (Unicode vs Zawgyi) on older phones.
- [ ] Preset cards and quick-phrase chips in more languages.
- [ ] Pin an entry so it is never archived.

---

# **v2.0.0**

Bigger features that need new messaging or a format change.

- [ ] Two-way chat between a sender and a specific helper, matched by a
      `check_id` per SOS session. Needs bidirectional BLE messaging.
- [ ] Authenticated SOS and cancel messages, so they cannot be forged.
- [ ] "Private circle": share a key with family or friends in advance (for example
      by QR code) so an SOS can be encrypted for them, as an optional mode next to
      the public SOS.
- [ ] Longer messages (more than 180 bytes). Use a new extra if possible; see the
      format rules.
- [ ] Sending your own SOS and relaying at the same time, on hardware that
      supports several advertisers.
- [ ] An iOS app, only if iOS ever allows custom advertisement data (today it
      does not, so an iPhone cannot send ResQ's format).

---

# Reference

## Format rules (so v1.0.0 can still grow)

Once v1.0.0 is on Play, phones with that version will stay in the field for
years. These rules keep old and new phones able to see each other:

1. Never reorder, resize or reuse an existing field. Only add.
2. New record information is a new **extra type**. Old phones skip types they do
   not know.
3. New beacon information uses a reserved **flag bit**. Old phones ignore bits
   they do not know.
4. Unknown extras or flag bits must never cause an error or a crash.
5. Only bump the version byte for a change that cannot be made any other way.
   Old phones ignore beacons with a version they do not know, so they stop seeing
   the new ones (and the other way round) until both update.
6. New builds must keep decoding every older format.
7. Keep the beacon service ID `0xFFF1` and the GATT service and characteristic
   IDs unchanged.
8. The message limit stays 180 bytes unless a new extra carries more.

## Known limits (not fixed by anything above)

- Range through concrete and rubble may be only a few metres, so relays depend
  on people being nearby.
- A sender cannot always hang up on a helper. Seen on a Samsung Galaxy S9
  (Android 10): the app asks for the disconnect 1.5 seconds after the read (and
  at 10 seconds at the latest), and stops serving that helper, but the Bluetooth
  link stays open because Samsung's own GATT service shares it. The beacon is
  restarted after any helper leaves, and at most 4 helpers are served at once.
  Check on a second phone brand.
- Crowd behaviour cannot be tested before release. The closed test is the first
  real data.
- Some phone makers (Xiaomi, Oppo, Huawei, Samsung) kill background apps and may
  stop a broadcast, even with the battery row and guidance.
- Some older phones cannot send Bluetooth broadcasts at all; they can only
  receive.
- The broadcast is not encrypted, and the app itself shows every nearby SOS to
  whoever installs it. Encrypting with a key built into the app would protect
  nothing, so ResQ is honest about it instead.
- Anyone can forge an SOS or a cancel until messages are authenticated (v2.0.0).
- A sender with a wrong phone clock sends wrong times (position time, SOS started).
- After a reboot the phone is locked, so nothing resumes until it is unlocked.
- No iPhone support: iOS blocks custom advertisement data.
- No internet means no call to emergency services and no delivery confirmation
  for the sender.
- A phone sending its own SOS does not relay others (one advertising slot).
- "Another phone has passed on your SOS" is encouraging but not proof that a
  rescuer knows, and not hearing an echo does not mean nobody heard you.

## Decisions already made

- Message limit is 180 UTF-8 bytes (one comfortable GATT read).
- The broadcast repeats about once a second in low-power mode; the position
  refreshes every send interval (default 5 minutes).
- The saved "My location" line is set once in Settings and sent ahead of every
  message, so it still goes out when the user can barely type.
- Format: fixed beacon and fixed record core, with new record fields as TLV extras
  and reserved beacon flag bits.
- Times: system clock (UTC seconds) for position time and SOS started; the
  stopwatch (`elapsedRealtime`) for "heard X ago"; ages can be added later as an
  extra.
- Presets: Start always starts at once; cards then appear while the message is empty
  and a tap fills it. Chips stay for adding detail while typing.
- Battery: a Settings row that opens the system battery page, not the restricted
  permission, until Play's policy is checked.
- Resume after Android kills the app goes into v1.0.0; the boot receiver waits for
  v1.1.0.
- Help text: a first-launch screen, a "How ResQ works" page in Settings, and
  one-line hints on empty screens. Nothing permanent on the Send screen.
- Review decisions: marker `A1`, new sender ID at every Start, Clear all with
  confirmation, own echo shown on the Send screen, cancel shown as unverified, no
  checksum.
- Encryption: not in v1.0.0, with clear in-app and store disclosure instead.
- Quick-phrase chips show only while the message box is focused, in a strip
  pinned above the keyboard.
- The app draws edge-to-edge on every Android version, so keyboard handling is
  the same everywhere.
- Hop count is only a safety fuse. Receivers see Nearby or Relayed.
