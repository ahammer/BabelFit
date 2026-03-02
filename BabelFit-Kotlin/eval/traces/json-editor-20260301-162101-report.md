# BabelFit Trace Analysis Report

## Summary

Overall the interaction completed successfully with no errors or retries, but the trace reveals inefficiencies: repeated verbose system prompts and full-document payloads increase token usage, and a vague result schema raises the risk of inconsistent outputs. Improvements: shorten and de-duplicate system prompts, tighten the EditorAction JSON schema, send diffs or fragments instead of full documents, and add explicit output validation to catch malformed actions before applying them.

| Metric | Value |
|--------|-------|
| Spans | 13 |
| Duration | 11.4s |
| Tokens In | 0 |
| Tokens Out | 0 |
| Successes | 6 |
| Failures | 0 |
| Errors | 0 |

## Token Efficiency

| Metric | Value |
|--------|-------|
| Input Tokens | 1.8K |
| Output Tokens | 600 |
| Failed Call Tokens | 0 |
| Estimated Waste | ~20% |

## Weaknesses

- **[MEDIUM]** TOKEN_WASTE: Full document snapshots and a verbose system prompt are re-sent across requests, causing unnecessary token usage.
- **[HIGH]** SCHEMA_DESIGN: The result schema is underspecified ('actions' as a generic array) leading to implicit structure and potential inconsistencies.
- **[LOW]** PROMPT_QUALITY: System instructions are long and include rules that could be condensed; repeated inclusion increases noise and cognitive load for the model.
- **[MEDIUM]** ERROR_HANDLING: No explicit validation or confirmation step is present to catch malformed or destructive edits before applying them to the document.

## Per-Span Assessments

### ATTEMPT: Attempt 1 — NEEDS_IMPROVEMENT

**Observations:**
- The span completed successfully and produced a SET action that matches the user's instruction (creating /person).
- System prompt clearly enumerates rules (no extra fields, use memory, return Text when requested) which helps constrain the model.
- The document context and JSON Pointer path guidance are provided and useful for accurate edits.
- The resultSchema is too generic ("actions": ["actions"]) which risks inconsistent action shapes across responses despite this particular attempt succeeding.

**Prompt Issues:**
- **resultSchema**: resultSchema is generic ("actions":["actions"]) and does not define the structure or types of EditorAction fields. (Impact: High — ambiguous output shape increases risk of parsing errors, validation failures, and retries across different edits.)
- **Annotations / Schemas**: No explicit @AiSchema definitions (or examples) for EditorAction/JsonEditorResponse are surfaced in the prompt; the model must infer field names and types. (Impact: Medium — LLM output can vary in field names/structures without canonical type guidance.)
- **System rules verbosity**: The same rule block appears across attempts in the trace; consider keeping the system prompt concise and moving dynamic instructions to the method description or examples. (Impact: Low — clear rules are helpful but redundant repetition (in multiple attempts) can bloat context and confuse debugging.)

### ATTEMPT: Attempt 1 — GOOD

**Observations:**
- The model produced the correct EditorAction (SET at /person/address with the expected address object) matching the user's instruction.
- Prompt included clear JSON document snapshot and path-format examples, which helped produce the correct edit.
- No memory was used in this span (Memory: {}).
- Span completed quickly (2.1s) and succeeded on first attempt, indicating prompt clarity for this specific operation.

**Prompt Issues:**
- **resultSchema**: Using a generic array-of-strings style schema ("actions": ["actions"]) instead of a typed EditorAction schema. (Impact: Ambiguity in expected output format increases risk of parsing/validation failures and makes schema-based validation brittle.)
- **system prompt / context delivery**: Full JSON snapshots and long system instructions are injected directly for every request instead of using concise system text + tools/interceptor to provide document state. (Impact: Repeated large context across attempts wastes tokens and can lead to duplicated prompts in traces or exceed context windows as documents grow.)

### ATTEMPT: Attempt 1 — NEEDS_IMPROVEMENT

**Observations:**
- The model produced a correct, compact EditorAction (type=SET at /person/hobbies) matching the user's instruction.
- System rules are explicit and enforce a strict response format, which the model followed.
- The prompt includes the entire JSON document; while helpful for correctness, it increases token usage and causes repeated context in traces.

