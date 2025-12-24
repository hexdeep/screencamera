plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val gitCommitId: String = project.findProperty("GIT_COMMIT_ID") as String? ?: "xxx"

android {
    namespace = "com.xzh.hexdeep"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.xzh.hexdeep"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "GIT_COMMIT_ID", "\"$gitCommitId\"")
    }

    signingConfigs {
        create("release") {
            storeFile = file("${rootDir}/key.jks")
            storePassword = "haoyun."
            keyAlias = "key0"
            keyPassword = "haoyun."
        }
    }

    splits {
        abi {
            isEnable = true            // 开启按架构拆分
            reset()                    // 清除默认设置
            include("armeabi-v7a", "arm64-v8a") // 仅打包你想支持的架构
            isUniversalApk = true     // 不生成包含所有架构的 APK
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }

//        applicationVariants.all {
//            if (buildType.name == "release") {
//                outputs.all {
//                    (this as com.android.build.gradle.internal.api.ApkVariantOutputImpl).outputFileName =
//                        "HexDeep_v1.0.apk"
//                }
//            }
//        }
    }

    buildFeatures {
        compose = true   // ⭐ 启用 Compose
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }



}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // ⭐ Jetpack Compose
    implementation(platform("androidx.compose:compose-bom:2024.04.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.navigation:navigation-compose:2.7.3")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("io.github.webrtc-sdk:android:137.7151.05")
    implementation("org.java-websocket:Java-WebSocket:1.6.0")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
