package com.exmworkspace.exmwsmail.di

import android.content.Context
import com.exmworkspace.exmwsmail.data.local.MailDatabase
import com.exmworkspace.exmwsmail.data.mail.ImapGateway
import com.exmworkspace.exmwsmail.data.mail.MailConnectionTester
import com.exmworkspace.exmwsmail.data.mail.SmtpGateway
import com.exmworkspace.exmwsmail.data.prefs.CredentialStore
import com.exmworkspace.exmwsmail.data.repository.AuthRepository
import com.exmworkspace.exmwsmail.data.repository.MailRepository
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
