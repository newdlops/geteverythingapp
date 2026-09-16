package com.getevapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.getevapp.data.displayDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForumScreen(vm: AppViewModel) {
    val forum = vm.forum
    Box(Modifier.fillMaxSize()) {
        when {
            forum.loading && forum.items.isEmpty() -> LoadingContent()
            forum.error != null && forum.items.isEmpty() -> NoticeScreen("게시글을 불러오지 못했어요", forum.error, action = "다시 시도", onAction = vm::refreshPosts)
            forum.loaded && forum.items.isEmpty() -> NoticeScreen("첫 게시글을 남겨보세요", "할인 정보와 구매 경험을 나누는 공간입니다.")
            else -> PullToRefreshBox(isRefreshing = forum.loading, onRefresh = vm::refreshPosts) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
                    if (forum.error != null) item { ErrorBanner(forum.error, vm::refreshPosts) }
                    items(forum.items, key = { it.id }) { post ->
                        Surface(color = if (post.notice) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
                            Column(Modifier.fillMaxWidth().clickable { vm.showPost(post) }.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (post.notice) BadgeText("공지")
                                Text(post.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                Text(post.author.ifBlank { "작성자" }, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${displayDate(post.createdAt)} · 댓글 ${post.comments} · 조회 ${post.views}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = vm::compose, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("글쓰기") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

@Composable
fun PostDetailScreen(vm: AppViewModel, loggedIn: Boolean) {
    val detail = vm.detail
    if (detail.loading && detail.post == null) { LoadingContent(); return }
    if (detail.post == null) { NoticeScreen("게시글을 불러오지 못했어요", detail.error.orEmpty(), action = "다시 시도", onAction = vm::refreshDetail); return }
    val post = detail.post
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            if (post.notice) BadgeText("공지")
            Text(post.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Text("${post.author} · ${displayDate(post.createdAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("조회 ${post.views}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { NativeHtml(post.content, vm::message) }
        item { HorizontalDivider() }
        item { Text("댓글 ${detail.comments.size}", style = MaterialTheme.typography.titleMedium) }
        if (detail.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (detail.error != null) item { ErrorBanner(detail.error, vm::refreshDetail) }
        if (!detail.loading && detail.error == null && detail.comments.isEmpty()) item {
            Text("첫 댓글을 남겨보세요.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(detail.comments, key = { it.id }) { comment ->
            Column(Modifier.fillMaxWidth().padding(start = if (comment.parent != null) 16.dp else 0.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${if (comment.parent != null) "답글 · " else ""}사용자 ${comment.author} · ${displayDate(comment.createdAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(comment.content, style = MaterialTheme.typography.bodyMedium)
            }
            HorizontalDivider(Modifier.padding(top = 12.dp))
        }
        item {
            if (!loggedIn) OutlinedButton(onClick = vm::login, modifier = Modifier.fillMaxWidth()) { Text("로그인하고 댓글 쓰기") }
            else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(vm.commentDraft, { vm.commentDraft = it }, label = { Text("댓글") }, minLines = 2,
                    modifier = Modifier.fillMaxWidth(), enabled = !vm.submitting)
                Button(onClick = vm::submitComment, enabled = !vm.submitting && vm.commentDraft.isNotBlank(), modifier = Modifier.align(Alignment.End)) {
                    Text(if (vm.submitting) "등록 중…" else "댓글 등록")
                }
            }
        }
    }
}
