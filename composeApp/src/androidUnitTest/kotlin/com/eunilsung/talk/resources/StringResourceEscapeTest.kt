package com.eunilsung.talk.resources

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 공용 문자열 리소스(`composeResources`)에 Android 식 `\'` 가 없는지 본다.
 *
 * Compose 리소스는 `\n` 은 줄바꿈으로 바꾸지만 `\'` 는 풀지 않아 역슬래시가 화면에 그대로 나온다
 * (`\'a.txt\' 다운로드 완료`). 작은따옴표는 그냥 `'` 로 쓴다.
 */
class StringResourceEscapeTest {

    @Test
    fun 공용_문자열에는_이스케이프한_작은따옴표가_없다() {
        val files = File("src/commonMain/composeResources")
            .walkTopDown()
            .filter { it.name == "strings.xml" }
            .toList()
        assertTrue(files.isNotEmpty(), "strings.xml 을 찾지 못했다")

        val offenders = files.flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                if ("\\'" in line) "${file.parentFile.name}/${file.name}:${index + 1} ${line.trim()}" else null
            }
        }
        assertTrue(offenders.isEmpty(), "\\' 가 남아 있다:\n" + offenders.joinToString("\n"))
    }
}
