plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("org.jetbrains.kotlin.kapt")
    alias(libs.plugins.navigation.safe.args)
    id("com.chaquo.python")
}

android {
    namespace = "com.lifeHub"
    compileSdk = 34

    defaultConfig {
        val aiUrl = providers.gradleProperty("AI_BACKEND_URL").orElse("http://10.0.2.2:8080/").get()
        require(aiUrl.matches(Regex("https?://[A-Za-z0-9._:/-]+/"))) { "AI_BACKEND_URL must be an HTTP(S) URL ending in /" }
        buildConfigField("String", "AI_BACKEND_URL", "\"$aiUrl\"")
        // Connected tests may uninstall their target. Never target the user's demo installation.
        val isolatedTests = providers.gradleProperty("isolatedTests").map { it.toBoolean() }
            .getOrElse(gradle.startParameter.taskNames.any { it.substringAfterLast(':').startsWith("connected") })
        applicationId = if (isolatedTests) "com.lifeHub.qa" else "com.lifeHub"
        minSdk = 24
        targetSdk = 34
        versionCode = 5
        versionName = "1.1.3"
        ndk { abiFilters += if (providers.gradleProperty("mobileArmOnly").orNull == "true") listOf("arm64-v8a") else listOf("arm64-v8a", "x86_64") }
        javaCompileOptions {
            annotationProcessorOptions { arguments["room.schemaLocation"] = "$projectDir/schemas" }
        }

        testInstrumentationRunner = "com.lifeHub.LifeHubTestRunner"
    }

    signingConfigs {
        val store = providers.environmentVariable("LIFEHUB_KEYSTORE").orNull
        if (store != null) {
            create("distribution") {
                storeFile = file(store)
                storePassword = providers.environmentVariable("LIFEHUB_STORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("LIFEHUB_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("LIFEHUB_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("distribution")
            isMinifyEnabled = false
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
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.toString()
    }

    buildFeatures {
        buildConfig = true
        compose = true
        viewBinding = true
    }
}
// Compress and extract native libraries to keep APK downloads smaller.
// Use the dynamic DSL across the Android/embedded-Python plugin API boundary.
extensions.getByName("android").withGroovyBuilder {
    "packagingOptions" { "jniLibs" { setProperty("useLegacyPackaging", true) } }
}

configurations.all {
    resolutionStrategy {
        force("com.google.android.material:material:1.12.0")
    }
}

dependencies {
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.10.2")

    implementation(libs.appcompat)
    implementation(libs.lifecycle.runtime.ktx)
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.activity:activity:1.9.3")
    implementation(platform(libs.compose.bom))
    implementation(libs.ui)
    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)
    implementation(libs.material3)
    implementation(libs.constraintlayout)
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.ui.test.junit4)
    debugImplementation(libs.ui.tooling)
    debugImplementation(libs.ui.test.manifest)

    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")

    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.google.android.material:material:1.12.0")


    kapt("androidx.room:room-compiler:2.7.2")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")

    implementation("com.squareup.okhttp3:logging-interceptor:4.10.0")
    implementation("javax.inject:javax.inject:1")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

}

// Share the existing deterministic validation rules with the offline Android client.
// Bundle validation modules only, never the HTTP server or evaluation datasets.
val syncOfflineRules by tasks.registering(Copy::class) {
    from(rootProject.file("backend")) { include("action_service.py", "action_rules.py", "task_service.py", "text_evidence.py") }
    into(layout.buildDirectory.dir("generated/offlinePython"))
}
chaquopy {
    defaultConfig {
        version = "3.11"
        providers.environmentVariable("LIFEHUB_BUILD_PYTHON").orNull?.let { buildPython(it) }
        pip { install("tzdata==2026.2") }
    }
    sourceSets.getByName("main") { srcDir(layout.buildDirectory.dir("generated/offlinePython")) }
}
tasks.configureEach {
    if (name.contains("Python") && name != "syncOfflineRules") dependsOn(syncOfflineRules)
}
