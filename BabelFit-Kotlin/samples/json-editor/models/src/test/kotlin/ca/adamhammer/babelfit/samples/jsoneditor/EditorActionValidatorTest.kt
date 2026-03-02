package ca.adamhammer.babelfit.samples.jsoneditor

import ca.adamhammer.babelfit.model.ValidationResult
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class EditorActionValidatorTest {

    @Test
    fun `valid SET action passes validation`() {
        val response = JsonEditorResponse(
            actions = listOf(
                EditorAction(type = ActionType.SET, path = "/name", value = "\"Alice\"", message = "Setting name"),
                EditorAction(type = ActionType.EXPLAIN, path = "", message = "Done")
            )
        )
        assertEquals(ValidationResult.Valid, EditorActionValidator.validate(response))
    }

    @Test
    fun `valid DELETE action passes validation`() {
        val response = JsonEditorResponse(
            actions = listOf(
                EditorAction(type = ActionType.DELETE, path = "/name", message = "Removing name"),
                EditorAction(type = ActionType.EXPLAIN, path = "", message = "Done")
            )
        )
        assertEquals(ValidationResult.Valid, EditorActionValidator.validate(response))
    }

    @Test
    fun `valid MOVE action passes validation`() {
        val response = JsonEditorResponse(
            actions = listOf(
                EditorAction(
                    type = ActionType.MOVE, path = "/contact_email",
                    from = "/email", to = "/contact_email", message = "Renaming"
                ),
                EditorAction(type = ActionType.EXPLAIN, path = "", message = "Done")
            )
        )
        assertEquals(ValidationResult.Valid, EditorActionValidator.validate(response))
    }

    @Test
    fun `valid EXPLAIN action passes validation`() {
        val response = JsonEditorResponse(
            actions = listOf(EditorAction(type = ActionType.EXPLAIN, path = "", message = "Hello"))
        )
        assertEquals(ValidationResult.Valid, EditorActionValidator.validate(response))
    }

    @Test
    fun `empty actions list is invalid`() {
        val result = EditorActionValidator.validate(JsonEditorResponse(actions = emptyList()))
        assertTrue(result is ValidationResult.Invalid)
        assertTrue((result as ValidationResult.Invalid).reason.contains("at least one action"))
    }

    @Test
    fun `SET missing value is invalid`() {
        val response = JsonEditorResponse(
            actions = listOf(EditorAction(type = ActionType.SET, path = "/name"))
        )
        val result = EditorActionValidator.validate(response)
        assertTrue(result is ValidationResult.Invalid)
        assertTrue((result as ValidationResult.Invalid).reason.contains("SET requires"))
    }

    @Test
    fun `SET with unparseable JSON value is invalid`() {
        val response = JsonEditorResponse(
            actions = listOf(EditorAction(type = ActionType.SET, path = "/name", value = "{broken"))
        )
        val result = EditorActionValidator.validate(response)
        assertTrue(result is ValidationResult.Invalid)
        assertTrue((result as ValidationResult.Invalid).reason.contains("not valid JSON"))
    }

    @Test
    fun `MOVE missing from is invalid`() {
        val response = JsonEditorResponse(
            actions = listOf(EditorAction(type = ActionType.MOVE, path = "/dest", to = "/dest"))
        )
        val result = EditorActionValidator.validate(response)
        assertTrue(result is ValidationResult.Invalid)
        assertTrue((result as ValidationResult.Invalid).reason.contains("'from'"))
    }

    @Test
    fun `MOVE missing to is invalid`() {
        val response = JsonEditorResponse(
            actions = listOf(EditorAction(type = ActionType.MOVE, path = "/dest", from = "/source"))
        )
        val result = EditorActionValidator.validate(response)
        assertTrue(result is ValidationResult.Invalid)
        assertTrue((result as ValidationResult.Invalid).reason.contains("'to'"))
    }

    @Test
    fun `EXPLAIN missing message is invalid`() {
        val response = JsonEditorResponse(
            actions = listOf(EditorAction(type = ActionType.EXPLAIN, path = ""))
        )
        val result = EditorActionValidator.validate(response)
        assertTrue(result is ValidationResult.Invalid)
        assertTrue((result as ValidationResult.Invalid).reason.contains("message"))
    }

    @Test
    fun `ASK missing message is invalid`() {
        val response = JsonEditorResponse(
            actions = listOf(EditorAction(type = ActionType.ASK, path = ""))
        )
        val result = EditorActionValidator.validate(response)
        assertTrue(result is ValidationResult.Invalid)
        assertTrue((result as ValidationResult.Invalid).reason.contains("message"))
    }

    @Test
    fun `DELETE at root is invalid`() {
        val response = JsonEditorResponse(
            actions = listOf(EditorAction(type = ActionType.DELETE, path = ""))
        )
        val result = EditorActionValidator.validate(response)
        assertTrue(result is ValidationResult.Invalid)
        assertTrue((result as ValidationResult.Invalid).reason.contains("root"))
    }

    @Test
    fun `DELETE at slash root is invalid`() {
        val response = JsonEditorResponse(
            actions = listOf(EditorAction(type = ActionType.DELETE, path = "/"))
        )
        val result = EditorActionValidator.validate(response)
        assertTrue(result is ValidationResult.Invalid)
        assertTrue((result as ValidationResult.Invalid).reason.contains("root"))
    }

    @Test
    fun `invalid JSON Pointer path is rejected`() {
        val response = JsonEditorResponse(
            actions = listOf(EditorAction(type = ActionType.SET, path = "no-slash", value = "1"))
        )
        val result = EditorActionValidator.validate(response)
        assertTrue(result is ValidationResult.Invalid)
        assertTrue((result as ValidationResult.Invalid).reason.contains("invalid JSON Pointer"))
    }

    @Test
    fun `valid multi-action response passes validation`() {
        val response = JsonEditorResponse(
            actions = listOf(
                EditorAction(type = ActionType.SET, path = "/a", value = "1", message = "Set a"),
                EditorAction(type = ActionType.SET, path = "/b", value = "\"two\"", message = "Set b"),
                EditorAction(type = ActionType.DELETE, path = "/c", message = "Remove c"),
                EditorAction(type = ActionType.EXPLAIN, path = "", message = "Done")
            )
        )
        assertEquals(ValidationResult.Valid, EditorActionValidator.validate(response))
    }

    @Test
    fun `safeFallback produces valid EXPLAIN action`() {
        val fallback = EditorActionValidator.safeFallback("something went wrong")
        assertEquals(ValidationResult.Valid, EditorActionValidator.validate(fallback))
        assertEquals(1, fallback.actions.size)
        assertEquals(ActionType.EXPLAIN, fallback.actions[0].type)
        assertTrue(fallback.actions[0].message!!.contains("something went wrong"))
    }
}
