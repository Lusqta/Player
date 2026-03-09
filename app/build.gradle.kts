plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.seunome.player"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.seunome.player"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        ndkVersion = "29.0.14206865"

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17 -O3 -Wall"
                arguments += "-DANDROID_STL=c++_shared"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildFeatures {
        prefab = true // Essencial para importar o Oboe nativamente
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
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    
    // MediaBrowserServiceCompat / MediaSession
    implementation("androidx.media:media:1.7.0")
    
    // Oboe (Motor de Áudio)
    implementation("com.google.oboe:oboe:1.8.1")
    
    // SAF DocumentFile para Pastas
    implementation("androidx.documentfile:documentfile:1.0.1")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    
    // Room (Banco de Dados Local)
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    ksp("androidx.room:room-compiler:$room_version")
}
