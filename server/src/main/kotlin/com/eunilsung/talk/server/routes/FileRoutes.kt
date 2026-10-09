package com.eunilsung.talk.server.routes

import com.eunilsung.talk.server.files.FileStorage
import com.eunilsung.talk.server.repository.AuthTokenRepository
import com.eunilsung.talk.server.repository.FileRepository
import com.eunilsung.talk.shared.api.ApiError
import com.eunilsung.talk.shared.api.ApiErrorCode
import com.eunilsung.talk.shared.api.ChatErrorCode
import com.eunilsung.talk.shared.api.MAX_FILE_BYTES
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.contentLength
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

/**
 * 파일 올리기·받기.
 *
 * 올린 파일은 그 방의 참여자만 받을 수 있다. 주소를 안다고 받을 수 있는 것이 아니라, 요청마다
 * 토큰으로 누구인지 확인하고 참여 여부를 본다.
 */
fun Route.fileRoutes(files: FileRepository, storage: FileStorage, tokens: AuthTokenRepository) {

    /** 본문이 파일 바이트 그대로다. 파일 이름은 쿼리 `name` 으로 받는다. */
    post("/rooms/{roomId}/files") {
        val userId = call.callerUserId(tokens) ?: return@post
        val name = call.request.queryParameters["name"].orEmpty().trim().take(MAX_NAME_LENGTH)
        if (name.isBlank()) {
            return@post call.respond(HttpStatusCode.BadRequest, ApiError(ApiErrorCode.BAD_REQUEST, "파일 이름이 필요합니다"))
        }
        /** 길이를 먼저 본다. 다 받고 나서 거절하면 큰 파일을 메모리에 올린 뒤다. */
        if ((call.request.contentLength() ?: 0) > MAX_FILE_BYTES) return@post call.respondTooLarge()
        val bytes = call.receive<ByteArray>()
        if (bytes.isEmpty()) {
            return@post call.respond(HttpStatusCode.BadRequest, ApiError(ApiErrorCode.BAD_REQUEST, "빈 파일입니다"))
        }
        if (bytes.size > MAX_FILE_BYTES) return@post call.respondTooLarge()

        val file = db { files.register(call.parameters["roomId"].orEmpty(), userId, name, bytes.size.toLong()) }
            ?: return@post call.respond(
                HttpStatusCode.NotFound,
                ApiError(ChatErrorCode.ROOM_NOT_FOUND, "대화방을 찾을 수 없습니다"),
            )
        db { storage.save(file.id, bytes) }
        call.respond(file)
    }

    get("/files/{fileId}") {
        val userId = call.callerUserId(tokens) ?: return@get
        val fileId = call.parameters["fileId"].orEmpty()
        val bytes = db { files.find(fileId, userId)?.let { file -> storage.read(file.id)?.let { file to it } } }
            ?: return@get call.respond(
                HttpStatusCode.NotFound,
                ApiError(ChatErrorCode.FILE_NOT_FOUND, "파일을 찾을 수 없습니다"),
            )
        call.response.header(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, bytes.first.name).toString(),
        )
        call.respondBytes(bytes.second, ContentType.Application.OctetStream)
    }
}

private suspend fun ApplicationCall.respondTooLarge() =
    respond(HttpStatusCode.PayloadTooLarge, ApiError(ChatErrorCode.FILE_TOO_LARGE, "파일이 너무 큽니다"))

private const val MAX_NAME_LENGTH = 255
