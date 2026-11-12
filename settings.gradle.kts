pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "data_you"

// M1: 순수 Kotlin 엔진 (안드로이드 의존성 없음 → 어디서나 빌드·테스트 가능)
include(":core-engine")

// M2에서 추가: AAOS 앱 모듈
// include(":app")