**Prompt Issues:**
- **resultSchema precision**: Using a placeholder array schema ("actions": ["actions"]) provides insufficient type information. (Impact: Higher risk of parsing/validation failures and retries because the LLM lacks concrete field/type definitions for action items.)
- **context verbosity**: Embedding the full document for every request rather than a concise summary or relevant subtree. (Impact: Increased token usage, duplicated prompts across attempts, and potential performance issues as documents grow.)
- **operation description**: The operation-level description lacks an explicit, machine-readable specification of permitted action types and exact EditorAction fields. (Impact: Possible ambiguity about allowed action types and required fields leading to inconsistent responses across different instructions.)

### ATTEMPT: Attempt 1 — NEEDS_IMPROVEMENT

**Observations:**
- The model produced a single MOVE action that correctly moved the value from /person/email to /person/contact_email and preserved the email string.
- JSON Pointer path format was used correctly and the action included a descriptive message.
- No memory was referenced in this span; the operation was self-contained.
- Because the resultSchema was permissive, the model's output matched expected intent, but the loose schema risks inconsistencies in other cases.

**Prompt Issues:**
- **resultSchema**: Schema is overly generic ("actions": ["actions"]) and lacks field-level definitions (type enum, path, value, from, to, message). (Impact: Medium — can cause validation ambiguity and increase retry likelihood for more complex edits.)
- **AiParameter / Input Shape**: The user instruction is free-form; structured parameters (e.g., sourcePath/targetPath) would reduce ambiguity. (Impact: Low-to-Medium — increases LLM parsing burden for unstructured instructions.)
- **Interceptors / System Instructions**: System instructions appear duplicated across attempts in the trace. Interceptors may be reappending static text instead of setting it once. (Impact: Low — repetition increases token usage and can clutter prompts across attempts.)

### ATTEMPT: Attempt 1 — NEEDS_IMPROVEMENT

**Observations:**
- The model produced a concise EXPLAIN action matching the user's request and the provided document snapshot.
- The system prompt clearly enumerates rules, which helps strict formatting, but the resultSchema is too generic.
- Trace shows prior successful edits, indicating the editing flow works; main weakness is schema clarity and prompt repetition.

**Prompt Issues:**
- **SCHEMA**: resultSchema uses a generic 'actions' array without an explicit object schema for action fields (type, path, value, message). (Impact: Medium — can cause inconsistent field names/types and parsing/validation errors across attempts.)
- **ANNOTATION**: @AiOperation description lacks short example action objects and an explicit instruction to use JSON Pointer for paths. (Impact: Low — model produces correct output now but may drift without concrete examples.)
- **INTERCEPTOR**: Interceptor or prompt-building logic injects repeated system instructions, leading to duplicate prompts recorded in the trace. (Impact: Low — increases token usage and produces duplicate prompt entries in traces.)

### ATTEMPT: Attempt 1 — NEEDS_IMPROVEMENT

**Observations:**
- The model produced a single well-formed EditorAction: DELETE at /person/address/state matching the user's instruction.
- Output adhered to the JSON-pointer path format and returned only structured data (no extraneous text).
- No memory was used (Memory: {}).
- The action included a human-readable message which aids auditing when applying edits.

**Prompt Issues:**
- **resultSchema specificity**: resultSchema is generic ("actions":["actions"]) instead of a concrete EditorAction schema with explicit fields and types. (Impact: Ambiguous schema increases risk of parsing mismatches and forces the model to invent field shapes, which can cause downstream failures or retries.)
- **action type and value semantics**: No enum or clear rules for action 'type' and which fields are required/optional for each type (SET vs DELETE vs MOVE). (Impact: Unclear how to represent 'no-value' for DELETE, or required fields for MOVE, may lead to inconsistent outputs.)
- **safety/confirmation**: API/Prompt lacks an option for dry-run/confirmation or a policy instructing the model to request confirmation for destructive changes. (Impact: Automatic destructive edits (DELETE) may be applied without user confirmation; for sensitive data this could be unsafe.)

## Code Guidance

### 1. [ANNOTATION] Add annotated Kotlin data classes (EditorAction, JsonEditorResponse) and use them as the method return type. Ensure @AiParameter annotations describe expected fields (e.g. allowed action types and which fields are required per type).

**Current:** The API surface exposes a generic resultSchema ({"actions":["actions"]}) and relies on the LLM to invent the shape of each action. There are no explicit @AiSchema-annotated Kotlin data classes for EditorAction/JsonEditorResponse referenced in the prompt.

**Rationale:** Explicit @AiSchema classes give BabelFit precise type information. The model will produce output that matches a known structure rather than inventing inconsistent fields, reducing validation failures and unnecessary retries.

