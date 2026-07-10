package com.localdev.webdavserver.data.local.model

/**
 * A single WebDAV request log entry.
 */
data class LogEntry(
    val id: Long,
    val timestampMillis: Long,
    val method: String,      // GET, PUT, PROPFIND, ...
    val path: String,
    val username: String,
    val statusCode: Int,
    val statusMessage: String,
    val ip: String = "-"     // client IP address
) {
    val isError: Boolean get() = statusCode >= 400
}
