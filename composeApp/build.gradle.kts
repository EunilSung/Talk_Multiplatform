import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.atomicfu)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.googleServices)
    alias(libs.plugins.firebaseCrashlytics)
}

/**
 * 서버 주소를 `local.properties` 의 `server.baseUrl` 에서 읽는다.
 *
 * 비워 두면 서버 없이 로컬 데이터만으로 도는 모드로 빌드된다. 값을 넣으면 그 서버에 붙는다.
 *   - Android 에뮬레이터: http://10.0.2.2:8090
 *   - iOS 시뮬레이터:     http://localhost:8090
 */
val serverBaseUrlProperty: String = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}.getProperty("server.baseUrl").orEmpty().trim()

/**
 * configuration cache 가 켜져 있어 빌드 스크립트 객체를 붙잡는 doLast 람다는 쓸 수 없다.
 * 입력과 출력을 프로퍼티로 선언한 타입 태스크로 만든다.
 */
abstract class GenerateServerConfigTask : DefaultTask() {
    @get:Input
    abstract val serverBaseUrl: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val directory = outputDir.get().asFile.resolve("com/eunilsung/talk/data/remote/server")
        directory.mkdirs()
        directory.resolve("ServerBuildConfig.kt").writeText(
            """
            package com.eunilsung.talk.data.remote.server

            /** local.properties 의 server.baseUrl 에서 빌드 시점에 생성됨. 직접 수정하지 말 것. */
            internal const val SERVER_BASE_URL: String = "${serverBaseUrl.get()}"
            """.trimIndent() + "\n"
        )
    }
}

val generateServerConfig by tasks.registering(GenerateServerConfigTask::class) {
    serverBaseUrl.set(serverBaseUrlProperty)
    outputDir.set(layout.buildDirectory.dir("generated/serverConfig/commonMain/kotlin"))
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            linkerOpts("-lsqlite3")
        }
    }
    
    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.splashscreen)
            implementation(libs.sqldelight.android.driver)
            implementation(libs.ktor.client.okhttp)

            // Firebase BOM — Cloud Messaging (FCM), Crashlytics. Android-only.
            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.firebase.messaging)
            implementation(libs.firebase.crashlytics)

            implementation(libs.shortcut.badger)   // 런처 앱아이콘 숫자 배지
            implementation(libs.androidx.biometric) // 지문/얼굴 BiometricPrompt
        }
        commonMain {
            kotlin.srcDir(generateServerConfig)
        }
        commonMain.dependencies {
            implementation(projects.shared)

            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)

            // Koin
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            // Ktor & Serialization
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.websockets)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)

            // Coil
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor)
            implementation(libs.coil.gif)   // 움직이는 이모티콘(GIF) 디코더

            // Multiplatform Settings
            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.no.arg)

            // Voyager
            implementation(libs.voyager.navigator)
            implementation(libs.voyager.screenModel)
            implementation(libs.voyager.bottomSheetNavigation)
            implementation(libs.voyager.transitions)
            implementation(libs.voyager.koin)

            // SQLDelight
            implementation(libs.sqldelight.runtime)

            // Krypto
            implementation(libs.krypto)
            
            // Atomicfu
            implementation(libs.kotlinx.atomicfu)

            // Compose WebView Multiplatform (HTML 본문 렌더링용)

            // ImagePickerKMP — 카메라 / 갤러리 / 파일 picker
            implementation(libs.imagepicker.kmp)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)   // runTest
            implementation(libs.multiplatform.settings.test) // MapSettings (인메모리)
        }
        androidUnitTest.dependencies {
            implementation(libs.sqldelight.sqlite.driver)  // JdbcSqliteDriver — 인메모리 DB
        }

        // iosMain / iosTest 는 기본 계층 템플릿이 만들어 준다.
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
            implementation(libs.ktor.client.darwin)
        }
        iosTest.dependencies {
            implementation(libs.sqldelight.native.driver)
        }
    }
}

sqldelight {
    databases {
        create("AppDatabase") {
            packageName.set("com.eunilsung.talk.db")
        }
    }
}

compose.resources {
    packageOfResClass = "multiplatformtalk.composeapp.generated.resources"
}

android {
    namespace = "com.eunilsung.talk"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.eunilsung.talk"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1000
        versionName = "10.0.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            // 쇼케이스 샘플이라 난독화 이득이 없고, keep 룰 없이 R8 을 켜면
            // Koin/Voyager/SQLDelight 리플렉션 경로가 런타임에 깨질 위험이 더 크다.
            isMinifyEnabled = false
        }
    }
    testOptions {
        unitTests {
            // Compose 리소스(getString)가 JVM 유닛테스트에서 android.content.res 를 건드린다.
            // 실제 문구를 검증하지 않는 저장소 테스트라 기본값 반환으로 충분하다.
            isReturnDefaultValues = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(libs.compose.uiTooling)
}

/**
 * 기능 테스트(`TALK_FUNCTEST_URL`)는 진짜 서버의 상태를 본다. 코드가 그대로여도 서버가 달라졌을 수
 * 있으므로, 주소가 주어졌을 때는 지난 결과를 다시 쓰지 않고 매번 돌린다.
 */
tasks.withType<Test>().configureEach {
    val functestUrl = providers.environmentVariable("TALK_FUNCTEST_URL").orElse("")
    inputs.property("functestUrl", functestUrl)
    outputs.upToDateWhen { functestUrl.get().isBlank() }
}
