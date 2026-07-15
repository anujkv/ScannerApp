# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# ML Kit General
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
-keep class com.google.android.gms.internal.mlkit_** { *; }

# Firebase/ML Kit Component Registrars (Crucial for dependency injection)
-keep class * extends com.google.firebase.components.ComponentRegistrar
-keep class com.google.firebase.components.ComponentRegistrar

# Google Play Services
-keep class com.google.android.gms.common.** { *; }
-keep class com.google.android.gms.tasks.** { *; }

# Google AI / Gemini SDK
-keep class com.google.ai.client.generativeai.** { *; }
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,Signature

# Kotlinx Serialization (used by Gemini SDK)
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
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# Kotlinx Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.coroutines.android.HandlerContext$Main {
  private static volatile java.lang.Object handler;
}

# Room
-keep class * extends androidx.room.RoomDatabase
-keep class * extends androidx.room.Entity
-keep class * extends androidx.room.Dao

# General rules to preserve line numbers for debugging
-keepattributes SourceFile,LineNumberTable
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-renamesourcefileattribute SourceFile
