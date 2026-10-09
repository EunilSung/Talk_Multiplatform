package com.eunilsung.talk

import com.eunilsung.talk.util.Log
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSBundle
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationState
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.UIKit.UIColor
import platform.UIKit.UIImage
import platform.UIKit.UIImageView
import platform.UIKit.UIScene
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UISceneDidActivateNotification
import platform.UIKit.UISceneDidEnterBackgroundNotification
import platform.UIKit.UISceneWillDeactivateNotification
import platform.UIKit.UITraitCollection
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIView
import platform.UIKit.UIViewAutoresizingFlexibleBottomMargin
import platform.UIKit.UIViewAutoresizingFlexibleHeight
import platform.UIKit.UIViewAutoresizingFlexibleLeftMargin
import platform.UIKit.UIViewAutoresizingFlexibleRightMargin
import platform.UIKit.UIViewAutoresizingFlexibleTopMargin
import platform.UIKit.UIViewAutoresizingFlexibleWidth
import platform.UIKit.UIViewContentMode
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowDidBecomeVisibleNotification
import platform.UIKit.UIWindowLevelNormal
import platform.UIKit.UIWindowScene
import platform.UIKit.colorWithDynamicProvider

/**
 * iOS 앱 전환기에 대화 내용이 그림으로 남지 않게 **창 맨 위에 앱 실행 첫 화면과 같은 가림막**을 올린다([Config.AppSwitcherSnapshot]).
 *
 * iOS 는 앱이 뒤로 갈 때 화면을 찍어 `Library/SplashBoard/Snapshots` 에 저장하고 앱 전환기에 보여 준다.
 * 시뮬레이터에서 9일 지난 스냅샷에 직원 이름·안읽음 수가 그대로 남아 있는 것을 확인했다.
 *
 * ### 왜 UIWindow 에 직접 붙이나
 *
 * - **Compose 로 그리면 안 된다.** CMP 는 백그라운드 진입 때 그리기를 멈춰, 그 뒤에 바꾼 화면은 스냅샷 전에
 *   그려지지 않는다. 같은 이유로 화면 잠금(ON_STOP 에서 잠금)도 앱 전환기 스냅샷은 못 가린다.
 * - **SwiftUI overlay 는 안 된다.** Compose Dialog 레이어와 QuickLook 같은 UIKit 모달 아래에 깔린다.
 * - **모달 화면으로 덮으면 안 된다.** Compose 가 ON_STOP 을 받아 화면 잠금이 걸리고, Face ID 가 비활성을 만들어
 *   가림막 → 잠금 → 프롬프트가 되풀이될 수 있다.
 * - **별도 창을 key window 로 만들면 안 된다.** keyWindow 로 최상위 화면을 찾는 코드(파일 열기·사진 선택 등)가
 *   가림막 창을 잡는다.
 *
 * ### 올리고 내리는 시점
 *
 * - **올리기: 비활성이 되는 순간**(willResignActive). 앱 전환기·알림센터·제어센터는 백그라운드 없이 비활성만 된다.
 *   백그라운드 진입 때 한 번 더 맨 위로 올린다 — 그 사이 뜬 모달(다운로드가 끝나 열린 QuickLook 등)을 덮는다.
 * - **내리기: 다시 활성이 되는 순간**(didBecomeActive). willEnterForeground 는 비활성만 됐다 돌아올 때 오지 않는다.
 * - iPad 여러 창은 씬 알림으로 창마다 처리하고, 앱 단위 알림은 **내리는 폴백**으로 반드시 둔다 —
 *   씬 알림이 빠지면 가림막이 안 내려가 앱이 빈 화면으로 멈춘 것처럼 보인다(IosForegroundRedraw.kt).
 * - 창이 비활성 상태에서 새로 보이면(앱이 백그라운드에서 시작된 경우) 그 창에도 올린다. 백그라운드 실행 뒤
 *   iOS 가 스냅샷을 새로 찍을 수 있다.
 *
 * 비활성만 되는 동안(권한 팝업·Face ID·알림센터)에도 뒤 화면이 가려진다. 보안상 의도한 동작이다.
 */
private var appSwitcherCoverRegistered = false

/** 가림막을 찾는 표식. 창의 직속 자식에서만 찾는다. */
private const val COVER_TAG: Long = 0x455A51

