//
//  AppDelegate.swift
//  iosApp
//
//  Firebase 초기화 + APNs 등록 + FCM 토큰 수신 후 Kotlin 으로 forward.
//
//  Kotlin 측 [PushTokenBridge.onTokenReceived] 가 호출되면 [PushTokenRepositoryImpl] 이
//  Settings 저장 + 로그인 중이면 서버에 pth=32 ptl=123 통보 (loginRepository.isLoggedIn 체크).
//  앱 처음 진입 / 미로그인 상태면 저장만 하고, 다음 로그인 패킷 slot[12] 에서 자동 전달됨.
//

import UIKit
import FirebaseCore
import FirebaseMessaging
import UserNotifications
import ComposeApp  // KMP 프레임워크 — PushTokenBridge 접근용

class AppDelegate: NSObject, UIApplicationDelegate, MessagingDelegate, UNUserNotificationCenterDelegate {

    // MARK: - Orientation
    /// Phone (iPhone) = 세로 고정, Tablet (iPad) = 회전 허용.
    /// Android 의 `smallestScreenWidthDp >= 600` 분기와 동일 정책 — userInterfaceIdiom 으로 판별.
    func application(_ application: UIApplication,
                     supportedInterfaceOrientationsFor window: UIWindow?) -> UIInterfaceOrientationMask {
        return UIDevice.current.userInterfaceIdiom == .pad ? .all : .portrait
    }

