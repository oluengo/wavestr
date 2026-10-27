package io.github.oluengo.radiostr.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteIconsTest {
    private val html = """
        <html><head>
        <link rel="shortcut icon" href="/favicon.ico">
        <link rel='icon' type='image/png' sizes='32x32' href='/img/icon-32.png'>
        <LINK REL="apple-touch-icon" SIZES="180x180" HREF="https://cdn.example.com/touch.png?v=2&amp;x=1">
        <meta property="og:image" content="//static.example.com/og.jpg" />
        <meta name=twitter:image content=/tw.png>
        <link rel="icon" href="data:image/png;base64,AAAA">
        <link rel="stylesheet" href="/style.css">
        <link rel="icon" type="image/svg+xml" href="logo.svg">
        </head><body><img src="/not-this.png"></body></html>
    """.trimIndent()

    @Test fun ranksCandidates() {
        val c = SiteIcons.fromHtml(html, "https://www.radio.example/es/home")
        assertEquals("https://cdn.example.com/touch.png?v=2&x=1", c[0])
        assertEquals("https://static.example.com/og.jpg", c[1])
        assertEquals("https://www.radio.example/tw.png", c[2])
        assertEquals("https://www.radio.example/es/logo.svg", c[3])
        assertEquals("https://www.radio.example/img/icon-32.png", c[4])
        assertEquals("https://www.radio.example/favicon.ico", c[5])
        assertEquals(6, c.size)
    }

    @Test fun fallbacksAndPages() {
        assertEquals("https://a.example/apple-touch-icon.png", SiteIcons.conventional("https://a.example/x/y")[0])
        val st = Station("rb:1", "X", listOf(StreamVariant("http://stream.example.org:8000/live", "MP3", 128)))
        assertEquals("https://stream.example.org/", SiteIcons.pageFor(st))
        assertEquals("https://radio.example", SiteIcons.pageFor(st.copy(homepage = "radio.example")))
        assertTrue(SiteIcons.goodDirectoryIcon("https://x/logo.png"))
        assertFalse(SiteIcons.goodDirectoryIcon("https://x/favicon.ico?v=1"))
        assertFalse(SiteIcons.goodDirectoryIcon(null))
        assertTrue(SiteIcons.fromHtml("<html>sin iconos</html>", "https://x").isEmpty())
    }
}
