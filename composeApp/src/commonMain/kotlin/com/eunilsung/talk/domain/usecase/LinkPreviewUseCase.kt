package com.eunilsung.talk.domain.usecase

import com.eunilsung.talk.domain.model.LinkPreviewState
import com.eunilsung.talk.domain.repository.LinkPreviewRepository
import kotlinx.coroutines.flow.StateFlow

/** 링크 미리보기. 처음 묻는 주소면 받아 오기 시작하고, 이미 물은 주소면 가지고 있던 것을 준다. */
class GetLinkPreviewUseCase(private val repository: LinkPreviewRepository) {
    suspend operator fun invoke(url: String): StateFlow<LinkPreviewState> = repository.getState(url)
}
