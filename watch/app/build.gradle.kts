import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kover)
}

val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val gistId: String = localProps.getProperty("gistId", "").trim()

android {
    namespace = "dev.imkdw.claudewatch"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.imkdw.claudewatch"
        minSdk = 36
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "GIST_ID", "\"$gistId\"")
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = false
        }
    }

    sourceSets {
        // 수집기와 같은 계약 픽스처를 테스트 리소스로 읽는다
        getByName("test").resources.srcDir("../../fixtures")
    }
}

kotlin {
    jvmToolchain(17)
}

// Robolectric은 SDK 36 샌드박스를 Java 21에서만 띄운다
tasks.withType<Test>().configureEach {
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(21) }
    jvmArgs(
        "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
        "--add-opens=java.base/java.io=ALL-UNNAMED",
    )
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.tiles)
    implementation(libs.protolayout)
    implementation(libs.protolayout.expression)
    implementation(libs.protolayout.material3)
    implementation(libs.complications.data.source.ktx)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.foundation)
    implementation(libs.activity.compose)
    implementation(libs.work.runtime.ktx)
    implementation(libs.datastore.preferences)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.guava)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver3)
    testImplementation(libs.robolectric)
    testImplementation(libs.work.testing)
    testImplementation(libs.tiles.testing)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.androidx.test.ext.junit.ktx)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(libs.androidx.test.ext.junit.ktx)
    androidTestImplementation(libs.work.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}

kover {
    reports {
        filters {
            includes {
                packages("dev.imkdw.claudewatch.domain", "dev.imkdw.claudewatch.data", "dev.imkdw.claudewatch.notify")
            }
        }
        verify {
            rule {
                minBound(90)
            }
        }
    }
}
