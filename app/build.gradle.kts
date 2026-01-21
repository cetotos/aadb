plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}


val adbRoot: String = (project.findProperty("adb.root") as String?)
    ?: System.getenv("ADB_ROOT")
    ?: File(rootDir, "adb").absolutePath
val adbBuildArm64: String = (project.findProperty("adb.build.arm64") as String?)
    ?: System.getenv("ADB_BUILD_DIR_ARM64")
    ?: File(adbRoot, "build-android").absolutePath
val adbBuildX86: String = (project.findProperty("adb.build.x86_64") as String?)
    ?: System.getenv("ADB_BUILD_DIR_X86_64")
    ?: File(adbRoot, "build-android-x86_64").absolutePath

android {
    namespace = "com.torpos.aadb"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.torpos.aadb"
        minSdk = 30
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.0-alpha01"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DADB_ROOT=$adbRoot",
                    "-DADB_BUILD_DIR_ARM64=$adbBuildArm64",
                    "-DADB_BUILD_DIR_X86_64=$adbBuildX86"
                )
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            isDebuggable = false
            isJniDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
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
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

val adbBuildScript = File(rootDir, "scripts/build_adb_android.sh")
val adbArm64Lib = File(adbBuildArm64, "libadb_host_wireless.a")
val adbX86Lib = File(adbBuildX86, "libadb_host_wireless.a")

tasks.register<Exec>("buildAdbDeps") {
    onlyIf { adbBuildScript.exists() && (!adbArm64Lib.exists() || !adbX86Lib.exists()) }
    commandLine("bash", adbBuildScript.absolutePath)
}

tasks.named("preBuild").configure {
    dependsOn("buildAdbDeps")
}
