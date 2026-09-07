# Retrofit/Gson data classes are accessed by Gson reflection.
-keepclassmembers,allowobfuscation class com.vortex.tts.model.** {
    <fields>;
}
-keep class com.vortex.tts.model.** { *; }

# Keep Retrofit interfaces and annotations intact.
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault
-keep,allowobfuscation,allowshrinking interface com.vortex.tts.data.GeminiApi
