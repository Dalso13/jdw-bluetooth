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

# Bluetooth Library ProGuard Rules

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keep class kotlinx.coroutines.** { *; }

# BLE Classes
-keep class com.jdw.module.core.bluetooth.** { *; }
-keepclassmembers class com.jdw.module.core.bluetooth.** { *; }

# Android BLE
-keep class android.bluetooth.** { *; }

# Logger (Reflection 사용하지 않지만 명시)
-keep class com.jdw.module.core.bluetooth.core.log.BleLogger { *; }
