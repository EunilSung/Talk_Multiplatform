package com.eunilsung.talk.domain.model

/**
 * 알림 사운드 카탈로그 — soundId(인덱스) 가 곧 등록 위치.
 * 항목 추가는 반드시 끝에 append (id 안정성 유지).
 */
internal object NotificationSoundCatalog {

    /** 알림음 파일 이름(확장자 제외). 화면에 보이는 이름은 화면이 기기 언어에 맞춰 붙인다. */
    data class Sound(val baseName: String)

    /** id 0..17 — 18개. */
    val SOUNDS: List<Sound> = listOf(
        Sound("s_ding"),
        Sound("s_button_1"),
        Sound("s_page"),
        Sound("s_pencil1"),
        Sound("s_bubble_1"),
        Sound("s_typing_1"),
        Sound("s_typing"),
        Sound("s_tick_2"),
        Sound("s_tick_1"),
        Sound("s_drum"),
        Sound("s_water_step"),
        Sound("s_page2"),
        Sound("s_ooph"),
        Sound("s_button_2"),
        Sound("s_yank_1"),
        Sound("s_yank_2"),
        Sound("s_bell_little"),
        Sound("s_dog"),
    )

    const val DEFAULT_SOUND_ID: Int = 0

    /** 안전한 lookup — 범위 밖이면 기본값(0번). */
    fun baseNameOrDefault(id: Int): String =
        SOUNDS.getOrElse(id) { SOUNDS[DEFAULT_SOUND_ID] }.baseName
}
