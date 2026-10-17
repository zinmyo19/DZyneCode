package com.dzynecode.app

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class OpenCodeClientTest {
    private lateinit var server: MockWebServer
    @Before fun start() { server = MockWebServer(); server.start() }
    @After fun stop() { server.shutdown() }
    private fun url() = server.url("/").toString()
    private fun response(body: String, status: Int = 200) { server.enqueue(MockResponse().setResponseCode(status).setBody(body)) }
    @Test fun validatesUrls() {
        assertEquals("https://example.test:9/path", OpenCodeClient.normalizeUrl("  https://example.test:9/path///  "))
        listOf("", "ftp://example.test", "https:///missing", "https://user:pass@example.test", "https://example.test?a=1", "https://example.test#x").forEach {
            try { OpenCodeClient.normalizeUrl(it); fail("accepted $it") } catch (_: IllegalArgumentException) {}
        }
    }
    @Test fun loadsHealthAndVisibleAgents() {
        response("{\"healthy\":true,\"version\":\"1.2.3\"}")
        response("[{\"name\":\"build\"},{\"name\":\"hidden\",\"hidden\":true},{\"name\":\"my-agent\"}]")
        val client = OpenCodeClient(OpenCodeConfig(url()))
        assertEquals("1.2.3", client.health())
        assertEquals(listOf("build", "my-agent"), client.agents())
        assertEquals("/global/health", server.takeRequest().path)
        assertEquals("/agent", server.takeRequest().path)
    }
    @Test fun sendsAgentModelAuthAndToolDenialThenDeletesSession() {
        response("{\"id\":\"s1\"}"); response("[\"custom_tool\",\"bash\"]")
        response("{\"info\":{},\"parts\":[{\"type\":\"text\",\"text\":\"```python\\nprint('fixed')\\n```\"}]}")
        response("true")
        val result = OpenCodeClient(OpenCodeConfig(url(), "u", "p", "my-agent", "openai/test-model")).fix("print('bad')", "error", "main.py")
        assertEquals("print('fixed')", result)
        val create = server.takeRequest()
        assertEquals("Basic dTpw", create.getHeader("Authorization"))
        assertEquals("/session", create.path)
        assertEquals("/experimental/tool/ids", server.takeRequest().path)
        val message = server.takeRequest()
        val body = message.body.readUtf8()
        assertEquals("/session/s1/message", message.path)
        assertTrue(body.contains("\"agent\":\"my-agent\""))
        assertTrue(body.contains("\"providerID\":\"openai\""))
        assertTrue(body.contains("\"modelID\":\"test-model\""))
        assertTrue(body.contains("\"*\":false"))
        assertTrue(body.contains("\"custom_tool\":false"))
        assertEquals("DELETE", server.takeRequest().method)
    }
    @Test fun abortsAndDeletesOnBadResponse() {
        response("{\"id\":\"s1\"}"); response("[]")
        response("{\"info\":{},\"parts\":[{\"type\":\"text\",\"text\":\"```python\\na\\n```\\n```python\\nb\\n```\"}]}")
        response("true"); response("true")
        try { OpenCodeClient(OpenCodeConfig(url())).fix("x", "", "x.py"); fail("accepted multiple blocks") } catch (e: IllegalStateException) { assertTrue(e.message!!.contains("multiple")) }
        repeat(3) { server.takeRequest() }
        assertEquals("/session/s1/abort", server.takeRequest().path)
        assertEquals("DELETE", server.takeRequest().method)
    }
    @Test fun extractsSingleCodeAndRejectsBlankOrExplanation() {
        assertEquals("x = 1", OpenCodeClient.extractCode("```python\nx = 1\n```"))
        assertEquals("x = 1", OpenCodeClient.extractCode("x = 1"))
        listOf("", "   ", "Here is the code: x = 1", "```python\n\n```").forEach {
            try { OpenCodeClient.extractCode(it); fail("accepted invalid output") } catch (_: IllegalStateException) {}
        }
    }
    @Test fun errorsAreReadableAndDoNotLeakServerBody() {
        response("secret", 401)
        try { OpenCodeClient(OpenCodeConfig(url(), password = "p")).health(); fail("expected auth error") } catch (e: IllegalStateException) { assertTrue(e.message!!.contains("authentication")); assertFalse(e.message!!.contains("secret")) }
        response("not-json")
        try { OpenCodeClient(OpenCodeConfig(url())).health(); fail("expected JSON error") } catch (e: IllegalStateException) { assertTrue(e.message!!.contains("invalid JSON")) }
    }
}
