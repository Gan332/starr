# Add project specific ProGuard rules here.
-keepclassmembers class com.auth2fa.app.data.Account { *; }
-keep class * extends androidx.room.RoomDatabase
-keep class * extends androidx.lifecycle.ViewModel
