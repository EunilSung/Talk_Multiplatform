package com.eunilsung.talk.data.remote.push

/**
 * 알림 사운드 카탈로그 — soundId(인덱스) 가 곧 등록 위치.
 * 항목 추가는 반드시 끝에 append (id 안정성 유지).
 */
internal object NotificationSoundCatalog {

    data class Sound(val baseName: String, val displayName: String)

    /** id 0..17 — 18개. */
    val SOUNDS: List<Sound> = listOf(
        Sound("s_ding",        "띵동"),
        Sound("s_button_1",    "보글"),
        Sound("s_page",        "스윽"),
        Sound("s_pencil1",     "펜쓰기"),
        Sound("s_bubble_1",    "물방울"),
        Sound("s_typing_1",    "키보드"),
        Sound("s_typing",      "타이핑"),
        Sound("s_tick_2",      "주사위"),
        Sound("s_tick_1",      "달각"),
        Sound("s_drum",        "둥"),
        Sound("s_water_step",  "수면걷기"),
        Sound("s_page2",       "페이지넘김"),
        Sound("s_ooph",        "우흐"),
        Sound("s_button_2",    "쏙"),
        Sound("s_yank_1",      "지익"),
        Sound("s_yank_2",      "태엽"),
        Sound("s_bell_little", "딩~"),
        Sound("s_dog",         "강아지"),
    )

    const val DEFAULT_SOUND_ID: Int = 0

    /** 안전한 lookup — 범위 밖이면 기본값(0번). */
    fun baseNameOrDefault(id: Int): String =
        SOUNDS.getOrElse(id) { SOUNDS[DEFAULT_SOUND_ID] }.baseName

    fun displayNameOrDefault(id: Int): String =
        SOUNDS.getOrElse(id) { SOUNDS[DEFAULT_SOUND_ID] }.displayName
}
