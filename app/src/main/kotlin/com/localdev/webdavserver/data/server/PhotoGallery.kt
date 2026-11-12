package com.localdev.webdavserver.data.server

import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.util.LruCache
import android.util.Size
import androidx.documentfile.provider.DocumentFile
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveChannel
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.core.readBytes
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class PhotoGallery(
    private val photos: PhotosByDate,
    private val saf: SafResolver,
    private val getHandler: GetHandler
) {
    private val thumbnailLock = Mutex()
    private val thumbnails = object : LruCache<ThumbnailKey, ByteArray>(8 * 1024 * 1024) {
        override fun sizeOf(key: ThumbnailKey, value: ByteArray): Int = value.size
    }

    private data class ThumbnailKey(val path: String, val modified: Long, val size: Long)

    suspend fun handle(
        call: ApplicationCall,
        root: DocumentFile,
        path: List<String>,
        enabled: Boolean,
        canDelete: Boolean
    ): HttpStatusCode {
        call.response.header("Cache-Control", "no-store")
        val mode = path.firstOrNull()?.takeIf { it == "original" || it == "thumbnail" }
        val datePath = if (mode == null) path else path.drop(1)
        val source = withContext(Dispatchers.IO) { if (enabled) photos.source(root) else null }
            ?: return message(call, "Photos by Date is unavailable.", HttpStatusCode.NotFound)
        val resource = try {
            if (mode == null) {
                val days = withContext(Dispatchers.IO) { photos.timeline(source) }
                if (datePath.size > 3 || (datePath.isNotEmpty() && days.none { it.path.take(datePath.size) == datePath })) {
                    return message(call, "Date not found.", HttpStatusCode.NotFound)
                }
                call.respondText(page(datePath, days, canDelete), ContentType.Text.Html)
                return HttpStatusCode.OK
            }
            withContext(Dispatchers.IO) { photos.resolve(source, datePath) }
        } catch (_: IOException) {
            return message(call, "Could not read Camera.", HttpStatusCode.InternalServerError)
        } ?: return message(call, "Photos by Date is unavailable.", HttpStatusCode.NotFound)

        val original = resource.originalPath
        return when {
            mode == "original" && original != null -> getHandler.handle(call, root, original)
            mode == "thumbnail" && original != null -> {
                val bytes = withContext(Dispatchers.IO) {
                    thumbnailLock.withLock {
                        val file = saf.resolve(root, original)?.uri?.path?.let(::File) ?: return@withLock null
                        val key = ThumbnailKey(file.path, file.lastModified(), file.length())
                        thumbnails.get(key) ?: makeThumbnail(file, resource.node.mimeType.startsWith("video/"))
                            ?.also { thumbnails.put(key, it) }
                    }
                } ?: return message(call, "Thumbnail unavailable.", HttpStatusCode.NotFound)
                call.respondBytes(bytes, ContentType.Image.JPEG)
                HttpStatusCode.OK
            }
            else -> message(call, "Not found.", HttpStatusCode.NotFound)
        }
    }

    private data class SelectedPhoto(val path: List<String>, val modified: Long, val size: Long)

    suspend fun deleteSelection(call: ApplicationCall, root: DocumentFile, enabled: Boolean): HttpStatusCode {
        call.response.header("Cache-Control", "no-store")
        if (!enabled) return message(call, "Gallery unavailable.", HttpStatusCode.NotFound)

        val body = call.receiveChannel().readRemaining(MAX_DELETE_BYTES + 1L).readBytes()
        if (body.size > MAX_DELETE_BYTES) {
            return message(call, "Selection is too large.", HttpStatusCode.PayloadTooLarge)
        }
        val selection = body.decodeToString().lineSequence().filter { it.isNotBlank() }.map { line ->
            val fields = line.split('\t')
            val path = SafResolver.splitPath(fields[0])?.takeIf { it.size == 4 }
            val modified = fields.getOrNull(1)?.toLongOrNull()
            val size = fields.getOrNull(2)?.toLongOrNull()
            if (fields.size == 3 && path != null && modified != null && size != null) {
                SelectedPhoto(path, modified, size)
            } else null
        }.toList()
        if (selection.isEmpty() || selection.any { it == null }) {
            return message(call, "Invalid photo selection.", HttpStatusCode.BadRequest)
        }

        val deleted = try {
            withContext(Dispatchers.IO) {
                val source = photos.source(root) ?: return@withContext null
                val filesByDay = photos.timeline(source).associate { day ->
                    day.path to day.files.associateBy { it.name }
                }
                selection.filterNotNull().distinct().count { selected ->
                    val path = selected.path
                    val node = filesByDay[path.take(3)]?.get(path.last()) ?: return@count false
                    if (node.lastModified != selected.modified || node.sizeBytes != selected.size) return@count false
                    val original = source.path + "/" + android.net.Uri.encode(node.name)
                    val file = saf.resolve(root, original) ?: return@count false
                    file.isFile && file.length() == node.sizeBytes &&
                        file.lastModified() == node.lastModified && file.delete()
                }.also { count ->
                    if (count > 0) {
                        photos.clear()
                        FileChangeBus.notifyChanged()
                    }
                }
            }
        } catch (_: IOException) {
            return message(call, "Could not read Camera.", HttpStatusCode.InternalServerError)
        } ?: return message(call, "Gallery unavailable.", HttpStatusCode.NotFound)

        call.respondText("""{"deleted":$deleted}""", ContentType.Application.Json)
        return HttpStatusCode.OK
    }

    private fun makeThumbnail(file: File, video: Boolean): ByteArray? {
        val size = Size(320, 320)
        val bitmap = try {
            if (video) ThumbnailUtils.createVideoThumbnail(file, size, null)
            else ThumbnailUtils.createImageThumbnail(file, size, null)
        } catch (_: IOException) {
            return null
        } catch (_: IllegalArgumentException) {
            return null
        }
        return try {
            val output = ByteArrayOutputStream()
            if (bitmap.compress(Bitmap.CompressFormat.JPEG, 78, output)) output.toByteArray() else null
        } finally {
            bitmap.recycle()
        }
    }

    private fun page(path: List<String>, days: List<PhotosByDate.PhotoDay>, canDelete: Boolean): String {
        val year = path.getOrNull(0)
        val month = path.getOrNull(1)
        val filters = buildString {
            append(datePicker("Year", emptyList(), year, days.map { it.path[0] }.distinct()))
            if (year != null) {
                val months = days.filter { it.path[0] == year }.map { it.path[1] }.distinct()
                append(datePicker("Month", listOf(year), month, months))
                if (month != null) {
                    val prefix = listOf(year, month)
                    val dates = days.filter { it.path.take(2) == prefix }.map { it.path[2] }
                    append(datePicker("Day", prefix, path.getOrNull(2), dates))
                }
            }
        }
        val sections = days.asSequence().filter { it.path.take(path.size) == path }.joinToString("\n") { day ->
            val (groupYear, groupMonth, groupDay) = day.path
            val cards = day.files.joinToString("\n") { node ->
                val next = day.path + node.name
                val label = WebDavXml.escape(node.name)
                val kind = if (node.mimeType.startsWith("video/")) " · Video" else ""
                val selection = if (canDelete) {
                    val value = WebDavXml.hrefEncode("/" + next.joinToString("/"))
                    """<input class="pick" type="checkbox" aria-label="Select $label" value="$value" data-modified="${node.lastModified}" data-size="${node.sizeBytes}">"""
                } else ""
                """<div class="photo">$selection<a href="${url(listOf("original") + next)}">
                   <img decoding="async" data-src="${url(listOf("thumbnail") + next)}" alt="">
                   <span>$label$kind</span></a></div>"""
            }
            val selectDay = if (canDelete) """<button class="select-day" type="button">Select day</button>""" else ""
            """<section><div class="day-heading">
               <h2>$groupYear <span>${groupMonth.toInt()}-${groupDay.toInt()}</span></h2>$selectDay</div>
               <div class="grid">$cards</div></section>"""
        }
        return """
            <!doctype html>
            <html lang="en"><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>Photo gallery</title>
            <style>
              * { box-sizing: border-box }
              body { margin: 0; background: #141518; color: #f4f4f5; font: 16px system-ui, sans-serif }
              main { max-width: 1200px; padding: 28px 20px; margin: auto }
              a { color: #b6d6ff }
              .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(min(100%, 150px), 1fr)); gap: 12px }
              header { position: sticky; top: 0; z-index: 1; background: #141518; padding: 12px 0 }
              h1 { margin: 10px 0 }
              .filters { display: flex; flex-wrap: wrap; gap: 10px }
              .filters label { display: grid; gap: 4px; font-size: 13px }
              select { color: inherit; background: #22242a; border: 1px solid #555; border-radius: 6px; padding: 7px }
              .day-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; border-bottom: 1px solid #3b3e44; margin-bottom: 12px }
              section h2 { display: flex; gap: 12px; font-size: 20px }
              button { color: inherit; background: #22242a; border: 1px solid #555; border-radius: 6px; padding: 7px 10px; cursor: pointer }
              button:disabled { opacity: .55; cursor: default }
              .selection { display: flex; align-items: center; gap: 10px; margin-top: 10px }
              .photo { position: relative }
              .photo a { display: block; color: inherit; text-decoration: none }
              .pick { position: absolute; top: 8px; left: 8px; width: 22px; height: 22px; cursor: pointer }
              .photo:has(.pick:checked) { outline: 2px solid #b6d6ff }
              section h2 span { color: #bfc3cc; font-weight: 400 }
              .photo { color: inherit; background: #22242a; border-radius: 10px; overflow: hidden; text-decoration: none }
              .photo:hover { background: #31343c }
              .photo img { display: block; width: 100%; aspect-ratio: 1; object-fit: contain; background: #191a1e }
              .photo span { display: block; padding: 12px; overflow-wrap: anywhere }
              .photo span { font-size: 13px }
            </style>
            <main><header><nav><a href="/">Server home</a></nav><h1>Photo gallery</h1>
            <div class="filters">$filters</div>
            ${if (canDelete) """<div class="selection"><button id="remove" type="button" disabled>Delete selected</button>
                <button id="clear" type="button" disabled>Clear selection</button><output id="count"></output></div>""" else ""}
            </header>
            ${sections.ifEmpty { "<p>No photos or videos.</p>" }}
            </main>
            <script>
              const observer = new IntersectionObserver(entries => {
                for (const entry of entries) {
                  if (!entry.isIntersecting) continue;
                  const image = entry.target;
                  image.src = image.dataset.src;
                  observer.unobserve(image);
                }
              }, { rootMargin: '240px 0px' });
              document.querySelectorAll('img[data-src]').forEach(image => observer.observe(image));

              const remove = document.getElementById('remove');
              if (remove) {
                const clear = document.getElementById('clear');
                const selected = () => [...document.querySelectorAll('.pick:checked')];
                let deleting = false;
                function updateSelection() {
                  const count = selected().length;
                  document.getElementById('count').textContent = count ? count + ' selected' : '';
                  remove.disabled = deleting || !count;
                  clear.disabled = deleting || !count;
                }
                document.addEventListener('change', event => {
                  if (event.target.matches('.pick')) updateSelection();
                });
                document.querySelectorAll('.select-day').forEach(button => {
                  button.addEventListener('click', () => {
                    const picks = [...button.closest('section').querySelectorAll('.pick')];
                    const check = picks.some(pick => !pick.checked);
                    picks.forEach(pick => pick.checked = check);
                    updateSelection();
                  });
                });
                clear.addEventListener('click', () => {
                  selected().forEach(pick => pick.checked = false);
                  updateSelection();
                });
                remove.addEventListener('click', async () => {
                  const picks = selected();
                  if (!picks.length || !confirm('Permanently delete ' + picks.length + ' original files from the phone?')) return;
                  deleting = true;
                  updateSelection();
                  try {
                    const response = await fetch('/$PATH/selection', {
                      method: 'DELETE',
                      body: picks.map(pick => [pick.value, pick.dataset.modified, pick.dataset.size].join('\t')).join('\n')
                    });
                    if (!response.ok) throw new Error('Delete failed');
                    const result = await response.json();
                    if (result.deleted !== picks.length) {
                      alert('Deleted ' + result.deleted + ' of ' + picks.length + ' files.');
                    }
                    location.reload();
                  } catch {
                    alert('Could not confirm the result. Reload the gallery to see which files are still present.');
                    deleting = false;
                    updateSelection();
                  }
                });
              }
            </script></html>
        """.trimIndent()
    }

    private fun datePicker(label: String, prefix: List<String>, selected: String?, values: List<String>): String {
        val options = values.joinToString("") { value ->
            val active = if (selected == value) " selected" else ""
            """<option value="${url(prefix + value)}"$active>${value.toInt()}</option>"""
        }
        return """<label>$label<select onchange="location.assign(this.value)">
            <option value="${url(prefix)}">All</option>$options</select></label>"""
    }

    private fun url(path: List<String>) = WebDavXml.hrefEncode("/" + (listOf(PATH) + path).joinToString("/"))

    private suspend fun message(call: ApplicationCall, text: String, status: HttpStatusCode): HttpStatusCode {
        call.respondText(text, status = status)
        return status
    }

    companion object {
        const val PATH = "__gallery"
        private const val MAX_DELETE_BYTES = 2 * 1024 * 1024
    }
}
