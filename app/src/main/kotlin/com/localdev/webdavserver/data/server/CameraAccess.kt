package com.localdev.webdavserver.data.server

data class CameraAccess(val camera: Boolean = true, val dates: Boolean = true) {
    fun blocks(segments: List<String>?): Boolean =
        !camera && segments?.firstOrNull()?.equals("Camera", ignoreCase = true) == true
}
