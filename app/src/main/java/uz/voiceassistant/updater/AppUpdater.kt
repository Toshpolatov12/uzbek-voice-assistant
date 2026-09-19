package uz.voiceassistant.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val versionName: String,
    val downloadUrl: String,
    val releaseNotes: String,
    val isNewer: Boolean
)

object AppUpdater {

    private const val TAG = "AppUpdater"
    private const val GITHUB_LATEST_RELEASE_URL =
        "https://api.github.com/repos/Toshpolatov12/uzbek-voice-assistant/releases/latest"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    suspend fun checkForUpdate(currentVersion: String): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(GITHUB_LATEST_RELEASE_URL)
            .addHeader("Accept", "application/vnd.github.v3+json")
            .addHeader("User-Agent", USER_AGENT)
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("GitHub serveri bilan bog'lanib bo'lmadi (${response.code})"))
            }

            val json = JSONObject(body)
            val tagName = json.optString("tag_name", "").removePrefix("v").trim()
            val releaseNotes = json.optString("body", "Yangi yaxshilanishlar kiritildi.")

            var apkUrl = ""
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk")) {
                        apkUrl = asset.optString("browser_download_url", "")
                        break
                    }
                }
            }

            if (apkUrl.isBlank()) {
                return@withContext Result.failure(Exception("Relizda o'rnatish APK fayli topilmadi."))
            }

            val isNewer = isVersionNewer(tagName, currentVersion)

            Result.success(
                UpdateInfo(
                    versionName = tagName,
                    downloadUrl = apkUrl,
                    releaseNotes = releaseNotes,
                    isNewer = isNewer
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for updates", e)
            Result.failure(e)
        }
    }

    private fun isVersionNewer(remoteVersion: String, currentVersion: String): Boolean {
        try {
            val remoteParts = remoteVersion.split(".").map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
            val currentParts = currentVersion.split(".").map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }

            val maxLen = maxOf(remoteParts.size, currentParts.size)
            for (i in 0 until maxLen) {
                val r = if (i < remoteParts.size) remoteParts[i] else 0
                val c = if (i < currentParts.size) currentParts[i] else 0
                if (r > c) return true
                if (r < c) return false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Version parse error", e)
        }
        return false
    }

    suspend fun downloadAndInstall(
        context: Context,
        downloadUrl: String,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(downloadUrl)
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Accept", "*/*")
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Yuklab olishda xatolik yuz berdi (${response.code})"))
            }

            val body = response.body ?: return@withContext Result.failure(Exception("Fayl bo'sh qaytdi."))
            val contentLength = body.contentLength()

            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val apkFile = File(updatesDir, "update.apk")
            if (apkFile.exists()) apkFile.delete()

            body.byteStream().use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (contentLength > 0) {
                            val progress = totalRead.toFloat() / contentLength.toFloat()
                            withContext(Dispatchers.Main) {
                                onProgress(progress)
                            }
                        }
                    }
                    output.flush()
                }
            }

            withContext(Dispatchers.Main) {
                launchInstaller(context, apkFile)
            }

            Result.success(apkFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading APK", e)
            Result.failure(e)
        }
    }

    fun launchInstaller(context: Context, apkFile: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(settingsIntent)
                return
            }
        }

        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            apkFile
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        context.startActivity(installIntent)
    }

    fun openInBrowser(context: Context, downloadUrl: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Cannot open browser", e)
        }
    }
}
