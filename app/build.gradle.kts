plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.lucas.player"
    compileSdk = 35
    ndkVersion = "26.3.11579264"

    defaultConfig {
        applicationId = "com.lucas.player"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        externalNativeBuild {
            cmake {
                cppFlags("-std=c++17", "-O3", "-fvisibility=hidden")
                arguments("-DANDROID_STL=c++_shared")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            ndk {
                abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a"))
            }
        }
        debug {
            ndk {
                abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a", "x86_64"))
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildFeatures {
        prefab = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // AndroidX estritamente essencial
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Oboe (C++ audio library via prefab)
    implementation("com.google.oboe:oboe:1.9.3")
}
