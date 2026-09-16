package com.getevapp.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.text.Editable
import android.text.Html
import android.text.InputType
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.*
import android.util.Base64
import android.view.Gravity
import androidx.appcompat.widget.AppCompatEditText
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@Composable
fun ComposePostScreen(vm: AppViewModel, onCancel: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var editor by remember { mutableStateOf<NativeEditor?>(null) }
    var importing by remember { mutableStateOf(false) }
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            importing = true
            try {
                val photo = withContext(Dispatchers.IO) { readPhoto(context, uri) }
                editor?.insertPhoto(photo.first, photo.second)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                vm.message("이미지를 불러오지 못했습니다. 다른 이미지를 선택해 주세요.")
            } finally { importing = false }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(vm.draftTitle, { vm.draftTitle = it.take(200) }, label = { Text("제목") },
            supportingText = { Text("${vm.draftTitle.length}/200") }, modifier = Modifier.fillMaxWidth(), enabled = !vm.submitting, maxLines = 3)
        Text("내용", style = MaterialTheme.typography.titleSmall)
        Text("글자를 선택한 뒤 서식을 적용하세요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surface) {
            Column {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    val actions = listOf(
                        Triple(Icons.Outlined.FormatBold, "굵게", "bold"), Triple(Icons.Outlined.FormatItalic, "기울임", "italic"),
                        Triple(Icons.Outlined.FormatUnderlined, "밑줄", "underline"), Triple(Icons.Outlined.StrikethroughS, "취소선", "strike"),
                        Triple(Icons.Outlined.FormatColorText, "강조 색상", "color"),
                    )
                    actions.forEach { (icon, label, type) ->
                        IconButton(onClick = { if (editor?.format(type) == false) vm.message("서식을 적용할 글자를 먼저 선택해 주세요.") }, enabled = !vm.submitting && !importing) { Icon(icon, label) }
                    }
                    IconButton(onClick = { picker.launch("image/*") }, enabled = !vm.submitting && !importing) { Icon(Icons.Outlined.AddPhotoAlternate, "이미지 추가") }
                }
                HorizontalDivider()
                AndroidView(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp),
                    factory = { ctx -> NativeEditor(ctx) { html, hasContent -> vm.draftHtml = html; vm.draftHasContent = hasContent }.apply {
                        restoreHtml(vm.draftHtml)
                        editor = this
                    } },
                    update = { it.isEnabled = !vm.submitting; it.setTextColor(textColor); it.setHintTextColor(hintColor) },
                )
            }
        }
        if (importing || vm.submitting) LinearProgressIndicator(Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f), enabled = !vm.submitting && !importing) { Text("취소") }
            Button(onClick = vm::submitPost, modifier = Modifier.weight(1f), enabled = !vm.submitting && !importing && vm.draftTitle.isNotBlank() && vm.draftHasContent) {
                Text(if (vm.submitting) "등록 중…" else "등록")
            }
        }
    }
}

internal class NativeEditor @JvmOverloads constructor(context: Context, private val changed: (String, Boolean) -> Unit = { _, _ -> }) : AppCompatEditText(context) {
    init {
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        gravity = Gravity.TOP or Gravity.START
        hint = "내용을 입력하세요"
        contentDescription = "게시글 내용"
        textSize = 16f
        minLines = 10
        maxLines = 20
        background = null
        val space = (16 * resources.displayMetrics.density).toInt()
        setPadding(space, space, space, space)
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) { emit() }
        })
    }
    private fun emit() {
        val value = text ?: return
        changed(Html.toHtml(value, Html.TO_HTML_PARAGRAPH_LINES_INDIVIDUAL), value.toString().isNotBlank())
    }
    fun restoreHtml(value: String) {
        if (value.isBlank()) return
        setText(Html.fromHtml(value, Html.FROM_HTML_MODE_COMPACT, { source ->
            if (!source.startsWith("data:image/") || source.length > 700_000) null
            else runCatching {
                val bytes = Base64.decode(source.substringAfter(','), Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                photoDrawable(bitmap)
            }.getOrNull()
        }, null))
    }
    fun format(type: String): Boolean {
        val start = selectionStart
        val end = selectionEnd
        if (start < 0 || start >= end) return false
        val value = text ?: return false
        val span: CharacterStyle = when (type) {
            "bold" -> StyleSpan(Typeface.BOLD)
            "italic" -> StyleSpan(Typeface.ITALIC)
            "underline" -> UnderlineSpan()
            "strike" -> StrikethroughSpan()
            else -> ForegroundColorSpan(0xFFB3261E.toInt())
        }
        val existing = value.getSpans(start, end, span.javaClass).filter {
            value.getSpanStart(it) <= start && value.getSpanEnd(it) >= end && (it !is StyleSpan || span !is StyleSpan || it.style == span.style)
        }
        if (existing.isEmpty()) value.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        else existing.forEach(value::removeSpan)
        emit()
        return true
    }
    private fun photoDrawable(bitmap: Bitmap): BitmapDrawable = BitmapDrawable(resources, bitmap).apply {
        val available = (resources.displayMetrics.widthPixels - 64 * resources.displayMetrics.density).toInt().coerceAtLeast(100)
        val w = bitmap.width.coerceAtMost(available)
        setBounds(0, 0, w, (w.toFloat() * bitmap.height / bitmap.width).toInt())
    }
    fun insertPhoto(bitmap: Bitmap, source: String) {
        val value = text ?: return
        val position = selectionStart.coerceIn(0, value.length)
        value.insert(position, "\n\uFFFC\n")
        value.setSpan(ImageSpan(photoDrawable(bitmap), source, ImageSpan.ALIGN_BASELINE), position + 1, position + 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        setSelection(position + 3)
        emit()
    }
}

private fun readPhoto(context: Context, uri: Uri): Pair<Bitmap, String> {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
    require(bounds.outWidth > 0 && bounds.outHeight > 0)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1280) sample *= 2
    val bitmap = context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
        ?: error("Invalid image")
    val out = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 75, out)
    require(out.size() <= 500_000) { "Image too large" }
    return bitmap to "data:image/jpeg;base64,${Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)}"
}
