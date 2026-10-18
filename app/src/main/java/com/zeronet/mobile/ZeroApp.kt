package com.zeronet.mobile

import android.app.Application
import android.os.Process
import com.zeronet.mobile.core.LocalProxyAuth
import com.zeronet.mobile.service.Engine

/**
 * Two processes run this class: the UI (main) process and the :vpn process.
 * Only :vpn touches native code. The UI process only reads the local proxy's
 * password here (one small file), so its cold start stays as short as
 * possible; both processes make requests through that proxy and need it.
 */
class ZeroApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Before the engine builds a config that carries the password.
        LocalProxyAuth.init(this)
        dropSharingLeftovers()
        if (isVpnProcess()) Engine.init(this)
    }

    /**
     * Nothing about a connect is shared any more. Earlier versions kept a
     * ranking cache, a remembered network name, a daily random value and a
     * queue of unsent mode totals on the phone; remove them, so no old
     * identifier outlives the feature. Safe to run on every start.
     */
    private fun dropSharingLeftovers() {
        deleteSharedPreferences("crowd")
        deleteSharedPreferences("mode-stats")
        java.io.File(cacheDir, "crowd").deleteRecursively()
    }

    private fun isVpnProcess(): Boolean {
        val name = if (android.os.Build.VERSION.SDK_INT >= 28) {
            getProcessName()
        } else {
            runCatching { java.io.File("/proc/${Process.myPid()}/cmdline").readText().trim('\u0000', ' ', '\n') }.getOrDefault("")
        }
        return name.endsWith(":vpn")
    }
}
