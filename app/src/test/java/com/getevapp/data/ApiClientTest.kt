package com.getevapp.data

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ApiClientTest {
    private lateinit var server: MockWebServer
    private val session = Session("7", "tester", "test@example.com", "service-token", "service-refresh", "kakao-token", "kakao-refresh")
    private val expired = mutableListOf<String>()
    private lateinit var client: ApiClient
    @Before fun setup() {
        server = MockWebServer().apply { start() }
        client = ApiClient(server.url("/api/").toString(), { session }, expired::add)
    }
    @After fun close() { server.shutdown() }

    @Test fun cursorIsEncodedOnceAndNeverChangesTheApiOrigin() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"results":[],"next":"https://untrusted.example/api/deals/?cursor=cD0xJTJCeA%3D%3D"}"""))
        val first = client.deals()
        assertEquals("cD0xJTJCeA==", first.cursor)
        server.enqueue(MockResponse().setBody("""{"results":[],"next":null}"""))
        client.deals(first.cursor)
        assertEquals("/api/deals/", server.takeRequest().path)
        val next = server.takeRequest()
        assertEquals("cD0xJTJCeA==", next.requestUrl!!.queryParameter("cursor"))
        assertNull(next.getHeader("Authorization"))
    }

    @Test fun publicFeedDoesNotSendOrExpireTheSavedToken() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        try { client.deals(); fail("Expected 401") } catch (e: ApiException) { assertEquals(401, e.status) }
        assertNull(server.takeRequest().getHeader("Authorization"))
        assertTrue(expired.isEmpty())
    }

    @Test fun write401ExpiresOnlyTheTokenUsedForThatRequest() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        try { client.addComment(42, "hello"); fail("Expected 401") } catch (e: ApiException) { assertEquals(401, e.status) }
        val request = server.takeRequest()
        assertEquals("Bearer service-token", request.getHeader("Authorization"))
        assertEquals("/api/posts/42/comments/", request.path)
        assertEquals(listOf("service-token"), expired)
    }

    @Test fun failedWriteReportsFailureAndDoesNotRetry() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("server failure"))
        try { client.addPost("title", "<p>body</p>"); fail("Expected error") } catch (e: ApiException) { assertEquals(500, e.status) }
        assertEquals(1, server.requestCount)
        assertEquals("POST", server.takeRequest().method)
    }

    @Test fun consecutiveReadsRecoverWhenTheServerClosesAKeepAliveConnection() = runBlocking {
        server.enqueue(MockResponse().setBody("[]").setSocketPolicy(SocketPolicy.DISCONNECT_AT_END))
        server.enqueue(MockResponse().setBody("[]"))
        assertTrue(client.posts().isEmpty())
        assertTrue(client.comments(42).isEmpty())
        assertEquals("/api/posts/", server.takeRequest().path)
        assertEquals("/api/posts/42/comments/", server.takeRequest().path)
    }

    @Test fun signupLogsInAgainToGetServiceTokens() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"detail":"회원가입 성공","user_info":{}}"""))
        server.enqueue(MockResponse().setBody("""{"id":7,"user_id":"tester","email":"test@example.com","access_token":"service","refresh_token":"refresh"}"""))
        val result = client.signup(KakaoCredentials("kakao", "kakao-refresh"))
        assertEquals("service", result.accessToken)
        assertEquals("kakao", result.kakaoAccessToken)
        assertEquals("/api/kakaosignup/", server.takeRequest().path)
        assertEquals("/api/kakaologin/", server.takeRequest().path)
    }

    @Test fun logoutUsesKakaoTokensRequiredByTheExistingServer() = runBlocking {
        server.enqueue(MockResponse().setBody("{}"))
        client.logout(session)
        val body = ApiJson.parse(server.takeRequest().body.readUtf8()).asJsonObject
        assertEquals("kakao-token", body.string("access_token"))
    }
}
