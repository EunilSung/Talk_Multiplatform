package com.eunilsung.talk.domain.usecase

import com.eunilsung.talk.domain.model.MainEvent
import com.eunilsung.talk.domain.model.PushNavRequest
import com.eunilsung.talk.domain.model.SharedContent
import com.eunilsung.talk.domain.repository.AppMaintenanceRepository
import com.eunilsung.talk.domain.repository.LoginRepository
import com.eunilsung.talk.domain.repository.MainRepository
import com.eunilsung.talk.domain.repository.PendingPushNavigationRepository
import com.eunilsung.talk.domain.repository.PendingShareNavigationRepository
import com.eunilsung.talk.domain.repository.PushNotifier
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 메인 화면의 유스케이스 묶음.
 *
 * 앱이 앞뒤로 오갈 때의 연결 관리, 강제 로그아웃 알림, 푸시·공유로 들어온 진입, 앱 아이콘 배지,
 * 앱 전체가 쓰는 프로필 사진 주소, 그리고 앱을 켤 때마다 하는 정리(이미지 캐시·버전 안내)가 모여 있다.
 */
data class MainUseCases(
    val observeConnection: ObserveConnectionUseCase,
    val setForeground: SetForegroundUseCase,
    val reconnect: ReconnectUseCase,
    val observeMainEvents: ObserveMainEventsUseCase,
    val observePendingPush: ObservePendingPushUseCase,
    val consumePendingPush: ConsumePendingPushUseCase,
    val observePendingShare: ObservePendingShareUseCase,
    val consumePendingShare: ConsumePendingShareUseCase,
    val setAppBadge: SetAppBadgeUseCase,
    val isImageCacheClearedOn: IsImageCacheClearedOnUseCase,
    val markImageCacheCleared: MarkImageCacheClearedUseCase,
    val observeProfileImages: ObserveProfileImagesUseCase,
    val downloadFile: DownloadFileUseCase,
    val openFile: OpenFileUseCase,
)

/** 서버와 연결돼 있는지. 끊기면 화면 위에 재연결 중 표시를 띄운다. */
class ObserveConnectionUseCase(private val repository: LoginRepository) {
    operator fun invoke(): StateFlow<Boolean> = repository.isConnected
}

/** 앱이 화면 앞에 있는지 알린다. 푸시 알림을 띄울지, 보고 있는 방의 새 대화를 읽은 것으로 칠지가 이 값으로 갈린다. */
class SetForegroundUseCase(private val repository: LoginRepository) {
    operator fun invoke(isForeground: Boolean) = repository.setForeground(isForeground)
}

/** 서버에 닿는지 다시 확인해 연결 상태를 맞춘다. 로그인돼 있지 않으면 아무것도 하지 않는다. */
class ReconnectUseCase(private val repository: LoginRepository) {
    operator fun invoke() = repository.checkConnectionAndRelogin()
}

/** 강제 로그아웃처럼 메인 화면이 받아서 알려야 하는 일. */
class ObserveMainEventsUseCase(private val repository: MainRepository) {
    operator fun invoke(): SharedFlow<MainEvent> = repository.events
}

/** 푸시 알림을 눌러 들어와 아직 열지 못한 화면. */
class ObservePendingPushUseCase(private val repository: PendingPushNavigationRepository) {
    operator fun invoke(): StateFlow<PushNavRequest?> = repository.pending
}

/** 꺼내 가면서 비운다. 꺼낸 쪽이 바로 그 화면으로 가야 한다. */
class ConsumePendingPushUseCase(private val repository: PendingPushNavigationRepository) {
    operator fun invoke(): PushNavRequest? = repository.consume()
}

/** 다른 앱의 공유로 들어와 아직 넘기지 못한 내용. */
class ObservePendingShareUseCase(private val repository: PendingShareNavigationRepository) {
    operator fun invoke(): StateFlow<SharedContent?> = repository.pending
}

/** 꺼내 가면서 비운다. */
class ConsumePendingShareUseCase(private val repository: PendingShareNavigationRepository) {
    operator fun invoke(): SharedContent? = repository.consume()
}

/** 앱 아이콘 배지 숫자. */
class SetAppBadgeUseCase(private val notifier: PushNotifier) {
    operator fun invoke(count: Int) = notifier.setBadge(count)
}

/** 이미지 캐시를 [date] 에 이미 비웠는지. */
class IsImageCacheClearedOnUseCase(private val repository: AppMaintenanceRepository) {
    operator fun invoke(date: String): Boolean = repository.imageCacheClearedDate() == date
}

class MarkImageCacheClearedUseCase(private val repository: AppMaintenanceRepository) {
    operator fun invoke(date: String) = repository.setImageCacheClearedDate(date)
}
