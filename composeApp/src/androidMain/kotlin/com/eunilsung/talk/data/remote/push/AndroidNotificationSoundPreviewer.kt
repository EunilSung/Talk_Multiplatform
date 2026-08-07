package com.eunilsung.talk.data.remote.push

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.eunilsung.talk.util.Log

/** Android [NotificationSoundPreviewer] — `MediaPlayer` 로 `res/raw/{baseName}` 재생. */
class AndroidNotificationSoundPreviewer(
    private val context: Context,
) : NotificationSoundPreviewer {

    private var player: MediaPlayer? = null

    override fun play(soundId: Int) {
        stop()
        val safeId = soundId.coerceIn(0, NotificationSoundCatalog.SOUNDS.lastIndex)
        val baseName = NotificationSoundCatalog.SOUNDS[safeId].baseName
        val resId = context.resources.getIdentifier(baseName, "raw", context.packageName)
        if (resId == 0) {
            Log.message("[SoundPreview/Android] resource not found: res/raw/$baseName.wav")
            return
        }
        runCatching {
            val mp = MediaPlayer.create(context, resId) ?: run {
                Log.message("[SoundPreview/Android] MediaPlayer.create returned null for $baseName")
                return
            }
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            mp.setOnCompletionListener {
                runCatching { it.release() }
                if (player === mp) player = null
            }
            mp.start()
            player = mp
        }.onFailure {
            Log.message("[SoundPreview/Android] play failed for $baseName: ${it.message}")
        }
    }

    override fun stop() {
        val p = player ?: return
        player = null
        runCatching {
            if (p.isPlaying) p.stop()
        }
        runCatching { p.release() }
    }
}
