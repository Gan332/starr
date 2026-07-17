# Add project specific ProGuard rules here.
-keepclassmembers class com.auth2fa.app.data.Account { *; }
-keepclassmembers class com.auth2fa.app.data.Category { *; }
-keepclassmembers class com.auth2fa.app.data.CategoryCount { *; }
-keep class * extends androidx.room.RoomDatabase
-keep class * extends androidx.lifecycle.ViewModel
-keep class com.auth2fa.app.data.AccountDao { *; }
-keep class com.auth2fa.app.data.CategoryDao { *; }
-keep class com.auth2fa.app.data.AccountRepository { *; }

# Keep Rust JNI bridge
-keep class com.auth2fa.app.rust.RustBridge { *; }
-keepclassmembers class com.auth2fa.app.rust.RustBridge {
    native <methods>;
}

# Keep native library
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Keep Compose
-keep class androidx.compose.** { *; }

# Keep ViewModel
-keepclassmembers class * extends androidx.lifecycle.AndroidViewModel {
    <init>(android.app.Application);
}

# Keep widget
-keep class com.auth2fa.app.widget.** { *; }

# Keep data classes for reflection
-keep class com.auth2fa.app.viewmodel.** { *; }
-keep class com.auth2fa.app.ui.** { *; }

# Keep notification
-keep class com.auth2fa.app.notification.** { *; }

# General Android optimization
-keep class * extends android.appwidget.AppWidgetProvider { *; }
-keep class * extends android.app.Application { *; }
