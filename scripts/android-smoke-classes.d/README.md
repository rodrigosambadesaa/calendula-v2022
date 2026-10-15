# Android emulator smoke test manifests

Place each new instrumentation test class in a separate `*.txt` file in this directory (one fully qualified Java/Kotlin class name per non-empty line). The test runner loads files in sorted filename order, validates names, refuses duplicates, and runs **every registered class** on the disposable Android API 23, 33, 36 and 37 emulator matrix.

Feature pull requests can add a new manifest file without editing `scripts/android-smoke-tests.sh`, reducing merge conflicts and preventing regressions from being compiled without execution. Avoid putting real patient data or network credentials in instrumentation tests.

To inspect all selected tests **without launching Gradle or an emulator**, run:

```bash
CALENDULA_SMOKE_LIST_ONLY=1 bash scripts/android-smoke-tests.sh
```

If a new class does not appear, it is not part of the smoke suite. The runner fails on malformed or repeated entries.
