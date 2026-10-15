plugins {
    alias(libs.plugins.android.application)
}

android {

    buildFeatures {
        buildConfig = true
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
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
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

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    implementation("androidx.cardview:cardview:1.0.0")

    implementation ("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation ("com.google.zxing:core:3.5.1")

    // ... autres dépendances
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    // Simplification des dépendances redondantes de Material
    // implementation ("com.google.android.material:material:1.12.0")
    // Note: 'material' est déjà inclus deux fois via libs.material et directement ci-dessus.
    // J'ai laissé la version unique 1.12.0 explicite pour la clarté.
}