plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {

    val floatingPetKeystorePath = System.getenv("FLOATINGPET_KEYSTORE_PATH")
    val floatingPetKeystorePassword = System.getenv("FLOATINGPET_KEYSTORE_PASSWORD")
    val floatingPetKeyAlias = System.getenv("FLOATINGPET_KEY_ALIAS")
    val floatingPetKeyPassword = System.getenv("FLOATINGPET_KEY_PASSWORD")
    namespace = "com.example.floatingpet"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.drapple233.floatingpet"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        create("floatingPet") {
            if (!floatingPetKeystorePath.isNullOrBlank()) {
                storeFile = file(floatingPetKeystorePath)
                storePassword = floatingPetKeystorePassword
                keyAlias = floatingPetKeyAlias
                keyPassword = floatingPetKeyPassword
            }
        }
    }

    buildTypes {
        getByName("debug") {
            // Use a persistent, Fox-only key when GitHub Actions secrets are configured.
            // Otherwise retain Android's standard debug signing for first-time builds.
            if (!floatingPetKeystorePath.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("floatingPet")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