    override init() {
        super.init()
        // SwiftUI App lifecycle (@main struct App) + AppDelegate 조합에서는 Scene-based lifecycle 이
        //   우선되어 UIKit 의 `applicationDidBecomeActive(_:)` 콜백이 발화하지 않을 수 있음.
        //   NotificationCenter 로 시스템 알림을 직접 관찰하면 lifecycle 패턴 무관하게 호출 보장.
        //   share content 폴링 / 알림 권한 미러 등 foreground 진입 시 매번 필요한 작업의 진입점.
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleDidBecomeActive),
            name: UIApplication.didBecomeActiveNotification,
            object: nil
        )
        // Compose Multiplatform iOS Skia glyph-cache eviction 회피 — 백그라운드 진입 중 iOS 가 메모리
        //   회수로 Skia 의 glyph atlas 텍스처를 dropp 하면 foreground 복귀 시 텍스트가 투명으로
        //   렌더되는 증상이 발생 (TopBar 타이틀 / 버튼 라벨 / 본문 텍스트 모두 안 보이고 배경/shape/
        //   image 만 보이는 패턴). willEnterForeground 시점에 ComposeUIViewController.view 에
        //   setNeedsLayout + setNeedsDisplay 를 강제로 걸어 Skia surface 재할당 + glyph atlas
        //   재구축을 유도한다.
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleWillEnterForeground),
            name: UIApplication.willEnterForegroundNotification,
            object: nil
        )
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
    }

    @objc private func handleDidBecomeActive() {
        print("[Share/iOS] ▶ didBecomeActiveNotification observed")
        SystemNotificationBridge.shared.refreshEnabled()
        checkShareDiagnostic()
        checkAndConsumeSharedContent()
        DispatchQueue.main.asyncAfter(deadline: .now() + 5.0) {
            SharedFileCleanupKt.cleanupOldSharedFiles(maxAgeMillis: 60 * 60 * 1000)
        }
    }

    /// 백그라운드 → foreground 전환 직전 호출. Compose 의 Skia rendering surface 와 glyph atlas 를
    /// 강제 재할당 시켜 텍스트가 투명으로 그려지는 증상을 회피한다.
    /// 자세한 배경은 init 안의 옵저버 등록 주석 참고.
    @objc private func handleWillEnterForeground() {
        print("[Compose/iOS] ▶ willEnterForegroundNotification — forcing root view layout")
        DispatchQueue.main.async {
            // 활성 scene 의 keyWindow → rootViewController.view 까지 도달해야 ComposeUIViewController
            // 의 root view 에 invalidation 이 전달됨. multi-scene 환경(iPad) 대비 connectedScenes 에서
            // 활성/비활성 foreground scene 만 필터.
            let foregroundScene = UIApplication.shared.connectedScenes.first { scene in
                scene.activationState == .foregroundActive
                    || scene.activationState == .foregroundInactive
            } as? UIWindowScene
            guard let window = foregroundScene?.windows.first(where: { $0.isKeyWindow })
                    ?? foregroundScene?.windows.first,
                  let root = window.rootViewController?.view else {
                print("[Compose/iOS] willEnterForeground — no root view found, skip")
                return
            }
            root.setNeedsDisplay()
            root.setNeedsLayout()
            root.layoutIfNeeded()
        }
    }

    /// 진단용 — ShareExtension 이 작성한 share_diag.log 가 있으면 읽어서 Console 에 표시.
    private func checkShareDiagnostic() {
        guard let containerURL = FileManager.default.containerURL(
            forSecurityApplicationGroupIdentifier: shareAppGroupID
        ) else { return }
        let url = containerURL.appendingPathComponent("share_diag.log")
        guard FileManager.default.fileExists(atPath: url.path),
              let content = try? String(contentsOf: url, encoding: .utf8) else {
            print("[Share/iOS] 🔍 share_diag.log 없음 — ShareExtension 이 container 접근 못 했거나 아예 실행 안 됨")
            return
        }
        print("[Share/iOS] 🔍 ===== ShareExt diagnostic log =====")
        for line in content.split(separator: "\n") {
            print("[Share/iOS] 🔍 \(line)")
        }
        print("[Share/iOS] 🔍 ===================================")
        try? FileManager.default.removeItem(at: url)
    }

    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {

        // 0) 앱이 killed 상태에서 푸시 탭으로 launch 된 경우 — launchOptions 에 remoteNotification 포함.
        //    PendingPushNavigation 에 등록해 두면 MainScreen 진입 후 라우팅됨.
        if let remote = launchOptions?[.remoteNotification] as? [AnyHashable: Any] {
            print("[Push/iOS] cold launch via push tap — userInfo=\(remote)")
            applyPushTapForRouting(userInfo: remote)
        }

        // 1) Firebase 초기화 — GoogleService-Info.plist 가 번들에 포함되어 있어야 함.
        FirebaseApp.configure()

        // 2) FCM messaging delegate 설정. autoInit 은 끔 — 앱 실행 시 토큰 자동 생성 없이
        //    로그인 성공 시 refreshFcmToken() 의 getToken 으로만 발급/등록.
        Messaging.messaging().delegate = self
        Messaging.messaging().isAutoInitEnabled = false

        // 3) 사용자 알림 권한 요청 + APNs 등록.
        let center = UNUserNotificationCenter.current()
        center.delegate = self

        // 현재 권한 상태 먼저 로그.
        center.getNotificationSettings { settings in
            print("[Push/iOS] current authorizationStatus = \(settings.authorizationStatus.rawValue) " +
                  "(0=notDetermined 1=denied 2=authorized 3=provisional 4=ephemeral)")
        }

        let options: UNAuthorizationOptions = [.alert, .sound, .badge]
        center.requestAuthorization(options: options) { granted, error in
            if let error = error {
                print("[Push/iOS] requestAuthorization error: \(error)")
                return
            }
            print("[Push/iOS] requestAuthorization granted=\(granted)")
            if granted {
                DispatchQueue.main.async {
                    UIApplication.shared.registerForRemoteNotifications()
                    print("[Push/iOS] called registerForRemoteNotifications")
                }
            } else {
                print("[Push/iOS] notification permission denied — 설정 → Talk → 알림 → 허용 필요")
            }
        }

        // 4) FCM 토큰 강제 재발급 액션 등록 — 로그인 성공 시 공통 코드가
        //    PushTokenBridge.requestRefresh() 로 아래 액션(deleteToken→token)을 호출한다.
        //    앱 실행 시점엔 재발급하지 않음(로그인 때만).
        PushTokenBridge.shared.setRefreshAction { [weak self] in
            self?.refreshFcmToken()
        }

        return true
    }

    /// FCM 토큰 강제 재발급 — deleteToken 후 새 토큰을 조회해 [PushTokenBridge] 로 전달.
    /// 로그인 성공 시 `PushTokenBridge.requestRefresh()` 로 호출됨.
    func refreshFcmToken() {
        Messaging.messaging().deleteToken { deleteError in
            if let deleteError = deleteError {
                print("[Push/iOS] deleteToken error: \(deleteError)")
            }
            Messaging.messaging().token { token, error in
                if let error = error {
                    print("[Push/iOS] fetchToken error: \(error)")
                    return
                }
                if let token = token, !token.isEmpty {
                    print("[Push/iOS] reissued token: \(token)")
                    PushTokenBridge.shared.onTokenReceived(token: token)
                }
            }
        }
    }

    // MARK: - Foreground enter (system notification permission mirror)

    /// SwiftUI App lifecycle 환경에서는 호출 안 되지만, 순수 UIKit lifecycle 사용 시를 대비해 남겨둠.
    /// 실제 처리는 `handleDidBecomeActive()` (NotificationCenter observer) 가 일원화.
    func applicationDidBecomeActive(_ application: UIApplication) {
        print("[Share/iOS] ▶ applicationDidBecomeActive (UIKit callback)")
        // 비워둠 — handleDidBecomeActive 가 NotificationCenter 로 모든 케이스 커버.
    }

    /// (deprecated) Share Extension URL scheme 우회 — iOS 17/18 에서 차단됨. 호환성 유지만.
    func application(_ app: UIApplication,
                     open url: URL,
                     options: [UIApplication.OpenURLOptionsKey: Any] = [:]) -> Bool {
        print("[Share/iOS] open URL — \(url.absoluteString)")
        return false
    }

    // MARK: - Share Extension → KMP forward

    private let shareAppGroupID = "group.com.eunilsung.talk"
    private let sharedJsonName = "shared.json"

    /// Share Extension 이 작성한 shared.json 이 있는지 확인 → 읽어서 [PendingShareNavigation] 에 등록
    /// 후 파일 삭제. 메인 앱이 foreground 진입 (cold launch / background→foreground) 마다 호출.
    /// shared.json 페이로드:
    ///   { "text": String, "filePaths": [String], "timestamp": Double }
    private func checkAndConsumeSharedContent() {
        guard let containerURL = FileManager.default.containerURL(
            forSecurityApplicationGroupIdentifier: shareAppGroupID
        ) else {
            print("[Share/iOS] ❌ App Group container not available — id=\(shareAppGroupID)")
            print("[Share/iOS]    → 메인 앱의 App Group capability 가 활성화되지 않았거나 entitlements 누락.")
            return
        }
        print("[Share/iOS] ✓ container=\(containerURL.path)")
        let jsonURL = containerURL.appendingPathComponent(sharedJsonName)
        let exists = FileManager.default.fileExists(atPath: jsonURL.path)
        print("[Share/iOS]   shared.json exists=\(exists) path=\(jsonURL.path)")
        guard exists else { return }

        do {
            let data = try Data(contentsOf: jsonURL)
            guard let payload = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
                print("[Share/iOS] shared.json: invalid JSON")
                try? FileManager.default.removeItem(at: jsonURL)
                return
            }
            let text = payload["text"] as? String ?? ""
            let filePaths = payload["filePaths"] as? [String] ?? []
            print("[Share/iOS] consumed shared.json text=\(text.count) files=\(filePaths.count)")

            if !text.isEmpty || !filePaths.isEmpty {
                let content = SharedContent(text: text, filePaths: filePaths)
                PendingShareNavigation.shared.set(content: content)
            }

            // 처리 완료 — 파일 제거 (중복 처리 방지).
            try? FileManager.default.removeItem(at: jsonURL)
        } catch {
            print("[Share/iOS] checkAndConsumeSharedContent failed: \(error)")
            // 손상된 파일은 제거 — 무한 재시도 방지.
            try? FileManager.default.removeItem(at: jsonURL)
        }
    }

    // MARK: - APNs Token

    /// 시스템이 APNs device token 을 발급하면 호출됨. Firebase 에 전달해 FCM 토큰으로 변환.
    /// 실제 FCM 토큰은 `messaging(_:didReceiveRegistrationToken:)` 에서 받음.
    func application(_ application: UIApplication,
                     didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        print("[Push/iOS] APNs token registered")
        Messaging.messaging().apnsToken = deviceToken
    }

    func application(_ application: UIApplication,
                     didFailToRegisterForRemoteNotificationsWithError error: Error) {
        print("[Push/iOS] APNs registration failed: \(error)")
    }

    // MARK: - FCM Token (MessagingDelegate)

    /// FCM 토큰 발급 / 갱신 시 호출. Kotlin 의 PushTokenBridge 로 forward.
    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        guard let token = fcmToken, !token.isEmpty else {
            print("[Push/iOS] didReceiveRegistrationToken: empty")
            return
        }
        print("[Push/iOS] FCM token: \(String(token.prefix(12)))…")
        PushTokenBridge.shared.onTokenReceived(token: token)
    }

    // MARK: - Remote Push (data-only) → Kotlin PushReceiver

    /// FCM data-only 푸시 도착 시 호출 (background + foreground).
    /// userInfo 에서 알려진 키들을 추출해 Kotlin PushReceiver 로 전달 → Notifier 가 알림 표시.
    func application(_ application: UIApplication,
                     didReceiveRemoteNotification userInfo: [AnyHashable : Any],
                     fetchCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void) {
        print("[Push/iOS] ▶ didReceiveRemoteNotification — appState=\(application.applicationState.rawValue)")
        print("[Push/iOS]   userInfo = \(userInfo)")
        forwardToKotlin(userInfo: userInfo, source: "didReceiveRemoteNotification")
        completionHandler(.newData)
    }

    private func forwardToKotlin(userInfo: [AnyHashable : Any], source: String) {
        func str(_ key: String) -> String {
            return userInfo[key] as? String ?? ""
        }
        let msg = str("msg")
        let msgKey = str("msgkey")
        let msgKind = str("msgkind")
        let msgType = str("msgtype")
        let senderName = str("senderName")
        let unreadCount = str("unReadCount")
        let categoryId = str("msgCategoryId")
        print("[Push/iOS]   parsed [\(source)] — kind='\(msgKind)' key='\(msgKey)' type='\(msgType)' sender='\(senderName)' body='\(msg)' unread='\(unreadCount)'")
        PushReceiver.shared.onPushReceived(
            msg: msg,
            msgKey: msgKey,
            msgKind: msgKind,
            msgType: msgType,
            senderName: senderName,
            unreadCount: unreadCount,
            categoryId: categoryId
        )
    }

    // MARK: - Foreground notification presentation

    /// 포그라운드 상태에서 푸시 도착 시 호출 (시스템 직배달 notification 또는 우리가 add 한 로컬 노티).
    /// banner + sound + badge 옵션 반환해 화면 상단 배너 + 사운드 + 배지 모두 표시.
    /// 포그라운드 상태에서 푸시 도착 시 호출. 항상 시스템 기본 팝업 표시 (banner + sound + badge).
    /// 서버가 `aps.alert.title` / `aps.alert.body` 를 채워서 보내면 시스템이 자동으로 그 내용으로 표시.
    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                willPresent notification: UNNotification,
                                withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        let req = notification.request
        let info = req.content.userInfo
        let isLocal = req.trigger == nil && req.identifier.hasPrefix("") // 우리 로컬 알림은 identifier=msgKey
        let hasAlert = !req.content.title.isEmpty || !req.content.body.isEmpty
        print("[Push/iOS] ▶ willPresent — id=\(req.identifier) title='\(req.content.title)' body='\(req.content.body)' isLocal=\(isLocal) hasAlert=\(hasAlert)")
        print("[Push/iOS]   notification.userInfo = \(info)")

        // 현재 보고 있는 대화방의 CHAT 푸시면 포그라운드 표시 억제 (Kotlin PushReceiver 와 동일 판정).
        //   서버가 alert(notification) 페이로드로 보내면 시스템이 직접 표시하려 하므로 여기서 차단.
        let msgKind = (info["msgkind"] as? String) ?? (info["talk_push_msgkind"] as? String) ?? ""
        let msgKey  = (info["msgkey"]  as? String) ?? (info["talk_push_msgkey"]  as? String) ?? ""
        if PushReceiver.shared.shouldSuppressForegroundChat(msgKind: msgKind, msgKey: msgKey) {
            print("[Push/iOS]   suppress — user is viewing room \(msgKey)")
            completionHandler([])
            return
        }

        // 서버가 일반 notification 으로 보낼 경우 본 핸들러에서 시스템이 자체 표시 → 미리보기 분기 우회됨.
        // data-only 면 이 핸들러가 호출되지 않거나 우리 로컬 알림 표시 경로로 흐름.
        completionHandler([.banner, .list, .sound, .badge])
    }

    /// 사용자가 알림을 탭했을 때 호출. userInfo 에서 라우팅 정보 추출 → PendingPushNavigation 에 등록.
    /// MainScreen 의 observer 가 로그인/소켓 준비되면 자동으로 화면 push.
    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                didReceive response: UNNotificationResponse,
                                withCompletionHandler completionHandler: @escaping () -> Void) {
        let info = response.notification.request.content.userInfo
        print("[Push/iOS] ▶ didReceive (user tap) — id=\(response.notification.request.identifier)")
        print("[Push/iOS]   userInfo = \(info)")
        applyPushTapForRouting(userInfo: info)
        completionHandler()
    }

    /// 푸시 탭 시 routing 정보 추출 → Kotlin PendingPushNavigation 에 등록.
    /// 키 추출 우선순위:
    ///  - 서버가 보낸 FCM data 페이로드 키 (`msgkind` / `msgkey` / `msgCategoryId`)
    ///  - 우리가 만든 로컬 알림의 키 (`talk_push_msgkind` / `talk_push_msgkey` / `talk_push_msgcategory`)
    private func applyPushTapForRouting(userInfo: [AnyHashable: Any]) {
        func str(_ keys: String...) -> String {
            for key in keys {
                if let v = userInfo[key] as? String, !v.isEmpty { return v }
            }
            return ""
        }
        let kind = str("msgkind", "talk_push_msgkind")
        let key = str("msgkey", "talk_push_msgkey")
        let cat = str("msgCategoryId", "talk_push_msgcategory")
        if !kind.isEmpty && !key.isEmpty {
            print("[Push/iOS] tap → kind=\(kind) key=\(key) cat=\(cat)")
            PendingPushNavigation.shared.set(kind: kind, msgKey: key, categoryId: cat)
        } else {
            print("[Push/iOS] tap — routing 키 부족, skip")
        }
    }
}
