# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the Android SDK tools proguard configuration.

# Keep sensor data model classes
-keep class com.robomanipal.imusensor.sensor.** { *; }
-keep class com.robomanipal.imusensor.streaming.** { *; }
