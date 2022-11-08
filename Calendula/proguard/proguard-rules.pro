-keepclasseswithmembers public class androidx.recyclerview.widget.RecyclerView { *; }

# ----------------------------------------------------------------------------------------
# Keep our own classes
# ----------------------------------------------------------------------------------------
-keep class es.usc.citius.servando.calendula.** { *; }
-dontwarn es.usc.citius.servando.calendula.**
-dump

# ----------------------------------------------------------------------------------------
# Rules applied to test code
# ----------------------------------------------------------------------------------------
-ignorewarnings
-keepattributes *Annotation*
-dontnote junit.framework.**
-dontnote junit.runner.**
-dontwarn android.test.**
-dontwarn androidx.test.**
-keep class androidx.core.app.CoreComponentFactory { *; }
-dontwarn org.junit.**
-dontwarn org.hamcrest.**
-dontwarn com.squareup.javawriter.JavaWriter