/** [MainViewController] 생성 시 1회. 화면 재생성 때 다시 불려도 한 번만 등록한다. */
fun registerAppSwitcherCover() {
    if (!Config.AppSwitcherSnapshot.IS_ENABLED) return
    if (appSwitcherCoverRegistered) return
    appSwitcherCoverRegistered = true

    val center = NSNotificationCenter.defaultCenter

    center.addObserverForName(UIApplicationWillResignActiveNotification, null, null) { _ ->
        showCover("willResignActive", windowsOf(null))
    }
    center.addObserverForName(UIApplicationDidEnterBackgroundNotification, null, null) { _ ->
        showCover("enterBackground", windowsOf(null))
    }
    center.addObserverForName(UIApplicationDidBecomeActiveNotification, null, null) { _ ->
        hideCover("didBecomeActive", windowsOf(null).filter { it.isInActiveScene() })
    }

    center.addObserverForName(UISceneWillDeactivateNotification, null, null) { note ->
        showCover("sceneWillDeactivate", windowsOf(note.sceneOrNull()))
    }
    center.addObserverForName(UISceneDidEnterBackgroundNotification, null, null) { note ->
        showCover("sceneEnterBackground", windowsOf(note.sceneOrNull()))
    }
    center.addObserverForName(UISceneDidActivateNotification, null, null) { note ->
        hideCover("sceneDidActivate", windowsOf(note.sceneOrNull()))
    }

    center.addObserverForName(UIWindowDidBecomeVisibleNotification, null, null) { note ->
        val window = note?.`object` as? UIWindow ?: return@addObserverForName
        if (UIApplication.sharedApplication.applicationState == UIApplicationState.UIApplicationStateActive) {
            return@addObserverForName
        }
        showCover("windowVisibleWhileInactive", listOf(window).filter { it.isAppContentWindow() })
    }

    /**
     * 첫 창은 이 등록보다 **먼저** 보이게 되어 위 알림을 놓친다 — SwiftUI 가 창을 띄운 뒤에 화면([MainViewController])을
     * 만든다. 백그라운드에서 시작됐으면(푸시·프리워밍) 지금 있는 창에 바로 올린다. 포그라운드로 올라와 활성이 되면
     * didBecomeActive·sceneDidActivate 가 내린다. 비활성일 뿐인 보통 실행에서는 올리지 않는다 — 활성 알림 전에 등록이
     * 끝났다는 보장이 없어 안 내려갈 수 있다.
     */
    if (UIApplication.sharedApplication.applicationState == UIApplicationState.UIApplicationStateBackground) {
        showCover("registeredInBackground", windowsOf(null))
    }
}

private fun NSNotification?.sceneOrNull(): UIScene? = this?.`object` as? UIScene

/** [scene] 의 앱 화면 창. null 이면 연결된 모든 씬의 창. 키보드 같은 시스템 창(레벨이 다른 창)은 뺀다. */
private fun windowsOf(scene: UIScene?): List<UIWindow> {
    val scenes = if (scene != null) listOf(scene)
    else UIApplication.sharedApplication.connectedScenes.mapNotNull { it as? UIScene }
    return scenes
        .mapNotNull { it as? UIWindowScene }
        .flatMap { windowScene -> windowScene.windows.mapNotNull { it as? UIWindow } }
        .filter { it.isAppContentWindow() }
}

private fun UIWindow.isAppContentWindow(): Boolean = windowLevel == UIWindowLevelNormal

/**
 * 앱 단위 활성 알림에서 내려도 되는 창인지 — 활성 씬의 창이거나, 씬이 하나뿐일 때.
 * iPad 에서 다른 창이 아직 뒤에 있으면 그 창의 가림막은 남긴다.
 */
private fun UIWindow.isInActiveScene(): Boolean {
    val scene = windowScene ?: return true
    if (UIApplication.sharedApplication.connectedScenes.size <= 1) return true
    return scene.activationState == UISceneActivationStateForegroundActive
}

@OptIn(ExperimentalForeignApi::class)
private fun showCover(reason: String, windows: List<UIWindow>) {
    if (windows.isEmpty()) return
    var added = 0
    windows.forEach { window ->
        val existing = window.coverOrNull()
        if (existing != null) {
            window.bringSubviewToFront(existing)
            return@forEach
        }
        window.addSubview(launchScreenCover(window))
        added++
    }
    Log.message("[Lifecycle] cover show reason=$reason windows=${windows.size} added=$added")
}

