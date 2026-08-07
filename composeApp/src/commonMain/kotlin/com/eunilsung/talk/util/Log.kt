package com.eunilsung.talk.util

import com.eunilsung.talk.Config

object Log{
    fun message(message: String) {
        if (Config.Log.IS_SHOW_LOG) println("### [Debug] $message")
    }
}