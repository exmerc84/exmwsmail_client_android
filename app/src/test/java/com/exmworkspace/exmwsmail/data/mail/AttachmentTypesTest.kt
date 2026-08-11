package com.exmworkspace.exmwsmail.data.mail

import org.junit.Assert.assertEquals
import org.junit.Test

class AttachmentTypesTest {

    @Test
    fun mime_type_wins_when_it_is_specific() {
        assertEquals(AttachmentType.PDF, attachmentTypeOf("application/pdf", "recibo"))
        assertEquals(AttachmentType.IMAGE, attachmentTypeOf("image/png", "foto"))
        assertEquals(AttachmentType.SHEET, attachmentTypeOf("text/csv", "datos"))
    }

    /** The common real-world case: octet-stream on everything, truth in the extension. */
    @Test
    fun octet_stream_falls_back_to_the_extension() {
        assertEquals(
            AttachmentType.PDF,
            attachmentTypeOf("application/octet-stream", "M316743.pdf"),
        )
        assertEquals(
            AttachmentType.SHEET,
            attachmentTypeOf("application/octet-stream", "Factura_F2.xml"),
        )
        assertEquals(
            AttachmentType.ARCHIVE,
            attachmentTypeOf("application/octet-stream", "backup.tar.gz"),
        )
    }

    /** CFDI invoices arrive as .xml — in this mailbox that is tabular data, not prose. */
    @Test
    fun xml_counts_as_sheet() {
        assertEquals(AttachmentType.SHEET, attachmentTypeOf(null, "Factura_F2.xml"))
    }

    @Test
    fun word_documents_and_plain_text_are_documents() {
        assertEquals(AttachmentType.DOCUMENT, attachmentTypeOf(null, "contrato.docx"))
        assertEquals(AttachmentType.DOCUMENT, attachmentTypeOf("text/plain", "notas"))
    }

    @Test
    fun unknown_everything_is_other() {
        assertEquals(AttachmentType.OTHER, attachmentTypeOf(null, "invite.ics"))
        assertEquals(AttachmentType.OTHER, attachmentTypeOf("", ""))
        assertEquals(AttachmentType.OTHER, attachmentTypeOf(null, null))
    }

    @Test
    fun case_is_ignored_in_both_signals() {
        assertEquals(AttachmentType.PDF, attachmentTypeOf("Application/PDF", null))
        assertEquals(AttachmentType.IMAGE, attachmentTypeOf(null, "FOTO.JPG"))
    }
}
