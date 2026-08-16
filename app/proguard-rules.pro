# Project-specific ProGuard/R8 rules.

# kotlinx.serialization — keep the generated serializers reachable.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class ** {
    @kotlinx.serialization.SerialName <fields>;
}

# Retrofit/OkHttp (wired in M1) — standard consumer rules ship with the libraries,
# these cover the reflective bits R8 cannot see.
-dontwarn org.jetbrains.annotations.**
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
