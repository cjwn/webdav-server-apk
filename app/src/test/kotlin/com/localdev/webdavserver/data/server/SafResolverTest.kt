package com.localdev.webdavserver.data.server

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import io.ktor.http.decodeURLPart
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.*

class SafResolverTest {
    @get:Rule val temp = TemporaryFolder()

    @Test
    fun `file paths resolve without enumerating the root and stay inside the share`() {
        val shared = temp.newFolder("shared")
        val camera = shared.resolve("Camera").apply { mkdir() }
        camera.resolve("photo.jpg").createNewFile()
        camera.resolve("._photo.jpg").createNewFile()
        val encodedName = camera.resolve("中文 + %2F").apply { mkdir() }
        encodedName.resolve("100%.jpg").createNewFile()
        val outside = temp.newFolder("shared-other")
        outside.resolve("private.jpg").createNewFile()
        Files.createSymbolicLink(shared.resolve("outside").toPath(), outside.toPath())

        val root = mock(DocumentFile::class.java)
        val uri = mock(Uri::class.java)
        `when`(root.uri).thenReturn(uri)
        `when`(uri.scheme).thenReturn("file")
        `when`(uri.path).thenReturn(shared.path)
        val saf = SafResolver(mock(Context::class.java))

        mockStatic(Uri::class.java).use { uris ->
            uris.`when`<String> { Uri.decode(anyString()) }.thenAnswer {
                it.getArgument<String>(0).decodeURLPart()
            }
            assertEquals("photo.jpg", saf.resolve(root, "/Camera/photo.jpg")?.name)
            assertEquals("._photo.jpg", saf.resolve(root, "/Camera/._photo.jpg")?.name)
            assertNull(saf.resolve(root, "/Camera/._missing.jpg"))
            assertNull(saf.resolve(root, "/outside/private.jpg"))
            assertNull(saf.resolve(root, "/%2e%2e/shared-other/private.jpg"))
            assertNull(saf.resolve(root, "/Camera%2Fphoto.jpg"))
            val childPath = "/Camera/%E4%B8%AD%E6%96%87%20+%20%252F/100%25.jpg"
            repeat(3) {
                assertEquals("100%.jpg", saf.resolve(root, childPath)?.name)
                assertEquals("Camera", saf.resolve(root, "/Camera/")?.name)
            }
            val destination = DestinationParser.parse("http://localhost$childPath")
            assertEquals(childPath, destination)
            assertEquals(encodedName.name, saf.resolveParent(root, checkNotNull(destination))?.first?.name)
            val parent = saf.resolveParent(root, "/Camera/new.jpg")
            assertEquals("Camera", parent?.first?.name)
            assertEquals("new.jpg", parent?.second)
            assertNull(saf.resolveParent(root, "/Camera/photo.jpg/child"))
        }

        verify(root, never()).findFile(anyString())
        verify(root, never()).listFiles()
    }
}
