import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.symbol.processing)
}

android {
    namespace = "com.a3solution.scannerapp"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.a3solution.scannerapp"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        val props = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localPropertiesFile.inputStream().use { props.load(it) }
        }

        create("release") {
            storeFile = props.getProperty("KEYSTORE_PATH")?.let { file(it) }
            storePassword = props.getProperty("KEYSTORE_PASSWORD")
            keyAlias = props.getProperty("KEY_ALIAS")
            keyPassword = props.getProperty("KEY_PASSWORD")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
            signingConfig = signingConfigs.getByName("release")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
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
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.mlkit.scanner)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.translate)
    implementation(libs.mlkit.language.id)
    implementation(libs.coil.compose)
    implementation(libs.androidx.print)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.google.ai.client)
// To recognize Latin script
    implementation("com.google.mlkit:text-recognition:16.0.1")
// To recognize Chinese script
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
// To recognize Devanagari script
    implementation("com.google.mlkit:text-recognition-devanagari:16.0.1")
// To recognize Japanese script
    implementation("com.google.mlkit:text-recognition-japanese:16.0.1")
// To recognize Korean script
    implementation("com.google.mlkit:text-recognition-korean:16.0.1")
    // To recognize Latin script
    implementation(libs.play.services.mlkit.text.recognition)
// To recognize Chinese script
    implementation(libs.play.services.mlkit.text.recognition.chinese)
// To recognize Devanagari script
    implementation(libs.play.services.mlkit.text.recognition.devanagari)
// To recognize Japanese script
    implementation(libs.play.services.mlkit.text.recognition.japanese)
// To recognize Korean script
    implementation(libs.play.services.mlkit.text.recognition.korean)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}