package com.example.exmwsmail.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.exmwsmail.MailApplication

fun CreationExtras.appContainer() =
    (this[APPLICATION_KEY] as MailApplication).container
