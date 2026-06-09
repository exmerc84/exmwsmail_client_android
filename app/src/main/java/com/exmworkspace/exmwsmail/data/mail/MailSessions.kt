package com.exmworkspace.exmwsmail.data.mail

import java.util.Properties
import javax.mail.Session

object MailSessions {

    fun imapSession(): Session {
        val props = Properties().apply {
            put("mail.store.protocol", "imaps")
            put("mail.imaps.host", MailConfig.HOST)
            put("mail.imaps.port", MailConfig.IMAP_PORT.toString())
            put("mail.imaps.ssl.enable", "true")
            put("mail.imaps.ssl.checkserveridentity", "true")
            put("mail.imaps.ssl.protocols", "TLSv1.2 TLSv1.3")
            put("mail.imaps.connectiontimeout", MailConfig.CONNECT_TIMEOUT_MS.toString())
            put("mail.imaps.timeout", MailConfig.READ_TIMEOUT_MS.toString())
            put("mail.imaps.writetimeout", MailConfig.READ_TIMEOUT_MS.toString())
            put("mail.imaps.partialfetch", "true")
            put("mail.mime.decodetext.strict", "false")
        }
        return Session.getInstance(props)
    }

    fun smtpSession(): Session {
        val props = Properties().apply {
            put("mail.transport.protocol", "smtps")
            put("mail.smtps.host", MailConfig.HOST)
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
}
