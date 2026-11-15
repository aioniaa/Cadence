package com.banbiao.app.core

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/**
 * In-app update path against the public GitHub Releases feed. The APK is
 * downloaded into the app cache, then handed to the system package installer.
 * Android still requires the one-time "install unknown apps" confirmation.
 */
object UpdateManager {
    private const val API = "https://api.github.com/repos/aioniaa/Cadence/releases/latest"
    private const val USER_AGENT = "Cadence-Android-Updater"

    data class Update(
        val version: String,
        val apkUrl: String,
        val notes: String,
    )

    sealed interface CheckResult {
        data class Available(val update: Update) : CheckResult
        data object UpToDate : CheckResult
        data class Failed(val message: String) : CheckResult
    }

    suspend fun checkForUpdate(currentVersion: String): CheckResult = withContext(Dispatchers.IO) {
        try {
            val connection = URL(API).openConnection() as HttpURLConnection
            connection.connectTimeout = 12_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", USER_AGENT)
            try {
                val code = connection.responseCode
                if (code != 200) {
                    return@withContext CheckResult.Failed(
                        if (code == 403) "更新服务请求过于频繁，请稍后再试"
                        else "更新服务暂不可用（HTTP $code）",
                    )
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val release = JSONObject(body)
                val tag = release.optString("tag_name").removePrefix("v")
                if (tag.isBlank()) {
                    return@withContext CheckResult.Failed("更新信息格式异常")
                }
                if (!isNewer(tag, currentVersion)) {
                    return@withContext CheckResult.UpToDate
                }
                val assets = release.optJSONArray("assets")
                    ?: return@withContext CheckResult.Failed("新版本没有可用的安装包")
                var apk: String? = null
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val name = asset.optString("name")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apk = asset.optString("browser_download_url").takeIf { it.isNotBlank() }
                        break
                    }
                }
                if (apk == null) {
                    return@withContext CheckResult.Failed("新版本没有可用的安装包")
                }
                CheckResult.Available(
                    Update(
                        version = tag,
                        apkUrl = apk,
                        notes = conciseNotes(release.optString("body")),
                    ),
                )
            } finally {
                connection.disconnect()
            }
        } catch (error: IOException) {
            CheckResult.Failed("网络不可用，请检查网络后重试")
        } catch (error: Exception) {
            CheckResult.Failed("检查更新失败，请稍后重试")
        }
    }

    suspend fun download(
        context: Context,
        update: Update,
        onProgress: (Float) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val target = File(context.cacheDir, "Cadence-${update.version}.apk")
        if (target.exists()) target.delete()
        val connection = URL(update.apkUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", USER_AGENT)
        try {
            if (connection.responseCode !in 200..299) {
                error("下载失败：HTTP ${connection.responseCode}")
            }
            val total = connection.contentLengthLong
            var read = 0L
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer)
                        if (count <= 0) break
                        output.write(buffer, 0, count)
                        read += count
                        if (total > 0) {
                            withContext(Dispatchers.Main.immediate) {
                                onProgress((read.toFloat() / total.toFloat()).coerceIn(0f, 1f))
                            }
                        }
                    }
                    output.flush()
                }
            }
        } catch (error: Exception) {
            target.delete()
            throw error
        } finally {
            connection.disconnect()
        }
        target
    }

    fun install(context: Context, file: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            clipData = ClipData.newRawUri("Cadence update", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun conciseNotes(body: String): String {
        val bullets = body.lineSequence()
            .map { it.trim() }
            .filter { line ->
                line.startsWith("- ") || line.startsWith("* ") ||
                    line.startsWith("• ") || line.startsWith("· ")
            }
            .map { line ->
                line.removePrefix("- ").removePrefix("* ")
                    .removePrefix("• ").removePrefix("· ").trim()
            }
            .filter { it.isNotBlank() }
            .take(8)
            .toList()
        return if (bullets.isEmpty()) {
            "有新版本可用，是否现在更新？"
        } else {
            bullets.joinToString("\n") { "• $it" }.take(600)
        }
    }

    private fun isNewer(candidate: String, current: String): Boolean {
        val a = candidate.split('.').map { it.toIntOrNull() ?: 0 }
        val b = current.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
