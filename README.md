# WifiGameController

Kotlin Android app that turns the phone into a game controller (gamepad) for another device (PC/TV/console emulator) — **only while both devices are on the same WiFi network**. No internet, no Bluetooth, no cloud relay.

## How it works

1. Phone and the target device (running a matching UDP listener) must be on the **same WiFi network**.
2. Enter the target device's local IP and a UDP port on the connect screen.
3. The app verifies the phone's active connection is actually WiFi (`ConnectivityManager` / `TRANSPORT_WIFI`) before allowing control — it will not send anything over mobile data.
4. Button presses and joystick movement are sent as small UDP text messages to `host:port`:
   - Buttons: `A:1` / `A:0` (down/up), same for `B`, `X`, `Y`, `START`, `SELECT`, `UP`, `DOWN`, `LEFT`, `RIGHT`.
   - Joystick: `JOY:x,y` where `x` and `y` are floats from `-1.0` to `1.0`, streamed at ~20/sec while touched, `JOY:0.0,0.0` on release.
5. If WiFi disconnects mid-session (`NetworkCallback`), the controller screen closes automatically.

## Project structure

- `MainActivity` — connect screen (host/IP/port entry + WiFi status check)
- `ControllerActivity` — landscape gamepad UI (D-pad, A/B/X/Y, Start/Select, joystick), sends UDP over a background coroutine
- `JoystickView` — custom analog stick `View`
- `NetworkUtils` / `UdpSender` — WiFi-only check + UDP socket sender

## Build

```
gradle assembleDebug
```

Debug APK also builds automatically via GitHub Actions on every push to `main` (see `.github/workflows/build.yml`) — download it from the workflow run's **Artifacts**.

## Requirements for the receiving side

This repo is the **controller only**. The target device needs its own small UDP listener on the chosen port that parses the same message format above and maps it to key presses / gamepad input (e.g. a Python/Node script using `vgamepad`/`uinput` on PC, or a receiver written for the TV's platform).

## Permissions

`INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` — no other permissions, no third-party network/analytics libraries.
