package com.localdev.webdavserver.data.server

import android.content.Context
import android.net.Uri
import com.localdev.webdavserver.data.local.model.Role
import com.localdev.webdavserver.domain.repository.LogRepository
import com.localdev.webdavserver.domain.repository.SettingsRepository
import com.localdev.webdavserver.domain.repository.UserRepository
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respondText
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * Observable server runtime state.
 */
data class ServerRuntimeState(
    val isRunning: Boolean = false,
    val port: Int = 8080,
    val ipAddress: String? = null,
    val startedAtMillis: Long = 0L,
    val bytesIn: Long = 0L,
    val bytesOut: Long = 0L,
    val lastError: String? = null
) {
    val addressLabel: String
        get() = if (ipAddress != null) "$ipAddress:$port" else "Not connected"
}

/**
 * Embedded WebDAV server using Ktor CIO. Manages lifecycle and dispatches every
 * supported method to the appropriate handler. Validates HTTP Basic Auth and the
 * per-user read-only flag on every request.
 */
class WebDavEngine(
    private val context: Context,
    private val userRepository: UserRepository,
    private val settingsRepository: SettingsRepository,
    private val logRepository: LogRepository
) {
    private val saf = SafResolver(context)
    private val lockManager = LockManager()
    private val router = RequestRouter(userRepository, logRepository)

    private val optionsHandler = OptionsHandler()
    private val faviconHandler = FaviconHandler(context)
    private val getHandler = GetHandler(saf) { n -> addBytesOut(n) }
    private val browserAccess = BrowserAccess(
        saf = saf,
        userRepository = userRepository,
        resolveEffectiveRoot = { user ->
            when (user.role) {
                Role.ADMIN -> rootUri
                Role.USER -> resolveUserHome(user.username, user.rootFolderUri)
            }
        },
        onBytesOut = { n -> addBytesOut(n) }
    )
    private val putHandler = PutHandler(saf) { n -> addBytesIn(n) }
    private val deleteHandler = DeleteHandler(saf)
    private val mkcolHandler = MkcolHandler(saf)
    private val propfindHandler = PropfindHandler(saf)
    private val proppatchHandler = ProppatchHandler(saf)
    private val copyHandler = CopyHandler(saf)
    private val moveHandler = MoveHandler(saf)
    private val lockHandler = LockHandler(lockManager)

    private var server: ApplicationEngine? = null
    private val bytesIn = AtomicLong(0)
    private val bytesOut = AtomicLong(0)

    private val _state = MutableStateFlow(ServerRuntimeState())
    val state: StateFlow<ServerRuntimeState> = _state.asStateFlow()

    @Volatile private var rootUri: String? = null
    @Volatile private var showHidden: Boolean = false

    /** Methods that mutate the filesystem (require non-read-only users). */
    private val writeMethods = setOf("PUT", "DELETE", "MKCOL", "MOVE", "COPY", "PROPPATCH")

    fun start(port: Int, rootFolderUri: String?, showHiddenFiles: Boolean): Result<Unit> {
        if (server != null) return Result.success(Unit)
        rootUri = rootFolderUri
        showHidden = showHiddenFiles
        return runCatching {
            val engine = embeddedServer(CIO, port = port, host = "0.0.0.0") {
                configureRouting()
            }
            engine.start(wait = false)
            server = engine
            _state.value = ServerRuntimeState(
                isRunning = true,
                port = port,
                ipAddress = NetworkUtils.getLocalIpAddress(context),
                startedAtMillis = System.currentTimeMillis()
            )
        }.onFailure { t ->
            _state.value = _state.value.copy(isRunning = false, lastError = t.message)
        }
    }

    fun stop() {
        runCatching { server?.stop(500, 1000) }
        server = null
        browserAccess.clear()
        bytesIn.set(0)
        bytesOut.set(0)
        _state.value = ServerRuntimeState(isRunning = false, port = _state.value.port)
    }

    fun refreshIp() {
        if (_state.value.isRunning) {
            _state.value = _state.value.copy(ipAddress = NetworkUtils.getLocalIpAddress(context))
        }
    }

    /** Adds to the "downloaded from device" (server -> client) counter, live. */
    fun addBytesOut(n: Long) {
        if (n <= 0) return
        val total = bytesOut.addAndGet(n)
        _state.value = _state.value.copy(bytesOut = total)
    }

    /** Adds to the "uploaded to device" (client -> server) counter, live. */
    fun addBytesIn(n: Long) {
        if (n <= 0) return
        val total = bytesIn.addAndGet(n)
        _state.value = _state.value.copy(bytesIn = total)
    }

    private fun io.ktor.server.application.Application.configureRouting() {
        routing {
            // A single catch-all route that accepts every supported WebDAV method
            // for any path; dispatch() inspects the method and routes internally.
            supportedMethods().forEach { m ->
                route("{...}", m) {
                    handle {
                        dispatch(call)
                    }
                }
            }
        }
    }

    private fun supportedMethods(): List<HttpMethod> = listOf(
        HttpMethod("OPTIONS"), HttpMethod.Get, HttpMethod.Head, HttpMethod.Post,
        HttpMethod.Put, HttpMethod.Delete, HttpMethod("MKCOL"), HttpMethod("PROPFIND"),
        HttpMethod("PROPPATCH"), HttpMethod("COPY"), HttpMethod("MOVE"),
        HttpMethod("LOCK"), HttpMethod("UNLOCK")
    )

    private suspend fun dispatch(call: ApplicationCall) {
        val methodName = call.request.httpMethod.value.uppercase()
        val path = Uri.decode(call.request.path())

        // OPTIONS is allowed without auth so clients can probe capabilities.
        if (methodName == "OPTIONS") {
            optionsHandler.handle(call)
            router.logResult(call, "-", HttpStatusCode.OK)
            return
        }

        // Public site icon for unauthenticated GET/HEAD (browsers auto-request /favicon.ico).
        // If the client already sent Authorization (typical WebDAV), fall through so an
        // on-disk favicon.ico in the storage root can still be fetched with Basic auth.
        if ((methodName == "GET" || methodName == "HEAD") && faviconHandler.isFaviconPath(path)) {
            val hasAuth = !call.request.headers["Authorization"].isNullOrBlank()
            if (!hasAuth) {
                val (status, bytes) = faviconHandler.handle(call, methodName, path)
                addBytesOut(bytes)
                router.logResult(call, "public", status)
                return
            }
        }

        // Browser portal: `/` = use client; file URLs = form login + one-time download.
        // Handled before Basic auth so browsers never cache HTTP credentials.
        val browserResult = browserAccess.tryHandle(call, methodName, path)
        if (browserResult.handled) {
            router.logResult(call, browserResult.username, browserResult.status)
            return
        }

        // POST is only for the browser login form above.
        if (methodName == "POST") {
            call.respondText("405 Method Not Allowed", status = HttpStatusCode.MethodNotAllowed)
            router.logResult(call, "-", HttpStatusCode.MethodNotAllowed)
            return
        }

        when (val auth = router.authenticate(call)) {
            is AuthResult.Unauthorized -> {
                router.challenge(call)
                return
            }
            is AuthResult.Authorized -> {
                val user = auth.user
                // Read-only users cannot mutate (admins never have readOnly set).
                if (methodName in writeMethods && user.readOnly) {
                    call.respondText("403 Forbidden — read-only user",
                        status = HttpStatusCode.Forbidden)
                    router.logResult(call, user.username, HttpStatusCode.Forbidden)
                    return
                }

                // Admins always see the global root. Regular users are confined
                // to their personal folder under the root (never the global root).
                val effectiveRoot = when (user.role) {
                    Role.ADMIN -> rootUri
                    Role.USER -> resolveUserHome(user.username, user.rootFolderUri)
                }
                if (effectiveRoot == null) {
                    call.respondText(
                        if (user.role == Role.USER) "403 Forbidden — user home not available"
                        else "500 Server root folder not configured",
                        status = if (user.role == Role.USER) HttpStatusCode.Forbidden
                        else HttpStatusCode.InternalServerError
                    )
                    router.logResult(
                        call,
                        user.username,
                        if (user.role == Role.USER) HttpStatusCode.Forbidden
                        else HttpStatusCode.InternalServerError
                    )
                    return
                }
                val root = saf.rootDocument(effectiveRoot)
                if (root == null || !root.exists()) {
                    call.respondText("500 Server root folder not configured",
                        status = HttpStatusCode.InternalServerError)
                    router.logResult(call, user.username, HttpStatusCode.InternalServerError)
                    return
                }

                // Lock conflict check for write methods.
                if (methodName in writeMethods) {
                    val ifToken = call.request.headers["If"]
                    if (lockManager.isLockedByOther(path, ifToken)) {
                        call.respondText("423 Locked", status = HttpStatusCode.Locked)
                        router.logResult(call, user.username, HttpStatusCode.Locked)
                        return
                    }
                }

                val status: HttpStatusCode = when (methodName) {
                    "GET", "HEAD" -> getHandler.handle(call, root, path)
                    "PUT" -> putHandler.handle(call, root, path)
                    "DELETE" -> deleteHandler.handle(call, root, path)
                    "MKCOL" -> mkcolHandler.handle(call, root, path)
                    "PROPFIND" -> propfindHandler.handle(call, root, path, showHidden)
                    "PROPPATCH" -> proppatchHandler.handle(call, root, path)
                    "COPY" -> copyHandler.handle(call, root, path)
                    "MOVE" -> moveHandler.handle(call, root, path)
                    "LOCK" -> { lockHandler.handleLock(call, path, user.username); HttpStatusCode.OK }
                    "UNLOCK" -> { lockHandler.handleUnlock(call); HttpStatusCode.NoContent }
                    else -> {
                        call.respondText("405 Method Not Allowed",
                            status = HttpStatusCode.MethodNotAllowed)
                        HttpStatusCode.MethodNotAllowed
                    }
                }
                // Notify the in-app Files "Live" browser — does not touch request logs.
                if (methodName in writeMethods && status.value in 200..299) {
                    FileChangeBus.notifyChanged()
                }
                router.logResult(call, user.username, status)
            }
        }
    }

    /**
     * Personal home for a regular user: prefer the stored path when it still
     * sits under the global root; otherwise create `{root}/{username}`.
     * Never falls back to the global root (that would leak all files).
     */
    private fun resolveUserHome(username: String, storedHome: String?): String? {
        if (storedHome != null && UserHomeFolders.isUnderGlobalRoot(rootUri, storedHome)) {
            val doc = saf.rootDocument(storedHome)
            if (doc != null && doc.exists()) return storedHome
        }
        return UserHomeFolders.ensureUserHome(rootUri, username)
    }
}
