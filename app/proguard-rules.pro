# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Garder les signatures génériques pour Retrofit (Trés important pour l'erreur Call<Foo>)
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes *Annotation*
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# Garder les interfaces Retrofit / API
-keep interface retrofit2.** { *; }
-keep class retrofit2.** { *; }
-dontwarn retrofit2.**

# Garder vos modèles de données (Product, User, Client, etc.)
# Remplacez "com.enkiba.app.models" par votre package de modèles si nécessaire
-keep class com.enkiba.app.models.** { *; }

# Si vous utilisez Supabase ou OkHttp
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn javax.annotation.**
-keepnames class com.fasterxml.jackson.** { *; }