package com.localdev.webdavserver.data.server

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import java.io.ByteArrayOutputStream

/**
 * Public (no-auth) site icon at the URL root path `/favicon.ico`.
 *
 * Browsers auto-request this path; without a public handler they hit WebDAV
 * Basic auth and log as 401. Served like a normal static web asset: GET/HEAD
 * only, anyone may download, body is the app launcher icon encoded as PNG
 * (modern browsers accept PNG at the classic favicon URL).
 */
class FaviconHandler(context: Context) {

    private val appContext = context.applicationContext

    @Volatile
    private var cachedPng: ByteArray? = null

    fun isFaviconPath(rawPath: String): Boolean {
        val p = rawPath.trim().let { if (it.startsWith("/")) it else "/$it" }
            .replace(Regex("/{2,}"), "/")
            .trimEnd('/')
            .lowercase()
        return p == "/favicon.ico" || p == "/favicon.png"
    }

    /**
     * Serves the icon. Returns status + body bytes transferred (0 for HEAD / errors).
     */
    suspend fun handle(call: ApplicationCall, method: String, rawPath: String): Pair<HttpStatusCode, Long> {
        val bytes = pngBytes()
        if (bytes == null || bytes.isEmpty()) {
            call.respond(HttpStatusCode.NotFound)
            return HttpStatusCode.NotFound to 0L
        }

        val fileName = if (rawPath.lowercase().contains("favicon.png")) "favicon.png" else "favicon.ico"
        call.response.header(HttpHeaders.CacheControl, "public, max-age=86400")
        call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
        call.response.header(
            HttpHeaders.ContentDisposition,
            "inline; filename=\"$fileName\""
        )

        if (method.equals("HEAD", ignoreCase = true)) {
            // HEAD: headers only — set length/type without a body.
            call.response.header(HttpHeaders.ContentType, ContentType.Image.PNG.toString())
            call.response.header(HttpHeaders.ContentLength, bytes.size.toString())
            call.respond(HttpStatusCode.OK)
            return HttpStatusCode.OK to 0L
        }

        // GET: let respondBytes set Content-Type / Content-Length (avoid duplicate headers).
        call.respondBytes(bytes, ContentType.Image.PNG, HttpStatusCode.OK)
        return HttpStatusCode.OK to bytes.size.toLong()
    }

    private fun pngBytes(): ByteArray? {
        cachedPng?.let { return it }
        return synchronized(this) {
            cachedPng?.let { return it }
            val built = runCatching { renderAppIconPng() }.getOrNull()
            cachedPng = built
            built
        }
    }

    private fun renderAppIconPng(): ByteArray {
        val drawable: Drawable =
            appContext.packageManager.getApplicationIcon(appContext.applicationInfo)
        val size = ICON_SIZE_PX
        val bitmap = drawableToBitmap(drawable, size)
        val out = ByteArrayOutputStream(size * size)
        // Prefer not to recycle scaled copies mid-flight; compress first.
        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
            error("PNG compress failed")
        }
        if (bitmap !== (drawable as? BitmapDrawable)?.bitmap && !bitmap.isRecycled) {
            bitmap.recycle()
        }
        return out.toByteArray()
    }

    private fun drawableToBitmap(drawable: Drawable, size: Int): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            val src = drawable.bitmap
            if (src.width == size && src.height == size) return src
            return Bitmap.createScaledBitmap(src, size, size, true)
        }
        // Adaptive / vector icons: paint on launcher blue so transparent tabs look solid.
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(LAUNCHER_BG)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return bmp
    }

    companion object {
        /** Comfortable browser tab size; PNG stays tiny. */
        private const val ICON_SIZE_PX = 64
        /** Matches res/values `ic_launcher_background` (#0A84FF). */
        private val LAUNCHER_BG: Int = Color.parseColor("#0A84FF")
    }
}
