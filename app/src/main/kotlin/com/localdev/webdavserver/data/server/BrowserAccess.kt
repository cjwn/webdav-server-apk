package com.localdev.webdavserver.data.server

import com.localdev.webdavserver.data.local.model.UserEntity
import com.localdev.webdavserver.domain.repository.UserRepository
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.header
import io.ktor.server.response.respondOutputStream
import io.ktor.server.response.respondText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * Result of browser-portal handling (for request logging).
 */
data class BrowserHandleResult(
    val handled: Boolean,
    val username: String = "browser",
    val status: HttpStatusCode = HttpStatusCode.OK
) {
    companion object {
        val NotHandled = BrowserHandleResult(handled = false)
    }
}

/**
 * Browser-only download portal for **direct file URLs**.
 *
 * - `/` → “use a WebDAV client” page (no browsing)
 * - `GET /path/to/file` in a browser → login form (credentials not stored)
 * - `POST /__browser/login` → one-time download token or reject page
 * - `GET /__browser/download?t=…` → streams file once, then token dies
 *
 * WebDAV clients never use this path (they use Basic auth + normal methods).
 */
class BrowserAccess(
    private val saf: SafResolver,
    private val userRepository: UserRepository,
    private val resolveEffectiveRoot: (UserEntity) -> String?,
    private val onBytesOut: (Long) -> Unit
) {
    private val tokens = ConcurrentHashMap<String, DownloadToken>()
    private val random = SecureRandom()

    fun clear() {
        tokens.clear()
    }

    /**
     * Handles browser portal traffic when applicable.
     */
    suspend fun tryHandle(
        call: ApplicationCall,
        method: String,
        rawPath: String
    ): BrowserHandleResult {
        val path = normalizePath(rawPath)

        // One-time file stream (must work even when Accept prefers HTML).
        if (method == "GET" && path == BROWSER_DOWNLOAD_PATH) {
            val status = handleTokenDownload(call)
            return BrowserHandleResult(true, "browser-dl", status)
        }

        // Form login (never uses Basic auth — so browsers do not cache credentials).
        if (method == "POST" && path == BROWSER_LOGIN_PATH) {
            return handleLoginPost(call)
        }

        if (method != "GET" && method != "HEAD") return BrowserHandleResult.NotHandled
        if (!looksLikeBrowser(call)) return BrowserHandleResult.NotHandled

        // Root: never list in a browser.
        if (path.isEmpty() || path == "/") {
            call.respondText(useClientHtml(), ContentType.Text.Html, HttpStatusCode.OK)
            return BrowserHandleResult(true, "browser", HttpStatusCode.OK)
        }

        // Favicon is served publicly by [FaviconHandler] (engine dispatches first).
        // Never treat it as a protected file URL if that path is reached here.
        val lower = path.lowercase().trimEnd('/')
        if (lower == "/favicon.ico" || lower == "/favicon.png") {
            return BrowserHandleResult.NotHandled
        }

        // Direct file URL: always ask for login again (no session cookie).
        call.respondText(
            loginHtml(filePath = path, error = null),
            ContentType.Text.Html,
            HttpStatusCode.OK
        )
        return BrowserHandleResult(true, "browser", HttpStatusCode.OK)
    }

    private suspend fun handleLoginPost(call: ApplicationCall): BrowserHandleResult {
        val params = runCatching { call.receiveParameters() }.getOrNull()
        val username = params?.get("username")?.trim().orEmpty()
        val password = params?.get("password").orEmpty()
        val filePath = normalizePath(params?.get("path").orEmpty())

        if (username.isEmpty() || password.isEmpty() || filePath.isEmpty() || filePath == "/") {
            call.respondText(
                rejectedHtml("Missing credentials or file path."),
                ContentType.Text.Html,
                HttpStatusCode.Unauthorized
            )
            return BrowserHandleResult(true, username.ifEmpty { "-" }, HttpStatusCode.Unauthorized)
        }

        val user = userRepository.authenticate(username, password)
        if (user == null) {
            call.respondText(
                rejectedHtml("Login rejected. Wrong username or password."),
                ContentType.Text.Html,
                HttpStatusCode.Unauthorized
            )
            return BrowserHandleResult(true, username, HttpStatusCode.Unauthorized)
        }

        val effectiveRoot = resolveEffectiveRoot(user)
        if (effectiveRoot == null) {
            call.respondText(
                rejectedHtml("Server root is not configured."),
                ContentType.Text.Html,
                HttpStatusCode.InternalServerError
            )
            return BrowserHandleResult(true, user.username, HttpStatusCode.InternalServerError)
        }
        val root = saf.rootDocument(effectiveRoot)
        if (root == null || !root.exists()) {
            call.respondText(
                rejectedHtml("Server root is not configured."),
                ContentType.Text.Html,
                HttpStatusCode.InternalServerError
            )
            return BrowserHandleResult(true, user.username, HttpStatusCode.InternalServerError)
        }

        val doc = saf.resolve(root, filePath)
        if (doc == null || !doc.exists()) {
            call.respondText(
                rejectedHtml("File not found, or you do not have access to this path."),
                ContentType.Text.Html,
                HttpStatusCode.NotFound
            )
            return BrowserHandleResult(true, user.username, HttpStatusCode.NotFound)
        }
        if (doc.isDirectory) {
            call.respondText(
                rejectedHtml("That path is a folder. Open a direct file URL, or use a WebDAV client."),
                ContentType.Text.Html,
                HttpStatusCode.BadRequest
            )
            return BrowserHandleResult(true, user.username, HttpStatusCode.BadRequest)
        }

        val fileName = doc.name ?: "download"
        val token = issueToken(
            DownloadToken(
                effectiveRoot = effectiveRoot,
                relativePath = filePath,
                fileName = fileName,
                username = user.username,
                expiresAtMillis = System.currentTimeMillis() + TOKEN_TTL_MS
            )
        )

        call.respondText(
            successHtml(fileName = fileName, token = token),
            ContentType.Text.Html,
            HttpStatusCode.OK
        )
        return BrowserHandleResult(true, user.username, HttpStatusCode.OK)
    }

    private suspend fun handleTokenDownload(call: ApplicationCall): HttpStatusCode {
        val token = call.request.queryParameters["t"].orEmpty()
        val entry = tokens.remove(token)
        if (entry == null || entry.expiresAtMillis < System.currentTimeMillis()) {
            call.respondText(
                rejectedHtml("Download link expired or already used. Open the file URL and sign in again."),
                ContentType.Text.Html,
                HttpStatusCode.Unauthorized
            )
            return HttpStatusCode.Unauthorized
        }

        val root = saf.rootDocument(entry.effectiveRoot)
        val doc = root?.let { saf.resolve(it, entry.relativePath) }
        if (doc == null || !doc.exists() || doc.isDirectory) {
            call.respondText(
                rejectedHtml("File is no longer available."),
                ContentType.Text.Html,
                HttpStatusCode.NotFound
            )
            return HttpStatusCode.NotFound
        }

        val mime = doc.type ?: SafResolver.guessMime(entry.fileName)
        val length = doc.length()
        call.response.header("Content-Length", length.toString())
        call.response.header(
            "Content-Disposition",
            "attachment; filename=\"${entry.fileName.replace("\"", "")}\""
        )
        call.response.header("Cache-Control", "no-store, no-cache, must-revalidate")
        call.response.header("Pragma", "no-cache")

        call.respondOutputStream(ContentType.parse(mime), HttpStatusCode.OK) {
            withContext(Dispatchers.IO) {
                saf.openInput(doc)?.use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        write(buffer, 0, read)
                        onBytesOut(read.toLong())
                    }
                }
            }
        }
        return HttpStatusCode.OK
    }

    private fun issueToken(data: DownloadToken): String {
        purgeExpired()
        val bytes = ByteArray(24)
        random.nextBytes(bytes)
        val token = bytes.joinToString("") { "%02x".format(it) }
        tokens[token] = data
        return token
    }

    private fun purgeExpired() {
        val now = System.currentTimeMillis()
        tokens.entries.removeIf { it.value.expiresAtMillis < now }
    }

    private data class DownloadToken(
        val effectiveRoot: String,
        val relativePath: String,
        val fileName: String,
        val username: String,
        val expiresAtMillis: Long
    )

    companion object {
        const val BROWSER_LOGIN_PATH = "/__browser/login"
        const val BROWSER_DOWNLOAD_PATH = "/__browser/download"
        private const val TOKEN_TTL_MS = 120_000L // 2 minutes, single use

        fun looksLikeBrowser(call: ApplicationCall): Boolean {
            val dest = call.request.headers["Sec-Fetch-Dest"].orEmpty()
            if (dest.equals("document", ignoreCase = true)) return true
            val mode = call.request.headers["Sec-Fetch-Mode"].orEmpty()
            if (mode.equals("navigate", ignoreCase = true)) return true
            val accept = call.request.headers["Accept"].orEmpty()
            val ua = call.request.headers["User-Agent"].orEmpty()
            val wantsHtml = accept.contains("text/html", ignoreCase = true)
            val mozilla = ua.contains("Mozilla", ignoreCase = true) ||
                ua.contains("Chrome", ignoreCase = true) ||
                ua.contains("Safari", ignoreCase = true) ||
                ua.contains("Firefox", ignoreCase = true) ||
                ua.contains("Edg/", ignoreCase = true)
            return wantsHtml && mozilla
        }

        fun normalizePath(path: String): String {
            val p = path.trim()
            if (p.isEmpty()) return "/"
            val withSlash = if (p.startsWith("/")) p else "/$p"
            return withSlash.replace(Regex("/{2,}"), "/")
        }

        private fun pageShell(title: String, bodyInner: String): String = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <meta http-equiv="Cache-Control" content="no-store">
              <link rel="icon" href="/favicon.ico" type="image/png">
              <title>$title</title>
              <style>
                html, body { height: 100%; margin: 0; background: #000; color: #f5f5f7; }
                body {
                  display: flex; align-items: center; justify-content: center;
                  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
                }
                .box {
                  border: 1px solid #3a3a3c; border-radius: 14px; padding: 2rem 2.25rem;
                  max-width: 24rem; width: 90%; text-align: center; background: #0c0c0e;
                  box-shadow: 0 12px 40px rgba(0,0,0,0.45);
                }
                h1 { font-size: 1.1rem; margin: 0 0 1rem; font-weight: 700; }
                p { font-size: 0.92rem; line-height: 1.55; color: #d1d1d6; margin: 0 0 1rem; }
                .thanks { font-weight: 700; font-size: 1.25rem; color: #fff; margin: 1rem 0 0; }
                label { display: block; text-align: left; font-size: 0.8rem; color: #aeaeb2; margin: 0.6rem 0 0.25rem; }
                input[type=text], input[type=password] {
                  width: 100%; box-sizing: border-box; padding: 0.65rem 0.75rem;
                  border-radius: 8px; border: 1px solid #3a3a3c; background: #1c1c1e; color: #fff;
                  font-family: inherit; font-size: 0.95rem;
                }
                button {
                  margin-top: 1.25rem; width: 100%; padding: 0.75rem;
                  border: none; border-radius: 10px; background: #0a84ff; color: #fff;
                  font-weight: 700; font-family: inherit; font-size: 0.95rem; cursor: pointer;
                }
                .err { color: #ff453a; margin-bottom: 0.75rem; }
                .ok { color: #30d158; }
                a { color: #0a84ff; }
              </style>
            </head>
            <body><div class="box">$bodyInner</div></body>
            </html>
        """.trimIndent()

        fun useClientHtml(): String = pageShell(
            title = "WebDAV Server",
            bodyInner = """
                <p>Use a WebDAV client to access this server instead.</p>
                <p class="thanks">Thankyou</p>
            """.trimIndent()
        )

        fun loginHtml(filePath: String, error: String?): String {
            val err = if (error != null) """<p class="err">${WebDavXml.escape(error)}</p>""" else ""
            val safePath = WebDavXml.escape(filePath)
            return pageShell(
                title = "Sign in to download",
                bodyInner = """
                    <h1>Download file</h1>
                    <p>Sign in to download.<br><small>${safePath}</small></p>
                    <p style="font-size:0.8rem;color:#8e8e93">Login is not saved — you must enter your password every time.</p>
                    $err
                    <form method="post" action="$BROWSER_LOGIN_PATH" autocomplete="off">
                      <input type="hidden" name="path" value="$safePath">
                      <label for="username">Username</label>
                      <input id="username" name="username" type="text" required autocomplete="username">
                      <label for="password">Password</label>
                      <input id="password" name="password" type="password" required autocomplete="off">
                      <button type="submit">Sign in &amp; download</button>
                    </form>
                """.trimIndent()
            )
        }

        fun successHtml(fileName: String, token: String): String {
            val safeName = WebDavXml.escape(fileName)
            // Single navigation only — token is one-use (iframe + redirect would burn it twice).
            val href = "$BROWSER_DOWNLOAD_PATH?t=${android.net.Uri.encode(token)}"
            return pageShell(
                title = "Download ready",
                bodyInner = """
                    <h1 class="ok">Success</h1>
                    <p>Authenticated. Downloading <strong>$safeName</strong>…</p>
                    <p><a id="dl" href="$href">Click here if the download does not start</a></p>
                    <p class="thanks">Thankyou</p>
                    <script>
                      setTimeout(function(){ window.location.replace("$href"); }, 350);
                    </script>
                """.trimIndent()
            )
        }

        fun rejectedHtml(message: String): String = pageShell(
            title = "Access rejected",
            bodyInner = """
                <h1 class="err">Rejected</h1>
                <p>${WebDavXml.escape(message)}</p>
                <p class="thanks">Thankyou</p>
            """.trimIndent()
        )
    }
}
