
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// ==========================================
// CONFIGURACIÓN ANDROID
// ==========================================

android {

    namespace = "com.example.edmotor"

    compileSdk {
        version = release(37)
    }

    defaultConfig {

        applicationId = "com.example.edmotor"

        minSdk = 24

        targetSdk = 37

        versionCode = 1

        versionName = "1.0"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"
    }

    // ======================================
    // TIPOS DE COMPILACIÓN
    // ======================================

    buildTypes {

        release {

            optimization {
                enable = false
            }
        }
    }

    // ======================================
    // COMPATIBILIDAD JAVA
    // ======================================

    compileOptions {

        sourceCompatibility = JavaVersion.VERSION_11

        targetCompatibility = JavaVersion.VERSION_11
    }

    // ======================================
    // JETPACK COMPOSE
    // ======================================

    buildFeatures {

        compose = true
    }
}

// ==========================================
// DEPENDENCIAS
// ==========================================

dependencies {

    // JETPACK COMPOSE

    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.activity.compose)

    implementation(libs.androidx.compose.foundation.layout)

    implementation(libs.androidx.compose.material3)

    implementation(libs.androidx.compose.ui)

    implementation(libs.androidx.compose.ui.graphics)

    implementation(libs.androidx.compose.ui.tooling.preview)

    implementation("androidx.compose.material:material-icons-extended")

    implementation(libs.androidx.compose.animation)

    implementation(libs.androidx.navigation.compose)


    // ANDROID CORE

    implementation(libs.androidx.core.ktx)


    // DATASTORE

    implementation(libs.androidx.datastore.preferences)


    // LIFECYCLE

    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.androidx.lifecycle.viewmodel.compose)


    // ROOM DATABASE

    implementation(libs.androidx.room.runtime)

    implementation(libs.androidx.room.ktx)

    ksp(libs.androidx.room.compiler)


    // TESTING UNITARIO

    testImplementation(libs.junit)


    // TESTING ANDROID

    androidTestImplementation(platform(libs.androidx.compose.bom))

    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    androidTestImplementation(libs.androidx.espresso.core)

    androidTestImplementation(libs.androidx.junit)


    // DEBUG

    debugImplementation(libs.androidx.compose.ui.test.manifest)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
