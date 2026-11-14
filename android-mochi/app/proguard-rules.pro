-keep class com.banbiao.app.core.NativeCore { *; }
-keepclasseswithmembernames class * { native <methods>; }
-dontwarn org.jetbrains.annotations.**
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.banbiao.app.MainActivity$LegacyBridge { *; }
-keep class com.banbiao.app.data.** { *; }
