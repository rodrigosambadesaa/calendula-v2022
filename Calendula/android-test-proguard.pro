# espresso-contrib 3.4.0 retains the deprecated AccessibilityChecks class even
# when its optional accessibility-test-framework dependency is excluded. Calendula
# does not use AccessibilityChecks, so these references are unreachable test code.
-dontwarn com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResultDescriptor
-dontwarn com.google.android.apps.common.testing.accessibility.framework.integrations.espresso.AccessibilityValidator
