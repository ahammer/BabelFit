package ca.adamhammer.babelfit.test

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TestHelpersTest {

    @Test
    fun `babelFitTest creates proxy with mock adapter`() {
        val mock = MockAdapter.scripted(SimpleResult("from-helper"))
        val (api, returnedMock) = babelFitTest<SimpleTestAPI>(mock)

        val result = api.get().get()
        assertEquals("from-helper", result.value)
        assertSame(mock, returnedMock)
        returnedMock.verifyCallCount(1)
    }

    @Test
    fun `babelFitTest captures context for verification`() {
        val mock = MockAdapter.scripted(SimpleResult("ctx-check"))
        val (api, adapter) = babelFitTest<SimpleTestAPI>(mock)

        api.getWithParam("hello-param").get()
        adapter.lastContext!!.assertMethodInvocationContains("hello-param")
    }

    @Test
    fun `babelFitStub returns default-constructed results`() {
        val api = babelFitStub<SimpleTestAPI>()

        val result = api.get().get()
        assertNotNull(result)
        assertEquals("default", result.value)
    }

    @Test
    fun `babelFitStub getString returns empty string`() {
        val api = babelFitStub<SimpleTestAPI>()

        val result = api.getString().get()
        assertEquals("", result)
    }
}
