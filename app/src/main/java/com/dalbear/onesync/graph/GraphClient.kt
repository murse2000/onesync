package com.dalbear.onesync.graph

import java.io.File
import java.io.RandomAccessFile
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class GraphException(val status: Int, body: String) : Exception("Graph $status: $body")

data class DriveInfo(
    val id: String,
    val driveType: String,
    val quotaTotal: Long,
    val quotaUsed: Long,
)

data class DriveItem(
    val id: String,
    val name: String,
    val isFolder: Boolean,
    val size: Long,
    val modified: String?,
    val childCount: Int,
    val quickXorHash: String?,
)

/**
 * Microsoft Graph v1.0 최소 클라이언트.
 * 토큰은 호출자(ViewModel/엔진)가 MSAL에서 받아 넘긴다.
 */
class GraphClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun drive(token: String): DriveInfo = withContext(Dispatchers.IO) {
        val json = get(token, "/me/drive?\$select=id,driveType,quota")
        val quota = json.optJSONObject("quota")
        DriveInfo(
            id = json.getString("id"),
            driveType = json.optString("driveType"),
            quotaTotal = quota?.optLong("total") ?: 0,
            quotaUsed = quota?.optLong("used") ?: 0,
        )
    }

    /** 폴더만. 원격 폴더 선택 화면에서 쓴다. */
    suspend fun folders(token: String, itemId: String? = null): List<DriveItem> =
        withContext(Dispatchers.IO) {
            val path = if (itemId == null) {
                "/me/drive/root/children"
            } else {
                "/me/drive/items/$itemId/children"
            }
            val json = get(
                token,
                "$path?\$select=id,name,folder,size,lastModifiedDateTime&\$top=200",
            )
            parseItems(json.optJSONArray("value")).filter { it.isFolder }
        }

    /** 파일과 폴더 전체. 동기화 스캔에서 쓴다. */
    suspend fun entries(token: String, itemId: String): List<DriveItem> =
        withContext(Dispatchers.IO) {
            val json = get(
                token,
                "/me/drive/items/$itemId/children" +
                    "?\$select=id,name,folder,file,size,lastModifiedDateTime&\$top=200",
            )
            parseItems(json.optJSONArray("value"))
        }

    suspend fun createFolder(token: String, parentId: String, name: String): DriveItem =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("name", name)
                .put("folder", JSONObject())
                .put("@microsoft.graph.conflictBehavior", "rename")
            parseItem(postJson(token, "/me/drive/items/$parentId/children", body))
        }

    /** 4MB 이하 단순 업로드. 같은 이름이 있으면 덮어쓴다. */
    suspend fun uploadSmall(
        token: String,
        parentId: String,
        name: String,
        bytes: ByteArray,
        mimeType: String,
    ): DriveItem = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(name, "UTF-8").replace("+", "%20")
        val request = Request.Builder()
            .url("$BASE/me/drive/items/$parentId:/$encoded:/content")
            .header("Authorization", "Bearer $token")
            .put(bytes.toRequestBody(mimeType.toMediaTypeOrNull()))
            .build()
        parseItem(executeJson(request))
    }

    /**
     * 4MB 초과 파일은 업로드 세션으로 나눠 올린다.
     * 청크 크기는 320KiB의 배수여야 한다(10MiB 사용).
     */
    suspend fun uploadLarge(
        token: String,
        parentId: String,
        name: String,
        file: File,
        onProgress: (Int) -> Unit = {},
    ): DriveItem = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(name, "UTF-8").replace("+", "%20")
        val session = postJson(
            token,
            "/me/drive/items/$parentId:/$encoded:/createUploadSession",
            JSONObject().put(
                "item",
                JSONObject().put("@microsoft.graph.conflictBehavior", "replace"),
            ),
        )
        val uploadUrl = session.getString("uploadUrl")
        val total = file.length()
        var start = 0L

        val raf = RandomAccessFile(file, "r")
        try {
            while (start < total) {
                val end = minOf(start + CHUNK_SIZE - 1, total - 1)
                val length = (end - start + 1).toInt()
                val buffer = ByteArray(length)
                raf.seek(start)
                raf.readFully(buffer)

                val request = Request.Builder()
                    .url(uploadUrl)
                    .header("Content-Range", "bytes $start-$end/$total")
                    .put(buffer.toRequestBody(OCTET_STREAM.toMediaTypeOrNull()))
                    .build()

                val response = http.newCall(request).execute()
                try {
                    when {
                        response.code == 202 -> Unit // 다음 청크
                        response.isSuccessful -> {
                            val json = JSONObject(response.body?.string().orEmpty())
                            return@withContext parseItem(json)
                        }
                        else -> throw GraphException(response.code, response.body?.string().orEmpty())
                    }
                } finally {
                    response.close()
                }

                start = end + 1
                onProgress(((start * 100) / total).toInt())
            }
        } finally {
            raf.close()
        }
        throw GraphException(500, "업로드 세션이 항목을 반환하지 않았습니다")
    }

    suspend fun download(token: String, itemId: String, target: File) =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$BASE/me/drive/items/$itemId/content")
                .header("Authorization", "Bearer $token")
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw GraphException(response.code, response.body?.string().orEmpty())
                }
                val stream = response.body?.byteStream()
                    ?: throw GraphException(response.code, "empty body")
                target.parentFile?.mkdirs()
                target.outputStream().use { output -> stream.use { it.copyTo(output) } }
            }
        }

    suspend fun deleteItem(token: String, itemId: String) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE/me/drive/items/$itemId")
            .header("Authorization", "Bearer $token")
            .delete()
            .build()
        http.newCall(request).execute().use { response ->
            // 이미 없어진 항목은 성공으로 본다.
            if (!response.isSuccessful && response.code != 404) {
                throw GraphException(response.code, response.body?.string().orEmpty())
            }
        }
    }

    private fun parseItem(item: JSONObject): DriveItem {
        val folder = item.optJSONObject("folder")
        val file = item.optJSONObject("file")
        return DriveItem(
            id = item.getString("id"),
            name = item.getString("name"),
            isFolder = folder != null,
            size = item.optLong("size"),
            modified = item.optString("lastModifiedDateTime").takeIf { it.isNotEmpty() },
            childCount = folder?.optInt("childCount") ?: 0,
            quickXorHash = file
                ?.optJSONObject("hashes")
                ?.optString("quickXorHash")
                ?.takeIf { it.isNotEmpty() },
        )
    }

    private fun parseItems(array: JSONArray?): List<DriveItem> {
        if (array == null) return emptyList()
        val result = ArrayList<DriveItem>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            result += parseItem(item)
        }
        return result
    }

    private fun get(token: String, path: String): JSONObject {
        val request = Request.Builder()
            .url("$BASE$path")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .get()
            .build()
        return executeJson(request)
    }

    private fun postJson(token: String, path: String, body: JSONObject): JSONObject {
        val request = Request.Builder()
            .url("$BASE$path")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE.toMediaTypeOrNull()))
            .build()
        return executeJson(request)
    }

    private fun executeJson(request: Request): JSONObject {
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw GraphException(response.code, body)
            return JSONObject(body)
        }
    }

    private companion object {
        const val BASE = "https://graph.microsoft.com/v1.0"
        const val JSON_MEDIA_TYPE = "application/json"
        const val OCTET_STREAM = "application/octet-stream"

        /** OneDrive 요구사항: 320KiB의 배수. */
        const val CHUNK_SIZE = 10L * 1024 * 1024
    }
}
