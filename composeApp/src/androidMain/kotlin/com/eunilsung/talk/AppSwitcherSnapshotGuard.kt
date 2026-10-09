package com.eunilsung.talk

import android.app.Activity
import android.app.ActivityManager
import android.content.res.Configuration
import android.os.Build
import android.view.WindowManager
import androidx.compose.ui.graphics.toArgb
import com.eunilsung.talk.ui.theme.AppColors

/**
 * Android 최근 앱 화면에 대화 내용이 그림으로 남지 않게 한다([Config.AppSwitcherSnapshot]).
 *
 * 두 가지를 함께 쓴다.
 *  - **Android 13 이상 — 스냅샷 끄기**(`setRecentsScreenshotEnabled(false)`). 앱이 맨 위일 때 뒤로 가면 화면 대신
 *    앱 테마 배경이 저장된다. 미리 걸어 두는 값이라 스냅샷 시점과 겨루지 않는다. 그 배경색은 앱 실행 첫 화면과 같은
 *    색으로 맞춘다([applyRecentsBackground]) — OS 가 색만 칠하므로 로고는 넣을 수 없다(에뮬레이터에서 확인).
 *  - **일시정지 동안 보안 창**(onPause 에 `FLAG_SECURE`, onResume 에 해제).
 *    - 12 이하는 스냅샷만 끄는 공개 API 가 없어 이것이 유일한 방법이다(부분 보호).
 *    - 13 이상에서도 필요하다. 권한 요청·사진 선택기·앱 선택 창 같은 **반투명 시스템 화면이 우리 태스크 맨 위에**
 *      있으면 OS 가 스냅샷 끄기 설정을 보지 않고 실제 화면을 찍는다. 에뮬레이터(API 35)에서 사진 권한
 *      창을 띄운 채 홈으로 나가자 그 아래 대화방이 스냅샷 파일에 그대로 찍혔다. 그 창이 뜨는 순간 우리 화면은
 *      일시정지되므로 여기서 보안 창으로 바꿔 둔다.
 *
 * 생명주기 훅은 플랫폼 진입점([MainActivity])에만 둔다 — commonMain 의 `LifecycleEventEffect` 로 옮기면 Compose 가
 * 없는 순간을 놓친다.
 */
internal object AppSwitcherSnapshotGuard {

    /** 무엇을 켤지. 순수 판정이라 기기 없이 확인할 수 있다. */
    data class Plan(val disableRecentsScreenshot: Boolean, val secureWhilePaused: Boolean)

    fun planFor(sdkInt: Int, enabled: Boolean, secureWhilePausedEnabled: Boolean): Plan =
        if (!enabled) Plan(disableRecentsScreenshot = false, secureWhilePaused = false)
        else Plan(
            disableRecentsScreenshot = sdkInt >= Build.VERSION_CODES.TIRAMISU,
            secureWhilePaused = secureWhilePausedEnabled,
        )

    private val plan: Plan
        get() = planFor(
            sdkInt = Build.VERSION.SDK_INT,
            enabled = Config.AppSwitcherSnapshot.IS_ENABLED,
            secureWhilePausedEnabled = Config.AppSwitcherSnapshot.IS_SECURE_WHILE_PAUSED_ENABLED,
        )

    /** 프로세스가 되살아나면 새 액티비티라 onCreate 마다 건다. */
    fun onCreate(activity: Activity) {
        applyRecentsBackground(activity)
        if (!plan.disableRecentsScreenshot) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.setRecentsScreenshotEnabled(false)
        }
    }

    /** 다크 모드가 바뀌어도 액티비티는 다시 만들어지지 않는다(manifest 의 configChanges 에 uiMode 가 있다). */
    fun onConfigurationChanged(activity: Activity) = applyRecentsBackground(activity)

    /**
     * 최근 앱 카드에 그려질 **배경색을 앱 실행 첫 화면과 같게** 맞춘다.
     *
     * 스냅샷을 끄면 OS 가 대신 그리는데 **배경색과 시스템 바 색만 칠한다**(이미지는 그리지 않아 로고는 못 넣는다).
     * 기본값은 앱 테마의 회색빛이라 첫 화면(흰색, 다크 모드 #121417)과 달라 보인다. 색을 맞추면 로고만 없는 첫 화면처럼 보인다.
     *
     * **Android 13 미만에서는 하지 않는다.** 이 색은 스냅샷을 끌 때(13 이상)만 보이고, 색을 정하는
     * `TaskDescription.Builder` 도 13 에서 처음 공개됐다. 9 부터 부르면 9~12 기기에서 앱을 켜거나 다크 모드를
     * 바꾸는 순간 `NoClassDefFoundError` 로 죽을 수 있다.
     */
    private fun applyRecentsBackground(activity: Activity) {
        if (!Config.AppSwitcherSnapshot.IS_ENABLED) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val isDark = (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val background = (if (isDark) AppColors.Dark.Bg else AppColors.Light.Bg).toArgb()
        val description = ActivityManager.TaskDescription.Builder()
            .setBackgroundColor(background)
            .setStatusBarColor(background)
            .setNavigationBarColor(background)
            .build()
        runCatching { activity.setTaskDescription(description) }
    }

    fun onPause(activity: Activity) {
        if (!plan.secureWhilePaused) return
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    /** 다시 앞으로 오면 풀어 사용자 캡처를 되돌린다. */
    fun onResume(activity: Activity) {
        if (!plan.secureWhilePaused) return
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}
