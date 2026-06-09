package com.exmworkspace.exmwsmail.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import com.exmworkspace.exmwsmail.MailApplication

fun CreationExtras.appContainer() =
    (this[APPLICATION_KEY] as MailApplication).container
