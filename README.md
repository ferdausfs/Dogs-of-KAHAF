# Guardian Shield (Dogs of KAHAF)

**Current version: v13 (2.1.3)** — Build-fail fix + 3-pass stability audit.
See [`CHANGELOG.md`](./CHANGELOG.md) for the full v13 fix list.

A privacy-first, on-device content blocker for Android. Uses the
Accessibility Service to monitor foreground apps & screen content and
combines:

- **App-level rules** — block / whitelist any installed app.
- **Keyword filter** — substring + regex match on visible text.
- **Time-based schedules** — block apps during recurring windows
  (e.g. social media 22:00–06:00).
- **AI NSFW detection** — TFLite-powered tiered classifier
  (SAFE / NATURAL / SUGGESTIVE / EXPLICIT) with anti-false-positive
  debouncing and per-app threshold boosts for "heavy image" apps.
- **Source-based 15-min auto-lock** — when AI confirms EXPLICIT
  material from a content-source app (Facebook / Instagram / etc.),
  the app is locked for 15 minutes.

All inference runs **on-device**. **No telemetry, no network calls.**

---

## Build (CI)

Push to `main` / `master` / `dev` → GitHub Actions builds the debug
APK and uploads it as a downloadable artifact (`GuardianShield-debug-<run>`).

The CI workflow (`.github/workflows/build-debug.yml`) provisions
Gradle 8.7 + JDK 17, regenerates the (gitignored) gradle-wrapper jar,
accepts SDK licenses, then runs `./gradlew assembleDebug`.

## Build (local)

```bash
cp local.properties.template local.properties
# edit local.properties → set sdk.dir to your Android SDK path
./gradlew assembleDebug
```

If `gradlew` reports `ClassNotFoundException: GradleWrapperMain`, the
gradle-wrapper jar is missing (gitignored). Regenerate it once:

```bash
gradle wrapper --gradle-version 8.7 --distribution-type bin
```

(You need a Gradle ≥ 8.7 binary on PATH for the regenerate step;
afterwards, `./gradlew` is self-sufficient.)

## Toolchain

| Tool        | Version     |
|-------------|-------------|
| AGP         | 8.5.2       |
| Gradle      | 8.7         |
| Kotlin      | 1.9.24      |
| KSP         | 1.9.24-1.0.20 |
| Hilt        | 2.52        |
| Room        | 2.6.1       |
| compileSdk  | 35          |
| minSdk      | 26          |
| targetSdk   | 35          |
| JDK target  | 17          |

## Permissions used

Required for protection: `BIND_ACCESSIBILITY_SERVICE`,
`SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE` +
`FOREGROUND_SERVICE_SPECIAL_USE`, `RECEIVE_BOOT_COMPLETED`,
`POST_NOTIFICATIONS`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.

Optional but recommended: `BIND_DEVICE_ADMIN` (uninstall protection),
`PACKAGE_USAGE_STATS`.

## License

Private project — no public license attached.
