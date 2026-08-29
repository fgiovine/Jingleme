plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Il numero di build di GitHub Actions diventa il versionCode: ogni APK pubblicato
// è più recente del precedente e si installa sopra senza disinstallare.
val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "0").toInt()

android {
    namespace = "it.ingenia.badumtss"
    compileSdk = 35

    // Chiave di firma fissa e versionata. Senza, ogni build del runner userebbe una
    // chiave di debug generata al momento e Android rifiuterebbe l'aggiornamento
    // per firma non corrispondente.
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("keystore/jingleme.jks")
            storePassword = "jingleme"
            keyAlias = "jingleme"
            keyPassword = "jingleme"
        }
    }

    defaultConfig {
        applicationId = "it.ingenia.badumtss"
        minSdk = 26
        targetSdk = 35
        versionCode = 1 + buildNumber
        versionName = "1.$buildNumber"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // Il modello .tflite non deve essere compresso: l'Interpreter lo mappa in memoria.
    androidResources {
        noCompress += "tflite"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")

    implementation(platform("androidx.compose:compose-bom:2024.09.02"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Opzionale: serve solo se metti yamnet.tflite in app/src/main/assets/.
    // Senza il modello l'app funziona lo stesso, in modalità prosodica.
    implementation("org.tensorflow:tensorflow-lite:2.16.1")
}
