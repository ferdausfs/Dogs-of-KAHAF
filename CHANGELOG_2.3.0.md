# Guardian Shield v2.3.0 — Optimization Pack

## Included improvements
- Added a first-run onboarding flow and automatic handoff to the permission checklist
- Hardened PIN storage with random salt generation and legacy hash migration
- Added TFLite `TFL3` header validation during model import
- Reduced unnecessary heavy scans with a scan budget policy that considers idle UI and low-battery state
- Added battery-aware WorkManager constraints for watchdog and cleanup jobs
- Reduced Accessibility node leak risk by recycling traversed nodes
- Replaced several hard-coded UI strings with string resources
- Added starter JVM unit tests for hashing, scan policy, and TFLite validation
- Bumped app version to `2.3.0` (`versionCode 6`)

## Notes
- This package is source-level optimized and ready for Android Studio review.
- The environment used for packaging did not include a full Android SDK toolchain, so APK assembly was not executed inside this workspace.
