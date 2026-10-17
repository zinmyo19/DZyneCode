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
class CompatibleAiClientTest {
    private lateinit var server: MockWebServer
    @Before fun start() { server = MockWebServer(); server.start() }
    @After fun stop() { server.shutdown() }
    private fun url() = server.url("/v1").toString()
    private fun response(body: String, status: Int = 200) { server.enqueue(MockResponse().setResponseCode(status).setBody(body)) }
    private fun fix(key: String = "", model: String = "test-model") = CompatibleAiClient(CompatibleAiConfig(url(), key, model)).fix("print(1)", "", "main.py")
    private fun fails(expected: String, block: () -> Unit) {
        try { block(); fail("Expected failure") } catch (e: Exception) { assertTrue("${e.message} missing $expected", e.message.orEmpty().contains(expected)) }
    }
    @Test fun sendsExpectedPathModelBearerAndExtractsCode() {
        response("{\"choices\":[{\"message\":{\"content\":\"```python\\nprint(2)\\n```\"}}]}")
        assertEquals("print(2)", fix("test-key"))
        val request = server.takeRequest()
        assertEquals("/v1/chat/completions", request.path)
        assertEquals("Bearer test-key", request.getHeader("Authorization"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"model\":\"test-model\""))
        assertFalse(body.contains("temperature"))
        assertTrue(body.contains("\"stream\":false"))
        assertTrue(body.contains("main.py"))
    }
    @Test fun localEndpointDoesNotRequireBearer() {
        response("{\"choices\":[{\"message\":{\"content\":\"pass\"}}]}")
        assertEquals("pass", fix())
        assertNull(server.takeRequest().getHeader("Authorization"))
    }
    @Test fun rejectsBlankModelAndSource() {
        fails("Model is required") { fix(model = " ") }
        fails("Source code") { CompatibleAiClient(CompatibleAiConfig(url(), model = "m")).fix(" ", "", "x.py") }
        assertEquals(0, server.requestCount)
    }
    @Test fun rejectsMalformedEmptyOrMissingContent() {
        response(""); fails("empty response") { fix() }
        response("not-json"); fails("malformed JSON") { fix() }
        response("{\"choices\":[{\"message\":{\"content\":\"\"}}]}"); fails("no message content") { fix() }
    }
    @Test fun rejectsTruncationAndToolCalls() {
        response("{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"pass\"}}]}")
        fails("incomplete") { fix() }
        response("{\"choices\":[{\"message\":{\"tool_calls\":[{}],\"content\":\"pass\"}}]}")
        fails("tool call") { fix() }
    }
    @Test fun httpErrorsDoNotLeakApiKeysOrServerBody() {
        response("response-secret-and-key", 401)
        try { fix("response-secret-and-key"); fail("expected failure") } catch (e: Exception) {
            assertFalse(e.message.orEmpty().contains("response-secret-and-key"))
            assertTrue(e.message.orEmpty().contains("HTTP 401"))
        }
    }
}
