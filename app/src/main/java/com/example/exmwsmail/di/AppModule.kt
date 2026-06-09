package com.example.exmwsmail.di

import android.content.Context
import com.example.exmwsmail.data.local.MailDatabase
import com.example.exmwsmail.data.mail.ImapGateway
import com.example.exmwsmail.data.mail.MailConnectionTester
import com.example.exmwsmail.data.mail.SmtpGateway
import com.example.exmwsmail.data.prefs.CredentialStore
import com.example.exmwsmail.data.repository.AuthRepository
import com.example.exmwsmail.data.repository.MailRepository
import java.io.File

class AppContainer(context: Context) {
    val credentialStore: CredentialStore = CredentialStore(context)
    val connectionTester: MailConnectionTester = MailConnectionTester()
    val imapGateway: ImapGateway = ImapGateway(credentialStore)
    val smtpGateway: SmtpGateway = SmtpGateway(credentialStore)

    private val database: MailDatabase = MailDatabase.create(context)

    val mailRepository: MailRepository = MailRepository(
        gateway = imapGateway,
        smtpGateway = smtpGateway,
        accountDao = database.accountDao(),
        folderDao = database.folderDao(),
        messageDao = database.messageDao(),
        messageBodyDao = database.messageBodyDao(),
        attachmentDao = database.attachmentDao(),
        attachmentsDir = File(context.filesDir, "attachments"),
    )

    val authRepository: AuthRepository = AuthRepository(
        credentialStore = credentialStore,
        connectionTester = connectionTester,
        imapGateway = imapGateway,
        mailRepository = mailRepository,
    )
}
