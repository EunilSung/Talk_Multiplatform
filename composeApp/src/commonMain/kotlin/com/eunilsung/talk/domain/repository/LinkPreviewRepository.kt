package com.eunilsung.talk.domain.repository

import com.eunilsung.talk.domain.model.LinkPreviewState
import kotlinx.coroutines.flow.StateFlow

/** 대화 본문의 링크에 붙일 미리보기. */
interface LinkPreviewRepository {
    /** [url] 의 미리보기 상태. 처음 묻는 주소면 받아 오기 시작하고, 이미 물은 주소면 가지고 있던 것을 준다. */
    suspend fun getState(url: String): StateFlow<LinkPreviewState>
}
