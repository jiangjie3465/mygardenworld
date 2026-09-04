# Protobuf Java Lite resolves fields by name from the generated message info
# strings, so generated message fields must keep their names.
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**

# OkHttp / Okio ship consumer rules; silence optional platform integrations.
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ZXing is used only for QR encoding; nothing reflective.
-dontwarn com.google.zxing.**

# Keep stack traces readable in crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
