plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    jacoco
}

val firebaseConfigured = layout.projectDirectory.file("google-services.json").asFile.isFile
if (firebaseConfigured) {
    apply(plugin = "com.google.gms.google-services")
}

fun String.asBuildConfigString(): String =
    "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val publicGatewayBaseUrl: String = providers
    .gradleProperty("FLOWER_SHOW_PUBLIC_GATEWAY_BASE_URL")
    .orElse("http://10.0.2.2:8088")
    .get()

jacoco {
    toolVersion = "0.8.12"
}

android {
    namespace = "com.example.flower_show"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.flower_show"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["firebaseConfigured"] = firebaseConfigured.toString()
        manifestPlaceholders["usesCleartextTraffic"] = "false"
        buildConfigField("boolean", "FIREBASE_CONFIGURED", firebaseConfigured.toString())
        buildConfigField(
            "String",
            "PUBLIC_GATEWAY_BASE_URL",
            publicGatewayBaseUrl.asBuildConfigString(),
        )
    }

    buildTypes {
        debug {
            enableUnitTestCoverage = true
            enableAndroidTestCoverage = true
            manifestPlaceholders["usesCleartextTraffic"] = "true"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
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
        buildConfig = true
        compose = true
    }
}

val jacocoCoverageExclusions = listOf(
    "**/R.class",
    "**/R$*.class",
    "**/BuildConfig.*",
    "**/Manifest*.*",
    "**/*Test*.*",
    "**/*Preview*.*",
    "**/ComposableSingletons*.*",
)

tasks.withType<Test>().configureEach {
    useJUnit()
}

// Espresso 3.7.0 supports newer platform InputManager APIs and requires Futures 1.2.0.
// Keep this alignment scoped to instrumentation tests so the production dependency graph is unchanged.
configurations.matching { it.name.contains("AndroidTest", ignoreCase = true) }.configureEach {
    resolutionStrategy.force("androidx.concurrent:concurrent-futures:1.2.0")
}

tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")
    group = "verification"
    description = "Generates Jacoco coverage reports for app debug unit tests."

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }

    sourceDirectories.setFrom(files("src/main/java"))
    classDirectories.setFrom(
        files(
            fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/debug")) {
                exclude(jacocoCoverageExclusions)
            },
            fileTree(layout.buildDirectory.dir("intermediates/javac/debug/classes")) {
                exclude(jacocoCoverageExclusions)
            },
        ),
    )
    executionData.setFrom(
        fileTree(layout.buildDirectory) {
            include(
                "jacoco/testDebugUnitTest.exec",
                "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec",
            )
        },
    )
}

tasks.withType<Test>().matching { it.name == "testDebugUnitTest" }.configureEach {
    finalizedBy("jacocoTestReport")
}

dependencies {
    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.foundation)

    // Lifecycle + ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Activity Compose
    implementation(libs.androidx.activity.compose)

    // Navigation Compose
    implementation(libs.androidx.navigation.compose)

    // Media3 ExoPlayer
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.ui)

    // Performance tracing for Macrobenchmark custom metrics
    implementation(libs.androidx.tracing.ktx)
    implementation(libs.androidx.profileinstaller)

    // Coil (Compose image loading)
    implementation(libs.coil.compose)

    // Gson (JSON)
    implementation(libs.gson)

    // HTTP + authentication
    implementation(libs.okhttp)

    // Firebase Cloud Messaging (FID registration API; intentionally not the retired KTX module)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    // On-device embedding inference
    implementation(libs.onnxruntime.android)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test)

    // Debug
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
