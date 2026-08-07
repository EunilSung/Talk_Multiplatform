package com.eunilsung.talk

import androidx.compose.runtime.mutableStateOf
import com.eunilsung.talk.util.Log
import platform.Foundation.NSDate
import platform.Foundation.NSNotificationCenter
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationWillEnterForegroundNotification

/** iOS 포그라운드 복귀 시 루트 트리 강제 재드로우 트리거. */
val foregroundRedrawTick = mutableStateOf(0)

private var backgroundedAtEpoch: Double = 0.0
private var lifecycleObserversRegistered = false

/** [MainViewController] 생성 시 1회 호출. 중복 등록 방지. */
fun registerIosForegroundRedraw() {
    if (lifecycleObserversRegistered) return
    lifecycleObserversRegistered = true

    val center = NSNotificationCenter.defaultCenter

    center.addObserverForName(
        name = UIApplicationDidEnterBackgroundNotification,
        `object` = null,
        queue = null,
    ) { _ ->
        backgroundedAtEpoch = NSDate().timeIntervalSince1970
        Log.message("[Lifecycle] enterBackground")
    }

    center.addObserverForName(
        name = UIApplicationWillEnterForegroundNotification,
        `object` = null,
        queue = null,
    ) { _ ->
        val bgSeconds =
            if (backgroundedAtEpoch > 0.0) NSDate().timeIntervalSince1970 - backgroundedAtEpoch else -1.0
        foregroundRedrawTick.value += 1
        Log.message("[Lifecycle] willEnterForeground bgSeconds=$bgSeconds redrawTick=${foregroundRedrawTick.value}")
    }

    center.addObserverForName(
        name = UIApplicationDidBecomeActiveNotification,
        `object` = null,
        queue = null,
    ) { _ ->
        foregroundRedrawTick.value += 1
        Log.message("[Lifecycle] didBecomeActive redrawTick=${foregroundRedrawTick.value}")
    }
}
