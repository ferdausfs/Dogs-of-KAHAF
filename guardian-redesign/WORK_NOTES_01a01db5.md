# Arena session work notes — arena/01a01db5-dogs-of-kahaf

Task: 4-phase completeness pass (reliability, accountability, motivation, polish).
Base: main @ 738d0db (v3.4.0 / versionCode 28, already published 2026-08-20).
Next version: v3.5.0 (29) — probed free at end before push.

Verification stack available in THIS sandbox (verified 2026-08-20):
- NO JDK, NO Android SDK, dl.google.com + Maven Central blocked, github.com/gh api + PyPI reachable.
- Local Kotlin gate: JDK 25 (jdk4py @ /tmp/ktgate) + K2JVMCompiler (kotlin-jupyter-kernel 0.19.0.944 fat jar, Kotlin 2.3.10-RC) + real android.jar android-35 (Sable/android-platforms @ /tmp/android.jar).
- res verify: guardian-redesign/tools/verify_res.py (in repo).
- Release gate: GitHub Actions `Build Release APK` on push to main (after PR merge).

Phase checklist:
1a tamper trace + fixes (device-admin watchdog + a11y-disabled tamper log)    [ ]
1b Crashlytics (conditional google-services) + local GuardianCrashHandler     [ ]
1c PIN recovery: mock + recovery code + 48h timed reset                       [ ]
2  accountability partner (observe tamper/protection-pause/high-conf enqueue) [ ]
3  clean streak + weekly comparison card on dashboard                         [ ]
4a backup/restore JSON (SAF)                                                  [ ]
4b Help/FAQ screen                                                            [ ]
4c notification content-leak: NotificationShieldService                        [ ]
FINAL version bump 3.5.0/29 + compile gate + push + PR + report section        [ ]
