# Previewing the app — laptop (scrcpy) and phone (wireless ADB)

Everything below assumes the toolchain env is loaded on `agent101`:

```fish
# fish (Kev's shell) — loaded automatically from ~/.config/fish/conf.d/android.fish
echo $ANDROID_HOME        # /home/kev/android-dev/sdk
```

```bash
# bash / scripts / CI
cd ~/projects/familyWatchList && source env.sh
```

---

## 1. Start the emulator on agent101 (headless)

```fish
emulator -avd family_test -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect -memory 2048 &
```

> `-no-snapshot` forces a real cold boot every time. Without it, Android's "quick boot" saves
> the entire running state (including whatever screen was on top) whenever the emulator shuts
> down and silently restores it on next launch — instead of your actual app default, you get
> whatever was open when it last died. Always include this flag.

```fish
adb wait-for-device
# wait until fully booted (not just adb-visible):
while test (adb shell getprop sys.boot_completed | string trim) != 1; sleep 2; end
adb devices     # -> emulator-5554  device
```

Install / reinstall the app:

```fish
cd ~/projects/familyWatchList
./gradlew installDebug          # or: adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n org.seg7.familywatchlist/.MainActivity
```

Shut it down when you're done (it holds ~2GB):

```fish
adb emu kill        # or: adb -s emulator-5554 emu kill
```

> **RAM rule (PLAN.md §8):** never run a full Gradle build and an emulator *first boot* at the
> same time. 16GB total, Gradle is capped at 4GB, the AVD at 2GB. Building while an
> already-booted emulator idles is fine.

---

## 2. Laptop viewing with scrcpy

The emulator's `adbd` lives inside QEMU's NAT, so it cannot be reached directly over TCP.
The working route is to let the **laptop's scrcpy talk to agent101's adb server**.

### Prerequisite on the laptop (once)

Kev's laptop is **EndeavourOS** (Arch-based); both packages live in the official `extra` repo:

```bash
# EndeavourOS / Arch (adb ships in android-tools)
sudo pacman -S scrcpy android-tools

# other distros, for reference:
# Debian/Ubuntu:  sudo apt install scrcpy adb
# macOS:          brew install scrcpy android-platform-tools
```

Arch notes: no extra udev/plugdev group setup is needed for this workflow — the laptop's adb
only talks to agent101's adb *server* over TCP, never to a USB device. If `scrcpy` ever
complains about missing video codecs, install `ffmpeg` (normally already present).

scrcpy must be **v2.0 or newer** (`scrcpy --version`) for `--tunnel-host`.

### Option A — SSH tunnel (recommended, nothing exposed to the LAN)

On the **laptop**:

```bash
# 1. Forward agent101's adb server port (5037) AND the scrcpy video-tunnel port (27183).
#    Keep this shell open.
ssh -4 -N -L 5037:localhost:5037 -L 27183:localhost:27183 kev@agent101
```

> **Why two ports:** `adb forward` (which scrcpy uses under the hood) always binds its
> listening socket on the machine running the *adb server* — agent101 — never on the
> client. So forwarding only 5037 (the adb control channel) leaves the client with nowhere
> to connect for the actual video stream. This two-port recipe is scrcpy's own documented
> pattern for exactly this "adb server on a remote machine" setup.
>
> `-4` forces IPv4. Without it, some systems (EndeavourOS included, if IPv6 loopback is
> disabled) fail with `bind [::1]:5037: Cannot assign requested address` — ssh tries the
> IPv6 loopback first and never falls back. No output after running this command is
> expected and means it worked; it just holds the tunnel open silently.

In a **second laptop shell**:

```bash
# 2. Point local adb/scrcpy at the forwarded server and mirror.
export ADB_SERVER_SOCKET=tcp:localhost:5037
adb devices                       # should list emulator-5554 (agent101's emulator)
scrcpy --tunnel-host=127.0.0.1 --port=27183 -s emulator-5554
```

> scrcpy's `--tunnel-host` wants a literal IP, not a hostname — `localhost` fails with
> `ERROR: Invalid IPv4 address: localhost` on scrcpy 4.x. Use `127.0.0.1`.
>
> `--port=27183` pins the video-tunnel port to match what was forwarded in step 1 —
> without it scrcpy may pick a different port from its default range and the connection
> will fail with repeated `connect: Connection refused` (the SSH tunnel not covering
> whatever port it actually picked).

