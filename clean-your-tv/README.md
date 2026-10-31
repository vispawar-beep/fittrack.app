# Clean Your TV

Turns off the bloatware and ad services on an Android TV, speeds it up, and gives
it an ad-free home screen, all from your phone over Wi-Fi. You don't need a
computer or root access.

Built and tuned for the **Nokia Smart TV, SCBC ATV R2 (Realtek), Android TV 11**
(`NOKIA/NOKIA/R2:11/RTT7.220308.001/1050`). It works on other Android TVs too, using
the generic rules.

## What's in it

| Part | Runs on | Does |
|---|---|---|
| `phone/` | your phone | Connects to the TV, shows each app with a safety rating, turns apps off and on, applies speed tweaks, and keeps an undo record |
| `tvhome/` | the TV | **Clean Home**, a fast home screen with a clock, your apps, and Live TV/Wi-Fi/Settings shortcuts. It has no ads, no recommendation rows and no network calls |
| `core/` | both | The Nokia R2 profile, the list of safe and protected apps, and the command builder, with tests built from your TV report |

## How it keeps the TV safe

- **Nothing is uninstalled.** Apps are turned off with `pm disable-user --user 0`, and
  the APK stays on the TV. A factory reset brings everything back.
- **Undo for everything.** Every app turned off and every setting changed is saved on
  the phone, with the setting's old value. **Undo → Undo everything** puts the TV back
  as it was.
- **Protected apps can't be selected.** This covers tuners, HDMI inputs, the remote
  (`com.realtek.keyinterceptserver`, `com.apps.bluetoothremote`), Wi-Fi, Play Store,
  Play services, the keyboard, firmware updates and the setup wizard. Any system app
  the catalog doesn't know is also protected.
- **The Home button is redirected, not taken over.** Your TV report shows
  `Home switch: blocked VENDOR_HOME`, because Realtek's key service locks the Home key
  to the stock home. So the stock home stays enabled. Clean Home's small accessibility
  service sees the stock home come up and opens Clean Home straight away. It listens
  only to the stock home's window changes and filters no keys, so volume, Netflix,
  YouTube and Inputs keys behave as before. Undo switches it off. Replacing the stock
  home outright is still offered as a 60-second trial with automatic rollback.

## What it turns off on your Nokia R2

These ratings follow the lists tested on two TVs with the same SCBC R2 Realtek board,
[SANYO ATV R2](https://github.com/Gathin23/cleanyourtv/tree/main/examples/sanyo-atv-r2) and
[Compac ATV R2](https://github.com/Gathin23/cleanyourtv/tree/main/examples/compac-atv-r2),
in the [CleanYourTV](https://github.com/Gathin23/cleanyourtv) project.

**Recommended (selected for you): ads, telemetry and bloatware**

| App | Why |
|---|---|
| `com.google.android.tvrecommendations` | Sponsored and "recommended" rows on the home screen |
| `com.apps.channels` | "S-Channel" promoted content rows (not the TV tuner: antenna channels stay in Live TV) |
| `com.google.android.videos` | Google TV / Play Movies promotions row |
| `com.apps.function.gdpr` | SCBC data-consent agent |
| `android.autoinstalls.config.scbc.device` | Silently installs partner apps after updates |
| `com.vewd.core.service` | Vewd/Opera TV web engine running in the background |
| `com.apps.eshow` | E-SHOW shop demo mode |
| `com.apps.clear` | "TV Booster" cleaner running in the background |
| `com.apps.networkspeed`, `com.google.android.apps.nbu.smartconnect.tv` | Vendor speed test, Google phone-connect helper |
| `com.google.android.youtube.tvmusic`, `com.google.android.play.games` | Rarely used apps that also add promoted rows to the home screen |
| `com.google.android.feedback`, `com.google.android.onetimeinitializer`, `com.android.printspooler`, `com.android.wallpaper.livepicker`, `com.android.hotspot2.osulogin`, `com.android.dynsystem` | Background services that do nothing on a TV |

**Your choice (not selected):** YouTube, Netflix, Prime Video, Chromecast built-in,
FastCast mirroring, Ambient screensaver, Gallery, USB media player, Wi-Fi hotspot, world
clock, show-sticker overlay, TalkBack, text-to-speech.

**Advanced (not selected; the app shows what you lose):** Google Assistant/voice
search, `com.realtek.leanbacklauncher.partnercustomizer` (it also supplies the stock
home's Inputs button), firmware updater.

## Speed tweaks

- **Faster menus:** animations run at 0.5×. Undo restores the old values.
- **Stop background Wi-Fi scanning.** Undo restores it.
- **Clear app caches**, **stop background apps**, and **optimise apps**. These are
  one-time clean-ups, so there's nothing to undo.

## Getting the apps

1. Open **Actions → Build Clean Your TV → Run workflow** in this GitHub repo (it also
   runs on every push that changes `clean-your-tv/`).
2. Download **clean-your-tv-apks** and install `clean-your-tv-phone.apk` on your phone.
   The phone app already contains Clean Home, so you don't need to sideload
   `clean-home-tv.apk`.

To build on your own computer with the Android SDK:
`cd clean-your-tv && gradle :tvhome:assembleDebug`, copy the APK to
`phone/src/main/assets/clean-home.apk`, then `gradle :phone:assembleDebug`.
Run the tests with `gradle :core:test`.

## Using it

1. **On the TV:** Settings → Device Preferences → About → select **Build** 7 times.
   Then go to Developer options and turn on **USB debugging**. Note the TV's IP address
   from Settings → Network & Internet.
2. **On the phone** (same Wi-Fi): open Clean Your TV, enter the IP (port 5555), and tap
   **Connect**. On the TV, tick **Always allow** and press OK.
3. **Clean tab:** **Recommended** is pre-selected. Tap **Turn off**, then restart the TV.
4. **Speed tab:** tap **Speed up my TV**.
5. **Look tab:** tap **Install Clean Home**, then **Make the Home button open Clean
   Home**. Press Home on the remote: the stock home flashes for a moment, then Clean
   Home opens.
6. **Undo tab:** turn individual apps back on, or undo everything.
7. **Report** (top bar): shares a text report of the TV and what was changed.

When you're done, you can turn USB debugging off again. The changes stay.

## Credits

The package ratings for the SCBC R2 board, the Home redirect approach (`stock_home_redirect`)
and the push-then-install workaround come from
[Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv) (MIT licence). This project
uses those findings but none of its code.
