# ----------------------------
# Kotlin & General
# ----------------------------
-keepattributes Signature,RuntimeVisibleAnnotations

# ----------------------------
# WebRTC Android SDK & JNI
# ----------------------------
-keep class org.webrtc.** { *; }
-keepclassmembers class org.webrtc.** { *; }
-dontwarn org.webrtc.**
-keep class org.jni_zero.** { *; }      # ⚠ 必须保留 JNI 初始化类
-keepclasseswithmembers class * {
    native <methods>;
}


# ----------------------------
# Prevent resource shrinking issues for Compose
# ----------------------------
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

