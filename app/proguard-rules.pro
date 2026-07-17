# Add project specific ProGuard rules here.
-keepclassmembers class com.auth2fa.app.data.Account { *; }
-keep class * extends androidx.room.RoomDatabase
-keep class * extends androidx.lifecycle.ViewModel

# Keep Rust JNI bridge
-keep class com.auth2fa.app.rust.RustBridge { *; }
-keepclassmembers class com.auth2fa.app.rust.RustBridge {
    native <methods>;
}

# Keep native library
-keepclasseswithmembernames class * {
    native <methods>;
}
