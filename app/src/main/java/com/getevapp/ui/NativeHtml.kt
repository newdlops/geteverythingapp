package com.getevapp.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.text.Html
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.view.View
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import coil.imageLoader
import coil.request.Disposable
import coil.request.ImageRequest
import com.getevapp.data.safeWebUrl

@Composable
fun NativeHtml(html: String, onError: (String) -> Unit) {
    val context = LocalContext.current
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
    var width by remember { mutableIntStateOf(0) }
    val requests = remember(html, width) { mutableListOf<Disposable>() }
    DisposableEffect(requests) { onDispose { requests.forEach { it.dispose() }; requests.clear() } }
    AndroidView(
        modifier = Modifier.fillMaxWidth().onSizeChanged { width = it.width },
        factory = { TextView(it).apply { textSize = 16f; setLineSpacing(8f, 1f); movementMethod = LinkMovementMethod.getInstance() } },
        update = { view ->
            view.setTextColor(textColor)
            view.setLinkTextColor(linkColor)
            val key = html to width
            if (view.tag != key && width > 0) {
                view.tag = key
                val parsed = SpannableStringBuilder(Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT, { source ->
                    val drawable = HtmlImage(context.resources.displayMetrics.scaledDensity)
                    drawable.setBounds(0, 0, width, (56 * context.resources.displayMetrics.density).toInt())
                    val data: Any? = if (source.startsWith("data:image/") && source.length < 700_000) {
                        runCatching { android.util.Base64.decode(source.substringAfter(','), android.util.Base64.DEFAULT) }.getOrNull()
                    } else safeWebUrl(source)
                    if (data != null) requests += context.imageLoader.enqueue(ImageRequest.Builder(context).data(data).size(width).target(
                        onSuccess = { image ->
                            val height = (width.toFloat() * image.intrinsicHeight.coerceAtLeast(1) / image.intrinsicWidth.coerceAtLeast(1)).toInt().coerceIn(1, width * 3)
                            drawable.setBounds(0, 0, width, height)
                            drawable.image = image
                            view.text = view.text
                        },
                        onError = { drawable.failed = true; view.invalidate() },
                    ).build())
                    else drawable.failed = true
                    drawable
                }, null))
                parsed.getSpans(0, parsed.length, URLSpan::class.java).forEach { link ->
                    val start = parsed.getSpanStart(link)
                    val end = parsed.getSpanEnd(link)
                    parsed.removeSpan(link)
                    if (safeWebUrl(link.url) != null) parsed.setSpan(object : ClickableSpan() {
                        override fun onClick(widget: View) { openExternal(context, link.url, onError) }
                    }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                view.text = parsed
            }
        },
    )
}

private class HtmlImage(private val textScale: Float) : Drawable() {
    var image: Drawable? = null
    var failed: Boolean = false
    override fun draw(canvas: Canvas) {
        val loaded = image
        if (loaded != null) { loaded.bounds = bounds; loaded.draw(canvas) }
        else {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = 0xFFF0F1F5.toInt()
            canvas.drawRect(bounds, paint)
            paint.color = 0xFF5E5E6D.toInt()
            paint.textSize = 14 * textScale
            canvas.drawText(if (failed) "이미지를 불러올 수 없습니다." else "이미지 불러오는 중…", bounds.left + 12f, bounds.centerY().toFloat(), paint)
        }
    }
    override fun setAlpha(alpha: Int) { image?.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { image?.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
