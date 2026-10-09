plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    application
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("com.eunilsung.talk.server.ApplicationKt")
}

dependencies {
    implementation(projects.shared)

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.logback.classic)

    implementation(libs.hikaricp)
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.postgresql)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)

    testImplementation(libs.embedded.postgres)
    testImplementation(platform(libs.embedded.postgres.binaries.bom))
    testRuntimeOnly(libs.embedded.postgres.binaries.darwin.arm64)
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
    testLogging {
        events("passed", "skipped", "failed")
    }
}

/**
 * 로컬 개발용 실행 — 내장 PostgreSQL 을 함께 띄운다.
 *
 * PostgreSQL 을 따로 설치하지 않아도 서버가 뜬다. 데이터는 `server/.localdb` 에 남아 다시 켜도 유지된다.
 * 내장 PostgreSQL 은 테스트 의존성이라 배포본(`installDist`)에는 들어가지 않는다.
 *
 *   ./gradlew :server:runLocal
 */
tasks.register<JavaExec>("runLocal") {
    group = "application"
    description = "내장 PostgreSQL 과 함께 서버를 띄운다 (로컬 개발용)"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.eunilsung.talk.server.local.LocalServerKt")
    workingDir = projectDir
    jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(17)) })
}
