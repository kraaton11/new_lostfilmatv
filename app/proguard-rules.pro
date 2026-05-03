# Project-specific ProGuard rules.

# Keep framework-generated and reflection-driven app classes reachable after release shrinking.
-keep class * extends dagger.hilt.android.internal.managers.* { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep class org.jsoup.** { *; }

# OkHttp/Okio publish consumer rules, but keep warnings quiet for transitive optional classes.
-dontwarn okhttp3.**
-dontwarn okio.**

# Tink references compile-time Error Prone annotations that are not packaged at runtime.
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi
