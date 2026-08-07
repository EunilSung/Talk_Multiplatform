package com.eunilsung.talk.data.local

/** 파일 path / URI 의 metadata 를 플랫폼별 API 로 해석하는 abstraction. */
interface FileMetadataResolver {
    suspend fun resolve(path: String): FileMetadata

    /** path 의 원본 파일 바이트를 읽어 반환. 실패 시 null. */
    suspend fun readBytes(path: String): ByteArray?

    /**
     * 이미지 로더·플레이어가 직접 열 수 있는 경로로 바꾼다.
     *
     * iOS 갤러리 항목은 실제 파일 경로가 아니라 PHAsset 식별자라, 그대로 저장하면
     * 말풍선에서 이미지가 뜨지 않고 동영상도 재생되지 않는다. 그래서 바이트를 읽어
     * 캐시 파일로 복사한 뒤 그 경로를 돌려준다.
     *
     * 이미 열 수 있는 경로(Android `content://`, 일반 파일 경로)면 그대로 반환한다.
     *
     * @param filename 복사본에 쓸 파일명 — 호출부가 충돌하지 않게 만들어 넘긴다.
     */
    suspend fun materializeForDisplay(path: String, filename: String): String = path

    /** 바이트를 앱 cache 디렉터리에 [filename] 으로 저장하고 절대 path 반환. 실패 시 null. */
    suspend fun writeCacheFile(filename: String, bytes: ByteArray): String?

    /** 다운로드 파일을 사용자에게 노출되는 영역에 저장. 성공 시 path/URI, 실패 시 null. */
    suspend fun saveDownloadedFile(filename: String, bytes: ByteArray): String?

    /** 이미지 바이트를 시스템 갤러리(사진 앱)에 저장. 성공 시 path/URI/asset id, 실패 시 null. */
    suspend fun saveToGallery(filename: String, bytes: ByteArray): String?

    /** 동영상을 갤러리에 저장. 성공 시 경로/URI, 실패 시 null. */
    suspend fun saveVideoToGallery(filename: String, bytes: ByteArray): String?

    /** 이전에 [saveDownloadedFile] 로 받아둔 동일 파일 존재 여부 검사. 존재 시 path, 없으면 null. */
    suspend fun findDownloadedFile(filename: String): String?
}

/**
 * @property originalName  원본 파일명(확장자 포함). 못 알면 "".
 * @property extension     점 포함 확장자. 못 알면 "".
 * @property widthHeight   이미지일 때 `"width:height"`. 비이미지/못 알면 "".
 * @property sizeBytes     byte 크기, 못 알면 -1.
 */
data class FileMetadata(
    val originalName: String,
    val extension: String,
    val widthHeight: String,
    val sizeBytes: Long = -1L,
)
