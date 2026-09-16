package com.getevapp.data

import org.junit.Assert.*
import org.junit.Test

class ApiJsonTest {
    @Test fun malformedThumbnailsAndNonWebLinksAreNotLoaded() {
        assertNull(thumbnailUrl("https:None"))
        assertNull(thumbnailUrl(null))
        assertNull(safeWebUrl("javascript:alert(1)"))
        assertNull(safeWebUrl("content://private/file"))
        assertNull(safeWebUrl("https://user:password@example.com/a"))
        assertEquals("https://example.com/image.jpg", thumbnailUrl("//example.com/image.jpg"))
    }

    @Test fun dealIdentityIncludesCommunityAndAcceptsNumericPrice() {
        val page = ApiJson.deals("""{"results":[{"article_id":"12","community_name":"ARCA","subject":"한돈","price":17898,"currency":"WON","thumbnail":"https:None"},{"article_id":"12","community_name":"FM","price":null}],"next":null}""")
        assertEquals(2, page.items.distinctBy { it.key }.size)
        assertEquals("17,898원", page.items[0].displayPrice)
        assertEquals("가격 확인", page.items[1].displayPrice)
        assertNull(page.items[0].thumbnail)
    }

    @Test fun noticesAreSortedAndHtmlContentIsPreserved() {
        val posts = ApiJson.posts("""[{"id":2,"post_type":"NORMAL","content":"<p>본문</p>"},{"id":1,"post_type":"NOTICE"}]""")
        assertEquals(1L, posts.first().id)
        assertEquals("<p>본문</p>", posts.last().content)
    }

    @Test fun repliesReturnedTwiceAreShownOnce() {
        val comments = ApiJson.comments("""[{"id":1,"author":7,"content":"부모","replies":[{"id":2,"parent":1,"content":"답글"}]},{"id":2,"parent":1,"content":"답글"}]""")
        assertEquals(listOf(1L, 2L), comments.map { it.id })
        assertEquals(1L, comments[1].parent)
    }

    @Test(expected = IllegalArgumentException::class)
    fun signupResponseCannotBeMistakenForAnAuthenticatedSession() {
        ApiJson.session("""{"detail":"회원가입 성공","user_info":{}}""", KakaoCredentials("a", "b"))
    }
}
