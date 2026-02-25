import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    kotlin("kapt") // added kapt plugin for Room annotation processing
    alias(libs.plugins.kotlin.compose)
}

// Read MAPS_API_KEY from local.properties
val localPropsFile = rootProject.file("local.properties")
val mapsApiKey: String = run {
    if (!localPropsFile.exists()) return@run ""
    val props = Properties()
    FileInputStream(localPropsFile).use { fis: FileInputStream -> props.load(fis) }
    props.getProperty("MAPS_API_KEY") ?: ""
}

android {
    namespace = "com.hacksrm.nirbhay"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hacksrm.nirbhay"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // Inject Maps API key from local.properties into AndroidManifest placeholder
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey

        // Also expose it as a BuildConfig field so Kotlin code can read it directly
        buildConfigField("String", "MAPS_API_KEY", "\"$mapsApiKey\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Prevent Gradle from compressing the TFLite model inside the APK.
    // TFLite needs to memory-map the file, which requires it uncompressed.
    androidResources {
        noCompress += "tflite"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("me.bridgefy:android-sdk:1.2.3@aar") {
        isTransitive = true
    }
    implementation("com.google.android.gms:play-services-location:21.2.0")
    implementation("androidx.navigation:navigation-compose:2.7.0")

    // Required by Bridgefy/libsignal for newer java.* APIs on older Android runtimes
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    // Bridgefy SDK uses Dagger internally (runtime classes required)
    implementation("com.google.dagger:dagger:2.59.2")
    implementation("javax.inject:javax.inject:1")

    // Bridgefy SDK expects OkHttp logging interceptor at runtime
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Bridgefy SDK requires kotlinx-serialization-json at runtime
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    // Bridgefy SDK requires kotlinx-coroutines at runtime
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("io.coil-kt:coil-compose:2.6.0")

    // ── TensorFlow Lite – Audio Classification (YAMNet) ──
    implementation("org.tensorflow:tensorflow-lite-task-audio:0.4.4")

    // ── LocalBroadcastManager (for SOS event bus) ──
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")

    // Retrofit + Gson converter
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // WorkManager (periodic upload worker)
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Room (local cache for SOS events)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // Google Maps
    implementation(libs.maps.compose)
    implementation(libs.play.services.maps)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
