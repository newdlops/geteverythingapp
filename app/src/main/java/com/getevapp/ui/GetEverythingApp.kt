package com.getevapp.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.getevapp.data.safeWebUrl

private data class Tab(val label: String, val icon: ImageVector)
private val tabs = listOf(
    Tab("메인화면", Icons.Outlined.Home), Tab("포럼", Icons.AutoMirrored.Outlined.Chat),
    Tab("관심상품", Icons.Outlined.FavoriteBorder), Tab("알림설정", Icons.Outlined.NotificationsNone),
    Tab("내계정", Icons.Outlined.PersonOutline),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GetEverythingApp(vm: AppViewModel) {
    val session by vm.store.session.collectAsStateWithLifecycle()
    val favorites by vm.store.favorites.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val stateHolder = rememberSaveableStateHolder()
    val useRail = LocalConfiguration.current.screenWidthDp >= 600
    val largeText = LocalDensity.current.fontScale > 1.3f
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val onBack = {
        if (vm.route == "compose" && (vm.draftTitle.isNotBlank() || vm.draftHasContent)) confirmDiscard = true
        else vm.back()
    }
    BackHandler(enabled = vm.route != "tabs" || vm.tab != 0) {
        if (!vm.submitting && !vm.authBusy) onBack()
    }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(when (vm.route) {
                    "detail" -> "게시글"
                    "compose" -> "글쓰기"
                    "login" -> "로그인"
                    "signup" -> "회원가입"
                    else -> tabs[vm.tab].label
                }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (vm.route != "tabs") IconButton(onClick = onBack, enabled = !vm.submitting && !vm.authBusy) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "뒤로가기")
                    }
                },
                actions = {
                    if (vm.route == "tabs" && vm.tab <= 1) IconButton(
                        onClick = { if (vm.tab == 0) vm.refreshDeals() else vm.refreshPosts() },
                        enabled = if (vm.tab == 0) !vm.feed.loading else !vm.forum.loading,
                    ) { Icon(Icons.Outlined.Refresh, "새로고침") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Brand, titleContentColor = Color.White, actionIconContentColor = Color.White, navigationIconContentColor = Color.White),
            )
        },
        bottomBar = {
            if (vm.route == "tabs" && !useRail) NavigationBar(containerColor = Brand, contentColor = Color.White) {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = vm.tab == index,
                        onClick = { vm.selectTab(index) },
                        icon = { Icon(tab.icon, null) },
                        label = { Text(if (largeText) listOf("홈", "포럼", "관심", "알림", "계정")[index] else tab.label, maxLines = 1) },
                        modifier = Modifier.semantics { contentDescription = tab.label },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White, selectedTextColor = Color.White,
                            indicatorColor = Color(0xFF4A13B9), unselectedIconColor = Color.White, unselectedTextColor = Color.White,
                        ),
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { insets ->
        Row(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets).imePadding()) {
            if (useRail && vm.route == "tabs") NavigationRail(containerColor = Brand, contentColor = Color.White, modifier = Modifier.fillMaxHeight().verticalScroll(rememberScrollState())) {
                tabs.forEachIndexed { index, tab ->
                    NavigationRailItem(selected = vm.tab == index, onClick = { vm.selectTab(index) },
                        icon = { Icon(tab.icon, null) }, label = { Text(tab.label) },
                        colors = NavigationRailItemDefaults.colors(selectedIconColor = Color.White, selectedTextColor = Color.White,
                            unselectedIconColor = Color.White, unselectedTextColor = Color.White, indicatorColor = Color(0xFF4A13B9)))
                    Spacer(Modifier.height(8.dp))
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = 720.dp).fillMaxSize()) {
                when (vm.route) {
                    "login", "signup" -> AuthScreen(vm, vm.route == "signup")
                    "detail" -> PostDetailScreen(vm, session != null)
                    "compose" -> ComposePostScreen(vm, onBack)
                    else -> stateHolder.SaveableStateProvider(vm.tab) {
                        when (vm.tab) {
                            0 -> DealsScreen(vm, favorites.map { it.key }.toSet())
                            1 -> ForumScreen(vm)
                            2 -> FavoritesScreen(favorites, session != null, vm::login, vm::favorite, vm::message)
                            3 -> NoticeScreen("알림 기능을 준비하고 있어요", "새로운 핫딜 알림은 아직 지원하지 않습니다. 메인화면에서 최신 상품을 확인해 주세요.", Icons.Outlined.NotificationsNone)
                            4 -> AccountScreen(vm, session)
                        }
                    }
                }
            }
            }
        }
    }
    if (confirmDiscard) AlertDialog(
        onDismissRequest = { confirmDiscard = false },
        title = { Text("작성을 취소할까요?") },
        text = { Text("작성 중인 제목과 내용이 삭제됩니다.") },
        confirmButton = { TextButton(onClick = { confirmDiscard = false; vm.clearDraft(); vm.back() }) { Text("작성 취소") } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("계속 작성") } },
    )
}

@Composable
fun NoticeScreen(title: String, message: String, icon: ImageVector = Icons.Outlined.Info, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) { Spacer(Modifier.height(24.dp)); Button(onClick = onAction) { Text(action) } }
    }
}

@Composable
fun ErrorBanner(message: String, retry: (() -> Unit)? = null) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
            if (retry != null) TextButton(onClick = retry) { Text("다시 시도") }
        }
    }
}

@Composable
fun LoadingContent() { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }

fun openExternal(context: Context, value: String, onError: (String) -> Unit) {
    val url = safeWebUrl(value)
    if (url == null) { onError("열 수 있는 링크가 없습니다."); return }
    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    catch (_: android.content.ActivityNotFoundException) { onError("링크를 열 수 있는 앱이 없습니다.") }
}
