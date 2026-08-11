package com.exmworkspace.exmwsmail.ui

import android.content.Context
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import com.exmworkspace.exmwsmail.MailApplication
import com.exmworkspace.exmwsmail.di.AppContainer

fun CreationExtras.appContainer() =
    (this[APPLICATION_KEY] as MailApplication).container

/** For composables that need a singleton directly (e.g. the shared authenticated HTTP client). */
fun Context.mailContainer(): AppContainer =
    (applicationContext as MailApplication).container
