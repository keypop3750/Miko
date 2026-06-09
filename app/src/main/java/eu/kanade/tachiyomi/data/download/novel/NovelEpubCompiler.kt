package eu.kanade.tachiyomi.data.download.novel

import android.content.Context
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import yokai.domain.novel.Novel
import java.io.File
import java.io.FileOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Compiles downloaded novel chapter text files into a valid EPUB.
 *
 * Based on QuickNovel's approach:
 * - Individual chapters are stored as .txt files: title\n<html content>
 * - The EPUB is compiled on-demand (or after batch download) from these files
 * - The EPUB serves as both offline cache and exportable format
 *
 * EPUB structure:
 *   mimetype                    (stored, uncompressed)
 *   META-INF/container.xml
 *   OEBPS/content.opf
 *   OEBPS/toc.ncx
 *   OEBPS/cover.jpg             (if available)
 *   OEBPS/cover.html
 *   OEBPS/chapter_001.html
 *   OEBPS/chapter_002.html
 *   ...
 */
object NovelEpubCompiler {

    private const val TAG = "NovelEpubCompiler"
    private const val LOCAL_EPUB = "local_epub.epub"
    internal const val LOCAL_EPUB_MIN_SIZE = 1000L

    /**
     * Compile downloaded chapters into an EPUB for the given novel.
     *
     * @param context Android context
     * @param novel the novel to compile
     * @param author optional author name
     * @param synopsis optional synopsis/description
     * @param posterUrl optional cover image URL (already downloaded to poster.jpg)
     * @param chapterFiles list of downloaded chapter .txt files, sorted by order
     * @return the compiled EPUB File, or null if compilation failed
     */
    suspend fun compile(
        context: Context,
        novel: Novel,
        author: String? = null,
        synopsis: String? = null,
        posterFile: File? = null,
        chapterFiles: List<File>,
    ): File? = withContext(Dispatchers.IO) {
        try {
            if (chapterFiles.isEmpty()) {
                Logger.w(TAG) { "No chapter files to compile for ${novel.title}" }
                return@withContext null
            }

            val novelDir = File(context.filesDir, "novels/${novel.source}/${novel.id}")
            val epubFile = File(novelDir, LOCAL_EPUB)

            // Ensure directory exists
            novelDir.mkdirs()

            // If a valid EPUB already exists, only rebuild if chapters changed
            val existingEpubValid = epubFile.exists() && epubFile.length() > LOCAL_EPUB_MIN_SIZE
            if (existingEpubValid) {
                // Could add smarter invalidation (e.g., compare chapter count + timestamps)
                // For now, always rebuild to ensure freshness
                Logger.d(TAG) { "Rebuilding EPUB for ${novel.title}" }
            }

            FileOutputStream(epubFile).use { fos ->
                ZipOutputStream(fos).use { zos ->
                    // 1. mimetype must be FIRST and STORED (uncompressed)
                    writeMimetype(zos)

                    // 2. META-INF/container.xml
                    writeContainerXml(zos)

                    // 3. Read all chapters
                    val chapters = chapterFiles.mapIndexed { index, file ->
                        val text = file.readText()
                        val firstNewline = text.indexOf('\n')
                        val title = if (firstNewline > 0) text.substring(0, firstNewline) else "Chapter ${index + 1}"
                        val body = if (firstNewline >= 0) text.substring(firstNewline + 1) else text
                        ChapterEntry(index + 1, title, body, file.nameWithoutExtension.toLongOrNull() ?: 0L)
                    }

                    // 4. Write cover image if available
                    val hasCover = posterFile?.exists() == true && posterFile.length() > 0
                    if (hasCover) {
                        writeCoverImage(zos, posterFile!!)
                        writeCoverHtml(zos, novel.title)
                    }

                    // 5. Write chapter HTML files
                    chapters.forEach { chapter ->
                        writeChapterHtml(zos, chapter)
                    }

                    // 6. Write NCX (table of contents)
                    writeTocNcx(zos, novel.title, chapters, hasCover)

                    // 7. Write OPF (package document)
                    writeContentOpf(zos, novel, author, synopsis, chapters, hasCover)
                }
            }

            Logger.i(TAG) { "Compiled EPUB for ${novel.title}: ${epubFile.length()} bytes" }
            epubFile

        } catch (e: Exception) {
            Logger.e(TAG) { "Failed to compile EPUB: ${e.message}" }
            null
        }
    }

    /**
     * Get the compiled EPUB file for a novel if it exists and is valid.
     */
    fun getCompiledEpub(context: Context, novel: Novel): File? {
        val file = File(context.filesDir, "novels/${novel.source}/${novel.id}/$LOCAL_EPUB")
        return if (file.exists() && file.length() > LOCAL_EPUB_MIN_SIZE) file else null
    }

    /**
     * Delete the compiled EPUB for a novel (forces rebuild on next access).
     */
    fun invalidateEpub(context: Context, novel: Novel) {
        val file = File(context.filesDir, "novels/${novel.source}/${novel.id}/$LOCAL_EPUB")
        if (file.exists()) {
            file.delete()
            Logger.d(TAG) { "Invalidated EPUB for ${novel.title}" }
        }
    }

    // ==================== ZIP writing helpers ====================

    private fun writeMimetype(zos: ZipOutputStream) {
        val bytes = "application/epub+zip".toByteArray(Charsets.UTF_8)
        val entry = ZipEntry("mimetype").apply {
            method = ZipEntry.STORED
            size = bytes.size.toLong()
            compressedSize = size
            val crc32 = CRC32()
            crc32.update(bytes)
            crc = crc32.value
        }
        zos.putNextEntry(entry)
        zos.write(bytes)
        zos.closeEntry()
    }

