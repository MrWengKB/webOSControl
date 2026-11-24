# webOSControl

A native Android remote control for LG webOS TVs, written in Kotlin with Jetpack Compose.
[NOTE] There is a newer app that supports various TV OSes: [SmartTVControl](https://github.com/MrWengKB/SmartTVControl) is now in progress.

## Features

- Finds TVs on your Wi-Fi automatically (SSDP), or connect by IP address.
- Pairs once with the TV's on-screen prompt and remembers the key, so later connections are silent.
- Power off, and power on with Wake-on-LAN (the app learns the TV's MAC address the first time it connects).
- Volume up/down/mute (the phone's volume keys also work), channel up/down.
- D-pad with OK, Back, Home, Settings, Info, color keys and a number pad.
- Touchpad mode for the TV's pointer, with a scroll strip.
- Media controls (play, pause, stop, rewind, fast forward).
- Launch any installed app and switch HDMI inputs.
- Type text into the TV's focused text box from the phone keyboard.

## Install

Install the APK from the latest entry on the Releases page; new releases install over it as updates.

Every push to `main` also builds a debug APK in GitHub Actions. It installs as a separate app, "webOS Remote (debug)", because it is signed with a throwaway key. Open the latest run of
**Android build** under the repository's Actions tab, download the `webOSControl-debug-apk`
artifact, unzip it and install the APK on your phone (allow installs from unknown sources).

### Versions and releases

[`versions.md`](versions.md) is the version history and the single source of the version
number. To release, add a new section at the top and push to main:

```md
# v0.2.0
- What changed
```

The **Release** workflow then builds the APK and publishes it on the Releases page as `v0.2.0`,
with that section as the release notes. Pushes that don't add a new version publish nothing.
Use semantic versioning: bump the last number for fixes, the middle one for new features.
Android's `versionCode` is derived from the version (1.2.3 becomes 1002003), and the app shows
the version at the bottom of the TV list.

So that each release installs as an update over the previous one, sign them all with the same
key. Create one once and add it as repository secrets (Settings > Secrets and variables > Actions):

```sh
keytool -genkeypair -v -keystore release.jks -alias webos -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 release.jks   # value for SIGNING_KEYSTORE_BASE64
```

| Secret | Value |
| --- | --- |
| `SIGNING_KEYSTORE_BASE64` | the base64 output above |
| `SIGNING_STORE_PASSWORD` | the keystore password |
| `SIGNING_KEY_ALIAS` | `webos` (or the alias you chose) |
| `SIGNING_KEY_PASSWORD` | the key password |

Keep `release.jks` safe; without the secrets the release is signed with a throwaway debug key.

To build it yourself you need JDK 17 and the Android SDK:

```sh
./gradlew :app:assembleDebug        # APK in app/build/outputs/apk/debug/
./gradlew :core:test                # protocol tests, no Android SDK needed
```

Requires Android 8.0 (API 26) or newer.

## TV setup

- Phone and TV must be on the same network.
- On first connect the TV asks whether to allow the device; accept it with the TV remote.
- For power-on, enable **Settings > General > Devices > TV Management > Turn on via Wi-Fi**
  (called *Mobile TV On* on some models). Wake-on-LAN is most reliable when the TV uses Ethernet.

## How it works

The `core` module is a plain Kotlin library with no Android dependencies:

- `SsdpDiscovery` sends an M-SEARCH for `urn:lge-com:service:webos-second-screen:1` and reads the
  TV's name from its UPnP description.
- `WebOsClient` speaks LG's SSAP protocol: JSON over a WebSocket on `wss://<tv>:3001` (2018+
  firmware), falling back to `ws://<tv>:3000`. It registers with LG's signed pairing manifest,
  then sends `request`/`subscribe` messages such as `ssap://audio/volumeUp`.
- `PointerSocket` drives the separate pointer input socket used for d-pad buttons and the touchpad.
- `WakeOnLan` sends the magic packet for power-on.

The `app` module is the Compose UI (`RemoteScreen`, `DevicesScreen`) and `RemoteViewModel`.

The TV presents a certificate from LG's private CA on port 3001, so the app accepts any certificate
for these LAN connections. The pairing manifest in `core/src/main/resources` comes from the
[lgtv2](https://github.com/hobbyquaker/lgtv2) project (MIT).
