# Foreground service declaration (Google Play Console)

ResQ declares two foreground service types in `AndroidManifest.xml`: `connectedDevice` and `location`.
Play Console asks, for each type: what the feature is, a link to a short video, and what happens to the
user if the service is delayed or stopped. One video can cover both types, so paste the same link twice.

Where: Policy and programs, App content, Foreground service permissions.

## Answers to paste

### connectedDevice

**What does this foreground service do for the user?**
ResQ sends an SOS and listens for SOS from nearby phones over Bluetooth Low Energy: it advertises the SOS,
scans for other ResQ phones, and connects briefly to read the full message. The user starts it with the Start
button (or Start scan), and a persistent notification with a Stop button shows while it runs.

**Why must it be a foreground service?**
The SOS must keep going with the screen off and the app in the background. If Android stopped it, the SOS
would silently stop reaching nearby phones.

**Impact if it is delayed or interrupted**
Helpers nearby would stop receiving the SOS and its updates, which defeats the purpose of the app.

### location

**What does this foreground service do for the user?**
While an SOS is on, ResQ reads the phone's GPS position at the interval the user chose (every 1, 5 or 15
minutes, or hourly with Battery saver) and puts it in the SOS, so helpers can find the person.

**Why must it be a foreground service?**
The position must keep updating while the screen is off. ResQ does not request background location
permission: it reads the position only while the user has an SOS running and the notification is showing.

**Impact if it is delayed or interrupted**
The SOS would show no position, or an old one, so helpers could not find the person.

## Video (one video, about 60 to 75 seconds)

Record the phone screen. Upload to YouTube as **Unlisted** (not Private) and paste the link into both fields.

1. **0:00 to 0:08** Open ResQ (the first-launch guide may appear; press "I understand"). Show the Send screen.
2. **0:08 to 0:20** Press **Start**. Show the permission prompts (Bluetooth, location, notifications) and allow
   them. To show the prompts, use a fresh install or remove the app's permissions first in Android Settings.
3. **0:20 to 0:30** The Send screen now says "Broadcasting SOS". Pull down the notification shade and show the
   ResQ notification ("Broadcasting SOS") with its Stop button.
4. **0:30 to 0:42** Press Home, then lock the screen. Wake it and show that the notification is still there:
   the service keeps running with the screen off.
5. **0:42 to 0:52** Open ResQ again. Show "Last sent" updating, which proves the position is still being read.
   (This is where real coordinates appear. See the privacy note below.)
6. **0:52 to 1:05** Press Scan, then **Start scan**. Show the notification change to "Broadcasting & listening".
   Optional, if a second phone is nearby: show the SOS arriving on it.
7. **1:05 to 1:15** Press **Stop** in the app. Show the notification disappear (after the short "Telling nearby
   phones you stopped" message).

Say or add captions only if you like. Google needs to see the feature, the notification and the user action.

## Privacy note

Step 5 shows your real position. Record away from home, or crop or blur the "Last position" line before
uploading (a simple editor such as iMovie is enough). Do not show your real address in the message.

## Recording options

- Samsung: pull down the quick panel and use **Screen recorder**.
- Mac with the phone plugged in: `adb -d shell screenrecord /sdcard/resq.mp4` (stops after 3 minutes), then
  `adb -d pull /sdcard/resq.mp4`. Starting Start on the phone is still up to you.
