# Room entities/DAOs are referenced via generated code; keep annotations.
-keepattributes *Annotation*

# Kotlin coroutines on older Android runtimes.
-dontwarn kotlinx.coroutines.flow.**Flow

# Keep SOS wire-protocol model classes stable across proguard renaming so
# on-wire encoding (BlePayloadCodec) never silently drifts between builds.
-keep class com.resq.app.core.data.model.** { *; }
-keep class com.resq.app.core.ble.BlePayload { *; }
