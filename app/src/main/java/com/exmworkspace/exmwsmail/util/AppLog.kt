package com.exmworkspace.exmwsmail.util

import android.util.Log
import com.exmworkspace.exmwsmail.BuildConfig

/**
 * Thin logging facade. Delegates to [android.util.Log] only in debug builds so
 * release APKs stay quiet (no leaked diagnostics, no logcat noise).
 *
 * Call sites that previously used `android.util.Log` can switch by importing this
 * object, or by aliasing the import: `import ...util.AppLog as Log`.
 */
object AppLog {
    fun d(tag: String, msg: String) {
        if (BuildConfig.DEBUG) Log.d(tag, msg)
    }

    fun i(tag: String, msg: String) {
        if (BuildConfig.DEBUG) Log.i(tag, msg)
    }

    fun w(tag: String, msg: String) {
        if (BuildConfig.DEBUG) Log.w(tag, msg)
    }

    fun w(tag: String, msg: String, t: Throwable) {
        if (BuildConfig.DEBUG) Log.w(tag, msg, t)
    }

    fun e(tag: String, msg: String) {
        if (BuildConfig.DEBUG) Log.e(tag, msg)
    }

    fun e(tag: String, msg: String, t: Throwable) {
        if (BuildConfig.DEBUG) Log.e(tag, msg, t)
    }
}
