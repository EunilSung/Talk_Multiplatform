package com.eunilsung.talk.data.remote.push

/** 알림 사운드 미리듣기 — 설정 화면에서 사운드 행을 누르면 즉시 재생. */
interface NotificationSoundPreviewer {

    /** [NotificationSoundCatalog] 의 인덱스로 사운드 재생. */
    fun play(soundId: Int)

    /** 현재 재생 중인 사운드 정지 + 리소스 해제. 중복 호출 안전. */
    fun stop()
}
