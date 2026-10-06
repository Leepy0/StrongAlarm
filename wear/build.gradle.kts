import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "io.github.leepy0.strongalarm.wear"
    compileSdk = 36

    defaultConfig {
        // 폰 앱과 동일한 applicationId (Data Layer 메시지 수신 조건)
        applicationId = "io.github.leepy0.strongalarm"
        minSdk = 33
        targetSdk = 36
        // CI가 워치 관련 파일(wear·core) 커밋 수로 버전을 넘겨줌. 로컬 빌드는 1
        val watchVersion = System.getenv("WATCH_VERSION_CODE")?.toIntOrNull() ?: 1
        versionCode = watchVersion
        versionName = "0.2.$watchVersion"
    }

    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint { checkReleaseBuilds = false }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.play.services.wearable)
}
