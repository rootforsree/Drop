package com.rootdrop.share.server

/**
 * Builds the index page served at GET /.
 *
 * The page is one self-contained HTML document: inline CSS only, no
 * external assets, no scripts. It lists each shared file with a link
 * to /files/{index} and a link to /all.zip. File names are HTML-escaped
 * here, so a name containing markup characters cannot break the page.
 */
internal fun buildIndexHtml(files: List<SharedFile>): String {
    val items: String = if (files.isEmpty()) {
        "<p>No files are being shared.</p>"
    } else {
        files.mapIndexed { index: Int, file: SharedFile ->
            "<li><a href=\"/files/$index\">${escapeHtml(file.displayName)}</a>" +
                " <span class=\"size\">${formatSize(file.sizeBytes)}</span></li>"
        }.joinToString(separator = "\n")
    }
    val downloadAll: String = if (files.isEmpty()) {
        ""
    } else {
        "<p><a class=\"all\" href=\"/all.zip\">Download all (.zip)</a></p>"
    }
    return "<!DOCTYPE html>\n" +
        "<html lang=\"en\">\n" +
        "<head>\n" +
        "<meta charset=\"utf-8\">\n" +
        "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n" +
        "<title>Drop</title>\n" +
        "<style>" +
        "body{background:#111;color:#eee;font-family:system-ui,sans-serif;" +
        "margin:0;padding:24px;line-height:1.5}" +
        "h1{font-size:22px;margin:0 0 16px}" +
        "ul{list-style:none;padding:0;margin:0}" +
        "li{padding:12px 0;border-bottom:1px solid #333}" +
        "a{color:#007AFF;text-decoration:none}" +
        "a.all{display:inline-block;padding:12px 16px;border:1px solid #007AFF;" +
        "border-radius:8px}" +
        ".size{color:#999;margin-left:8px}" +
        "</style>\n" +
        "</head>\n" +
        "<body>\n" +
        "<h1>Shared files</h1>\n" +
        "<ul>\n$items\n</ul>\n" +
        "$downloadAll\n" +
        "</body>\n" +
        "</html>\n"
}

internal fun escapeHtml(value: String): String {
    val result = StringBuilder()
    for (character: Char in value) {
        when (character) {
            '&' -> result.append("&amp;")
            '<' -> result.append("&lt;")
            '>' -> result.append("&gt;")
            '"' -> result.append("&quot;")
            '\'' -> result.append("&#39;")
            else -> result.append(character)
        }
    }
    return result.toString()
}

internal fun formatSize(sizeBytes: Long): String {
    val size: Long = if (sizeBytes < 0L) 0L else sizeBytes
    return when {
        size < 1024L -> "$size B"
        size < 1024L * 1024L -> "%.1f KB".format(size.toDouble() / 1024.0)
        size < 1024L * 1024L * 1024L -> "%.1f MB".format(size.toDouble() / (1024.0 * 1024.0))
        else -> "%.1f GB".format(size.toDouble() / (1024.0 * 1024.0 * 1024.0))
    }
}
