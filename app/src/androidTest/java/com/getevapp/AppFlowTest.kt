package com.getevapp

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun favoritesRequireLoginAndUnconfiguredSignupStaysDisabled() {
        compose.onAllNodesWithText("관심상품").onLast().performClick()
        compose.onNodeWithText("관심상품을 모아보세요").assertIsDisplayed()
        compose.onNodeWithText("로그인").performClick()
        compose.onNodeWithText("카카오로 로그인").assertIsDisplayed()
        compose.onNodeWithText("처음이신가요? 회원가입").performClick()
        compose.onNodeWithText("이용약관·개인정보처리방침 미설정").assertIsDisplayed()
        compose.onNodeWithText("동의하고 가입하기").assertIsNotEnabled()
        compose.activityRule.scenario.recreate()
        // OAuth credentials are not persisted across process recreation; the unavailable signup state remains safe.
        compose.onNodeWithText("동의하고 가입하기").assertIsNotEnabled()
        compose.onNodeWithContentDescription("뒤로가기").performClick()
        compose.onNodeWithText("관심상품을 모아보세요").assertIsDisplayed()
    }

    @Test fun unavailableNotificationsAreExplainedWithoutAnInertToggle() {
        compose.onAllNodesWithText("알림설정").onLast().performClick()
        compose.onNodeWithText("알림 기능을 준비하고 있어요").assertIsDisplayed()
        compose.onAllNodesWithText("내계정").onLast().performClick()
        compose.onNodeWithText("로그인이 필요해요").assertIsDisplayed()
    }
}