Useful scrcpy flags: `--max-size=1080` (less bandwidth), `--stay-awake`,
`--window-title="family_test"`, `--no-audio` (audio forwarding needs Android 11+ and adds load).

> If `adb devices` on the laptop says "unauthorized" or restarts a local server, kill the
> laptop's own daemon first: `adb kill-server`, then re-export `ADB_SERVER_SOCKET`.

### Option B — adb server listening on the LAN (PLAN.md §6 variant)

Only on a trusted home LAN: an open ADB server is **unauthenticated remote code execution**
on agent101 for anyone who can reach port 5037.

On **agent101**:

```fish
adb kill-server
adb -a -P 5037 nodaemon server        # foreground; Ctrl-C to stop. Binds 0.0.0.0:5037
```

On the **laptop**:

```bash
export ADB_SERVER_SOCKET=tcp:agent101:5037
scrcpy --tunnel-host=agent101 -s emulator-5554
```

### Option C — no scrcpy, just stills

Cheapest sanity check, works over plain SSH:

```fish
adb exec-out screencap -p > /tmp/shot.png     # on agent101
```

```bash
scp kev@agent101:/tmp/shot.png .              # from the laptop
```

`docs/first-boot.png` was captured exactly this way.

---

## 3. Kev's phone over wireless ADB (Android 11+)

Phone and agent101 must be on the **same Wi-Fi/LAN**.

### On the phone (once per pairing)

1. Settings → About phone → tap **Build number** 7× to unlock Developer options.
2. Settings → System → Developer options → **Wireless debugging** → On.
3. Tap **Pair device with pairing code**. The dialog shows
   `IP:PORT` (a *pairing* port, e.g. `192.168.1.42:37109`) and a 6-digit code.
   Leave the dialog open.

### On agent101

```fish
# 1. Pair (uses the PAIRING port + code from the dialog)
adb pair 192.168.1.42:37109
# > Enter pairing code: 123456
# > Successfully paired to 192.168.1.42:37109

# 2. Connect (uses the port shown on the main Wireless debugging screen — a DIFFERENT port)
adb connect 192.168.1.42:41235

adb devices        # -> 192.168.1.42:41235   device
```

Discovery shortcut if the IP is unknown:

```fish
adb mdns services      # lists _adb-tls-pairing._tcp / _adb-tls-connect._tcp entries
```

### Install onto the phone

```fish
cd ~/projects/familyWatchList
adb devices                                   # note the phone's serial
set -x ANDROID_SERIAL 192.168.1.42:41235      # pick the phone when the emulator is also up
./gradlew installDebug
adb shell am start -n org.seg7.familywatchlist/.MainActivity
```

Disconnect / re-pair:

```fish
adb disconnect 192.168.1.42:41235
set -e ANDROID_SERIAL
```

Pairing survives reboots of the phone but **not** turning Wireless debugging off and on again
with a new port — if `adb connect` fails, re-read the port from the Wireless debugging screen.
Full re-pair is only needed if the phone forgets agent101.

### scrcpy to the phone from the laptop

Same as §2 Option A, just target the phone's serial:

```bash
export ADB_SERVER_SOCKET=tcp:localhost:5037     # with the two-port SSH tunnel running (§2)
scrcpy --tunnel-host=127.0.0.1 --port=27183 -s 192.168.1.42:41235
```

---

## 4. Troubleshooting

| Symptom | Fix |
|---|---|
| `adb: command not found` in bash | `source ~/projects/familyWatchList/env.sh` |
| `emulator: command not found` | fish: new shell (conf.d loads it) or `source ~/.config/fish/conf.d/android.fish` |
| Emulator hangs on boot | `adb emu kill`; delete `~/.android/avd/family_test.avd/*.lock`; re-launch with `-no-snapshot` |
| `KVM is not installed / permission denied` | `ls -l /dev/kvm` and `groups` — `kev` must be in `kvm`. It is; no sudo needed |
| scrcpy: `ERROR: Could not find any ADB device` | the laptop is talking to its own adb server — `adb kill-server`, re-export `ADB_SERVER_SOCKET` |
| scrcpy connects then hangs at "device disconnected" | add `--force-adb-forward`, or check `--tunnel-host` matches the adb-server host |
| Emulator + build both slow | check `free -g`; stop one of them (`./gradlew --stop` / `adb emu kill`) |
