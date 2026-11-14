package com.apkup.util

import android.content.Context

class Stringer(val context: Context) {

    @Suppress("UNCHECKED_CAST", "SENSELESS_COMPARISON")
    fun get(id: Int): String = (context.getString(id) as String?) ?: context.resources.getString(id)

    @Suppress("UNCHECKED_CAST", "SENSELESS_COMPARISON")
    fun get(id: Int, vararg params: Any?): String =
        (context.getString(id, *params) as String?) ?: context.resources.getString(id, *params)

}
