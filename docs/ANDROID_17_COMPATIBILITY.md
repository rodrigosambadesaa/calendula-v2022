# Android 17 (API 37) compatibility — incremental validation

Status: **in progress**, 2026-10-10. This unofficial medical-records fork is not a medically validated product.

## Platform and application configuration

Android 17 has been stable since 2026-06-16. It uses API level 37.0 for the Android SDK/emulator package; the runtime reports `Build.VERSION.SDK_INT == 37`.

| Dimension | Current setting | Reason |
| --- | --- | --- |
| minSdk | 23 (Android 6) | Preserve existing minimum compatibility |
| compileSdk | 36 | Keep the verified AGP 8.10.1 build while SDK 37.0 resolution is audited |
| targetSdk | 36 | Avoid opting into unvalidated Android 17 target-specific behavior |
| New runtime test | 37.0 (Android 17) | Detect regressions when executing on the current OS |
| Existing runtime tests | 23, 33, 36 | Preserve established Android 6/13/16 acceptance gates |

**Running on API 37 is not the same as targeting API 37.** We must not claim the application fully supports API 37-targeted behavior until the build system, UX and platform requirements have passed a separate migration.

## New compatibility workflow

`.github/workflows/android-17-compatibility.yml` isolates the Android 17 emulator from the mandatory 23/33/36 matrix. It installs current SDK command-line tools before configuring an `api-level: 37.0` AVD, checks the actual runtime API is 37, then runs a synthetic-data subset of the medication, stock, notification, reboot/alarm restoration, and installation integration tests. It retains failing results for review instead of interpreting an emulator start failure as an app defect. A green run means **those selected tests passed on that revision**, not comprehensive medication reliability.

Older command-line tools on CI have generated `target=android-0` when creating minor-versioned AVDs, sometimes crashing SurfaceFlinger. Update tooling first rather than applying workarounds to production code. Prefer a current Android 17 Google APIs system image (Google documents earlier API 37 Play certification problems and recommends revision 5 or newer).

## Behavior requiring a separate targetSdk 37 review

1. **Adaptive UI:** Android 17 removes the large-screen portrait/resize opt-out for apps targeting API 37. The current manifest has portrait-locked activities and an Android 16 compatibility opt-out; neither is a substitute for verifying landscape, tablets, desktop windows, font scales, rotation and state preservation.
2. **Alarm and notification delivery:** Test runtime permissions, exact-alarm access and its inexact fallback, Doze/background execution, delayed and repeated reminders, process death, boot recovery and notification actions with disposable events. Do not rely solely on PendingIntent existence assertions.
3. **Network and security:** Review Android 17 local-network default restrictions, certificate transparency and dynamic native library loading rules for the eventual targetSdk 37 change; the existing VPN-aware backend preflight does not grant local-network permissions.
4. **Legacy compatibility:** Audit reflection against static final fields, message queues and activity configuration changes where targeting 37 changes the behavior. Preserve Android 6 while migrating dependencies cautiously.
5. **SDK toolchain:** Android 17 publishes minor-versioned SDK platforms such as `platforms;android-37.0`. Confirm AGP and Gradle minor SDK support before increasing `compileSdk`; do not install a nonexistent `platforms;android-37` package or add a fake SDK alias as a permanent solution.

## Exit criteria

- Android 17 runtime smoke workflow passes all selected tests on an immutable PR commit.
- Add end-to-end UX/Doze tests and state-retention checks against a real Android 17 device; test on a Samsung Galaxy S25 Ultra where accessible without assuming its firmware has been independently tested.
- Verify the new targetSdk 37 configuration with a supported AGP and SDK 37.0 platform, full unit/R8/lint suite, and API 23/33/36/37 emulator tests.
- Do not close release blockers for retired medication archives (#216), reminder recovery (#525), inventory integrity (#552), or historical migrations merely because the app starts on Android 17.

## References

- Official Android 17 release: https://android-developers.googleblog.com/2026/06/Android-17.html
- Android 17 SDK: https://developer.android.com/about/versions/17/setup-sdk
- Adaptive-screen changes: https://android-developers.googleblog.com/2026/02/prepare-your-app-for-resizability-and.html
- Android Emulator known issues: https://developer.android.com/studio/run/emulator-troubleshooting
- Relevant emulator runner issue: https://github.com/ReactiveCircus/android-emulator-runner/issues/482
