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

    object Watermark {
        /** 화면 전역 워터마크(내 아이디 + 내 이름) 표시 on/off */
        const val IS_ENABLED: Boolean = true
    }

}
