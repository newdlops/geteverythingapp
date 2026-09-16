package com.getevapp.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.getevapp.GetEverythingApplication
import com.getevapp.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.io.IOException

data class FeedState(
    val items: List<Deal> = emptyList(), val cursor: String? = null,
    val loading: Boolean = false, val loadingMore: Boolean = false,
    val loaded: Boolean = false, val error: String? = null, val pageError: String? = null,
)
data class ForumState(val items: List<Post> = emptyList(), val loading: Boolean = false, val loaded: Boolean = false, val error: String? = null)
data class DetailState(val post: Post? = null, val comments: List<Comment> = emptyList(), val loading: Boolean = false, val error: String? = null)

class AppViewModel(application: Application, private val savedState: SavedStateHandle) : AndroidViewModel(application) {
    private val app = application as GetEverythingApplication
    val store = app.store
    private val api = app.api
    var tab by mutableIntStateOf(savedState.get<Int>("tab") ?: 0)
        private set
    var route by mutableStateOf(savedState.get<String>("route") ?: "tabs")
        private set
    var selectedPostId: Long? = savedState["postId"]
        private set
    var feed by mutableStateOf(FeedState())
        private set
    var forum by mutableStateOf(ForumState())
        private set
    var detail by mutableStateOf(DetailState())
        private set
    var query by mutableStateOf(savedState.get<String>("query").orEmpty())
        private set
    var draftTitle by mutableStateOf("")
    var draftHtml by mutableStateOf("")
    var draftHasContent by mutableStateOf(false)
    var commentDraft by mutableStateOf("")
    var submitting by mutableStateOf(false)
        private set
    var authBusy by mutableStateOf(false)
        private set
    var authError by mutableStateOf<String?>(null)
        private set
    private var signupCredentials: KakaoCredentials? = null
    private var loginReturnRoute = "tabs"
    private var feedJob: Job? = null
    private var detailJob: Job? = null
    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()

    init {
        if (route == "signup") navigate("login")
        refreshDeals()
        if (tab == 1) refreshPosts()
        selectedPostId?.takeIf { route == "detail" }?.let(::loadDetail)
    }

    fun selectTab(value: Int) {
        tab = value
        savedState["tab"] = value
        navigate("tabs")
        if (value == 1 && !forum.loaded && !forum.loading) refreshPosts()
    }
    fun updateQuery(value: String) { query = value; savedState["query"] = value }
    private fun navigate(value: String) { route = value; savedState["route"] = value }
    fun back() {
        if (submitting || authBusy) return
        when (route) {
            "login", "signup" -> { signupCredentials = null; authError = null; navigate(loginReturnRoute) }
            "compose", "detail" -> navigate("tabs")
            else -> if (tab != 0) selectTab(0)
        }
    }
    fun login() { loginReturnRoute = route.takeUnless { it == "login" || it == "signup" } ?: "tabs"; authError = null; navigate("login") }
    fun signupScreen() { authError = null; navigate("signup") }
    fun compose() { if (store.session.value == null) login() else navigate("compose") }
    fun clearDraft() { draftTitle = ""; draftHtml = ""; draftHasContent = false }
    fun showPost(post: Post) {
        selectedPostId = post.id
        savedState["postId"] = post.id
        commentDraft = ""
        detail = DetailState(post = post)
        navigate("detail")
        loadDetail(post.id)
    }
    fun refreshDetail() { selectedPostId?.let(::loadDetail) }
    fun message(value: String) { messageChannel.trySend(value) }

