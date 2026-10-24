package com.eunilsung.talk.server.routes

import com.eunilsung.talk.server.files.FileNames
import com.eunilsung.talk.server.files.FileStorage
import com.eunilsung.talk.server.limits.ServerLimits
import com.eunilsung.talk.server.limits.UserConcurrencyLimiter
import com.eunilsung.talk.server.repository.AuthTokenRepository
import com.eunilsung.talk.server.repository.FileRegistration
import com.eunilsung.talk.server.repository.FileRepository
import com.eunilsung.talk.shared.api.ApiError
import com.eunilsung.talk.shared.api.ApiErrorCode
import com.eunilsung.talk.shared.api.ChatErrorCode
import com.eunilsung.talk.shared.api.MAX_FILE_BYTES
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.http.content.LocalFileContent
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.contentLength
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * 파일 올리기·받기.
 *
 * 올린 파일은 그 방의 참여자만 받을 수 있다. 주소를 안다고 받을 수 있는 것이 아니라, 요청마다
 * 토큰으로 누구인지 확인하고 참여 여부를 본다.
 *
 * 한 사람이 하루에 올릴 수 있는 개수와 용량에 한도가 있다([ServerLimits.fileUploadsPerDay]·[ServerLimits.fileBytesPerDay]).
 * 동시에 올리는 수([ServerLimits.concurrentUploadsPerUser])와 한 건을 받는 시간([ServerLimits.uploadTimeout])에도 한도가 있다.
 * 바이트는 메모리에 통째로 올리지 않고 받는 대로 디스크에 쓰고, 내려줄 때도 디스크에서 흘려보낸다.
 */
fun Route.fileRoutes(
    files: FileRepository,
    storage: FileStorage,
    tokens: AuthTokenRepository,
    limits: ServerLimits = ServerLimits(),
) {
    val uploads = UserConcurrencyLimiter(limits.concurrentUploadsPerUser)

    /** 본문이 파일 바이트 그대로다. 파일 이름은 쿼리 `name` 으로 받고, 경로 조각·제어 문자를 정리해 저장한다. */
    post("/rooms/{roomId}/files") {
        val userId = call.callerUserId(tokens) ?: return@post
        val name = FileNames.clean(call.request.queryParameters["name"].orEmpty())
        if (name.isBlank()) {
            return@post call.respond(HttpStatusCode.BadRequest, ApiError(ApiErrorCode.BAD_REQUEST, "파일 이름이 필요합니다"))
        }
        /** 길이와 남은 한도를 먼저 본다. 다 받고 나서 거절하면 큰 파일을 받아 둔 뒤다. */
        val declared = call.request.contentLength()
        if ((declared ?: 0) > MAX_FILE_BYTES) return@post call.respondTooLarge()
        if (!db { files.hasQuotaFor(userId, declared ?: 0) }) return@post call.respondFileQuotaExceeded()
        if (!uploads.tryAcquire(userId)) return@post call.respondTooManyRequests()
        try {
            val incoming = try {
                withTimeout(limits.uploadTimeout) { storage.receive(call.receiveChannel(), MAX_FILE_BYTES) }
            } catch (_: TimeoutCancellationException) {
                return@post call.respond(
                    HttpStatusCode.RequestTimeout,
                    ApiError(ApiErrorCode.BAD_REQUEST, "파일을 받는 데 너무 오래 걸렸습니다"),
                )
            } ?: return@post call.respondTooLarge()
            /** 밝힌 길이보다 적게 왔으면 연결이 중간에 끊긴 것이다. 잘린 파일을 다 받은 것처럼 남기지 않는다. */
            if (incoming.size == 0L || (declared != null && incoming.size != declared)) {
                db { storage.discard(incoming) }
                val reason = if (incoming.size == 0L) "빈 파일입니다" else "파일이 끝까지 오지 않았습니다"
                return@post call.respond(HttpStatusCode.BadRequest, ApiError(ApiErrorCode.BAD_REQUEST, reason))
            }

            when (val registration = db { files.register(call.parameters["roomId"].orEmpty(), userId, name, incoming.size) }) {
                is FileRegistration.Registered -> {
                    db { storage.keep(incoming, registration.file.id) }
                    call.respond(registration.file)
                }
                FileRegistration.QuotaExceeded -> {
                    db { storage.discard(incoming) }
                    call.respondFileQuotaExceeded()
                }
                FileRegistration.RoomNotFound -> {
                    db { storage.discard(incoming) }
                    call.respond(HttpStatusCode.NotFound, ApiError(ChatErrorCode.ROOM_NOT_FOUND, "대화방을 찾을 수 없습니다"))
                }
            }
        } finally {
            uploads.release(userId)
        }
    }

    get("/files/{fileId}") {
        val userId = call.callerUserId(tokens) ?: return@get
        val fileId = call.parameters["fileId"].orEmpty()
        val found = db { files.find(fileId, userId)?.let { file -> storage.stored(file.id)?.let { file to it } } }
            ?: return@get call.respond(
                HttpStatusCode.NotFound,
                ApiError(ChatErrorCode.FILE_NOT_FOUND, "파일을 찾을 수 없습니다"),
            )
        call.response.header(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, found.first.name).toString(),
        )
        call.respond(LocalFileContent(found.second, ContentType.Application.OctetStream))
    }
}

private suspend fun ApplicationCall.respondTooLarge() =
    respond(HttpStatusCode.PayloadTooLarge, ApiError(ChatErrorCode.FILE_TOO_LARGE, "파일이 너무 큽니다"))
