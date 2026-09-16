package com.getevapp.data

import com.google.gson.JsonObject
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ApiException(val status: Int, message: String) : IOException(message)

class ApiClient(
    baseUrl: String,
    private val session: () -> Session?,
    private val onExpired: (String) -> Unit,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .retryOnConnectionFailure(false)
        .build(),
) {
    private val base = baseUrl.trimEnd('/').plus('/').toHttpUrl()
    // Safe reads may reconnect when the server closes an idle pooled connection.
    // Writes keep automatic retries disabled to avoid duplicate posts/comments.
    private val readClient = client.newBuilder().retryOnConnectionFailure(true).build()

    suspend fun deals(cursor: String? = null): Page<Deal> = ApiJson.deals(request("deals/", cursor = cursor))
    suspend fun posts(): List<Post> = ApiJson.posts(request("posts/"))
    suspend fun post(id: Long): Post = ApiJson.post(ApiJson.parse(request("posts/$id/")).asJsonObject)
    suspend fun comments(id: Long): List<Comment> = ApiJson.comments(request("posts/$id/comments/"))
    suspend fun addPost(title: String, html: String): Post = ApiJson.post(ApiJson.parse(
        request("posts/", "POST", json("title" to title, "content" to html), authenticated = true)
    ).asJsonObject)
    suspend fun addComment(postId: Long, content: String) {
        request("posts/$postId/comments/", "POST", json("content" to content), authenticated = true)
    }
    suspend fun login(kakao: KakaoCredentials): Session = ApiJson.session(
        request("kakaologin/", "POST", credentials(kakao)), kakao
    )
    suspend fun signup(kakao: KakaoCredentials): Session {
        // The existing signup endpoint creates a user but does not return service JWTs.
        request("kakaosignup/", "POST", credentials(kakao))
        return login(kakao)
    }
    suspend fun logout(current: Session) {
        request("logout/", "POST", credentials(KakaoCredentials(current.kakaoAccessToken, current.kakaoRefreshToken)))
    }

    private suspend fun request(path: String, method: String = "GET", body: String? = null, cursor: String? = null, authenticated: Boolean = false): String {
        val token = if (authenticated) session()?.accessToken ?: throw ApiException(401, "로그인이 필요합니다.") else null
        val url = requireNotNull(base.resolve(path)).newBuilder().apply {
            cursor?.let { addQueryParameter("cursor", it) }
        }.build()
        val request = Request.Builder().url(url).header("Accept", "application/json")
            .apply { token?.let { header("Authorization", "Bearer $it") } }
            .method(method, body?.toRequestBody("application/json; charset=utf-8".toMediaType())).build()
        return suspendCancellableCoroutine { continuation ->
            val call = (if (method == "GET") readClient else client).newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (!continuation.isCancelled) continuation.resumeWithException(e)
                }
                override fun onResponse(call: Call, response: Response) {
                    try { response.use {
                        val raw = it.body?.string().orEmpty()
                        if (it.code == 401 && token != null) onExpired(token)
                        if (it.isSuccessful) continuation.resume(raw)
                        else {
                            val message = when (it.code) {
                                401 -> "로그인이 만료되었습니다. 다시 로그인해 주세요."
                                403 -> "이 작업을 할 권한이 없습니다."
                                404 -> "요청한 정보를 찾을 수 없습니다."
                                429 -> "요청이 많습니다. 잠시 후 다시 시도해 주세요."
                                in 500..599 -> "서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요."
                                else -> "요청을 처리하지 못했습니다. 입력 내용을 확인해 주세요."
                            }
                            continuation.resumeWithException(ApiException(it.code, message))
                        }
                    } } catch (e: IOException) {
                        if (!continuation.isCancelled) continuation.resumeWithException(e)
                    }
                }
            })
        }
    }
    private fun credentials(kakao: KakaoCredentials) = json("access_token" to kakao.accessToken, "refresh_token" to kakao.refreshToken)
    private fun json(vararg pairs: Pair<String, String>): String = JsonObject().apply { pairs.forEach { (k, v) -> addProperty(k, v) } }.toString()
}
