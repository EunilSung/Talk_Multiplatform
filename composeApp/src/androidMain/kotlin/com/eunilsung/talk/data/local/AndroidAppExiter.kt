package com.eunilsung.talk.data.local

import com.eunilsung.talk.util.Log
import kotlin.system.exitProcess

class AndroidAppExiter : AppExiter {
    override fun exit() {
        Log.message("[AppExit/Android] forced exit")
        runCatching {
            AndroidActivityHolder.activity?.finishAndRemoveTask()
        }
        exitProcess(0)
    }
}
