package ca.adamhammer.babelfit

import ca.adamhammer.babelfit.annotations.AiOperation
import ca.adamhammer.babelfit.annotations.AiParameter
import ca.adamhammer.babelfit.annotations.AiResponse
import ca.adamhammer.babelfit.annotations.AiSchema
import ca.adamhammer.babelfit.interfaces.TypeAdapter
import ca.adamhammer.babelfit.model.TypeAdapterRegistry
import ca.adamhammer.babelfit.test.MockAdapter
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.concurrent.Future
import kotlin.reflect.KClass

class TypeAdapterTest {

    // ── Plain POJO (no @Serializable, no @AiSchema) ────────────────────────

    class PointPojo(val x: Int, val y: Int) {
        override fun equals(other: Any?) =
            other is PointPojo && x == other.x && y == other.y
        override fun hashCode() = 31 * x + y
    }

    // ── Mirror type — fully annotated, BabelFit-native ───────────────────────

    @Serializable
    @AiSchema(title = "Point", description = "A 2D point")
    data class PointMirror(
        @AiSchema(description = "X coordinate") val x: Int = 0,
        @AiSchema(description = "Y coordinate") val y: Int = 0
    )

    // ── TypeAdapter bridging the two ────────────────────────────────────────

    class PointAdapter : TypeAdapter<PointPojo, PointMirror> {
        override val pojoClass: KClass<PointPojo> = PointPojo::class
        override val mirrorClass: KClass<PointMirror> = PointMirror::class
        override fun toMirror(pojo: PointPojo) = PointMirror(pojo.x, pojo.y)
        override fun fromMirror(mirror: PointMirror) = PointPojo(mirror.x, mirror.y)
    }

    // ── Registry unit tests ─────────────────────────────────────────────────

    @Test
    fun `registry returns null for unregistered classes`() {
        val registry = TypeAdapterRegistry()
        assertNull(registry.resolve(String::class))
        assertFalse(registry.hasAdapter(String::class))
    }

    @Test
    fun `register and resolve adapter`() {
        val registry = TypeAdapterRegistry()
        registry.register(PointAdapter())
        assertNotNull(registry.resolve(PointPojo::class))
        assertTrue(registry.hasAdapter(PointPojo::class))
    }

    @Test
    fun `mirrorClassFor returns mirror when registered`() {
        val registry = TypeAdapterRegistry()
        registry.register(PointAdapter())
        assertEquals(PointMirror::class, registry.mirrorClassFor(PointPojo::class))
    }

    @Test
    fun `mirrorClassFor returns original when no adapter`() {
        val registry = TypeAdapterRegistry()
        assertEquals(String::class, registry.mirrorClassFor(String::class))
    }

    @Test
    fun `convertArg converts POJO to mirror`() {
        val registry = TypeAdapterRegistry()
        registry.register(PointAdapter())
        val result = registry.convertArg(PointPojo(3, 4))
        assertEquals(PointMirror(3, 4), result)
    }

    @Test
    fun `convertArg returns value unchanged when no adapter`() {
        val registry = TypeAdapterRegistry()
        val input = "hello"
        assertSame(input, registry.convertArg(input))
    }

    @Test
    fun `convertResult converts mirror back to POJO`() {
        val registry = TypeAdapterRegistry()
        registry.register(PointAdapter())
        val result = registry.convertResult(PointMirror(5, 6), PointPojo::class)
        assertEquals(PointPojo(5, 6), result)
    }

    @Test
    fun `convertResult returns mirror unchanged when no adapter`() {
        val registry = TypeAdapterRegistry()
        val mirror = PointMirror(7, 8)
        val result = registry.convertResult(mirror, PointMirror::class)
        assertSame(mirror, result)
    }

    // ── Integration: POJO through the BabelFit proxy ─────────────────────────

    interface PointAPI {
        @AiOperation(summary = "Get a point")
        @AiResponse(description = "A 2D point", responseClass = PointPojo::class)
        fun getPoint(): Future<PointPojo>

        @AiOperation(summary = "Translate a point")
        @AiResponse(description = "Translated point", responseClass = PointPojo::class)
        fun translate(
            @AiParameter(description = "The point to translate") point: PointPojo,
            @AiParameter(description = "X offset") dx: Int
        ): Future<PointPojo>
    }

    @Test
    fun `proxy returns POJO via type adapter bridge`() {
        // MockAdapter returns the mirror type (what the AI pipeline would produce)
        val mock = MockAdapter.scripted(PointMirror(10, 20))

        val instance = BabelFitBuilder(PointAPI::class)
            .adapter(mock)
            .typeAdapter(PointAdapter())
            .build()

        val result = instance.api.getPoint().get()
        assertEquals(PointPojo(10, 20), result)
    }

    @Test
    fun `proxy converts POJO parameter to mirror in context`() {
        val mock = MockAdapter.scripted(PointMirror(11, 22))

        val instance = BabelFitBuilder(PointAPI::class)
            .adapter(mock)
            .typeAdapter(PointAdapter())
            .build()

        val result = instance.api.translate(PointPojo(1, 2), 10).get()
        assertEquals(PointPojo(11, 22), result)

        // Verify the adapter saw the mirror type in the method invocation
        val invocation = mock.lastContext!!.methodInvocation
        assertTrue(invocation.contains("\"x\""), "Mirror fields should appear in prompt: $invocation")
    }
}