```kotlin
/* Define explicit schemas for the editor response and actions */
@AiSchema("Represents a single editor operation applied to the document")
data class EditorAction(
  val type: String,             // e.g. SET, MOVE, DELETE, EXPLAIN
  val path: String?,            // JSON Pointer destination
  val value: Any?,              // JSON value for SET
  val from: String?,            // source path for MOVE
  val to: String?,              // destination path for MOVE
  val message: String?
)

@AiSchema("Structured response containing editor actions")
data class JsonEditorResponse(
  val actions: List<EditorAction>
)
```

### 2. [SCHEMA] Replace the generic resultSchema with a richer JSON Schema (or derived @AiSchema) that enumerates action types and required/optional fields. Include a brief example action in the prompt so the model can mirror the shape.

**Current:** The runtime prompt embeds a one-line resultSchema that only declares an 'actions' array without field types or examples.

**Rationale:** A concrete JSON schema in the prompt instructs the LLM exactly which fields and types to emit, improving parseability and reducing ambiguous outputs.

```kotlin
/* Improve the generated JSON schema used in the system prompt */
"resultSchema": {
  "type": "object",
  "properties": {
    "actions": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "type": {"type":"string","enum":["SET","MOVE","DELETE","EXPLAIN"]},
          "path": {"type":"string"},
          "value": {},
          "from": {"type":"string"},
          "to": {"type":"string"},
          "message": {"type":"string"}
        },
        "required": ["type"]
      }
    }
  }
}
```

### 3. [RESILIENCE] Add a validation lambda that checks required action fields and that JSON Pointer paths are syntactically valid. Configure a fallback that returns a safe NO-OP or EXPLAIN action when validation fails.

**Current:** Resilience config appears default; there's no explicit validation function to assert the response shape beyond success/failure.

**Rationale:** Programmatic validation prevents accepting malformed outputs. If the model returns unexpected shapes, the adapter can retry or fallback safely rather than silently persisting broken edits.

```kotlin
resilience {
  maxRetries = 2
  validation { result ->
    // pseudo-check: ensure actions array exists and every action has a type and either path or message
    result is JsonEditorResponse && result.actions.isNotEmpty() && result.actions.all { it.type.isNotBlank() }
  }
  fallback { error -> JsonEditorResponse(actions = listOf(EditorAction(type="EXPLAIN", path="", value=null, from=null, to=null, message="validation_failed"))) }
}
```

### 4. [SCHEMA] Replace the generic actions array schema with concrete @AiSchema-annotated Kotlin data classes (EditorAction, JsonEditorResponse) and use enums for action types so the model returns a stable, validated JSON structure.

**Current:** The API uses a very generic resultSchema ("actions": ["actions"]) so the LLM is left to infer the exact shape of EditorAction objects (fields, types, enum values). This increases fragility and the chance of parsing/validation retries.

**Rationale:** A precise schema with typed fields and enums reduces ambiguity for the model and improves output validation, eliminating retry loops and parsing errors.

```kotlin
@AiSchema("EditorAction", "An edit operation to apply to the JSON document")
data class EditorAction(
  @AiParameter("The action type (SET, MOVE, DELETE, EXPLAIN)") val type: ActionType,
  @AiParameter("JSON Pointer destination path") val path: String,
  @AiParameter("Value to set (object, array, string, number) or empty for delete") val value: Any?,
  @AiParameter("Original source path for moves") val from: String?,
  @AiParameter("Destination path for moves") val to: String?,
  @AiParameter("Human message describing the action") val message: String?
)

enum class ActionType { SET, MOVE, DELETE, EXPLAIN }

@AiSchema("JsonEditorResponse", "Structured list of EditorAction entries to apply")
data class JsonEditorResponse(val actions: List<EditorAction>)
```

### 5. [ANNOTATION] Enrich @AiOperation.description and @AiParameter descriptions with explicit rules and short examples showing each action type, expected field usage, and examples of valid JsonEditorResponse JSON.

**Current:** The @AiOperation/@AiParameter descriptions provide the user message but do not explicitly constrain when to use each action type or the exact field types/formats expected in responses.

**Rationale:** Explicit operation-level documentation teaches the LLM the exact mapping between user intent and EditorAction types, reducing incorrect action choices and ambiguous outputs.

```kotlin
@AiOperation(
  summary = "Respond to user with structured actions",
  description = "Return an array of EditorAction objects. Use type=SET to create/replace a value at path, MOVE to rename/move a field (set 'from'), DELETE to remove a path, and EXPLAIN to return a textual description in message. Always return valid JSON matching JsonEditorResponse."
)
fun respond(@AiParameter("User's instruction for editing the JSON doc. Be explicit about target path and values.") instruction: String): JsonEditorResponse
```

