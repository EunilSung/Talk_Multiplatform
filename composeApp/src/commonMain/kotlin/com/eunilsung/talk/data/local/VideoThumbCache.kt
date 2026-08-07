package com.eunilsung.talk.data.local

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 동영상 첫 프레임 캐시 — 경로별로 한 번만 디코드한다.
 *
 * 말풍선은 스크롤로 컴포지션을 드나들 때마다 다시 읽는데, 매번 디코더를 새로 만들면
 * 되돌아올 때 프레임·길이가 잠깐 초기값으로 깜빡인다.
 */
class VideoThumbCache(
    private val delegate: VideoThumbnailLoader,
    private val maxEntries: Int = 32,
) : VideoThumbnailLoader {

    private val mutex = Mutex()
    private val entries = LinkedHashMap<String, VideoThumb>()

    override suspend fun load(path: String): VideoThumb {
        if (path.isBlank()) return VideoThumb()
        mutex.withLock { entries[path] }?.let { return it }

        val loaded = delegate.load(path)
        mutex.withLock {
            entries[path] = loaded
            // 가장 오래 전에 넣은 것부터 버린다.
            while (entries.size > maxEntries) {
                entries.remove(entries.keys.first())
            }
        }
        return loaded
    }
}
