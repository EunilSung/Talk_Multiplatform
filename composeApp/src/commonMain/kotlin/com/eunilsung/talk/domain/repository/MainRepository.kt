package com.eunilsung.talk.domain.repository

import kotlinx.coroutines.flow.SharedFlow
import com.eunilsung.talk.domain.model.MainEvent

interface MainRepository {
    val events: SharedFlow<MainEvent>
}