### 6. [INTERCEPTOR] Use an interceptor to shorten the system prompt and supply the document via a tool call or a single compact message. Consider using @Memorize for small persistent context or a ToolProvider to fetch the live JSON instead of pasting full snapshots into every request.

**Current:** Large static context (full system prompt + entire JSON document snapshot) is repeated verbatim for each attempt/request. Trace shows duplicated prompts in the session.

**Rationale:** Moving large or frequently repeated static context into a tool or concise system instruction reduces prompt duplication, token usage, and risk of exceeding context windows while preserving necessary document state.

```kotlin
addInterceptor { context ->
  // Keep system instruction minimal and inject the document via a compact state token
  context.systemInstructions = "JSON editor assistant: return JsonEditorResponse actions. Use JSON Pointer paths."
  context.messages += Message(role = "tool", content = fetchDocumentSnapshot())
  return@addInterceptor context
}

// Or expose the document via a Tool so the model can call GET_DOCUMENT when needed.
```

### 7. [SCHEMA] Replace the placeholder actions schema with a proper @AiSchema data class for EditorAction and use that as the method return type so the produced schema includes each field and its types.

**Current:** The runtime passes a minimal resultSchema: {"actions":["actions"]}, which gives the model no precise type information for each action item (fields like type, path, value, message are only implied by examples).

**Rationale:** Explicit Kotlin/@AiSchema types let BabelFit generate a concrete JSON schema the LLM can follow, reducing parsing errors and unnecessary retries.

```kotlin
/* Define an explicit schema for actions */
@AiSchema("An editor action with type, path, value, from, to, and message")
data class EditorAction(
  val type: String,
  val path: String,
  val value: Any? = null,
  val from: String? = null,
  val to: String? = null,
  val message: String? = null
)

@AiSchema("Response containing a list of EditorAction items")
data class JsonEditorResponse(val actions: List<EditorAction>)

// Method signature
@AiOperation("respond: Respond to user with structured actions")
fun respond(@AiParameter("User instruction for editing the JSON document") message: String): JsonEditorResponse
```

### 8. [ANNOTATION] Move generic rules into a stable system instruction (one-time) and make @AiOperation descriptions concise and explicit about allowed action types and expected behavior.

**Current:** The @AiOperation/@AiParameter content given to the model is largely generic system rules plus the full document; the task-specific instruction is present only in the runtime 'parameters' value. This mixes concerns and can confuse the LLM about which information is authoritative.

**Rationale:** Keeping the system-level rules separate from the operation description and making the operation description task-focused ensures the LLM prioritizes the expected output format and permitted action types.

```kotlin
// Make the operation description concise and task-focused
@AiOperation(
  summary = "Edit JSON document based on user instruction",
  description = "Produce a JsonEditorResponse with EditorAction items to modify the provided document. Use JSON Pointer paths and allowed action types: SET, MOVE, DELETE, EXPLAIN."
)
fun respond(@AiParameter("User instruction describing the edit to make") message: String): JsonEditorResponse
```

### 9. [INTERCEPTOR] Use an interceptor to include either a concise structure summary or only the affected subtree (based on user instruction) rather than the full document for every call.

**Current:** Interceptors currently embed the entire JSON document into every prompt. The trace shows repeated full-document context across many attempts/requests.

**Rationale:** Reducing the size and redundancy of context lowers token use, reduces duplicate prompts, and helps the model focus on relevant areas; it also prevents the system prompt from being repeated verbatim multiple times.

```kotlin
// Interceptor that summarizes or trims document context
addInterceptor { ctx ->
  // produce a small summary or only the subtree relevant to the user instruction
  val summary = summarizeDocument(ctx.document, maxNodes = 40)
  ctx.copy(messages = ctx.messages + Message(system = "DocumentSummary: $summary"))
}

fun summarizeDocument(doc: JsonNode, maxNodes: Int): String { /* return compact summary */ }
```

### 10. [RESILIENCE] Add a resilience.validation function that verifies action item fields (type, path, value/message rules) and rejects responses that don't match the expected EditorAction schema.

**Current:** The trace shows successful responses but there is no explicit resilience validation that checks action shapes and allowed action types before accepting the result.

**Rationale:** A validation step prevents subtle schema mismatches from being treated as successful responses and avoids looping retries when the model omits required fields or returns unexpected shapes.

