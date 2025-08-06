# Basic Android ProGuard rules
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep llama.cpp JNI interface
-keep class android.llama.cpp.** { *; }

# Keep your app classes
-keep class com.metao.ai.** { *; }

# Keep Android framework classes
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# Keep Compose classes
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Keep Koin DI classes
-keep class org.koin.** { *; }
-dontwarn org.koin.**

# Keep Room database classes
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**

# Keep ViewModels
-keep class * extends androidx.lifecycle.ViewModel { *; }

# Keep data classes and models
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}

# Keep sealed classes
-keep class * extends kotlin.Enum { *; }
-keepclassmembers class * extends kotlin.Enum {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep sealed classes and their subclasses
-keep class * extends kotlin.coroutines.jvm.internal.SuspendLambda { *; }
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }

# Keep all sealed classes
-keep class **$WhenMappings { *; }

# Disable obfuscation for now
-dontobfuscate
