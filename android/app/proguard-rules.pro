# ML Kit retrouve ses composants par réflexion (registres déclarés dans le manifeste) :
# sans ces règles, R8 les retire et la lecture plante dans la version release.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text_common.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_common.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text_bundled_common.** { *; }
-keep class com.google.firebase.components.** { *; }
