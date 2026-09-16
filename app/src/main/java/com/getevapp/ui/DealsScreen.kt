package com.getevapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.getevapp.data.Deal
import com.getevapp.data.displayDate
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DealsScreen(vm: AppViewModel, favorites: Set<String>) {
    val feed = vm.feed
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val filtered = remember(feed.items, vm.query) {
        val q = vm.query.trim()
        if (q.isEmpty()) feed.items else feed.items.filter { "${it.title} ${it.category} ${it.community}".contains(q, ignoreCase = true) }
    }
    val nearEnd by remember { derivedStateOf { (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) >= listState.layoutInfo.totalItemsCount - 4 } }
    val showScrollTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 6 } }
    LaunchedEffect(nearEnd, feed.items.size, feed.cursor, vm.query) {
        if (nearEnd && feed.loaded && feed.items.isNotEmpty() && feed.pageError == null && vm.query.isBlank()) vm.nextDeals()
    }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = vm.query, onValueChange = vm::updateQuery,
            label = { Text("불러온 핫딜 검색") },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = { if (vm.query.isNotEmpty()) IconButton(onClick = { vm.updateQuery("") }) { Icon(Icons.Outlined.Close, "검색어 지우기") } },
            singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Box(Modifier.weight(1f)) {
            when {
                feed.loading && feed.items.isEmpty() -> LoadingContent()
                feed.error != null && feed.items.isEmpty() -> NoticeScreen("핫딜을 불러오지 못했어요", feed.error, action = "다시 시도", onAction = vm::refreshDeals)
                feed.loaded && feed.items.isEmpty() -> NoticeScreen("아직 등록된 핫딜이 없어요", "잠시 후 새로고침해 주세요.", action = "새로고침", onAction = vm::refreshDeals)
                else -> PullToRefreshBox(isRefreshing = feed.loading, onRefresh = vm::refreshDeals) {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
                        if (feed.error != null) item { ErrorBanner(feed.error, vm::refreshDeals) }
                        if (vm.query.isNotBlank()) item {
                            Text("불러온 ${feed.items.size}개 중 ${filtered.size}개", Modifier.padding(16.dp), style = MaterialTheme.typography.labelLarge)
                        }
                        if (filtered.isEmpty() && vm.query.isNotBlank()) item {
                            Column(Modifier.padding(24.dp)) {
                                Text("검색 결과가 없어요", style = MaterialTheme.typography.titleMedium)
                                Text("검색어를 바꾸거나 다음 상품을 불러와 주세요.", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        items(filtered, key = { it.key }) { deal ->
                            DealRow(deal, deal.key in favorites, { vm.favorite(deal) }, vm::message)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        item {
                            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                when {
                                    feed.loadingMore -> CircularProgressIndicator(Modifier.size(28.dp))
                                    feed.pageError != null -> ErrorBanner(feed.pageError, vm::nextDeals)
                                    feed.cursor != null -> OutlinedButton(onClick = vm::nextDeals, enabled = !feed.loading) { Text("다음 상품 불러오기") }
                                    feed.loaded -> Text("마지막 상품입니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
            if (showScrollTop) SmallFloatingActionButton(
                onClick = { scope.launch { listState.animateScrollToItem(0) } },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            ) { Icon(Icons.Outlined.ArrowUpward, "맨 위로") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DealRow(deal: Deal, favorite: Boolean, onFavorite: () -> Unit, onError: (String) -> Unit) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(88.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClickLabel = "${deal.title} 원문 열기") { openExternal(context, deal.originUrl, onError) }, contentAlignment = Alignment.Center) {
            SubcomposeAsyncImage(
                model = deal.thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                loading = { Box(contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) } },
                error = { Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.ImageNotSupported, "상품 이미지 없음", tint = MaterialTheme.colorScheme.onSurfaceVariant) } },
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(Modifier.fillMaxWidth().clickable(onClickLabel = "상품 원문 열기") { openExternal(context, deal.originUrl, onError) }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (deal.category.isNotBlank()) BadgeText(deal.category)
                    BadgeText(deal.community)
                    if (deal.ended) BadgeText("종료")
                }
                Text(deal.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(deal.displayPrice, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                Text(displayDate(deal.writtenAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("추천 ${deal.recommendations} · 조회 ${deal.views}", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconToggleButton(checked = favorite, onCheckedChange = { onFavorite() }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                    Icon(if (favorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, if (favorite) "관심상품 삭제" else "관심상품 저장")
                }
            }
        }
    }
}

@Composable
fun BadgeText(value: String) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(4.dp)) {
        Text(value, Modifier.padding(horizontal = 6.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
fun FavoritesScreen(items: List<Deal>, loggedIn: Boolean, onLogin: () -> Unit, onFavorite: (Deal) -> Unit, onError: (String) -> Unit) {
    when {
        !loggedIn -> NoticeScreen("관심상품을 모아보세요", "로그인하면 마음에 드는 핫딜을 이 기기에 저장할 수 있어요.", Icons.Outlined.FavoriteBorder, "로그인", onLogin)
        items.isEmpty() -> NoticeScreen("저장한 관심상품이 없어요", "메인화면의 하트 버튼으로 상품을 저장해 보세요.", Icons.Outlined.FavoriteBorder)
        else -> LazyColumn(Modifier.fillMaxSize()) {
            item { Text("이 기기에 저장한 상품 ${items.size}개", Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium) }
            items(items, key = { it.key }) { deal -> DealRow(deal, true, { onFavorite(deal) }, onError); HorizontalDivider() }
        }
    }
}
