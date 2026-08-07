package com.eunilsung.talk.data.local

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.eunilsung.talk.util.Log

class AndroidAppVersionProvider(private val context: Context) : AppVersionProvider {
    override fun currentVersionCode(): Long {
        return runCatching {
            val pkgInfo: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pkgInfo.versionCode.toLong()
            }
        }.getOrElse {
            Log.message("[AppVersion/Android] failed: ${it.message}")
            0L
        }
    }

    override fun currentVersionName(): String {
        return runCatching {
            val pkgInfo: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            pkgInfo.versionName ?: ""
        }.getOrElse {
            Log.message("[AppVersion/Android] versionName failed: ${it.message}")
            ""
        }
    }
}
