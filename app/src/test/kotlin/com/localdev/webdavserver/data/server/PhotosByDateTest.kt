package com.localdev.webdavserver.data.server

import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.localdev.webdavserver.data.local.model.FileNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito.*

class PhotosByDateTest {
    @Test
    fun `date folders show only that day's media and refresh after Camera changes`() {
        val saf = mock(SafResolver::class.java)
        val camera = mock(DocumentFile::class.java)
        val cameraUri = mock(Uri::class.java)
        `when`(camera.uri).thenReturn(cameraUri)
        `when`(cameraUri.toString()).thenReturn("file:///DCIM/Camera")
        `when`(camera.lastModified()).thenReturn(100L)

        val first = FileNode("", "IMG_20240518_120000.jpg", false, 5, 0, "image/jpeg")
        val second = first.copy(name = "IMG_20240519_120000.jpg")
        val unrelated = first.copy(name = "notes.txt", mimeType = "text/plain")
        `when`(saf.listChildrenForPropfind(camera, false)).thenReturn(listOf(first, unrelated))

        val photos = PhotosByDate(saf)
        val source = PhotosByDate.Source(camera, "/Camera")
        val day = listOf("2024", "05", "18")
        assertEquals(listOf("2024"), photos.resolve(source, emptyList())?.children?.map { it.name })
        assertEquals(listOf(first), photos.resolve(source, day)?.children)
        assertNull(photos.resolve(source, day + second.name))

        mockStatic(Uri::class.java).use { uris ->
            uris.`when`<String> { Uri.encode(first.name) }.thenReturn(first.name)
            assertEquals("/Camera/${first.name}", photos.resolve(source, day + first.name)?.originalPath)
        }
        verify(saf).listChildrenForPropfind(camera, false)

        `when`(camera.lastModified()).thenReturn(200L)
        `when`(saf.listChildrenForPropfind(camera, false)).thenReturn(listOf(first, second))
        assertEquals(listOf(second), photos.resolve(source, listOf("2024", "05", "19"))?.children)
    }
}
