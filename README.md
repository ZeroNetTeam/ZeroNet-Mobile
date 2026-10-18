# ZeroNet Mobile

An Android VPN client for people in Iran. Tap the orb:

1. ZeroNet pulls community configs from GitHub feeds.
2. It tests them on your network.
3. It connects through the first one that really carries traffic, via
   [Zray-Core](https://github.com/ZeroNetTeam/Zray-Core) (Rust).
4. It keeps looking in the background and balances across the best few.

- Kotlin ↔ Rust boundary: [docs/native-contract.md](docs/native-contract.md).
- The desktop app is [ZeroNet-Desktop](https://github.com/ZeroNetTeam/ZeroNet-Desktop);
  the engine and the speed measurements are in [Zray-Core](https://github.com/ZeroNetTeam/Zray-Core).

## Architecture

```
UI process (Compose)                       :vpn process
─────────────────────                      ─────────────────────────────────────────
ui/            screens, orb, design system
client/        EngineClient ── Messenger ─► service/EngineService ─► Engine
               ServerRepository (reads)     service/ZeroVpnService (VpnService, TUN,
data/          SettingsStore (settings.json)         notification, network tracking)
               ServerStore (SQLite, WAL) ◄── writes ─ Engine (discovery, health, stats)
                                            core/ZrayNative ── JNI ─► libzray_mobile.so
```

- **The UI process never loads native code.** The tunnel lives in `:vpn`, so
  a UI crash can't drop it.
- **Discovery, delay tests, config building and the Cloudflare scanner run in
  Rust** (`zero-discovery` from Zray-Core, `native/zray-mobile` with the
  `jni` feature).
- **Connecting is a race.** History winners go first, then streamed discovery,
  and the tunnel comes up on the first working config.
- **More working configs join Zray's `leastPing` balancer** through a hot
  reload that never drops the interface.

## Building

**Requirements.**
- JDK 17+.
- Android SDK: platform 37, build-tools 37, NDK r29.
- Rust (see `native/rust-toolchain.toml`) with targets `aarch64-linux-android`
  and `armv7-linux-androideabi`.
- `cargo install cargo-ndk`.

`local.properties` (not committed):

```properties
sdk.dir=/home/you/Android/Sdk
```

The engine is a git dependency on Zray-Core, pinned by `native/Cargo.lock`
(`cargo update -p zero-core` in `native/` moves it). To build against your own
checkout of Zray-Core instead, clone them side by side and run
`Zray-Core/tools/link-local.sh ZeroNet-Mobile/native`; delete
`native/.cargo/config.toml` to go back to the pinned revision.

Build (the Gradle task `buildZrayNative` compiles the Rust library for all
three ABIs first):

```bash
ANDROID_NDK_HOME=$HOME/Android/Sdk/ndk/29.0.14206865 ./gradlew assembleRelease
```

APKs land in `app/build/outputs/apk/`, one per ABI plus a universal one.

**Other build switches.**
- `-Pzray.skipNative=true` skips the Rust build, e.g. for UI-only work.
- Release signing reads `keystore.properties` (`storeFile`, `storePassword`,
  `keyAlias`, `keyPassword`). Without it, release builds are signed with the
  debug key so they stay installable.

### Building from inside Iran

Google blocks `dl.google.com` (the SDK and Google Maven) for Iranian IPs, so
route the JVM through a local proxy. A running ZeroNet on another device
shared over Wi-Fi works; so does `zray run` with a working config:

```bash
export JAVA_TOOL_OPTIONS="-Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=10809 -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=10809"
```

`JAVA_TOOL_OPTIONS` is needed because Gradle forks a daemon, and command-line
`-D` flags don't reach it. Maven Central, the Gradle Plugin Portal and GitHub
work without a proxy.

## Tests

```bash
./gradlew -Pzray.skipNative=true testDebugUnitTest lintDebug
```

- Unit tests cover settings, the IPC codecs and scoring.
- Roborazzi screenshot tests render every screen on the JVM:
  `recordRoborazziDebug` and `verifyRoborazziDebug`.
- The Rust side: `cargo test` in `native/`, including a host JNI smoke test
  (`native/zray-mobile/tests/jni-host/run.sh`) and a check that the geodata
  bundled in `app/src/main/assets/geo` decodes and covers every rule the app
  can emit. The discovery engine's own tests are in Zray-Core.
- Config-feed measurements: [tools/feed-eval](tools/feed-eval).

## License

GPL-3.0, like Zray-Core. See [LICENSE](LICENSE). Third-party components keep
their own licences (listed in the app under Settings → About → Open-source licences).
