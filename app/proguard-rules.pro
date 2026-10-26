# Pluck — ProGuard rules.
#
# NOT ACTIVE YET. app/build.gradle.kts has isMinifyEnabled = false, so this
# file is not consumed by the build. It exists as a placeholder so that when
# minification is enabled later, the rules are already curated and the build
# script only needs:
#
#   proguardFiles(
#       getDefaultProguardFile("proguard-android-optimize.txt"),
#       "proguard-rules.pro"
#   )
#
# Rules below match Pluck's dependency set. Everything here is a keep rule
# that prevents R8 from stripping code reached only via reflection or JNI.

# --- Kotlinx Serialization ---------------------------------------------------
# @Serializable classes are reached via reflection at deserialize time.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class xyz.ximanta.pluck.**$$serializer { *; }
-keepclassmembers class xyz.ximanta.pluck.** {
    *** Companion;
}
-keepclasseswithmembers class xyz.ximanta.pluck.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- OkHttp ------------------------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# --- Kotlin coroutines -------------------------------------------------------
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# --- Compose runtime ---------------------------------------------------------
# Compose uses reflection for tooling and previews only; runtime is fine to shrink.
-keepclassmembers class androidx.compose.runtime.** {
    <init>(...);
}

# --- Android framework bits --------------------------------------------------
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- Keep BuildConfig --------------------------------------------------------
-keep class xyz.ximanta.pluck.BuildConfig { *; }

# --- Preserve enum values used via SerialName --------------------------------
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
