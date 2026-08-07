package com.eunilsung.talk.data.remote.push

import kotlinx.cinterop.ExperimentalForeignApi
import com.eunilsung.talk.util.Log
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.Foundation.NSBundle

/** iOS [NotificationSoundPreviewer] — `AVAudioPlayer` 로 앱 bundle 의 wav 파일 재생. */
@OptIn(ExperimentalForeignApi::class)
class IosNotificationSoundPreviewer : NotificationSoundPreviewer {

    private var player: AVAudioPlayer? = null

    override fun play(soundId: Int) {
        stop()
        val safeId = soundId.coerceIn(0, NotificationSoundCatalog.SOUNDS.lastIndex)
        val baseName = NotificationSoundCatalog.SOUNDS[safeId].baseName

        val url = NSBundle.mainBundle.URLForResource(name = baseName, withExtension = "wav") ?: run {
            Log.message("[SoundPreview/iOS] resource not found: $baseName.wav (bundle 에 wav 미추가?)")
            return
        }

        runCatching {
            runCatching {
                AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryPlayback, error = null)
            }
            val p = AVAudioPlayer(contentsOfURL = url, error = null)
            p.prepareToPlay()
            p.play()
            player = p
        }.onFailure {
            Log.message("[SoundPreview/iOS] play failed for $baseName: ${it.message}")
        }
    }

    override fun stop() {
        val p = player ?: return
        player = null
        runCatching { if (p.isPlaying()) p.stop() }
    }
}
