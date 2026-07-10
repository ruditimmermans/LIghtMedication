package com.light.medication.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.light.medication.BuildConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    val assets: List<GitHubAsset>
)

@Serializable
data class GitHubAsset(
    @SerialName("browser_download_url") val downloadUrl: String,
    val name: String
)

class AppUpdater(private val context: Context) {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private val repoUrl = "https://api.github.com/repos/ruditimmermans/LightMedication/releases/latest"

    suspend fun checkForUpdate(): GitHubRelease? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(repoUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val release = json.decodeFromString<GitHubRelease>(body)
                
                val currentVersion = BuildConfig.VERSION_NAME
                val latestVersion = release.tagName.removePrefix("v")
                
                if (isNewerVersion(currentVersion, latestVersion)) {
                    release
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun isNewerVersion(current: String, latest: String): Boolean {
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        
        for (i in 0 until maxOf(currentParts.size, latestParts.size)) {
            val curr = currentParts.getOrElse(i) { 0 }
            val late = latestParts.getOrElse(i) { 0 }
            if (late > curr) return true
            if (late < curr) return false
        }
        return false
    }

    suspend fun downloadAndInstall(release: GitHubRelease) = withContext(Dispatchers.IO) {
        val asset = release.assets.find { it.name.endsWith(".apk") } ?: return@withContext
        val request = Request.Builder().url(asset.downloadUrl).build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext
            
            val apkFile = File(context.externalCacheDir, "update.apk")
            FileOutputStream(apkFile).use { output ->
                response.body?.byteStream()?.copyTo(output)
            }
            
            withContext(Dispatchers.Main) {
                installApk(apkFile)
            }
        }
    }

    private fun installApk(file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