    private fun writeContainerXml(zos: ZipOutputStream) {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
    <rootfiles>
        <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
    </rootfiles>
</container>
""".trimIndent()
        writeZipEntry(zos, "META-INF/container.xml", xml)
    }

    private fun writeContentOpf(
        zos: ZipOutputStream,
        novel: Novel,
        author: String?,
        synopsis: String?,
        chapters: List<ChapterEntry>,
        hasCover: Boolean,
    ) {
        val manifestItems = buildString {
            if (hasCover) {
                appendLine("""        <item id="cover" href="cover.jpg" media-type="image/jpeg"/>""")
                appendLine("""        <item id="cover-page" href="cover.html" media-type="application/xhtml+xml"/>""")
            }
            chapters.forEach { ch ->
                appendLine("""        <item id="chapter${ch.id}" href="chapter_${ch.id}.html" media-type="application/xhtml+xml"/>""")
            }
        }

        val spineItems = buildString {
            if (hasCover) {
                appendLine("""        <itemref idref="cover-page" linear="no"/>""")
            }
            chapters.forEach { ch ->
                appendLine("""        <itemref idref="chapter${ch.id}"/>""")
            }
        }

        val authorXml = author?.let {
            val escaped = xmlEscape(it)
            "        <dc:creator opf:role=\"aut\">$escaped</dc:creator>\n"
        } ?: ""

        val descXml = synopsis?.let {
            val escaped = xmlEscape(it)
            "        <dc:description>$escaped</dc:description>\n"
        } ?: ""

        val xml = """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="BookId">
    <metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">
        <dc:title>${xmlEscape(novel.title)}</dc:title>
$authorXml$descXml        <dc:identifier id="BookId">urn:novel:${novel.id}</dc:identifier>
        <dc:language>en</dc:language>
    </metadata>
    <manifest>
        <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
$manifestItems    </manifest>
    <spine toc="ncx">
$spineItems    </spine>
</package>
""".trimIndent()

        writeZipEntry(zos, "OEBPS/content.opf", xml)
    }

    private fun writeTocNcx(
        zos: ZipOutputStream,
        novelTitle: String,
        chapters: List<ChapterEntry>,
        hasCover: Boolean,
    ) {
        val navPoints = buildString {
            var playOrder = 1
            if (hasCover) {
                appendLine("""        <navPoint id="cover" playOrder="${playOrder++}">""")
                appendLine("""            <navLabel><text>Cover</text></navLabel>""")
                appendLine("""            <content src="cover.html"/>""")
                appendLine("""        </navPoint>""")
            }
            chapters.forEach { ch ->
                val title = xmlEscape(ch.title)
                appendLine("""        <navPoint id="chapter${ch.id}" playOrder="${playOrder++}">""")
                appendLine("""            <navLabel><text>$title</text></navLabel>""")
                appendLine("""            <content src="chapter_${ch.id}.html"/>""")
                appendLine("""        </navPoint>""")
            }
        }

        val xml = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE ncx PUBLIC "-//NISO//DTD ncx 2005-1//EN" "http://www.daisy.org/z3986/2005/ncx-2005-1.dtd">
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
    <head>
        <meta name="dtb:uid" content="urn:novel"/>
        <meta name="dtb:depth" content="1"/>
        <meta name="dtb:totalPageCount" content="0"/>
        <meta name="dtb:maxPageNumber" content="0"/>
    </head>
    <docTitle><text>${xmlEscape(novelTitle)}</text></docTitle>
    <navMap>
$navPoints    </navMap>
</ncx>
""".trimIndent()

        writeZipEntry(zos, "OEBPS/toc.ncx", xml)
    }

    private fun writeCoverImage(zos: ZipOutputStream, posterFile: File) {
        val entry = ZipEntry("OEBPS/cover.jpg")
        zos.putNextEntry(entry)
        posterFile.inputStream().use { it.copyTo(zos) }
        zos.closeEntry()
    }

    private fun writeCoverHtml(zos: ZipOutputStream, title: String) {
        val html = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.1//EN" "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
    <title>${xmlEscape(title)}</title>
    <style type="text/css">
        body { margin: 0; padding: 0; text-align: center; }
        img { max-width: 100%; height: auto; }
    </style>
</head>
<body>
    <img src="cover.jpg" alt="Cover"/>
</body>
</html>
""".trimIndent()
        writeZipEntry(zos, "OEBPS/cover.html", html)
    }

    private fun writeChapterHtml(zos: ZipOutputStream, chapter: ChapterEntry) {
        val body = wrapInXhtml(chapter.title, chapter.body)
        writeZipEntry(zos, "OEBPS/chapter_${chapter.id}.html", body)
    }

    private fun wrapInXhtml(title: String, bodyContent: String): String {
        // Escape any stray XML special chars in the body that aren't valid HTML
        val cleanBody = bodyContent
            .replace("&nbsp;", "\u00A0") // Keep non-breaking spaces

        return """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.1//EN" "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
    <title>${xmlEscape(title)}</title>
    <style type="text/css">
        body { font-family: serif; line-height: 1.6; margin: 1em; }
        p { margin: 0.5em 0; }
    </style>
</head>
<body>
<h1>${xmlEscape(title)}</h1>
$cleanBody
</body>
</html>
""".trimIndent()
    }

    private fun writeZipEntry(zos: ZipOutputStream, path: String, content: String) {
        val entry = ZipEntry(path)
        zos.putNextEntry(entry)
        zos.write(content.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }

    private fun xmlEscape(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private data class ChapterEntry(
        val id: Int,
        val title: String,
        val body: String,
        val chapterId: Long,
    )
}
