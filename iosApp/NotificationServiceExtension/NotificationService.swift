//
//  NotificationService.swift
//  Talk Notification Service Extension
//
//  서버가 보낸 일반 APNs notification 을 수신 직후 본문/사운드 가공.
//  메인 앱이 App Group `NSUserDefaults` 에 저장한 noti 설정 값을 읽어 다음 분기 적용:
//   - `noti_preview = false`      → body 를 "새로운 메시지가 있습니다" 로 대체
//   - `noti_sound_enabled = false` → sound 를 nil (무음)
//   - `noti_sound_id` (Int)        → "{baseName}.wav" 사운드 파일 적용
//
//  서버는 APNs payload 의 `aps` 에 `mutable-content: 1` 을 포함해서 보내야 본 Extension 이 호출됨.
//

import UserNotifications

class NotificationService: UNNotificationServiceExtension {

    /// App Group identifier — 메인 앱 (composeApp) 의 `AppGroupNotificationMirror.kt` 와 동일.
    private let appGroupId = "group.com.eunilsung.talk"

    // App Group UserDefaults 키 — Kotlin 측 `APP_GROUP_KEY_*` 와 동일.
    private let keyPreview = "noti_preview"
    private let keySoundEnabled = "noti_sound_enabled"
    private let keySoundId = "noti_sound_id"

    /// 미리보기 OFF 일 때 본문 대체 텍스트 — Kotlin `PushNotifier.PREVIEW_HIDDEN_BODY` 와 동일.
    private let hiddenBody = "새로운 메시지가 있습니다"

    /// soundId → wav baseName. Kotlin `NotificationSoundCatalog.SOUNDS` 와 순서 일치.
    /// 인덱스 범위 밖은 0번 (`s_ding`) 으로 fallback.
    private let soundBaseNames: [String] = [
        "s_ding",        // 0  띵동
        "s_button_1",    // 1  보글
        "s_page",        // 2  스윽
        "s_pencil1",     // 3  펜쓰기
        "s_bubble_1",    // 4  물방울
        "s_typing_1",    // 5  키보드
        "s_typing",      // 6  타이핑
        "s_tick_2",      // 7  주사위
        "s_tick_1",      // 8  달각
        "s_drum",        // 9  둥
        "s_water_step",  // 10 수면걷기
        "s_page2",       // 11 페이지넘김
        "s_ooph",        // 12 우흐
        "s_button_2",    // 13 쏙
        "s_yank_1",      // 14 지익
        "s_yank_2",      // 15 태엽
        "s_bell_little", // 16 딩~
        "s_dog",         // 17 강아지
    ]

    var contentHandler: ((UNNotificationContent) -> Void)?
    var bestAttemptContent: UNMutableNotificationContent?

    override func didReceive(_ request: UNNotificationRequest,
                             withContentHandler contentHandler: @escaping (UNNotificationContent) -> Void) {
        self.contentHandler = contentHandler
        self.bestAttemptContent = (request.content.mutableCopy() as? UNMutableNotificationContent)

        guard let content = bestAttemptContent else {
            contentHandler(request.content)
            return
        }

        let defaults = UserDefaults(suiteName: appGroupId)

        // 1) 미리보기 분기 — 키 없으면 기본 true (메인 앱 첫 실행 전).
        let previewEnabled: Bool = {
            guard let d = defaults else { return true }
            if d.object(forKey: keyPreview) == nil { return true }
            return d.bool(forKey: keyPreview)
        }()
        if !previewEnabled {
            content.body = hiddenBody
            // 제목 (발신자명) 은 유지 — 메신저 패턴.
        }

        // 2) 소리 분기.
        let soundEnabled: Bool = {
            guard let d = defaults else { return true }
            if d.object(forKey: keySoundEnabled) == nil { return true }
            return d.bool(forKey: keySoundEnabled)
        }()
        if !soundEnabled {
            content.sound = nil
        } else {
            let soundId = defaults?.integer(forKey: keySoundId) ?? 0
            let baseName = soundBaseNameFor(id: soundId)
            content.sound = UNNotificationSound(named: UNNotificationSoundName("\(baseName).wav"))
        }

        contentHandler(content)
    }

    /// 30초 타임아웃 직전 호출. 현재 시점의 best-attempt content 를 그대로 전달.
    override func serviceExtensionTimeWillExpire() {
        if let contentHandler = contentHandler, let bestAttemptContent = bestAttemptContent {
            contentHandler(bestAttemptContent)
        }
    }

    private func soundBaseNameFor(id: Int) -> String {
        guard id >= 0 && id < soundBaseNames.count else { return soundBaseNames[0] }
        return soundBaseNames[id]
    }
}
