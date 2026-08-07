package com.eunilsung.talk.data.local

import com.eunilsung.talk.util.Log
import platform.Foundation.NSBundle

class IosAppVersionProvider : AppVersionProvider {
    override fun currentVersionCode(): Long {
        val raw = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleVersion") as? String
        val parsed = raw?.toLongOrNull() ?: 0L
        if (parsed == 0L) {
            Log.message("[AppVersion/iOS] CFBundleVersion blank or non-numeric: '$raw'")
        }
        return parsed
    }

    override fun currentVersionName(): String {
        val raw = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String
        if (raw.isNullOrBlank()) {
            Log.message("[AppVersion/iOS] CFBundleShortVersionString blank")
        }
        return raw ?: ""
    }
}
