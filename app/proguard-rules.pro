# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ---- Gson ----
# WasteCollectionMessage's property names ARE the wire keys (no @SerializedName — Gson binds by
# Kotlin property name), so R8 must not rename them or every publish silently breaks the contract.
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.stream.** { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.mitas.ppnam.station4aa.data.mqtt.dto.** { *; }

# ---- HiveMQ MQTT client (shaded) ----
# Shaded jar, not an AAR, so its consumer rules (if any) aren't picked up automatically.
# It relocates its own Netty copy internally, which does TLS/ALPN provider lookup and
# transport selection by reflection.
-keep class com.hivemq.** { *; }
-dontwarn com.hivemq.**
-dontwarn io.netty.util.internal.Hidden$NettyBlockHoundIntegration
