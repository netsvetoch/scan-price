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
-keep class ru.ainetico.honestprice.data.Scan { *; }
-keep class ru.ainetico.honestprice.data.Store { *; }
-keep class ru.ainetico.honestprice.data.ScanStatus { *; }
-keep class ru.ainetico.honestprice.data.SyncStatus { *; }
-keep class ru.ainetico.honestprice.data.Converters { *; }
-keep class ru.ainetico.honestprice.data.ScanDao { *; }
-keep class ru.ainetico.honestprice.data.StoreDao { *; }
-keep class ru.ainetico.honestprice.data.AppDatabase { *; }

# Keep model classes used in JSON parsing
-keep class ru.ainetico.honestprice.model.** { *; }

# Kotlin coroutines
-dontwarn kotlinx.coroutines.**

# ML Kit
-dontwarn com.google.mlkit.**

# OkHttp (if still transitively included)
-dontwarn okhttp3.**
-dontwarn okio.**
