import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.kotlin.compose)
}

android {

    buildFeatures {
        buildConfig = true
        compose = true
    }

    namespace = "com.enkiba.app"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.enkiba.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 10
        versionName = "10.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            buildConfigField("String", "SUPABASE_URL", "\"https://kjggrqklyptngpfyuuon.supabase.co/\"")
            buildConfigField("String", "SUPABASE_KEY", "\"sb_publishable__OvL970ysqZ_0qBlvtrrCg_hcH7uCe8\"")
        }
        release {
            buildConfigField("String", "SUPABASE_URL", "\"https://brnlbtlhuamtvpwbwypm.supabase.co/\"")
            buildConfigField("String", "SUPABASE_KEY", "\"sb_publishable_Q7w6vjWFs5S7Gm1_28FWlA_bwQroePU\"")
            isMinifyEnabled = true
            // Retire les ressources que R8 a rendues inutiles (vérifié sur émulateur avec un build release branché sur dev)
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // Tests unitaires Robolectric (base Room en mémoire)
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // ✅ AJOUT : Configuration pour passer l'option de dépréciation au compilateur Java
    tasks.withType<JavaCompile> {
        options.compilerArgs.add("-Xlint:deprecation")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

// Schéma Room exporté et versionné (base des migrations et de leurs tests)
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    implementation ("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation ("com.google.zxing:core:3.5.1")

    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Interface : Jetpack Compose (Material 3)
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    debugImplementation(libs.compose.ui.tooling)

    // Base locale : Room (entités et DAO en Kotlin)
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    testImplementation(libs.room.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.test.core)
    // Simplification des dépendances redondantes de Material
    // implementation ("com.google.android.material:material:1.12.0")
    // Note: 'material' est déjà inclus deux fois via libs.material et directement ci-dessus.
    // J'ai laissé la version unique 1.12.0 explicite pour la clarté.
}