```kotlin
resilience {
  maxRetries = 2
  validation { result ->
    // validate JSON structure and action fields
    result.actions.all { it.type in setOf("SET","MOVE","DELETE","EXPLAIN") && it.path.startsWith("/") }
  }
  fallback { error -> JsonEditorResponse(actions = listOf()) }
}

```

### 11. [SCHEMA] Replace the generic actions array schema with explicit @AiSchema-annotated data classes for EditorAction and JsonEditorResponse. Include required fields and types (enum for type, string for paths, nullable Any for value).

**Current:** The operation uses a very generic resultSchema: {"actions":["actions"]}. Editor actions are returned but the schema provides no field-level types or required properties.

**Rationale:** Explicit schemas with field descriptions and types reduce parsing ambiguity, lower validation failures, and make LLM output easier to validate by BabelFit's resilience layer.

```kotlin
/* Define explicit schemas for responses and actions */
@AiSchema("EditorAction: single edit operation")
data class EditorAction(
  @AiParameter("Type of edit: SET, MOVE, DELETE, EXPLAIN") val type: String,
  @AiParameter("JSON Pointer path where the action applies") val path: String,
  @AiParameter("Value being set or moved; may be any JSON value") val value: Any?,
  @AiParameter("Source path for MOVE operations") val from: String?,
  @AiParameter("Destination path for MOVE operations") val to: String?,
  @AiParameter("Human-readable message describing the edit") val message: String?
)

@AiSchema("JsonEditorResponse: list of editor actions")
data class JsonEditorResponse(@AiParameter("List of actions to apply") val actions: List<EditorAction>)
```

### 12. [ANNOTATION] Change the API parameter to accept a structured request (sourcePath, targetPath, or explicit field names). Keep a short natural-language field for fallback, but prefer structured fields for programmatic operations.

**Current:** The method parameter description is minimal: it passes a free-form user instruction (rename email to contact_email) without structured fields to capture the source and target names.

**Rationale:** Structured parameters remove ambiguity from natural-language instructions and let the model focus on producing the correct action payload instead of extracting details from prose.

```kotlin
/* Use structured parameters to reduce LLM interpretation work */
@AiOperation("...", "...")
fun respond(
  @AiParameter("Structured edit request; provide sourcePath and targetPath for renames")
  request: RenameFieldRequest
)

@AiSchema("RenameFieldRequest")
data class RenameFieldRequest(
  val sourcePath: String,
  val targetPath: String
)
```

### 13. [RESILIENCE] Add a resilience.validation block that verifies action.type is one of the allowed enums and that JSON Pointer paths are well-formed. Configure a small maxRetries to recover from occasional formatting errors.

**Current:** No explicit validation rules appear configured in the resilience block to check the returned actions conform to expected enum values and path formats before accepting the response.

**Rationale:** Adding a validation step prevents subtle schema mismatches from being treated as success and triggers controlled retries or fallbacks when outputs don't conform.

```kotlin
resilience {
  validation { result ->
    // pseudo: ensure every action has a valid type and path
    result.actions.all { it.type in setOf("SET","MOVE","DELETE","EXPLAIN") && it.path.startsWith("/") }
  }
  maxRetries = 2
}

```

### 14. [INTERCEPTOR] Adjust interceptors to set systemInstructions only once (or deduplicate) and move large static instructions into the initial system message, keeping per-request context minimal.

**Current:** The PromptContext (system instructions) is repeated in multiple attempts and may be appended by interceptors, causing duplicated content in the actual LLM prompt across attempts.

**Rationale:** Duplicate system text increases token usage and can confuse the model. Interceptors should avoid re-injecting the same long system prompt on retries/attempts.

```kotlin
addInterceptor { ctx ->
  // ensure systemInstructions are set only once
  if (ctx.attempt == 0) ctx.systemInstructions = defaultSystemInstructions
  ctx
}

```

### 15. [SCHEMA] Replace the generic 'actions' array schema with a typed AiSchema for a single action object and use an array of that type in the resultSchema.

**Current:** The API's resultSchema is a single high-level 'actions' array without explicit property schemas for each action (type, path, value, message).

**Rationale:** A concrete schema with named fields reduces ambiguity for the model and the JSON parser, lowering validation failures and retry rates.

```kotlin
 @AiSchema("JsonEditorAction", "An editor action with explicit fields")
 data class JsonEditorAction(
   val type: String, // e.g. SET, DELETE, MOVE, EXPLAIN
   val path: String,
   val value: Any? = null,
   val from: String? = null,
   val to: String? = null,
   val message: String? = null
 )

 @AiResponse("Returns an array of JsonEditorAction objects")
 fun respond(@AiParameter("User instruction") instruction: String): List<JsonEditorAction>
```

