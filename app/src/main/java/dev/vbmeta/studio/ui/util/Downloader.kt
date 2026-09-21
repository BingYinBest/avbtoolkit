package dev.vbmeta.studio.ui.util

import dev.vbmeta.studio.templateApp
import okhttp3.Request
import org.json.JSONObject

/**
 * 查询 GitHub Releases 最新版。资产命名规则见 app/build.gradle.kts archivesName：
 * VbmetaStudio_<versionName>_<versionCode>[-<variant>].apk
 */
fun checkNewVersion(): LatestVersionInfo {
    if (!isNetworkAvailable(templateApp)) return LatestVersionInfo()
    val url = "https://api.github.com/repos/BingYinBest/avbtoolkit/releases/latest"
    val defaultValue = LatestVersionInfo()
    runCatching {
        templateApp.okhttpClient.newCall(Request.Builder().url(url).build()).execute()
            .use { response ->
                if (!response.isSuccessful) {
                    return defaultValue
                }
                val json = JSONObject(response.body.string())
                val changelog = json.optString("body")
                val assets = json.getJSONArray("assets")
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.getString("name")
                    if (!name.endsWith(".apk")) continue
                    val match = Regex("_([0-9]+)(?:-|\\.apk)").find(name) ?: continue
                    val versionCode = match.groupValues[1].toInt()
                    val downloadUrl = asset.getString("browser_download_url")
                    return LatestVersionInfo(
                        versionCode = versionCode,
                        downloadUrl = downloadUrl,
                        changelog = changelog,
                    )
                }
            }
    }
    return defaultValue
}