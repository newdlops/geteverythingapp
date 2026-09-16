package com.getevapp.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.getevapp.BuildConfig
import com.getevapp.data.KakaoCredentials
import com.getevapp.data.Session
import com.getevapp.data.safeWebUrl
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.common.model.ClientError
import com.kakao.sdk.common.model.ClientErrorCause
import com.kakao.sdk.user.UserApiClient

@Composable
fun AuthScreen(vm: AppViewModel, signingUp: Boolean) {
    val context = LocalContext.current
    var agreed by rememberSaveable(signingUp) { mutableStateOf(false) }
    val configured = BuildConfig.KAKAO_NATIVE_APP_KEY.isNotBlank()
    // TODO: 실제 약관 URL을 local.properties 또는 CI의 TERMS_URL / PRIVACY_URL에 설정한다.
    // 사용자 요청: 원본의 예시 약관을 복사하지 않고 미설정 상태를 표시하며 가입을 비활성화한다.
    val termsConfigured = safeWebUrl(BuildConfig.TERMS_URL) != null && safeWebUrl(BuildConfig.PRIVACY_URL) != null
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("핫딜모아", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(if (signingUp) "카카오 계정으로 시작하세요" else "마음에 드는 핫딜을 모으고\n구매 경험을 함께 나누세요.", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(32.dp))
        if (signingUp) {
            if (termsConfigured) {
                TextButton(onClick = { openExternal(context, BuildConfig.TERMS_URL, vm::message) }) { Text("이용약관 보기") }
                TextButton(onClick = { openExternal(context, BuildConfig.PRIVACY_URL, vm::message) }) { Text("개인정보처리방침 보기") }
                Row(Modifier.fillMaxWidth().toggleable(value = agreed, role = Role.Checkbox, enabled = !vm.authBusy, onValueChange = { agreed = it }).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(agreed, null, enabled = !vm.authBusy)
                    Text("이용약관과 개인정보처리방침에 동의합니다.", Modifier.weight(1f))
                }
            } else {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("이용약관·개인정보처리방침 미설정", style = MaterialTheme.typography.titleSmall)
                        Text("가입 안내가 준비되면 회원가입을 진행할 수 있습니다.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
        if (!configured) ErrorBanner("카카오 로그인을 준비하고 있습니다. 잠시 후 다시 이용해 주세요.")
        vm.authError?.let { ErrorBanner(it) }
        Button(
            onClick = {
                if (vm.beginKakao()) {
                    val existing = if (signingUp) vm.existingSignupCredentials() else null
                    if (existing != null) vm.kakaoReceived(existing, true)
                    else kakaoLogin(context, { vm.kakaoReceived(it, signingUp) }, vm::kakaoFailed)
                }
            },
            enabled = configured && !vm.authBusy && (!signingUp || (termsConfigured && agreed)),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFEE500), contentColor = Color(0xFF191919)),
        ) {
            Text(if (vm.authBusy) "연결 중…" else if (signingUp) "동의하고 가입하기" else "카카오로 로그인", fontWeight = FontWeight.Bold)
        }
        if (vm.authBusy) { Spacer(Modifier.height(16.dp)); CircularProgressIndicator(Modifier.size(24.dp)) }
        if (!signingUp) TextButton(onClick = vm::signupScreen, enabled = !vm.authBusy) { Text("처음이신가요? 회원가입") }
    }
}

@Composable
fun AccountScreen(vm: AppViewModel, session: Session?) {
    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    if (session == null) {
        NoticeScreen("로그인이 필요해요", "카카오 계정으로 로그인하고 관심상품과 포럼을 이용해 보세요.", Icons.Outlined.PersonOutline, "로그인", vm::login)
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(Icons.Outlined.PersonOutline, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Text("내 계정", style = MaterialTheme.typography.headlineSmall)
        Text(session.name.ifBlank { "회원 ${session.id}" }, style = MaterialTheme.typography.titleMedium)
        if (session.email.isNotBlank()) Text(session.email, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider()
        Text("관심상품은 이 기기에 계정별로 저장됩니다.", style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = { confirmLogout = true }, enabled = !vm.authBusy) { Text(if (vm.authBusy) "로그아웃 중…" else "로그아웃") }
        Text("버전 ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (confirmLogout) AlertDialog(
        onDismissRequest = { confirmLogout = false }, title = { Text("로그아웃할까요?") },
        confirmButton = { TextButton(onClick = {
            confirmLogout = false
            vm.logout()
            if (BuildConfig.KAKAO_NATIVE_APP_KEY.isNotBlank()) UserApiClient.instance.logout { }
        }) { Text("로그아웃") } },
        dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("취소") } },
    )
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

private fun kakaoLogin(context: Context, success: (KakaoCredentials) -> Unit, failure: (Boolean) -> Unit) {
    val activity = context.activity() ?: run { failure(false); return }
    val callback: (OAuthToken?, Throwable?) -> Unit = { token, error ->
        when {
            error != null -> failure(error is ClientError && error.reason == ClientErrorCause.Cancelled)
            token != null -> success(KakaoCredentials(token.accessToken, token.refreshToken))
            else -> failure(false)
        }
    }
    if (UserApiClient.instance.isKakaoTalkLoginAvailable(activity)) {
        UserApiClient.instance.loginWithKakaoTalk(activity) { token, error ->
            if (error is ClientError && error.reason == ClientErrorCause.Cancelled) failure(true)
            else if (error != null) UserApiClient.instance.loginWithKakaoAccount(activity, callback = callback)
            else callback(token, error)
        }
    } else UserApiClient.instance.loginWithKakaoAccount(activity, callback = callback)
}