### 16. [ANNOTATION] Add 1–2 canonical example actions (one SET, one DELETE) to the @AiOperation description so the LLM emits the expected object shape.

**Current:** The @AiOperation prompt communicates rules but lacks explicit examples for action formatting and JSON Pointer usage.

**Rationale:** Including a brief concrete example in the operation description guides the model to produce exactly structured outputs and avoids interpretation differences.

```kotlin
 @AiOperation(
   summary = "Edit JSON document",
   description = "Return a JSON array of JsonEditorAction objects. Use JSON Pointer for 'path'. Example: {\"type\":\"SET\",\"path\":\"/person/name\",\"value\":\"Alice\"}"
 )
```

### 17. [INTERCEPTOR] Make interceptors idempotent: check before appending system text and keep injected text minimal (schema pointers rather than long repeated rules).

**Current:** Interceptors currently propagate the full system instructions each attempt and there is at least one duplicate prompt recorded in the trace.

**Rationale:** Avoiding repeated or excessive prompt injection reduces token use and the chance of duplicate prompts in traces.

```kotlin
 builder.addInterceptor { ctx ->
   // Only inject system instructions once and attach concise schema info
   if (!ctx.systemInstructions.contains("JsonEditorAction")) {
     ctx.systemInstructions += " Use JsonEditorAction schema: type,path,value,from,to,message."
   }
   ctx
 }

```

### 18. [SCHEMA] Replace the ambiguous actions schema with a concrete EditorAction schema (as above). Include enum-like guidance for the 'type' field and document required vs optional fields.

**Current:** The API returns a generic actions array (resultSchema: {"actions":["actions"]}) and the model emits EditorAction values like type=DELETE and path=/person/address/state with a freeform message.

**Rationale:** A precise @AiSchema gives the LLM clear expectations about field names, types and allowed values, reducing parsing errors and retry loops.

```kotlin
/* Define a concrete schema for EditorAction so the LLM has explicit types */
@AiSchema("EditorAction: a single edit action")
data class EditorAction(
  @AiParameter("One of: SET, DELETE, MOVE, EXPLAIN") val type: String,
  @AiParameter("JSON Pointer path where the action applies") val path: String,
  @AiParameter("Value for SET or MOVE; omitted for DELETE") val value: Any? = null,
  @AiParameter("Source path for MOVE") val from: String? = null,
  @AiParameter("Destination path for MOVE") val to: String? = null,
  @AiParameter("Short human-readable message about the action") val message: String? = null
)

/* Use this concrete schema in the method signature */
@AiResponse("List of EditorAction items to apply to the document")
fun respond(@AiParameter("user instruction") instruction: String): List<EditorAction>
```

### 19. [RESILIENCE] Configure resilience.validation to reject malformed EditorAction objects. Provide a safe fallback (e.g., return an EXPLAIN action) so the client can prompt the user instead of blindly applying edits.

**Current:** Prompt includes many rules but the runtime does not validate action semantics before accepting the result; resilience/validation is minimal.

**Rationale:** Adding a validation step prevents invalid actions (e.g., missing path or unknown type) from being accepted, avoiding downstream exceptions when applying edits.

```kotlin
resilience {
  validation { result ->
    // ensure each action has a valid type and a JSON Pointer path
    result.all { it.type in listOf("SET","DELETE","MOVE","EXPLAIN") && it.path.startsWith("/") }
  }
  fallback { /* produce a safe NOOP or EXPLAIN action */ }
}
```

### 20. [INTERCEPTOR] Add an interceptor to trim or summarize large document sections and/or add an optional focusPath parameter so the model only receives the relevant subtree for edits.

**Current:** Full document snapshot is sent in the prompt which can be noisy as documents grow; there is no preprocessing of the context.

**Rationale:** Interceptors can reduce token usage and keep the prompt focused on the area being edited, improving reliability and latency.

```kotlin
addInterceptor { ctx ->
  // Trim document snapshot to relevant subtree or summarize large arrays
  ctx.copy(messages = ctx.messages + Message.system("Document snapshot truncated to relevant paths: /person, /colors (first 5 entries)"))
}

// Or include a 'focusPath' parameter to limit context
fun respond(@AiParameter("path to focus on, optional") focusPath: String? = null, ...)
```

