plugins {
    id("com.android.library")
}

android {
    namespace = "com.arm.aichat"
    compileSdk = 36

    ndkVersion = "30.0.14904198"

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        ndk {
             abiFilters += listOf("arm64-v8a")
        }
        externalNativeBuild {
            cmake {
                arguments += "-DCMAKE_BUILD_TYPE=Release"
                arguments += "-DCMAKE_MESSAGE_LOG_LEVEL=DEBUG"
                arguments += "-DCMAKE_VERBOSE_MAKEFILE=ON"

                arguments += "-DBUILD_SHARED_LIBS=ON"
                arguments += "-DLLAMA_BUILD_COMMON=ON"
                arguments += "-DLLAMA_OPENSSL=OFF"

                arguments += "-DGGML_NATIVE=OFF"
                arguments += "-DGGML_BACKEND_DL=ON"
                arguments += "-DGGML_CPU_ALL_VARIANTS=ON"
                arguments += "-DGGML_LLAMAFILE=OFF"
            }
        }
    }
    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
}

// Strip all .so files after native build for release
afterEvaluate {
    tasks.matching { it.name.startsWith("buildCMakeRelease") }.configureEach {
        doLast {
            val stripPath = "toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip"
            val ndkDir = File(System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT") ?: "/home/netsvetoch/Android/Sdk", "ndk/${android.ndkVersion}")
            val strip = File(ndkDir, stripPath)
            if (!strip.exists()) {
                logger.warn("llvm-strip not found at $strip")
                return@doLast
            }
            listOf(file(".cxx/Release"), file("build/intermediates/cxx/Release"))
                .filter { it.exists() }
                .flatMap { dir -> dir.walkTopDown().filter { it.extension == "so" }.toList() }
                .forEach { so ->
                    val sizeBefore = so.length()
                    Runtime.getRuntime().exec(arrayOf(strip.absolutePath, so.absolutePath)).waitFor()
                    logger.lifecycle("Stripped: ${so.name} ${sizeBefore/1024}K → ${so.length()/1024}K")
                }
        }
    }
}
