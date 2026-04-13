# Keep line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# llama.cpp JNI bindings — InferenceEngineImpl native methods
-keep class com.arm.aichat.internal.InferenceEngineImpl {
    private native <methods>;
    private static volatile com.arm.aichat.InferenceEngine instance;
}
-keep class com.arm.aichat.** { *; }

# Room entities and DAOs
-keep class ru.ainetico.scanprice.data.Scan { *; }
-keep class ru.ainetico.scanprice.data.Store { *; }
-keep class ru.ainetico.scanprice.data.ScanStatus { *; }
-keep class ru.ainetico.scanprice.data.SyncStatus { *; }
-keep class ru.ainetico.scanprice.data.Converters { *; }
-keep class ru.ainetico.scanprice.data.ScanDao { *; }
-keep class ru.ainetico.scanprice.data.StoreDao { *; }
-keep class ru.ainetico.scanprice.data.AppDatabase { *; }

# Kotlin coroutines
-dontwarn kotlinx.coroutines.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