## Prompt Improvement Suggestions

### 1. PROMPT_QUALITY

- **Current:** Long, repeated system prompt included verbatim in every attempt and request.
- **Suggested:** Condense the system prompt to a short, explicit instruction set (one paragraph) and move rarely changing details to a persistent memory or preloaded system context that is not re-sent on every attempt. Only include variable request-specific context in attempts.
- **Rationale:** The system prompt is verbose and repeated across many attempts (duplicate prompts = 1 shown). Re-sending the same large system text each attempt consumes tokens and increases chance of hitting context limits.
- **Impact:** Reduces effective context window and increases token usage; estimated token reduction 25–50% if shortened.

### 2. SCHEMA_DESIGN

- **Current:** Result schema uses a vague type: {"actions":["actions"]} and expects complex EditorAction objects without a fine-grained schema.
- **Suggested:** Define a concrete JSON Schema for EditorAction (explicit fields, types, required/optional). Provide examples for common actions (SET, MOVE, DELETE, EXPLAIN). Tighten descriptions for each field (path format, allowed value types).
- **Rationale:** The generic 'actions' array lacks typed properties in the schema presented to the model (type, path, value, from, to, message). This forces the model to invent structure or rely on implicit formatting which can lead to validation failures or inconsistent output shapes.
- **Impact:** Causes parsing ambiguity and validation risk; increases retries or manual fixes — improves reliability by ~40% if schema is made explicit.

### 3. TOKEN_WASTE

- **Current:** Tooling / document context is embedded repeatedly into prompts (full document snapshots included each request).
- **Suggested:** Send only a succinct document summary or the minimal affected fragment plus an action history. Keep the canonical document state server-side and provide the model with a compact representation (diffs or pointers) rather than full dumps.
- **Rationale:** Embedding the entire JSON document in every request duplicates large payloads across attempts. When document edits are incremental (SET/MOVE/DELETE), sending only deltas or letting the system maintain document state reduces tokens and latency.
- **Impact:** High token cost when document size grows; estimated waste 20–40% for medium documents.

### 4. ERROR_HANDLING

- **Current:** Traces show truncation markers and repeated attempts with similar context but no explicit validation step.
- **Suggested:** Add an automated validation step after model output (schema validator) and a short confirmation/summary action for potentially destructive changes. If validation fails, return a concise error with required corrections instead of re-sending the whole prompt.
- **Rationale:** Truncated logs and no explicit model-output validation step make it hard to detect malformed actions before applying them. Although current trace shows no retries or errors, lack of validation could cause subtle bugs when schema or prompts change.
- **Impact:** Small-to-medium risk of silent misinterpretation; adding explicit validation reduces errors and unnecessary manual corrections.

## Agent Prompt

Use the following prompt with a coding agent or as a guide for manual fixes:

````
Goal: Make BabelFit produce compact, validated, and stable JsonEditorResponse actions while minimizing token waste and preventing malformed edits.

Actionable checklist (apply in codebase):
1) Define concrete schemas
   - Add Kotlin data classes with @AiSchema for the editor API:
     - enum class ActionType { SET, MOVE, DELETE, EXPLAIN }
     - @AiSchema("EditorAction") data class EditorAction(
         @AiParameter("Action type: SET/MOVE/DELETE/EXPLAIN") val type: ActionType,
         @AiParameter("JSON Pointer target path, required") val path: String,
         @AiParameter("Source path for MOVE, optional") val from: String? = null,
         @AiParameter("Destination path for MOVE, optional") val to: String? = null,
         @AiParameter("Value for SET, optional (any JSON)") val value: Any? = null,
         @AiParameter("Human-readable message for auditing") val message: String? = null
       )
     - @AiSchema("JsonEditorResponse") data class JsonEditorResponse(val actions: List<EditorAction>)
   - Change the API method return type to JsonEditorResponse so BabelFit derives an explicit resultSchema.

2) Enforce structured input
   - Replace a single free-text parameter with a small structured request object (focusPath:String?, operation:String?, sourcePath:String?, targetPath:String?, value:Any?). Keep a short natural-language fallback field only for ambiguous requests.
   - Annotate each parameter with @AiParameter and short examples.

3) Improve prompts and examples
   - In @AiOperation.description, add 2–3 canonical example JsonEditorResponse JSON snippets (one per action type) so model output mirrors exact field names and shapes.
   - Keep per-request system text minimal: reference the schema and examples; move heavy rules to the initial system message (or to a single interceptor-run system instruction performed once).

