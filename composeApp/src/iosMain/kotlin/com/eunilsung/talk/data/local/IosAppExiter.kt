package com.eunilsung.talk.data.local

import com.eunilsung.talk.util.Log
import platform.posix.exit

class IosAppExiter : AppExiter {
    override fun exit() {
        Log.message("[AppExit/iOS] forced exit")
        exit(0)
    }
}
