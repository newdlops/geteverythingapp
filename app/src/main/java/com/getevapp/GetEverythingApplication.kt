package com.getevapp

import android.app.Application
import com.getevapp.data.ApiClient
import com.getevapp.data.LocalStore
import com.kakao.sdk.common.KakaoSdk

class GetEverythingApplication : Application() {
    val store by lazy { LocalStore(this) }
    val api by lazy { ApiClient(BuildConfig.API_BASE_URL, { store.session.value }, store::clearSession) }
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.KAKAO_NATIVE_APP_KEY.isNotBlank()) KakaoSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY)
    }
}
