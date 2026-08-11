package com.exmworkspace.exmwsmail.data.mail

/** Coarse attachment families — what an icon and a tint can meaningfully distinguish. */
enum class AttachmentType { IMAGE, PDF, SHEET, DOCUMENT, ARCHIVE, OTHER }

/**
 * Classifies an attachment by MIME type first, filename extension as fallback — senders are
 * sloppy with `Content-Type` (`application/octet-stream` on everything is common) but rarely
 * rename the file.
 *
 * Pure so the mapping is unit-tested; the icons and colours it feeds live in the UI layer.
 */
fun attachmentTypeOf(contentType: String?, filename: String?): AttachmentType {
    val type = contentType.orEmpty().lowercase()
    val name = filename.orEmpty().lowercase()

    fun hasExt(vararg exts: String) = exts.any { name.endsWith(".$it") }

    return when {
        type.startsWith("image/") || hasExt("png", "jpg", "jpeg", "gif", "webp", "bmp", "svg") ->
            AttachmentType.IMAGE
        type == "application/pdf" || hasExt("pdf") -> AttachmentType.PDF
        type.contains("spreadsheet") || type == "text/csv" ||
            hasExt("xls", "xlsx", "csv", "ods", "xml") -> AttachmentType.SHEET
        type.contains("word") || type.startsWith("text/") ||
            hasExt("doc", "docx", "odt", "rtf", "txt", "md") -> AttachmentType.DOCUMENT
        type.contains("zip") || type.contains("compressed") ||
            hasExt("zip", "rar", "7z", "tar", "gz") -> AttachmentType.ARCHIVE
        else -> AttachmentType.OTHER
    }
}
