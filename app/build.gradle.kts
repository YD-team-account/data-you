plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "team.datayou.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "team.datayou.app"
        minSdk = 28
        targetSdk = 34
        versionCode = 3
        versionName = "0.3.0-m3"
        // 시연용(true, 기본): 첫 화면에서 녹화 시나리오·실시간을 고른다.
        // 실차용(false): 첫 화면 없이 실시간 차량 데이터로 바로 주행 화면을 연다.
        //   ./gradlew :app:assembleDebug -PdemoMode=false
        val demoMode = (project.findProperty("demoMode") as String?)?.toBooleanStrictOrNull() ?: true
        buildConfigField("boolean", "DEMO_MODE", demoMode.toString())
    }

    // AAOS Car API (CarPropertyManager, CarUxRestrictionsManager)
    useLibrary("android.car")

    // 엔진 시연용 주행 CSV를 앱에 함께 넣는다
    sourceSets["main"].assets.srcDir(rootProject.file("sample-data"))

    buildTypes {
        release {
            isMinifyEnabled = false
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
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                // 화면 스크린샷 기록: ./gradlew :app:testDebugUnitTest → app/screenshots/*.png
                it.systemProperty("roborazzi.test.record", "true")
            }
        }
    }
}

dependencies {
    implementation(project(":core-engine"))

    // M3: Pleos Connect SDK
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:NaviHelper:3.0.5")

    // 실시간 모드 실제 지도 (OpenStreetMap, 키·구글 서비스 불필요)
    implementation("org.osmdroid:osmdroid-android:6.1.20")

    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // 화면 스크린샷 테스트 (에뮬레이터 없이 JVM에서 Compose 렌더링)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.26.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.26.0")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
