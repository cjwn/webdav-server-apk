package com.localdev.webdavserver.data.server

import android.util.Base64
import android.util.Log
import android.net.Uri
import com.localdev.webdavserver.data.local.model.UserEntity
import com.localdev.webdavserver.domain.repository.LogRepository
import com.localdev.webdavserver.domain.repository.UserRepository
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.origin
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.header
import io.ktor.server.response.respondText

/**
 * Shared WebDAV constants.
 */
object WebDavConstants {
    val XML_UTF8 = ContentType.parse("application/xml; charset=utf-8")
    const val DAV_COMPLIANCE = "1, 2"
    const val ALLOWED_METHODS =
        "OPTIONS, GET, HEAD, PUT, DELETE, MKCOL, COPY, MOVE, PROPFIND, PROPPATCH, LOCK, UNLOCK"
    const val REALM = "WebDAV"
}

/**
 * Small XML utilities.
 */
object WebDavXml {
    fun escape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    fun hrefEncode(path: String): String =
        path.split("/").joinToString("/") { android.net.Uri.encode(it) }
}

/**
 * Result of authenticating an incoming request.
 */
sealed interface AuthResult {
    data class Authorized(val user: UserEntity) : AuthResult
    data object Unauthorized : AuthResult
}

/**
 * Parses HTTP Basic Auth and validates against the user list.
 */
class RequestRouter(
    private val userRepository: UserRepository,
    private val logRepository: LogRepository
) {

    fun authenticate(call: ApplicationCall): AuthResult {
        val header = call.request.headers["Authorization"] ?: return AuthResult.Unauthorized
        if (!header.startsWith("Basic ", ignoreCase = true)) return AuthResult.Unauthorized
        val decoded = runCatching {
            String(Base64.decode(header.substring(6).trim(), Base64.DEFAULT), Charsets.UTF_8)
        }.getOrNull() ?: return AuthResult.Unauthorized
        val sep = decoded.indexOf(':')
        if (sep < 0) return AuthResult.Unauthorized
        val username = decoded.substring(0, sep)
        val password = decoded.substring(sep + 1)
        val user = userRepository.authenticate(username, password) ?: return AuthResult.Unauthorized
        return AuthResult.Authorized(user)
    }

    suspend fun challenge(call: ApplicationCall) {
        call.response.header("WWW-Authenticate", "Basic realm=\"${WebDavConstants.REALM}\"")
        call.respondText(
            "401 Unauthorized",
            status = HttpStatusCode.Unauthorized
        )
        logRepository.log(
            method = call.request.httpMethod.value,
            path = call.request.path(),
            username = "-",
            statusCode = 401,
            statusMessage = "Unauthorized",
            ip = remoteIp(call)
        )
        Log.i(TAG, "${call.request.httpMethod.value} ${call.request.path()} -> 401 Unauthorized")
    }

    fun logResult(call: ApplicationCall, user: String, status: HttpStatusCode) {
        val method = call.request.httpMethod.value
        val path = call.request.path()
        if (status == HttpStatusCode.NotFound &&
            (method == "PROPFIND" || method == "GET" || method == "HEAD")
        ) {
            val name = Uri.decode(path.trimEnd('/').substringAfterLast('/'))
            if (name.startsWith("._") || name == ".DS_Store") return
        }

        logRepository.log(
            method = method,
            path = path,
            username = user,
            statusCode = status.value,
            statusMessage = status.description,
            ip = remoteIp(call)
        )
        Log.i(TAG, "$method $path user=$user -> ${status.value} ${status.description}")
    }

    private companion object {
        const val TAG = "WebDavRequest"
    }

    private fun remoteIp(call: ApplicationCall): String =
        runCatching { call.request.origin.remoteHost }.getOrNull()?.takeIf { it.isNotBlank() } ?: "-"
}
