package com.localdev.webdavserver.data.server

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respondText

/**
 * Handles OPTIONS — advertises DAV compliance and allowed methods.
 */
class OptionsHandler {
    suspend fun handle(call: ApplicationCall) {
        call.response.header("DAV", WebDavConstants.DAV_COMPLIANCE)
        call.response.header("Allow", WebDavConstants.ALLOWED_METHODS)
        call.response.header("MS-Author-Via", "DAV")
        call.response.header("Accept-Ranges", "bytes")
        call.respondText("", status = HttpStatusCode.OK)
    }
}
