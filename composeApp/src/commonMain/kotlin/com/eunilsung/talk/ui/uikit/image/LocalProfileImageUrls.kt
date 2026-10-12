package com.eunilsung.talk.ui.uikit.image

import androidx.compose.runtime.compositionLocalOf
import com.eunilsung.talk.domain.model.ProfileImageUrls

/**
 * 프로필 사진 주소. 메인 화면이 [MainViewModel][com.eunilsung.talk.ui.main.MainViewModel] 의 상태로 채운다.
 *
 * 아바타는 앱 곳곳의 목록 항목이라 화면마다 ViewModel 을 두지 않고 여기서 읽는다. 사진이 바뀌면 값이 바뀌어
 * 이 값을 읽는 아바타만 다시 그려진다. Preview 에서는 비어 있어 기본 배경만 보인다.
 */
val LocalProfileImageUrls = compositionLocalOf { ProfileImageUrls.EMPTY }
