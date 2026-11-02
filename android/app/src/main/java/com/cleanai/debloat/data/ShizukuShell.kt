package com.cleanai.debloat.data

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/** Runs shell commands as the ADB "shell" user through Shizuku. */
object ShizukuShell {
    enum class State { NOT_RUNNING, NO_PERMISSION, READY }

    const val PERMISSION_REQUEST = 4201
    const val SHIZUKU_PKG = "moe.shizuku.privileged.api"

    data class Result(val code: Int, val output: String) {
        val ok get() = code == 0 && !output.contains("Failure", ignoreCase = true) &&
            !output.contains("Exception")
    }

    fun state(): State = try {
        when {
            !Shizuku.pingBinder() || Shizuku.isPreV11() -> State.NOT_RUNNING
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> State.READY
            else -> State.NO_PERMISSION
        }
    } catch (e: Throwable) {
        State.NOT_RUNNING
    }

    fun isInstalled(ctx: Context) =
        runCatching { ctx.packageManager.getPackageInfo(SHIZUKU_PKG, 0) }.isSuccess

    fun requestPermission() {
        runCatching { Shizuku.requestPermission(PERMISSION_REQUEST) }
    }

    fun run(cmd: String): Result = try {
        // Shizuku.newProcess is hidden in API 13 but still the simplest way to run a shell command.
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java,
        )
        method.isAccessible = true
        val process = method.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
        val out = process.inputStream.bufferedReader().readText()
        val err = process.errorStream.bufferedReader().readText()
        Result(process.waitFor(), (out + err).trim())
    } catch (e: Throwable) {
        Result(-1, e.cause?.message ?: e.message ?: "Shizuku error")
    }

    // Callers must check Guard.blockReason first; the package name is validated there.
    fun remove(pkg: String) = run("pm uninstall -k --user 0 $pkg")
    fun disable(pkg: String) = run("pm disable-user --user 0 $pkg")
    fun enable(pkg: String) = run("pm enable --user 0 $pkg")
    fun reinstall(pkg: String): Result {
        val r = run("cmd package install-existing --user 0 $pkg")
        return if (r.ok) r else run("pm install-existing --user 0 $pkg")
    }
}
