# Keep kotlinx.serialization generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.clocktower.grimoire.**$$serializer { *; }
-keepclassmembers class com.clocktower.grimoire.** { *** Companion; }
-keepclasseswithmembers class com.clocktower.grimoire.** { kotlinx.serialization.KSerializer serializer(...); }
# The persisted @Serializable models live in the engine module.
-keep,includedescriptorclasses class com.clocktower.engine.**$$serializer { *; }
-keepclassmembers class com.clocktower.engine.** { *** Companion; }
-keepclasseswithmembers class com.clocktower.engine.** { kotlinx.serialization.KSerializer serializer(...); }
