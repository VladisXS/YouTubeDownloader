# youtubedl-android / JavaCV native bindings rely on reflection.
-keep class com.yausername.** { *; }
-keep class org.bytedeco.** { *; }
-dontwarn org.bytedeco.**
-dontwarn com.yausername.**
