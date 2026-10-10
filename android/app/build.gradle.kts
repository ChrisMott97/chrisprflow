import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Base URL and token come from local.properties, which git ignores.
val local = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "app.dictation"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.dictation"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "d1"
        val baseUrl = local.getProperty("dictation.baseUrl", "https://dictation-mock.chrisamott97.workers.dev")
        val token = local.getProperty("dictation.token", "mock-token")
        buildConfigField("String", "BASE_URL", "\"$baseUrl\"")
        buildConfigField("String", "TOKEN", "\"$token\"")
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
