rootProject.name = "MultiplatformTalk"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

/**
 * 서버만 빌드하는가 — 배포본을 만들 때 켠다.
 *
 * 앱 모듈은 Android SDK 가 있어야 설정 단계부터 통과한다. SDK 가 없는 곳(컨테이너 등)에서
 * 서버만 빌드할 수 있도록 안드로이드 쪽을 통째로 뺀다.
 */
val serverOnly = System.getenv("SERVER_ONLY") == "1"

if (!serverOnly) include(":composeApp")

/** 앱과 서버가 요청·응답 모델을 공유한다. 빌드를 쪼개면 공유가 안 되므로 한 빌드에 둔다. */
include(":shared")
include(":server")