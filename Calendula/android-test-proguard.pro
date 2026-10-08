# espresso-contrib 3.7.0 retains the deprecated AccessibilityChecks class even
# when its optional accessibility-test-framework dependency is excluded. Calendula
# does not use AccessibilityChecks, so these references are unreachable test code.
-dontwarn com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResultDescriptor
-dontwarn com.google.android.apps.common.testing.accessibility.framework.integrations.espresso.AccessibilityValidator
# AndroidX Test 1.7.x pulls Error Prone annotations whose metadata references
# the JDK compiler enum javax.lang.model.element.Modifier. That class is not
# part of Android and the annotation is compile-time metadata only.
-dontwarn javax.lang.model.element.Modifier
