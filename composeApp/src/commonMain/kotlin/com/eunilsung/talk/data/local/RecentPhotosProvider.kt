package com.eunilsung.talk.data.local

/** 사용자 갤러리의 최근 사진을 조회하는 플랫폼별 소스. 권한 결과는 [RecentPhotosResult] 로 구분. */
interface RecentPhotosProvider {
    /**
     * @param onPartial 로딩 중 부분 결과(누적 리스트) 콜백 — 그리드 점진 표시용.
     * @return 권한 상태별 [RecentPhotosResult].
     */
    suspend fun fetchRecent(
        limit: Int,
        onPartial: (RecentPhotosResult) -> Unit = {},
    ): RecentPhotosResult

    /**
     * 특정 앨범(폴더)의 사진/동영상 조회 — 갤러리 시트의 앨범 필터에 사용.
     *
     * @param albumId [fetchAlbums] 가 준 앨범 식별자. null 이면 전체(= [fetchRecent] 와 동일).
     */
    suspend fun fetchByAlbum(
        albumId: String?,
        limit: Int,
        onPartial: (RecentPhotosResult) -> Unit = {},
    ): RecentPhotosResult = fetchRecent(limit, onPartial)

    /**
     * 사진이 들어 있는 앨범(폴더) 목록 — 최신 항목이 많은 순.
     *
     *  - Android : `MediaStore` 의 BUCKET(= 실제 폴더) 기준. 한 파일은 폴더 하나에만 속한다.
     *  - iOS     : `PHAssetCollection`(스마트 앨범 + 사용자 앨범). 한 asset 이 여러 앨범에
     *              속할 수 있어 개수 합이 전체와 다를 수 있다.
     *
     * 권한이 없으면 빈 리스트.
     */
    suspend fun fetchAlbums(): List<PhotoAlbum> = emptyList()

    /** 동기 권한 체크 — 즉시 결정이 필요한 UI 분기용. */
    fun hasPhotoAccess(): Boolean

    /** 권한 거부 후 앱 설정 화면으로 이동. */
    fun openAppSettings()

    /** 시스템 권한 prompt 시도 — 영구 거부 시 앱 설정 화면으로 fallback. */
    fun requestPhotoAccess()
}

/** [RecentPhotosProvider.fetchRecent] 결과. */
sealed interface RecentPhotosResult {
    /** 권한 부여 (또는 권한 불필요) — [photos] 가 비어있으면 단순히 갤러리에 사진이 없는 경우. */
    data class Granted(val photos: List<RecentPhoto>) : RecentPhotosResult

    /** iOS Limited 권한 — 사용자가 선택한 일부 사진만 접근 가능. strip 은 표시. */
    data class Limited(val photos: List<RecentPhoto>) : RecentPhotosResult

    /** 사용자가 권한 거부 — UI 가 "설정 열기" 행을 노출. */
    data object Denied : RecentPhotosResult
}

/**
 * 갤러리 앨범(폴더) 한 개.
 *
 * @param id 플랫폼별 식별자 — Android BUCKET_ID / iOS localIdentifier. null 은 "전체" 를 뜻하므로
 *   실제 앨범 id 로는 쓰지 않는다.
 * @param count 앨범에 든 사진·동영상 수.
 * @param coverUri 목록에 보여줄 대표 이미지. iOS 는 uri 로 직접 로드할 수 없어 비어 있다.
 * @param coverBytes 미리 디코드한 대표 이미지(JPEG). iOS 전용 — [coverUri] 보다 우선 사용한다.
 */
data class PhotoAlbum(
    val id: String,
    val name: String,
    val count: Int,
    val coverUri: String = "",
    val coverBytes: ByteArray? = null,
)

/** 최근 사진 한 장의 raw 정보 — UI 측 [MultimediaRecentPhoto] 로 매핑됨. */
data class RecentPhoto(
    val id: String,
    val uri: String,
    val thumbnailBytes: ByteArray? = null,
    /** 동영상 여부 — 셀에 재생 아이콘/길이 배지를 표시할지 결정. */
    val isVideo: Boolean = false,
    /** 동영상 길이(초). 이미지면 0. */
    val durationSec: Long = 0,
)
