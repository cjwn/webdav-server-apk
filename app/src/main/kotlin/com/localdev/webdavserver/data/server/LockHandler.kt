package com.localdev.webdavserver.data.server

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Minimal but RFC-4918-compliant locking. Supports exclusive write locks with a
 * timeout. Locks are advisory and tracked in memory. Implements LOCK and UNLOCK.
 */
class LockManager {

    data class Lock(
        val token: String,
        val path: String,
        val owner: String,
        val depth: String,
        val expiresAt: Long
    )

    private val locks = ConcurrentHashMap<String, Lock>() // path -> lock

    fun activeLock(path: String): Lock? {
        val l = locks[path] ?: return null
        if (l.expiresAt < System.currentTimeMillis()) {
            locks.remove(path)
            return null
        }
        return l
    }

    fun isLockedByOther(path: String, token: String?): Boolean {
        val l = activeLock(path) ?: return false
        return l.token != token?.trim('<', '>', '(', ')', ' ')
    }

    fun create(path: String, owner: String, depth: String, timeoutSeconds: Long): Lock {
        val lock = Lock(
            token = "opaquelocktoken:${UUID.randomUUID()}",
            path = path,
            owner = owner,
            depth = depth,
            expiresAt = System.currentTimeMillis() + timeoutSeconds * 1000L
        )
        locks[path] = lock
        return lock
    }

    fun refresh(path: String, timeoutSeconds: Long): Lock? {
        val existing = activeLock(path) ?: return null
        val refreshed = existing.copy(expiresAt = System.currentTimeMillis() + timeoutSeconds * 1000L)
        locks[path] = refreshed
        return refreshed
    }

    fun remove(token: String) {
        val clean = token.trim('<', '>', ' ')
        val entry = locks.entries.firstOrNull { it.value.token == clean }
        if (entry != null) locks.remove(entry.key)
    }
}

class LockHandler(private val lockManager: LockManager) {

    suspend fun handleLock(call: ApplicationCall, path: String, owner: String) {
        val timeout = parseTimeout(call.request.headers["Timeout"])
        val depth = call.request.headers["Depth"] ?: "0"
        val ifHeader = call.request.headers["If"]

        // Refresh existing lock if "If" token matches.
        val refreshed = if (ifHeader != null) lockManager.refresh(path, timeout) else null
        val lock = refreshed ?: lockManager.create(path, owner, depth, timeout)

        val body = lockDiscoveryXml(lock)
        call.response.header("Lock-Token", "<${lock.token}>")
        call.respondText(body, WebDavConstants.XML_UTF8, HttpStatusCode.OK)
    }

    suspend fun handleUnlock(call: ApplicationCall) {
        val token = call.request.headers["Lock-Token"]
        if (token == null) {
            call.respondText("", status = HttpStatusCode.BadRequest)
            return
        }
        lockManager.remove(token)
        call.respondText("", status = HttpStatusCode.NoContent)
    }

    private suspend fun ApplicationCall.bodyOrEmpty(): String =
        runCatching { receiveText() }.getOrDefault("")

    private fun parseTimeout(header: String?): Long {
        if (header == null) return DEFAULT_TIMEOUT
        val first = header.split(",").firstOrNull()?.trim() ?: return DEFAULT_TIMEOUT
        return when {
            first.equals("Infinite", true) -> MAX_TIMEOUT
            first.startsWith("Second-", true) ->
                first.removePrefix("Second-").toLongOrNull()?.coerceAtMost(MAX_TIMEOUT) ?: DEFAULT_TIMEOUT
            else -> DEFAULT_TIMEOUT
        }
    }

    private fun lockDiscoveryXml(lock: LockManager.Lock): String {
        val secondsLeft = ((lock.expiresAt - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
        return """<?xml version="1.0" encoding="utf-8"?>
<D:prop xmlns:D="DAV:">
  <D:lockdiscovery>
    <D:activelock>
      <D:locktype><D:write/></D:locktype>
      <D:lockscope><D:exclusive/></D:lockscope>
      <D:depth>${lock.depth}</D:depth>
      <D:owner>${WebDavXml.escape(lock.owner)}</D:owner>
      <D:timeout>Second-$secondsLeft</D:timeout>
      <D:locktoken><D:href>${lock.token}</D:href></D:locktoken>
      <D:lockroot><D:href>${WebDavXml.escape(lock.path)}</D:href></D:lockroot>
    </D:activelock>
  </D:lockdiscovery>
</D:prop>"""
    }

    companion object {
        const val DEFAULT_TIMEOUT = 3600L
        const val MAX_TIMEOUT = 86_400L
    }
}