    fun refreshDeals() {
        feedJob?.cancel()
        feed = feed.copy(loading = true, loadingMore = false, error = null, pageError = null)
        feedJob = viewModelScope.launch {
            try {
                val result = api.deals()
                feed = FeedState(items = result.items.distinctBy { it.key }, cursor = result.cursor, loaded = true)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                feed = feed.copy(loading = false, loaded = true, error = userMessage(e))
            }
        }
    }
    fun nextDeals() {
        val cursor = feed.cursor ?: return
        if (feed.loading || feed.loadingMore) return
        feed = feed.copy(loadingMore = true, pageError = null)
        feedJob = viewModelScope.launch {
            try {
                val result = api.deals(cursor)
                feed = feed.copy(items = (feed.items + result.items).distinctBy { it.key }, cursor = result.cursor.takeUnless { it == cursor }, loadingMore = false)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                feed = feed.copy(loadingMore = false, pageError = userMessage(e))
            }
        }
    }
    fun refreshPosts() {
        if (forum.loading) return
        forum = forum.copy(loading = true, error = null)
        viewModelScope.launch {
            try { forum = ForumState(api.posts(), loaded = true) }
            catch (e: Exception) {
                if (e is CancellationException) throw e
                forum = forum.copy(loading = false, loaded = true, error = userMessage(e))
            }
        }
    }
    private fun loadDetail(id: Long) {
        detailJob?.cancel()
        detail = detail.copy(loading = true, error = null)
        detailJob = viewModelScope.launch {
            try {
                val post = api.post(id)
                detail = detail.copy(post = post)
                detail = DetailState(post, api.comments(id))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                detail = detail.copy(loading = false, error = userMessage(e))
            }
        }
    }
    fun submitPost() {
        if (submitting || draftTitle.isBlank() || !draftHasContent) return
        if (store.session.value == null) { login(); return }
        submitting = true
        viewModelScope.launch {
            try {
                val post = api.addPost(draftTitle.trim(), draftHtml)
                clearDraft()
                refreshPosts()
                showPost(post)
                message("게시글을 등록했습니다.")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                message(userMessage(e))
            } finally { submitting = false }
        }
    }
    fun submitComment() {
        val postId = selectedPostId ?: return
        if (submitting || commentDraft.isBlank()) return
        if (store.session.value == null) { login(); return }
        submitting = true
        viewModelScope.launch {
            try {
                api.addComment(postId, commentDraft.trim())
                commentDraft = ""
                refreshDetail()
                message("댓글을 등록했습니다.")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                message(userMessage(e))
            } finally { submitting = false }
        }
    }
    fun favorite(deal: Deal) {
        if (store.session.value == null) { login(); return }
        val removing = store.favorites.value.any { it.key == deal.key }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { store.toggleFavorite(deal) }
                message(if (removing) "관심상품에서 삭제했습니다." else "관심상품에 저장했습니다.")
            } catch (e: Exception) { message(userMessage(e)) }
        }
    }
    fun beginKakao(): Boolean {
        if (authBusy) return false
        authBusy = true
        authError = null
        return true
    }
    fun kakaoFailed(cancelled: Boolean) {
        authBusy = false
        if (!cancelled) authError = "카카오 로그인에 실패했습니다. 잠시 후 다시 시도해 주세요."
    }
    fun kakaoReceived(credentials: KakaoCredentials, signingUp: Boolean) {
        viewModelScope.launch {
            try {
                val session = if (signingUp) api.signup(credentials) else api.login(credentials)
                withContext(Dispatchers.IO) { store.saveSession(session) }
                signupCredentials = null
                navigate(loginReturnRoute)
                message("로그인했습니다.")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (!signingUp && e is ApiException && e.status == 404) {
                    signupCredentials = credentials
                    navigate("signup")
                } else authError = userMessage(e)
            } finally { authBusy = false }
        }
    }
    fun existingSignupCredentials(): KakaoCredentials? = signupCredentials
    fun logout() {
        if (authBusy) return
        val current = store.session.value ?: return
        authBusy = true
        viewModelScope.launch {
            try { api.logout(current) }
            catch (e: Exception) { if (e is CancellationException) throw e }
            finally {
                store.clearSession(current.accessToken)
                clearDraft()
                commentDraft = ""
                authBusy = false
                message("로그아웃했습니다.")
            }
        }
    }
}

private fun userMessage(error: Exception): String = when (error) {
    is ApiException -> error.message.orEmpty()
    is IOException -> "인터넷 연결을 확인하고 다시 시도해 주세요."
    else -> "정보를 처리하지 못했습니다. 다시 시도해 주세요."
}
