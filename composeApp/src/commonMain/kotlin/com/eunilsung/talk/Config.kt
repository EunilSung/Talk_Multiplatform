package com.eunilsung.talk


object Config {
    /** 디버그 로그 on/off — 기본 꺼짐. */
    object Log{
        const val IS_SHOW_LOG: Boolean = false
    }

    object UserProfile{
        const val IS_KEEP_CACHE_DAY: Boolean = false
    }

    /** 로컬 데이터베이스 (SQLDelight) 설정. [NAME] 을 바꾸면 새 DB 로 시작. */
    object Database {
        const val NAME: String = "AppDatabase_v12.db"
    }

    object MyInfo {
        var userId: String = ""
        var password: String = ""
        var userName: String = ""
        var email: String = ""
        var maxGroupListNum: String = ""
        var maxGroupMemberNum: String = ""
        var birthday: String = ""
        var statusMessage: String = ""
        var organName: String = ""
        var positionCode: String = ""
        var positionName: String = ""
        var nickname: String = ""
        var userType: String = ""
        var companyCode: String = ""
        var etcStatus: String = ""
        var pcStatus: String = ""
        var phoneNumber: String = ""
        var mobileStatus: String = ""
    }

    object GroupType {
        const val BASE_GRP_ID = "0"
        const val BASE_MYGRP_ID = "-1"
        const val BASE_GROUP_MYPROFILE = "-2"
    }

    /** 대화방(ChatRoom) 화면의 기능 옵션 플래그. */
    object ChatRoom {
        /** 대화 검색 기능 전체 on/off — TopBar 검색 아이콘 노출 / 검색 모드 전환 */
        const val IS_SEARCH_ENABLED: Boolean = true

        /** 검색 모드 내 "사용자 필터" 기능 on/off — ChatSearchNav 의 사용자 아이콘 노출 */
        const val IS_SEARCH_USER_ENABLED: Boolean = true

        /** 검색 모드 내 "날짜 필터" 기능 on/off — ChatSearchNav 의 날짜 아이콘 노출 */
        const val IS_SEARCH_DATE_ENABLED: Boolean = true

        /** 책갈피 — 상단바 책갈피 목록 버튼 + 롱클릭 메뉴 "책갈피" 추가·해제 */
        const val IS_BOOK_MARK_ENABLED: Boolean = true

        /** 답장 — 롱클릭 메뉴 "답장" + 스와이프 답장 동작 */
        const val IS_REPLY_ENABLED: Boolean = true

        /** 회수 — 롱클릭 메뉴 "회수"(내가 보낸 대화) 동작 */
        const val IS_RECALL_ENABLED: Boolean = true

        /** 복사 — 롱클릭 메뉴 "복사"(텍스트·답장 대화) */
        const val IS_COPY_ENABLED: Boolean = true

        /** 투표 — 대화방 서랍의 "투표" 메뉴(목록·만들기) (대화 속 투표 말풍선 표시·참여는 유지) */
        const val IS_VOTE_ENABLED: Boolean = true

        /** 이모티콘 — 입력바 이모티콘 버튼/패널 */
        const val IS_EMOTICON_ENABLED: Boolean = true

        /** 멀티미디어(사진/파일) — 입력바 멀티미디어(+) 버튼 */
        const val IS_MULTIMEDIA_ENABLED: Boolean = true

        /** 멘션 — 입력 중 "@" 사용자 멘션 박스 */
        const val IS_MENTION_ENABLED: Boolean = true

        /** 공지 — 롱클릭 메뉴 "공지" 등록 동작 (공지 바 표시는 유지) */
        const val IS_NOTICE_ENABLED: Boolean = true

        /** 공감 — 롱클릭 다이얼로그의 공감 아이콘 전송 (대화의 공감 표시는 유지) */
        const val IS_EMPATHY_ENABLED: Boolean = true

        /** 대화 그룹 — 대화함의 그룹 칩 / 그룹 편집 버튼, 롱클릭 메뉴의 그룹 추가·해제 */
        const val IS_CHAT_GROUP_ENABLED: Boolean = true
    }

    /** 이모티콘 옵션. */
    object Emoticon {
        /**
         * 대화 말풍선의 움직이는 이모티콘을 몇 번 재생하고 멈출지 — 멈추면 마지막 장면에 서고, 말풍선을 누르면 다시 이 횟수만큼
         * 재생한다. 0 이면 멈추지 않는다. 이모티콘 미리보기는 이 값과 상관없이 계속 움직인다(GIF 파일에는 모두 "무한 반복" 으로 적혀 있다).
         */
        const val BUBBLE_ANIMATION_PLAY_COUNT: Int = 3
    }

    /** 앱 전환 화면(최근 앱·앱 전환기)에 대화 내용이 그림으로 남지 않게 하는 옵션. */
    object AppSwitcherSnapshot {
        /**
         * 스냅샷 가리기 on/off.
         *  - Android 13 이상: `setRecentsScreenshotEnabled(false)`. 부작용 없음. 12 이하는 [IS_SECURE_WHILE_PAUSED_ENABLED].
         *  - iOS: 앱이 비활성이 되는 순간 창 맨 위에 앱 첫 화면과 같은 가림막을 올리고 다시 활성이 되면 내린다.
         */
        const val IS_ENABLED: Boolean = true

        /**
         * Android 에서 **앱이 가려져 일시정지된 동안만** 캡처를 막는다(onPause 에 `FLAG_SECURE`, onResume 에 해제).
         *
         * - 12 이하: 스냅샷만 끄는 공개 API 가 없어 쓰는 차선책이다. 부분 보호다 — 제스처 내비게이션에서는 썸네일이
         *   onPause 보다 먼저 찍힐 수 있다.
         * - 13 이상: 권한 요청·사진 선택기·앱 선택 창 같은 반투명 시스템 화면이 앱 위에 떠 있으면 OS 가 실제 화면을
         *   찍는다. 그 경우를 막는다.
         *
         * 대신 그런 창이 위에 떠 있는 동안에는 사용자 캡처가 검게 나온다. 앱이 맨 위일 때의 캡처는 그대로다.
         */
        const val IS_SECURE_WHILE_PAUSED_ENABLED: Boolean = true
    }

    object Watermark {
        /** 화면 전역 워터마크(내 아이디 + 내 이름) 표시 on/off */
        const val IS_ENABLED: Boolean = true
    }

}
