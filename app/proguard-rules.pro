# Keep Room-generated code
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }

# Google Maps / Play services
-keep class com.google.android.gms.maps.** { *; }
-dontwarn com.google.android.gms.**