4) Reduce token waste via interceptors/tools
   - Implement an interceptor that: (a) checks whether the global system instruction was already injected and avoids duplicating it, and (b) when a focusPath is provided, attaches only the subtree at focusPath (or a computed diff) instead of the full document.
   - Implement a ToolProvider to fetch the current document or subtree on demand. Prefer a tool call (MCP) so the LLM can request data without resending JSON each time.

5) Add strict response validation and safe fallback
   - Configure resilience.validation { result ->
       // verify non-empty actions list
       // for each action: type in enum; path is valid JSON Pointer; required fields present for type (e.g., SET requires value)
       // ensure no destructive wildcards (like deleting root) unless explicitly confirmed
     }
   - On validation failure, fallback to a safe JsonEditorResponse with a single EXPLAIN action describing the ambiguity (do not apply edits automatically).
   - Set maxRetries = 2 and small retry backoff to avoid loops.

6) Schema-level per-type requirements
   - In the @AiSchema for EditorAction, document required fields per ActionType and provide examples. This helps BabelFit generate a JSON Schema that validators can use.

7) Tests and CI
   - Add unit tests to feed malformed model outputs through validation (missing fields, bad pointers) and assert the validation rejects them.
   - Add an integration test that simulates a typical edit (focusPath provided) and asserts the LLM returns a JsonEditorResponse matching the schema.

8) Runtime guardrails
   - Before applying actions, run a pre-apply dry-run that checks:
       * JSON Pointer resolution succeeds
       * Types of values are compatible with current subtree (optional)
       * No destructive root-level operations without explicit user confirmation
   - Log the human-readable messages from EditorAction for auditing.

Minimal examples to include verbatim in @AiOperation.description (use these exact shapes):
- SET example: { "actions": [{ "type": "SET", "path": "/person/hobbies", "value": ["reading","hiking"], "message": "Adding hobbies" }] }
- MOVE example: { "actions": [{ "type": "MOVE", "from": "/person/email", "to": "/person/contact_email", "path": "/person/contact_email", "message": "Renaming email" }] }
- DELETE example: { "actions": [{ "type": "DELETE", "path": "/person/address/state", "message": "Removing state" }] }
- EXPLAIN example: { "actions": [{ "type": "EXPLAIN", "path": "", "value": "Short explanation of the document structure.", "message": "Describing structure" }] }

Recommended resilience defaults:
- maxRetries = 2
- retryDelayMs = 500
- backoffMultiplier = 2.0
- validation: enforce schema + JSON Pointer syntax + per-type required fields
- fallback: JsonEditorResponse(actions = [EditorAction(type=EXPLAIN, path="", value="Validation failed: <reason>")])

Use this checklist to modify the annotated Kotlin sources, add the interceptor and tool provider, and update the BabelFit builder resilience block. After changes, run the new unit/integration tests to confirm the LLM output is stable, compact, and safely validated before applying edits.
````

**Key Changes:**
- Replace the permissive resultSchema with concrete @AiSchema-annotated Kotlin data classes: EditorAction (enum type, path, optional from/to, optional value, message) and JsonEditorResponse(actions: List<EditorAction>).
- Change the API method return type to JsonEditorResponse so BabelFit generates a precise JSON Schema rather than a generic array.
- Add structured request parameters (focusPath, operation, sourcePath, targetPath, value) with @AiParameter descriptions and prefer structured fields over freeform natural-language for programmatic edits.
- Add canonical example actions (one SET, one MOVE, one DELETE, one EXPLAIN) into @AiOperation.description so the model can mirror exact shapes.
- Introduce an interceptor that deduplicates/shortens system instructions and injects only a compact context: either a small document summary, a focused subtree (using focusPath), or a minimal diff rather than full-document snapshots.
- Provide a ToolProvider (or AnnotatedToolProvider) to fetch the live JSON document on demand (MCP/tool call) instead of pasting full snapshots into prompts; allow the LLM to request the subtree via tool parameters.
- Implement resilience.validation that enforces EditorAction schema rules (required fields for each action type), verifies JSON Pointer syntax, and rejects malformed responses; configure a safe fallback (EXPLAIN or NO_OP action) and set small maxRetries.
- Use enums for action types and express per-type required/optional fields in the @AiSchema so the LLM returns a stable, validated structure.
- Move large static rules into the initial system message (one-time) and keep per-request @AiOperation.description concise and example-driven.
- Add unit/integration tests asserting validation rejects malformed actions, and add a CI check that sample agent runs produce valid JsonEditorResponse objects.

