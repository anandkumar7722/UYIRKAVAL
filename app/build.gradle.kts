plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
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
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
