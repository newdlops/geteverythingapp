package com.getevapp.data

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class Deal(
    val articleId: String,
    val community: String,
    val originUrl: String,
    val thumbnail: String?,
    val title: String,
    val category: String,
    val price: String,
    val currency: String,
    val recommendations: Int,
    val views: Int,
    val writtenAt: String,
    val ended: Boolean,
) {
    // Article IDs can overlap between communities.
    val key: String get() = "$community:$articleId"
    val displayPrice: String get() {
        val amount = price.toBigDecimalOrNull() ?: return price.ifBlank { "가격 확인" }
        val formatted = NumberFormat.getNumberInstance(Locale.KOREA).format(amount)
        return when (currency.uppercase(Locale.ROOT)) {
            "WON", "KRW", "" -> "${formatted}원"
            "USD", "DOLLAR" -> "$$formatted"
            else -> "$formatted $currency"
        }
    }
}

data class Post(
    val id: Long,
    val title: String,
    val content: String,
    val author: String,
    val createdAt: String,
    val views: Int,
    val comments: Int,
    val notice: Boolean,
)

data class Comment(val id: Long, val author: String, val content: String, val createdAt: String, val parent: Long?)
data class Page<T>(val items: List<T>, val cursor: String?)
data class KakaoCredentials(val accessToken: String, val refreshToken: String)
data class Session(
    val id: String,
    val name: String,
    val email: String,
    val accessToken: String,
    val refreshToken: String,
    val kakaoAccessToken: String,
    val kakaoRefreshToken: String,
)

fun safeWebUrl(value: String?): String? {
    val raw = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val url = (if (raw.startsWith("//")) "https:$raw" else raw).toHttpUrlOrNull() ?: return null
    return url.takeIf { it.username.isEmpty() && it.password.isEmpty() }?.toString()
}

fun thumbnailUrl(value: String?): String? {
    val raw = value?.trim()?.takeUnless { it.isEmpty() || it.equals("null", true) || it.contains("None", true) } ?: return null
    return safeWebUrl(if (raw.startsWith("//") || raw.contains(":")) raw else "https://$raw")
}

fun cursorFromUrl(url: String?): String? = url?.toHttpUrlOrNull()?.queryParameter("cursor")?.takeIf { it.isNotBlank() }

fun displayDate(value: String): String = runCatching {
    OffsetDateTime.parse(value).format(DateTimeFormatter.ofPattern("MM.dd HH:mm"))
}.getOrElse { value.take(16).replace('T', ' ') }

internal fun JsonObject.string(name: String): String = get(name)?.takeUnless { it.isJsonNull }?.let {
    if (it.isJsonPrimitive) it.asString else ""
}.orEmpty()
internal fun JsonObject.long(name: String): Long = string(name).toLongOrNull() ?: 0
internal fun JsonObject.int(name: String): Int = string(name).toIntOrNull() ?: 0

object ApiJson {
    fun parse(raw: String): JsonElement = JsonParser.parseString(raw)
    private fun rows(element: JsonElement): List<JsonObject> = when {
        element.isJsonArray -> element.asJsonArray.map { it.asJsonObject }
        element.isJsonObject && element.asJsonObject.has("results") -> rows(element.asJsonObject.get("results"))
        else -> error("목록 응답 형식이 올바르지 않습니다.")
    }

    fun deals(raw: String): Page<Deal> {
        val root = parse(raw)
        return Page(rows(root).map { obj ->
            Deal(
                articleId = obj.string("article_id"), community = obj.string("community_name"),
                originUrl = safeWebUrl(obj.string("origin_url")).orEmpty(), thumbnail = thumbnailUrl(obj.string("thumbnail")),
                title = obj.string("subject"), category = obj.string("category"), price = obj.string("price"),
                currency = obj.string("currency"), recommendations = obj.int("recommend_count"),
                views = obj.int("view_count"), writtenAt = obj.string("write_at"), ended = obj.string("is_end") == "true",
            )
        }, if (root.isJsonObject) cursorFromUrl(root.asJsonObject.string("next")) else null)
    }

    fun post(obj: JsonObject) = Post(
        id = obj.long("id"), title = obj.string("title"), content = obj.string("content"),
        author = obj.string("user_name"), createdAt = obj.string("created_at"), views = obj.int("view_count"),
        comments = obj.int("comment_count"), notice = obj.string("post_type") == "NOTICE",
    )

    fun posts(raw: String): List<Post> = rows(parse(raw)).map(::post).sortedByDescending { it.notice }

    fun comments(raw: String): List<Comment> {
        val result = linkedMapOf<Long, Comment>()
        fun add(obj: JsonObject) {
            val comment = Comment(obj.long("id"), obj.string("author"), obj.string("content"), obj.string("created_at"), obj.string("parent").toLongOrNull())
            result[comment.id] = comment
            obj.get("replies")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { add(it.asJsonObject) }
        }
        rows(parse(raw)).forEach(::add)
        return result.values.toList()
    }

    fun session(raw: String, kakao: KakaoCredentials): Session {
        val obj = parse(raw).asJsonObject
        val access = obj.string("access_token")
        val id = obj.string("id")
        require(access.isNotBlank() && id.isNotBlank()) { "로그인 응답에 사용자 정보가 없습니다." }
        return Session(id, obj.string("user_id"), obj.string("email"), access, obj.string("refresh_token"), kakao.accessToken, kakao.refreshToken)
    }
}
