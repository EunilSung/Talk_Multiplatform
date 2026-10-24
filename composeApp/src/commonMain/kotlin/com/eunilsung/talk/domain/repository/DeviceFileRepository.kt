package com.eunilsung.talk.domain.repository

/** 대화로 받은 파일을 기기의 다운로드 폴더·사진첩에 저장하고 다른 앱으로 연다. */
interface DeviceFileRepository {

    /** [source] 를 다운로드 폴더에 [displayName] 으로 저장한다. 저장한 경로, 읽거나 쓰지 못하면 null. */
    suspend fun saveToDownloads(source: String, displayName: String): String?

    /**
     * [source] 를 앱 안에서만 보이는 캐시에 [displayName] 으로 복사한다. 열기 전용 사본이라 공용 폴더(다운로드·파일 앱)에는 남지 않는다.
     * 복사한 경로, 읽거나 쓰지 못하면 null.
     */
    suspend fun copyForOpen(source: String, displayName: String): String?

    /** 기기의 다른 앱으로 연다. 열었으면 true. */
    suspend fun open(path: String): Boolean

    /** [source] 를 사진첩에 [saveAs] 로 저장한다. 저장했으면 true. */
    suspend fun saveToGallery(source: String, saveAs: String, isVideo: Boolean): Boolean
}
