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
        // Pleos Connect SDK (공개 저장소, 인증 불필요)
        maven("https://nexus-playground.pleos.ai/repository/maven-releases/")
    }
}

rootProject.name = "data_you"

// M1: 순수 Kotlin 엔진 (안드로이드 의존성 없음 → 어디서나 빌드·테스트 가능)
include(":core-engine")

// M2: AAOS 앱 모듈 (Compose UI + 데이터 소스 어댑터)
include(":app")
