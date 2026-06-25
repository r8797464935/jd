# Keep liblinphone JNI-facing classes — they are reached reflectively from native code.
-keep class org.linphone.core.** { *; }
-keep class org.linphone.mediastream.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
