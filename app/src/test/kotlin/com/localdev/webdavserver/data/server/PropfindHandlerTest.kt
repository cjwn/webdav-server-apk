package com.localdev.webdavserver.data.server

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import androidx.documentfile.provider.DocumentFile
import com.localdev.webdavserver.data.local.model.FileNode
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.application.call
import io.ktor.server.routing.*
import io.ktor.server.testing.testApplication
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

class PropfindHandlerTest {
    private fun checkDepth(depth: String, expectedEntries: Int) {
        val saf = mock(SafResolver::class.java)
        val root = mock(DocumentFile::class.java)
        `when`(saf.resolve(root, "")).thenReturn(root)
        `when`(root.exists()).thenReturn(true)
        `when`(root.isDirectory).thenReturn(true)
        `when`(root.name).thenReturn("Root")
        `when`(root.type).thenReturn("inode/directory")
        `when`(saf.listChildrenForPropfind(root, false)).thenReturn(listOf(
            FileNode("file:///root/a.txt", "a.txt", false, 12, 0, "text/plain"),
            FileNode("file:///root/child", "child", true, 0, 0, "inode/directory")
        ))
        testApplication {
                application {
                    routing {
                        route("/") {
                            handle {
                                // Android static mocks are thread-local: keep these stubs
                                // on the handler thread, with listing undispatched.
                                mockStatic(Log::class.java).use {
                                    mockStatic(Uri::class.java).use { uris ->
                                        uris.`when`<String> { Uri.encode(anyString()) }
                                            .thenAnswer { it.getArgument<String>(0) }
                                        PropfindHandler(saf, Dispatchers.Unconfined)
                                            .handle(call, root, "", false)
                                    }
                                }
                            }
                        }
                    }
                }
                val response = client.request("/") {
                    method = HttpMethod("PROPFIND")
                    header("Depth", depth)
                }
                assertEquals(HttpStatusCode.MultiStatus, response.status)
                val xml = response.bodyAsText()
                assertEquals(expectedEntries.toString(), response.headers["X-WebDAV-Entries"])
                val listing = response.headers["X-WebDAV-Listing-Ms"]!!.toLong()
                val build = response.headers["X-WebDAV-Build-Ms"]!!.toLong()
                assertTrue(listing >= 0)
                assertTrue(build >= listing)
                assertEquals(xml.length.toString(), response.headers["X-WebDAV-Xml-Chars"])
                assertEquals(expectedEntries + 1, Regex("<D:response>").findAll(xml).count())
        }
        if (depth == "0") verify(saf, never()).listChildrenForPropfind(root, false)
        else verify(saf).listChildrenForPropfind(root, false)
        verify(saf, never()).listChildren(root, false)
    }
    @Test fun `depth one returns diagnostics matching the actual XML`() = checkDepth("1", 2)
    @Test fun `depth zero returns root only and skips child listing`() = checkDepth("0", 0)
}
