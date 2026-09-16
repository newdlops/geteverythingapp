package com.getevapp

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.getevapp.data.LocalStore
import com.getevapp.data.Session
import com.getevapp.ui.NativeEditor
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeStorageEditorTest {
    @Test fun sessionIsEncryptedRestoredAndOld401CannotLogOutANewerSession() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "storage_test_${System.nanoTime()}"
        try {
            val store = LocalStore(context, name)
            val current = Session("1", "name", "email", "test-access-token", "refresh", "kakao", "kakao-refresh")
            store.saveSession(current)
            val raw = context.getSharedPreferences(name, Context.MODE_PRIVATE).getString("session", "").orEmpty()
            assertFalse(raw.contains(current.accessToken))
            assertEquals(current, LocalStore(context, name).session.value)
            store.clearSession("older-token")
            assertEquals(current, store.session.value)
            store.clearSession(current.accessToken)
            assertNull(LocalStore(context, name).session.value)
        } finally { context.deleteSharedPreferences(name) }
    }

    @Test fun editorEmitsFormattedHtmlAndPreservesTextOnRestore() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            var html = ""
            var hasContent = false
            val editor = NativeEditor(instrumentation.targetContext) { value, content -> html = value; hasContent = content }
            editor.setText("네이티브 게시글")
            editor.setSelection(0, 4)
            assertTrue(editor.format("bold"))
            assertTrue(html.contains("<b>"))
            assertTrue(hasContent)
            val restored = NativeEditor(instrumentation.targetContext) { _, _ -> }
            restored.restoreHtml(html)
            assertTrue(restored.text.toString().contains("네이티브 게시글"))
        }
    }
}
