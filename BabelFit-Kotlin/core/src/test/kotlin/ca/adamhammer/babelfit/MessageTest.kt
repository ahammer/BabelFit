package ca.adamhammer.babelfit

import ca.adamhammer.babelfit.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MessageTest {

    @Test
    fun `text-only constructor wraps string in TextPart`() {
        val msg = Message(MessageRole.USER, "hello")

        assertEquals(1, msg.content.size)
        val part = msg.content[0]
        assertTrue(part is TextPart)
        assertEquals("hello", (part as TextPart).text)
    }

    @Test
    fun `textContent returns concatenated text parts`() {
        val msg = Message(MessageRole.USER, listOf(
            TextPart("Hello "),
            TextPart("World")
        ))

        assertEquals("Hello World", msg.textContent)
    }

    @Test
    fun `textContent skips non-text parts`() {
        val msg = Message(MessageRole.USER, listOf(
            TextPart("Describe this: "),
            ImagePart("iVBOR...", "image/png"),
            TextPart("What do you see?")
        ))

        assertEquals("Describe this: What do you see?", msg.textContent)
    }

    @Test
    fun `textContent returns empty string for image-only messages`() {
        val msg = Message(MessageRole.ASSISTANT, listOf(
            ImagePart("iVBOR...", "image/png")
        ))

        assertEquals("", msg.textContent)
    }

    @Test
    fun `text-only convenience preserves role`() {
        val user = Message(MessageRole.USER, "hi")
        val assistant = Message(MessageRole.ASSISTANT, "hello")
        val system = Message(MessageRole.SYSTEM, "you are helpful")

        assertEquals(MessageRole.USER, user.role)
        assertEquals(MessageRole.ASSISTANT, assistant.role)
        assertEquals(MessageRole.SYSTEM, system.role)
    }

    @Test
    fun `multimodal message preserves all parts`() {
        val parts = listOf(
            TextPart("Look at this:"),
            ImagePart("abc123", "image/jpeg"),
            TextPart("What is it?")
        )
        val msg = Message(MessageRole.USER, parts)

        assertEquals(3, msg.content.size)
        assertTrue(msg.content[0] is TextPart)
        assertTrue(msg.content[1] is ImagePart)
        assertTrue(msg.content[2] is TextPart)

        val image = msg.content[1] as ImagePart
        assertEquals("abc123", image.base64)
        assertEquals("image/jpeg", image.mediaType)
    }
}