private fun hideCover(reason: String, windows: List<UIWindow>) {
    var removed = 0
    windows.forEach { window ->
        window.coverOrNull()?.let {
            it.removeFromSuperview()
            removed++
        }
    }
    if (removed > 0) Log.message("[Lifecycle] cover hide reason=$reason removed=$removed")
}

private fun UIWindow.coverOrNull(): UIView? =
    subviews.mapNotNull { it as? UIView }.lastOrNull { it.tag == COVER_TAG }

/**
 * **앱을 켤 때 처음 보이는 화면과 같은 모습** — 배경색 위에 흰 둥근 상자(150), 그 안에 로고(120). 글자는 넣지 않는다(규칙 3).
 *
 * 크기는 첫 화면(`LoginScreen`)과 같게 맞춘다. 로고는 공용 리소스를 앱 번들에서 그대로 읽어 iOS 쪽에 사본을 두지 않는다.
 * 못 읽으면 배경색만 그린다 — 가리는 일 자체는 그대로다.
 *
 * 창 크기가 바뀌어도(회전·iPad 창 크기 조절) 가운데에 남도록 바깥 여백을 늘어나게 잡는다.
 */
@OptIn(ExperimentalForeignApi::class)
private fun launchScreenCover(window: UIWindow): UIView {
    val cover = UIView(frame = window.bounds).apply {
        tag = COVER_TAG
        autoresizingMask = UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight
        backgroundColor = coverColor()
        userInteractionEnabled = true
    }
    val logo = launchLogo() ?: return cover

    val (windowWidth, windowHeight) = window.bounds.useContents { size.width to size.height }
    val (imageWidth, imageHeight) = logo.size.useContents { width to height }
    val boxSize = LOGO_BOX_SIZE
    val logoWidth = LOGO_SIZE
    val logoHeight = if (imageWidth > 0.0) logoWidth * imageHeight / imageWidth else logoWidth

    val box = UIView(
        frame = CGRectMake(
            x = (windowWidth - boxSize) / 2.0,
            y = (windowHeight - boxSize) / 2.0,
            width = boxSize,
            height = boxSize,
        )
    ).apply {
        backgroundColor = UIColor.whiteColor
        layer.cornerRadius = LOGO_BOX_RADIUS
        layer.masksToBounds = true
        autoresizingMask = UIViewAutoresizingFlexibleTopMargin or UIViewAutoresizingFlexibleBottomMargin or
            UIViewAutoresizingFlexibleLeftMargin or UIViewAutoresizingFlexibleRightMargin
    }
    val logoView = UIImageView(
        frame = CGRectMake(
            x = (boxSize - logoWidth) / 2.0,
            y = (boxSize - logoHeight) / 2.0,
            width = logoWidth,
            height = logoHeight,
        )
    ).apply {
        image = logo
        contentMode = UIViewContentMode.UIViewContentModeScaleAspectFit
    }
    box.addSubview(logoView)
    cover.addSubview(box)
    return cover
}

/** 첫 화면(`LoginScreen`)의 로고 상자 — 한 변 150, 모서리 24, 안의 로고 폭 120. */
private const val LOGO_BOX_SIZE = 150.0
private const val LOGO_BOX_RADIUS = 24.0
private const val LOGO_SIZE = 120.0

/** 첫 화면의 로고 — 공용 리소스가 앱 번들의 `compose-resources` 로 들어간다. */
private fun launchLogo(): UIImage? {
    val path = NSBundle.mainBundle.pathForResource(
        name = "logo",
        ofType = "png",
        inDirectory = "compose-resources/composeResources/multiplatformtalk.composeapp.generated.resources/drawable",
    ) ?: return null
    return UIImage.imageWithContentsOfFile(path)
}

/** 첫 화면 배경과 같게 — 다크 #121417, 라이트 흰색(ContentView.swift 의 배경과 같다). */
private fun coverColor(): UIColor = UIColor.colorWithDynamicProvider { traits: UITraitCollection? ->
    if (traits?.userInterfaceStyle == UIUserInterfaceStyle.UIUserInterfaceStyleDark) {
        UIColor.colorWithRed(0x12 / 255.0, 0x14 / 255.0, 0x17 / 255.0, 1.0)
    } else {
        UIColor.whiteColor
    }
}
