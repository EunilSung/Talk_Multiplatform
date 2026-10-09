package com.eunilsung.talk.data.repository

object SettingsKeys {
    const val KEY_ID = "saved_id"
    const val KEY_PW = "saved_pw"
    const val KEY_SAVE_PW = "save_pw"
    /** 서버 인증 토큰 — 잠가서 저장한다. */
    const val KEY_AUTH_TOKEN = "auth_token"
    const val KEY_GROUP_EXPANDED_PREFIX = "group_expanded_"

    /** 메시지 알림 마스터 토글 */
    const val KEY_NOTI_ENABLED = "noti_enabled"
    /** 도착 알림 시 메시지 내용 미리보기 */
    const val KEY_NOTI_PREVIEW = "noti_preview"
    /** Push 알림 시간 설정 (시간대 제한) */
    const val KEY_NOTI_TIME_LIMIT = "noti_time_limit"
    /** Push 알림 시간 설정 값 (형식: 시작hhmm+종료hhmm, 예 "09001800") */
    const val KEY_NOTI_TIME_VALUE = "noti_time_value"
    /** PC 로그인 후 알림 받지 않음 */
    const val KEY_NOTI_PC_OFF = "noti_pc_off"
    /** 소리 알림 */
    const val KEY_NOTI_SOUND = "noti_sound"
    /** 진동 알림 */
    const val KEY_NOTI_VIBRATE = "noti_vibrate"
    /** 선택한 알림음 ID (Int) — 0=기본, 그 외 사용자 정의 인덱스 */
    const val KEY_NOTI_SOUND_ID = "noti_sound_id"

    /** Enter 키 눌러 대화 전송 */
    const val KEY_CHAT_ENTER_SEND = "chat_enter_send"
    /** 대화 글자 크기 (sp 값, Int). 허용값: 13/14/15/17/19/22/25 */
    const val KEY_CHAT_FONT_SIZE = "chat_font_size"
    /** 마지막 선택한 이모티콘 탭 인덱스 — 앱 재시작에도 유지. */
    const val KEY_EMOTICON_LAST_TAB = "emoticon_last_tab"

    /** 잠금 방식 — "NONE" / "PASSWORD" / "PATTERN" */
    const val KEY_LOCK_TYPE = "screen_lock_type"
    /** 비밀번호/패턴 salt+SHA-256 해시 */
    const val KEY_LOCK_HASH = "screen_lock_hash"
    /** 해시 salt (hex) */
    const val KEY_LOCK_SALT = "screen_lock_salt"
    /** 생체인증(지문/Face ID) 사용 */
    const val KEY_LOCK_BIOMETRIC = "screen_lock_biometric"
    /** 패턴 입력 궤적 표시 */
    const val KEY_LOCK_PATTERN_VISIBLE = "screen_lock_pattern_visible"

    /** 공지를 '확인'한 앱 버전명 — 이 값 != 현재 버전명이면 로그인 후 공지 1회 노출. */
    const val KEY_NOTICE_SHOWN_VERSION = "notice_shown_version"

    /** 이미지(Coil) 캐시를 마지막으로 비운 날짜("yyyy-MM-dd"). 하루 1회 초기화 판단용. */
    const val KEY_IMAGE_CACHE_CLEAR_DATE = "image_cache_clear_date"
}

