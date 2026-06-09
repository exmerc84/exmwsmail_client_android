package com.exmworkspace.exmwsmail.data.mail

import com.exmworkspace.exmwsmail.data.prefs.CredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.Properties
import javax.mail.Message
import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

import javax.activation.DataHandler
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMultipart
import javax.mail.util.ByteArrayDataSource

data class OutgoingMessage(
    val to: List<String>,
    val cc: List<String> = emptyList(),
    val bcc: List<String> = emptyList(),
    val subject: String,
    val body: String,
    val isHtml: Boolean = false,
    val inReplyTo: String? = null,
    val references: List<String> = emptyList(),
    val attachments: List<FileAttachment> = emptyList(),
)

data class FileAttachment(
    val name: String,
    val mimeType: String,
    val bytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FileAttachment) return false
        if (name != other.name) return false
        if (mimeType != other.mimeType) return false
        if (!bytes.contentEquals(other.bytes)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}

class SmtpGateway(
    private val credentialStore: CredentialStore,
) {
    suspend fun send(message: OutgoingMessage): MimeMessage = withContext(Dispatchers.IO) {
        val creds = credentialStore.load() ?: error("No hay credenciales guardadas")
        val displayName = credentialStore.displayName.value
        val session = buildSession()
        val fromAddress = if (displayName.isNotBlank()) {
            InternetAddress(creds.email, displayName, "UTF-8")
        } else {
            InternetAddress(creds.email)
        }
        val mime = MimeMessage(session).apply {
            setFrom(fromAddress)
            setRecipients(Message.RecipientType.TO, parseAddresses(message.to))
            if (message.cc.isNotEmpty()) {
                setRecipients(Message.RecipientType.CC, parseAddresses(message.cc))
            }
            if (message.bcc.isNotEmpty()) {
                setRecipients(Message.RecipientType.BCC, parseAddresses(message.bcc))
            }
            setSubject(message.subject, "UTF-8")
            sentDate = Date()
            if (message.inReplyTo != null) {
                setHeader("In-Reply-To", message.inReplyTo)
            }
            if (message.references.isNotEmpty()) {
                setHeader("References", message.references.joinToString(" "))
            }

            if (message.attachments.isEmpty()) {
                if (message.isHtml) {
                    setContent(message.body, "text/html; charset=UTF-8")
                } else {
                    setText(message.body, "UTF-8", "plain")
                }
            } else {
                val multipart = MimeMultipart()
                val bodyPart = MimeBodyPart().apply {
                    if (message.isHtml) {
                        setContent(message.body, "text/html; charset=UTF-8")
                    } else {
                        setText(message.body, "UTF-8", "plain")
                    }
                }
                multipart.addBodyPart(bodyPart)

                for (att in message.attachments) {
                    val attPart = MimeBodyPart().apply {
                        val source = ByteArrayDataSource(att.bytes, att.mimeType)
                        dataHandler = DataHandler(source)
                        fileName = att.name
                    }
                    multipart.addBodyPart(attPart)
                }
                setContent(multipart)
            }
            saveChanges()
        }
        val transport = session.getTransport("smtps")
        try {
            transport.connect(MailConfig.SMTP_HOST, MailConfig.SMTP_PORT, creds.email, creds.password)
            transport.sendMessage(mime, mime.allRecipients)
        } finally {
            runCatching { transport.close() }
        }
        mime
    }

    private fun buildSession(): Session {
        val props = Properties().apply {
            put("mail.transport.protocol", "smtps")
            put("mail.transport.protocol.rfc822", "smtps")
            put("mail.smtps.host", MailConfig.SMTP_HOST)
            put("mail.smtps.port", MailConfig.SMTP_PORT.toString())
            put("mail.smtps.auth", "true")
            put("mail.smtps.ssl.enable", "true")
            put("mail.smtps.ssl.checkserveridentity", "true")
            put("mail.smtps.ssl.protocols", "TLSv1.2 TLSv1.3")
            put("mail.smtps.connectiontimeout", MailConfig.CONNECT_TIMEOUT_MS.toString())
            put("mail.smtps.timeout", MailConfig.READ_TIMEOUT_MS.toString())
            put("mail.smtps.writetimeout", MailConfig.READ_TIMEOUT_MS.toString())
        }
        return Session.getInstance(props)
    }

    private fun parseAddresses(list: List<String>): Array<InternetAddress> =
        list.flatMap { entry ->
            InternetAddress.parse(entry, false).toList()
        }.toTypedArray()
}
