# Keep kotlinx.serialization generated serializers for our model classes.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.spotkofi.app.**$$serializer { *; }
-keepclassmembers class com.spotkofi.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.spotkofi.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
