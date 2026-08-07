import SwiftUI

@main
struct iOSApp: App {
    // SwiftUI App 에 AppDelegate 연결 — Firebase / APNs / FCM 콜백 수신용.
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
