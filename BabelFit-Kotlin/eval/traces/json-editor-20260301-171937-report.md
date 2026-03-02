# BabelFit Trace Analysis Report

## Summary

The trace shows repeated validation failures and retries caused primarily by an underspecified action schema (MOVE emitted without required 'from'/'to') and ambiguous user prompts (e.g., 'rename email' without explicit paths). Additional inefficiencies arise from resending large system prompts on each attempt and a rigid requirement that every response include EXPLAIN/ASK. Key fixes: make action schemas explicit with examples, persist heavy system instructions to avoid duplication, add lightweight automatic repairs for simple omissions, and relax or clarify the mandatory EXPLAIN/ASK rule so EXPLAIN is only used when needed.

| Metric | Value |
|--------|-------|
| Spans | 15 |
| Duration | 17.4s |
| Tokens In | 0 |
| Tokens Out | 0 |
| Successes | 5 |
| Failures | 3 |
| Errors | 4 |

## Token Efficiency

| Metric | Value |
|--------|-------|
| Input Tokens | 3.0K |
| Output Tokens | 1.8K |
| Failed Call Tokens | 900 |
| Estimated Waste | ~38% |

## Weaknesses

- **[CRITICAL]** SCHEMA_DESIGN: The action schema is underspecified for operation-specific requirements. The model emitted MOVE actions missing required 'from' or 'to' paths, causing repeated ResultValidationException errors.
- **[HIGH]** SCHEMA_DESIGN: Ambiguous semantics for 'rename' operations—no dedicated rename action and no examples—led to incorrect usage of MOVE.
- **[MEDIUM]** PROMPT_QUALITY: Natural-language edit requests omit explicit JSON Pointer paths and focus hints, increasing ambiguity and causing the model to guess paths.
- **[MEDIUM]** RETRY_OVERHEAD: Retries are occurring due to validation failures rather than substantive model reasoning errors, incurring latency and extra cost.
- **[MEDIUM]** TOKEN_WASTE: Large system prompt content was duplicated across attempts (duplicate prompts: 4), increasing token usage unnecessarily.
- **[HIGH]** ERROR_HANDLING: Validation exceptions (missing fields) are surfaced only after LLM output; there is no intermediate repair or clarifying question strategy, causing failed attempts.

## Per-Span Assessments

### ATTEMPT: Attempt 1 — NEEDS_IMPROVEMENT

**Observations:**
- Attempt succeeded and produced an action that matches the user's intent: a root-level SET creating the person object with contact_email.
- The response used the root path (empty string) which is allowed by the path format, but may be clearer to use '/' explicitly.
- Two explicit JSON editor rules were violated: (1) Every response MUST include at least one EXPLAIN or ASK action; (2) SET values must be valid JSON-encoded strings — the response returned a native object.
- Existing validation did not catch these violations; this allowed an invalid-but-intent-correct payload to be accepted as success.

**Prompt Issues:**
- **SET.value type enforcement**: The model returned value as an un-encoded JSON object instead of a JSON-encoded string as required by the JSON editor rules. (Impact: Executor expects a JSON-encoded string for SET.value; receiving a native object can cause parsing errors or runtime failures when applying edits.)
- **Mandatory EXPLAIN/ASK requirement**: Response contains no EXPLAIN or ASK action even though the rules mandate at least one such action per response. (Impact: Missing EXPLAIN/ASK violates protocol rules and may prevent downstream UX or CLI helpers from receiving the explanatory text they require.)
- **Validation and schema clarity**: Schema did not enforce SET.value as a string and resilience validation did not reject the missing EXPLAIN/ASK or incorrect value type. (Impact: Ambiguous or permissive schema and missing validation allow incorrect outputs to succeed, causing later failures and hard-to-debug behavior.)

### ATTEMPT: Attempt 1 — NEEDS_IMPROVEMENT

**Observations:**
- The attempt succeeded and generated a single SET action adding /person/address with the expected street, city, and state fields.
- The action uses path '/person/address' and includes a structured object for value, and a clear human message.
- The system prompt and parameter schema are currently underspecified about encoding requirements (the JSON Editor rules mandate SET.value be a JSON-encoded string).
- Other spans in the trace show repeated MOVE validation errors; improving the action schema and runtime validation would reduce these failures.

**Prompt Issues:**
- **Result schema vagueness**: resultSchema describes actions only as an 'Ordered list of actions' rather than a structured EditorAction object with typed fields and required properties. (Impact: Model may produce actions with missing or incorrectly-typed fields, causing validation failures and retries.)
- **SET.value encoding requirement**: The JSON Editor rules require SET.value to be a JSON-encoded string, but the prompt/schema do not clearly enforce or show examples of that encoding. (Impact: Inconsistent encoding (raw JSON object vs. JSON-encoded string) can cause downstream parsers to fail or ambiguity in how to apply the SET action.)
- **MOVE action requirements**: 'MOVE requires a non-empty from and to path' is stated in helper docs but not enforced in the method-level schema or prompt; the model may omit one or both fields. (Impact: Model returned MOVE actions without 'from' or 'to' in other attempts, causing ResultValidationException and retries.)

### ATTEMPT: Attempt 1 — NEEDS_IMPROVEMENT

**Observations:**
- The model produced a single SET action at path '/person/hobbies' with the intended array value — this matches the user's request and correct JSON Pointer path format.
- The response omitted any EXPLAIN or ASK action, even though the JSON EDITOR RULES require at least one EXPLAIN or ASK per response; this inconsistency may cause downstream confusion or noncompliance with policies.
- The system prompt enforces that SET values be JSON-encoded strings, but the response provided an array literal. The mismatch between instruction and actual encoding expectations is a source of intermittent validation errors.
- Overall the attempt succeeded for this specific edit, but surrounding trace entries show related failures (MOVE missing fields) indicating schema/constraint ambiguities across the application.

**Prompt Issues:**
- **Schema vs. Prompt Inconsistency**: System rules require SET values to be JSON-encoded strings, but the result schema and example outputs do not enforce or consistently represent this encoding. (Impact: Model outputs may appear valid but violate un-enforced encoding rules, causing either silent acceptance of nonconforming outputs or validation failures later (retry storms).)
- **Missing Explicit Field Requirements in @AiSchema/@AiParameter**: Parameter descriptions are generic; required fields and encoding expectations are not fully expressed in the machine schema. (Impact: The LLM can omit required fields (e.g., EXPLAIN/ASK, MOVE 'from'/'to') or produce malformed actions because the generated schema lacks precise required-property constraints and examples.)
- **Validation & Resilience Configuration**: No validation rule enforces 'must include EXPLAIN/ASK' or checks that MOVE has non-empty 'from' and 'to', leading to repeated ResultValidationExceptions seen elsewhere in the trace. (Impact: Without a clear resilience validation function for these editor rules, failures cause retries and noisy traces rather than a focused corrective prompt or fallback.)

### ATTEMPT: Attempt 1 — NEEDS_IMPROVEMENT

**Observations:**
- The model returned a MOVE action missing the required 'from' path; validation failed with ResultValidationException.
- User intent was ambiguous: the instruction asked to rename 'email' to 'contact_email', but the current document already contains 'contact_email' and no 'email' key—no explicit JSON Pointers were provided.
- Multiple attempts in the trace also failed earlier due to missing 'to' paths, indicating inconsistent action generation by the model.
- Retries and duplicate prompts in the trace increased latency and token usage.

**Prompt Issues:**
- **Ambiguous user instruction vs. document state**: User asked to rename 'email' but the document already has 'contact_email' and lacks 'email'; no explicit JSON Pointer paths were provided. (Impact: Leads the model to generate irrelevant or incomplete actions (e.g., MOVE without 'from'), causing validation failures.)
- **Schema constraints for EditorAction**: EditorAction schema does not require 'from'/'to' conditionally when type == 'MOVE'. (Impact: Allows invalid MOVE objects to be produced; validation fails only after model output, triggering retries.)
- **Insufficient examples/documentation in annotations**: @AiParameter/@AiSchema descriptions do not include explicit examples of MOVE/SET usages with JSON Pointers. (Impact: Model lacks concrete output examples to follow and may omit required fields.)

### ATTEMPT: Attempt 2 — NEEDS_IMPROVEMENT

**Observations:**
- The LLM produced a MOVE action missing a 'to' path, which violates the editor action requirements and triggered a ResultValidationException.
- The current document already contains 'contact_email', so the natural-language instruction to rename 'email'->'contact_email' is ambiguous (source '/person/email' does not exist).
- Multiple retries for the same invalid output indicate schema/prompt ambiguity rather than transient model errors.
- The system prompt enforces strict JSON output rules but the parameter descriptions lack guidance about pointer resolution and existence checks.

**Prompt Issues:**
- **Ambiguous user intent vs. document state**: The prompt/request did not instruct the model to verify that the source JSON Pointer exists or to ask for clarification when it doesn't. (Impact: Model attempted an operation on a non-existent source field, leading to invalid actions and validation failure.)
- **Schema laxness for MOVE actions**: EditorAction schema does not enforce conditional required fields for MOVE operations. (Impact: Allowed generation of MOVE without required 'from'/'to', causing ResultValidationException and retries.)
- **Missing preprocessing/normalization**: No interceptor or tool was used to normalize 'rename' requests into explicit from/to JSON Pointers or to resolve ambiguous field names against the current document. (Impact: Model must infer JSON Pointers from free-form text, increasing chances of omitted or incorrect fields.)
- **Retry/validation strategy**: Validation occurs post-response with no fast-fail or fallback that prompts the model to ask for clarification. (Impact: Repeated ATTEMPT spans wasted tokens and time because invalid outputs were retried instead of converted to clarifying questions or fallbacks.)

### ATTEMPT: Attempt 3 — NEEDS_IMPROVEMENT

**Observations:**
- Validation failed because the MOVE action lacked a 'to' path; earlier attempts showed similar MOVE-related errors (missing 'from' or 'to').
- The user's requested rename targeted 'email' but the current document already has 'contact_email' and no 'email' field — model should have detected that and ASKed or used SET+DELETE.
- Schema and system prompt are underspecified about required fields per action type, causing the model to emit incomplete actions.
- The JSON Editor Rules require every response include EXPLAIN or ASK; this constraint interacts with action generation and may cause models to omit required MOVE fields if not aligned.
- Lack of a document-aware interceptor or path-existence tool increases risk of invalid edits and retry loops, wasting tokens.

**Prompt Issues:**
- **Action schema ambiguity**: Single loose EditorAction schema doesn't make MOVE-specific required fields explicit; model can omit 'to'. (Impact: Model emits incomplete MOVE actions (missing 'to'/'from') leading to validation errors and retries.)
- **Missing document-aware guidance**: Prompt doesn't inject current-document checks or instruct the model to ASK when the source path is absent. (Impact: Model attempts edits referencing paths that do not exist, producing invalid operations instead of asking for clarification.)
- **Resilience & validation policy**: Resilience config lacks targeted validators for action invariants (e.g., MoveAction requires non-empty 'to') and a human-friendly fallback. (Impact: Retries repeat the same invalid output; no helpful fallback response is produced, causing all attempts to fail.)

### ATTEMPT: Attempt 1 — NEEDS_IMPROVEMENT

**Observations:**
- The response fulfilled the 'must include EXPLAIN or ASK' rule by returning two EXPLAIN actions describing the document structure.
- Content is thorough and lists all top-level fields including nested address and hobbies array.
- Formatting in EXPLAIN messages uses markdown-style emphasis and explicit line breaks; this may be undesirable for consumers expecting plain text.
- Prior traces showed validation failures for MOVE actions — indicates schema/validation gaps even though this specific attempt succeeded.

**Prompt Issues:**
- **SCHEMA**: Result schema is under-specified: 'actions' is generic and EditorAction fields/types are not defined, so the model guesses which fields to include. (Impact: Validation failures and retries (e.g., MOVE missing 'from'/'to') which cause wasted attempts and errors.)
- **INTERCEPTOR**: Static system instructions are re-injected on each attempt rather than centralized; the prompt context is noisy and duplicated. (Impact: Duplicate/verbose system prompts across attempts increase token usage and risk inconsistent instructions between attempts.)
- **ANNOTATION**: @AiOperation/@AiParameter descriptions do not explicitly constrain formatting or enumerate required fields per action type (SET/MOVE/DELETE/EXPLAIN/ASK). (Impact: Inconsistent output formatting (markdown vs plain text) and ambiguous field requirements cause integration friction.)

### ATTEMPT: Attempt 1 — NEEDS_IMPROVEMENT

**Observations:**
- This span successfully executed a DELETE action with the correct JSON pointer (/person/address/state) and a concise message describing the operation.
- The response adhered to the top-level rule of returning the expected JSON structure (JsonEditorResponse with actions).
- Earlier trace attempts showed validation errors related to MOVE actions lacking 'from'/'to' — indicating schema-level weaknesses elsewhere in the trace.
- The JSON Editor rules require every response to include at least one EXPLAIN or ASK action; this response omitted such an action. While the specific user request was a straightforward delete and the attempt succeeded, the missing EXPLAIN/ASK could violate higher-level expectations and caused inconsistencies in other spans.

**Prompt Issues:**
- **Operation description (system prompt / @AiOperation)**: The EXPLAIN/ASK requirement and per-action constraints are only present in the separate JSON EDITOR RULES block (not emphasized in the operation-level description). Move these constraints into the @AiOperation description so they become part of the system instructions the model receives for this method. (Impact: Model may not consistently include required EXPLAIN/ASK actions or follow action-specific invariants, leading to validation failures and retries.)
- **Result schema (EditorAction typing)**: EditorAction is not expressed as concrete subclasses with required fields per action type. The generated JSON schema should enforce required properties for each action type (DELETE requires path, MOVE requires from/to, SET requires a JSON-encoded 'value'). (Impact: Permissive schema allows invalid combinations (e.g., MOVE without 'from' or 'to'), causing ResultValidationException and multiple retries.)

## Code Guidance

### 1. [SCHEMA] Change the EditorAction.value type in the @AiSchema to String and add a clear description requiring a JSON-encoded string for SET. This forces the model to output quoted JSON and helps the validator detect incorrect types.

**Current:** The generated EditorAction schema allows SET.value to be returned as an un-encoded JSON object. The assistant returned value={...} (a native object) instead of a JSON-encoded string as required by the JSON editor rules.

**Rationale:** Requiring value to be a string at the schema level makes the LLM output expectation explicit and avoids downstream parsing mismatches where the system expects a JSON-encoded string but receives a native object.

```kotlin
/* Make the EditorAction.value explicitly a String in the AI-facing schema */
@AiSchema("EditorAction")
data class EditorAction(
  val type: String,
  val path: String?,
  val from: String? = null,
  val to: String? = null,
  @AiParameter("For SET actions this must be a JSON-encoded string (e.g. '{\"person\":{...}}')")
  val value: String? = null,
  val message: String? = null
)

```

### 2. [ANNOTATION] Update the @AiOperation description to explicitly enumerate the required actions (EXPLAIN/ASK requirement, SET.value encoding rule, batching rule) so the instruction appears near the top of the model prompt.

**Current:** The @AiOperation/system prompt communicates many rules, but the mandatory rule "Every response MUST include at least one EXPLAIN or ASK action" is only in the long System text and may be overlooked by the model.

**Rationale:** Putting the most critical protocol constraints directly into the method-level annotation ensures the generated system prompt and schema emphasize them. This reduces ambiguous interpretation by the model.

```kotlin
@AiOperation(
  summary = "respond",
  description = "Respond with an actions array. IMPORTANT: Every response must include at least one EXPLAIN or ASK action. For SET, value must be a JSON-encoded string. Batch all edits into a single response."
)
fun respond(request: JsonEditRequest): JsonEditorResponse

```

### 3. [RESILIENCE] Implement a resilience.validation lambda that enforces: at least one EXPLAIN or ASK action present, SET.value is a JSON-encoded string, and required fields per action type exist. On validation failure, trigger a retry or return a targeted ASK action.

**Current:** Resilience/validation currently allows this response to be treated as successful even though it violates explicit JSON Editor Rules (missing EXPLAIN/ASK and SET.value not encoded).

**Rationale:** Adding a strict, domain-aware validation step prevents incorrect outputs from being accepted and retried. It reduces silent failures when the model returns structurally incorrect actions.

```kotlin
babelFit<MyApi> {
  resilience {
    validation { result ->
      // pseudo-code validation
      val actions = result.actions
      val hasExplainOrAsk = actions.any { it.type == "EXPLAIN" || it.type == "ASK" }
      val setValuesAreStrings = actions.filter { it.type == "SET" }.all { it.value is String }
      hasExplainOrAsk && setValuesAreStrings
    }
    fallback { error -> /* emit a structured ASK asking for clarification */ }
  }
}

```

### 4. [INTERCEPTOR] Add an interceptor or response post-processor that normalizes SET.value into a JSON-encoded string and injects a minimal EXPLAIN if the model omitted one. Prefer normalization only when safe; otherwise fail validation to prompt a retry.

**Current:** The system currently relies solely on the model to obey formatting rules. No interceptor normalizes or corrects common deviations (e.g., returning objects instead of JSON strings or omitting required EXPLAIN/ASK).

**Rationale:** Interceptors or post-processors can automatically correct common format issues, reducing retries and making the system more robust to small model deviations.

```kotlin
// Interceptor that post-processes model output before validation/parsing
addInterceptor { ctx ->
  ctx.messages += Message.system("If the model returns a non-string SET.value, convert it to a JSON-encoded string. If no EXPLAIN/ASK action is present, add an EXPLAIN summarizing the edits.")
  ctx
}

// Or a response post-processor to normalize values
responsePostProcessor { raw ->
  val actions = parseRawActions(raw)
  actions.forEach { if (it.type == "SET" && it.value !is String) it.value = jsonEncode(it.value) }
  if (actions.none { it.type == "EXPLAIN" || it.type == "ASK" }) {
    actions.add(0, EditorAction(type="EXPLAIN", path="", message="Performed batched edits as requested."))
  }
  serialize(actions)
}

```

### 5. [SCHEMA] Replace the free-text 'actions' description with a strongly-typed EditorAction schema via @AiSchema and enums. Document that SET.value must be a JSON-encoded string and that MOVE must include both 'from' and 'to'. Ensure the method's resultSchema reflects the structured EditorAction type.

**Current:** The API returns EditorAction objects but the resultSchema in the prompt is loose ("actions": ["Ordered list of actions..."]). @AiSchema/@AiParameter descriptions are minimal and do not enforce field formats (e.g., SET.value must be a JSON-encoded string; MOVE requires non-empty 'from' and 'to').

**Rationale:** A concrete, typed schema in Kotlin/@AiSchema gives the LLM an explicit JSON schema to follow. It reduces ambiguity about required fields, types, and encoding (e.g., requiring value to be a JSON-encoded string), which cuts validation failures and retries.

```kotlin
/* Improve the EditorAction schema and parameter docs */
@AiSchema("EditorAction", "Represents a single edit action. Fields: type (ENUM), path (JSON Pointer), from (JSON Pointer), to (JSON Pointer), value (JSON-encoded string), message (string)")
data class EditorAction(
  val type: ActionType,
  val path: String?,
  val from: String?,
  val to: String?,
  val value: String?, // must be valid JSON-encoded string
  val message: String?
)

enum class ActionType { SET, DELETE, MOVE, EXPLAIN, ASK }

/* On the operation method */
@AiOperation("respond", "Respond to user with structured actions")
fun respond(@AiParameter("Structured edit request with message, optional focusPath, operation hint, and value") request: EditRequest): JsonEditorResponse
```

### 6. [INTERCEPTOR] Enrich @AiParameter text and use an interceptor to append a single-line, machine-friendly specification of the expected EditorAction JSON shape to systemInstructions.

**Current:** The method parameter description is generic and the system prompt repeats general rules. The prompt doesn't explicitly enumerate allowed EditorAction fields or encoding rules in a machine-enforceable way.

**Rationale:** Concretely specifying field names and required encodings in the prompt context reduces model interpretation differences and prevents malformed actions (e.g., missing 'to' on MOVE).

```kotlin
/* Add precise parameter descriptions and more specific system instructions via an interceptor */
@AiParameter("Edit request: { message, focusPath?, hint?, value? }. Expect response: JSON object { actions: EditorAction[] } with each EditorAction having 'type', 'path', 'from', 'to', 'value' (JSON string), 'message'.")

// Interceptor to inject compact, explicit schema guidance
addInterceptor { ctx ->
  ctx.systemInstructions += "\nOutput MUST be: { \"actions\": [ { \"type\": \"SET|DELETE|MOVE|EXPLAIN|ASK\", \"path\": \"/json/pointer\", \"from\": null-or-\"/json/pointer\", \"to\": null-or-\"/json/pointer\", \"value\": \"JSON-encoded string\", \"message\": \"...\" } ] }"
  ctx
}
```

### 7. [RESILIENCE] Implement concrete validation logic in the BabelFit resilience block to assert required fields per action type and provide a meaningful fallback action when validation fails.

**Current:** Resilience/validation currently triggers retries but validation rules are not strictly codified. The trace shows MOVE errors (missing 'from' or 'to') on other spans, indicating missing runtime checks before returning a result.

**Rationale:** Adding explicit validation for EditorAction shapes prevents malformed outputs from being accepted and triggers a controlled retry or deterministic fallback, reducing noisy ATTEMPT/REQUEST retries in traces.

```kotlin
resilience {
  validation { result ->
    result.actions.all { action ->
      when(action.type) {
        ActionType.SET -> action.path?.isNotBlank() == true && action.value?.isNotBlank() == true
        ActionType.MOVE -> action.from?.isNotBlank() == true && action.to?.isNotBlank() == true
        ActionType.DELETE -> action.path?.isNotBlank() == true
        else -> true
      }
    }
  }
  fallback { error -> JsonEditorResponse(actions = listOf(EditorAction(type = EXPLAIN, message = "Validation failed: ensure actions are well-formed"))) }
}
```

### 8. [SCHEMA] Make the expected types explicit in @AiSchema/@AiParameter, and add an interceptor that JSON-encodes SET 'value' fields before sending the prompt or normalizes the model output. This prevents ambiguity and reduces retries.

**Current:** The prompt contains detailed runtime/editor rules (e.g., SET values must be JSON-encoded strings, every response must include EXPLAIN or ASK). The generated response produced a SET action with a raw JSON array value and no EXPLAIN/ASK action, but the attempt was marked successful.

**Rationale:** The current prompt/rules require a specific encoding for SET values and also require EXPLAIN/ASK actions, but the schema and generation code do not enforce or normalize those constraints. This mismatch causes either silent acceptance of nonconforming outputs or validation errors and retries.

```kotlin
/* Improve the EditorAction schema and parameter descriptions */
@AiSchema("EditorAction", "Action object for JSON edits. 'value' MUST be a JSON-encoded string.")
data class EditorAction(
  val type: String,
  val path: String,
  @AiParameter("For SET: 'value' must be a JSON-encoded string, e.g. '\"[\\\"a\\\",\\\"b\\\"]\''")
  val value: String?,
  val message: String?
)

/* Add an interceptor to normalize values into JSON-encoded strings and ensure required EXPLAIN/ASK */
addInterceptor { ctx ->
  ctx.messages.replaceAll { msg ->
    // if outgoing expected schema contains EditorAction.SET ensure 'value' is JSON-stringified
    msg
  }
}

```

### 9. [RESILIENCE] Add resilience validation that enforces the Editor rules and surface clear fallback behavior if validation fails.

**Current:** The system prompt lists multiple hard constraints (formatting rules, Editor rules) but they are not expressed as machine-validated schema constraints or resilience validators.

**Rationale:** Expressing these constraints as runtime validators ensures failed outputs are retried with clearer guidance and avoids subtle schema/prompt mismatches causing multiple ATTEMPTs.

```kotlin
resilience {
  validation { result ->
    // ensure at least one EXPLAIN or ASK action and that all SET values are JSON-encoded strings
    result.actions.any { it.type in listOf("EXPLAIN", "ASK") } &&
    result.actions.filter { it.type == "SET" }.all { isJsonEncodedString(it.value) }
  }
}

fun isJsonEncodedString(value: String?): Boolean {
  if (value == null) return true
  return try { jsonMapper.readTree(value); true } catch(e: Exception) { false }
}

```

### 10. [ANNOTATION] Update @AiParameter text to include exact encoding examples and path format so prompt generation conveys unambiguous expectations.

**Current:** The generated model prompt describes EditorAction value encoding rules but the parameter description for the user-facing method is generic ("value").

**Rationale:** Clear @AiParameter descriptions appear in the generated system prompt and schema. Making the requirement explicit at the annotation level reduces ambiguity for the model.

```kotlin
@AiParameter("Structured edit request with message, optional focusPath, operation hint, and value. For SET operations, 'value' must be a JSON-encoded string (e.g. '\"[\\\"reading\\\",\\\"hiking\\\"]\''). Specify path using JSON Pointer format.")
fun respond(request: EditRequest): JsonEditorResponse
```

### 11. [INTERCEPTOR] Implement an interceptor or lightweight tool to validate/patch responses (inject missing EXPLAIN, convert ambiguous MOVE to DELETE+SET pattern, ensure 'from'/'to' exist) to reduce user-facing failures.

**Current:** Tooling for post-processing model outputs is not used; model outputs sometimes omit required EXPLAIN/ASK actions or produce MOVE with missing 'from'/'to' in other spans.

**Rationale:** A small post-processing step can catch common structural mistakes (missing EXPLAIN/ASK, malformed MOVE fields) and either fix them or convert them to validation errors with actionable diagnostics.

```kotlin
addInterceptor { ctx ->
  // post-process the parsed JsonEditorResponse and inject a default EXPLAIN if none present
  ctx
}

// or a small tool that validates and patches response before applying edits
toolProvider(AnnotatedToolProvider(...))

```

### 12. [ANNOTATION] Update @AiParameter/@AiSchema descriptions to require explicit JSON Pointer paths for rename operations and provide concrete examples in the annotation documentation.

**Current:** The API currently asks the model to produce EditorAction MOVE operations for renames, but the schema/annotations do not enforce or clearly document required 'from' and 'to' JSON Pointer paths. The model produced a MOVE with an empty 'from' path, causing ResultValidationException.

**Rationale:** Clearer parameter annotations and examples guide the model to produce the required 'from' and 'to' fields. The LLM tends to omit fields when the schema or examples are ambiguous.

```kotlin
// Improve the API method signature and parameter docs
@AiOperation("respond", "Respond to user with structured actions")
fun respond(
  @AiParameter("Structured edit request; include explicit JSON Pointers for operations. Example: {\"op\":\"MOVE\", \"from\":\"/person/email\", \"to\":\"/person/contact_email\"}")
  request: EditRequest
): JsonEditorResponse
```

### 13. [SCHEMA] Modify the AiSchema/JSON Schema for EditorAction to include conditional requirements (if type == 'MOVE' then require 'from' and 'to') and add inline examples for each action type.

**Current:** The EditorAction schema permits a MOVE without statically requiring 'from' and 'to' fields, so validation fails at runtime when the model omits them.

**Rationale:** A stricter schema prevents generating structurally invalid actions and reduces retries caused by validation errors.

```kotlin
// Strengthen the EditorAction schema so MOVE requires 'from' and 'to'
@AiSchema("EditorAction", "Action object: type must be one of SET, DELETE, MOVE, EXPLAIN. If type == MOVE, 'from' and 'to' JSON Pointer strings are required.")
data class EditorAction(
  val type: String,
  val path: String?,
  val from: String?,
  val to: String?,
  val value: Any?,
  val message: String?
)
// Also update generated JSON Schema to include conditional required properties for MOVE
```

### 14. [RESILIENCE] Add a validation lambda that checks MOVE actions for non-empty 'from' and 'to' paths and provide a fallback EXPLAIN action instead of retrying blindly.

**Current:** Current resilience/validation runs after the LLM response and triggers retries, causing latency and duplicate prompts when fields are missing.

**Rationale:** Catching missing required fields in the validation step (or even earlier) avoids repeated failed attempts and enables graceful fallbacks that surface a clear next action to the user.

```kotlin
babelFit<MyApi> {
  resilience {
    validation { result ->
      // Pre-validate EditorAction list before accepting
      result.actions.all { a ->
        when (a.type) {
          "MOVE" -> !a.from.isNullOrBlank() && !a.to.isNullOrBlank()
          else -> true
        }
      }
    }
    fallback { error ->
      // return an EXPLAIN recommending the user provide explicit paths
      JsonEditorResponse(listOf(EditorAction(type = "EXPLAIN", path = "", message = "Please provide explicit source and target JSON Pointer paths for MOVE operations.")))
    }
  }
}
```

### 15. [INTERCEPTOR] Add an interceptor that inspects the request and current document; if a rename is requested without explicit pointers, inject a clarifying hint or automatically resolve the likely 'from' path (and only proceed if resolution is unambiguous).

**Current:** The prompt/context does not explicitly assert the document state relative to the user's request (e.g., asking to rename 'email' when only 'contact_email' exists). The model attempted a MOVE but lacked concrete pointers and may have been confused by the current document.

**Rationale:** An interceptor can enrich the prompt with explicit guidance or heuristics based on the current document, reducing ambiguity and improving the model's likelihood of returning valid actions.

```kotlin
// Interceptor that injects an explicit, canonical target when user intent is ambiguous
addInterceptor { ctx ->
  val userMessage = ctx.messages.last().content
  if (userMessage.contains("rename") && !userMessage.contains("/")) {
    // augment the schema/value with a clarifying hint
    ctx.schema = ctx.schema + "\nHint: When renaming, include 'from' and 'to' JSON Pointer paths (e.g. /person/email -> /person/contact_email)."
  }
  ctx
}
```

### 16. [SCHEMA] Tighten the EditorAction schema to mark 'from' and 'to' required (or conditionally required when type == 'MOVE'), and include short descriptions that instruct the LLM to populate them with JSON Pointers.

**Current:** The model emitted a MOVE action without a non-empty 'to' path, which failed result validation and triggered retries.

**Rationale:** The validation error shows the schema allows producing MOVE without enforcing required fields. Making constraints explicit reduces model ambiguity and validation retries.

```kotlin
/* SCHEMA: Make MOVE require non-empty 'from' and 'to' fields and provide clearer descriptions */
@AiSchema("EditorAction")
data class EditorAction(
  val type: String,
  @AiParameter("JSON Pointer source path for MOVE; required for type == 'MOVE'.")
  val from: String?,
  @AiParameter("JSON Pointer destination path for MOVE; required for type == 'MOVE'.")
  val to: String?,
  val path: String?,
  val value: Any?,
  val message: String?
)

/* Add strict validation on the schema layer (server-side) to reject MOVE with empty from/to */
```

### 17. [INTERCEPTOR] Add an interceptor that (a) maps common field names to JSON Pointers using the current document, (b) fills 'from' and 'to' for MOVE operations, and (c) asks a clarifying question if the source path isn't found.

**Current:** User instruction was a natural-language rename request. The LLM attempted a MOVE but didn't resolve the correct JSON pointers or detect that the source path didn't exist.

**Rationale:** Normalizing ambiguous natural-language edits into explicit from/to paths avoids the model guessing or omitting required fields.

```kotlin
/* INTERCEPTOR: Normalize user edit requests into explicit operations before sending to the model */
addInterceptor { ctx ->
  val msg = ctx.messages.last().content
  if (msg.contains("Rename") && msg.contains("\"email\"")) {
    // resolve candidate source paths from current document
    val source = resolvePath(ctx.document, listOf("/person/email", "/person/contact_email"))
    val target = "/person/contact_email"
    ctx.parameters = mapOf("operation" to "MOVE", "from" to source, "to" to target)
  }
  ctx
}

fun resolvePath(doc: JsonNode, candidates: List<String>): String? { /* return first existing pointer or null */ }
```

### 18. [ANNOTATION] Update @AiParameter/@AiOperation descriptions to require explicit pointer resolution and to mandate asking questions when the source path is missing.

**Current:** The @AiParameter/@AiOperation descriptions do not instruct the model to verify source existence or to prefer SET+DELETE when MOVE is unsafe.

**Rationale:** Clearer parameter-level instructions help the LLM follow the expected behavior (resolve pointers, validate existence, or ask) and avoid output that fails schema validation.

```kotlin
/* ANNOTATION: Improve parameter guidance so the model validates existence before MOVE */
@AiOperation("Rename a field in the JSON document")
fun respond(
  @AiParameter("Message describing the edit. The model MUST: 1) resolve JSON Pointers for 'from' and 'to', 2) verify 'from' exists, 3) if 'from' does not exist, ASK a clarifying question rather than emitting invalid MOVE.") request: EditRequest
): JsonEditorResponse
```

### 19. [RESILIENCE] Add validation hooks that reject obviously invalid MOVE actions before retrying, and implement a fallback that converts failures into EXPLAIN/ASK actions instead of blind retries.

**Current:** Retries occurred due to ResultValidationException; validation only runs after the model response, causing wasted attempts.

**Rationale:** Failing early with clear validation and providing a fallback avoids repeated ATTEMPT spans and surfaces a user-facing clarification step.

```kotlin
/* RESILIENCE: Add pre-send validation and a fast-fail fallback to avoid repeated invalid attempts */
resilience {
  maxRetries = 2
  validation { result ->
    // perform lightweight structural checks before accepting result
    result.actions.all { action ->
      !(action.type == "MOVE" && (action.from.isNullOrBlank() || action.to.isNullOrBlank()))
    }
  }
  fallback { error -> JsonEditorResponse(actions = listOf(EditorAction(type = "EXPLAIN", message = "Could not perform rename: missing paths. Ask for clarification."))) }
}

```

### 20. [SCHEMA] Replace a single loose EditorAction schema with a sealed-class / oneOf representation exposing SetAction, MoveAction, DeleteAction. Ensure MoveAction declares both 'from' and 'to' as required.

**Current:** The LLM emitted a MOVE action without supplying a non-empty 'to' path, causing ResultValidationException. The schema and prompt do not enforce or clearly describe required fields for MOVE vs SET/DELETE when renaming.

**Rationale:** Using concrete subclasses makes required fields explicit in the generated JSON schema. The LLM is less likely to omit 'to' for MOVE if the schema shows MoveAction requires both 'from' and 'to'.

```kotlin
/* Define explicit action subclasses so the model knows required fields */
@AiSchema("An editor action; use subclasses for different action types")
sealed class EditorAction

@AiSchema("Set a value at a path")
data class SetAction(@AiParameter("JSON Pointer path to set") val path: String,
                     @AiParameter("JSON-encoded value") val value: String) : EditorAction()

@AiSchema("Move a node from one path to another (both required)")
data class MoveAction(@AiParameter("source JSON Pointer") val from: String,
                      @AiParameter("destination JSON Pointer") val to: String) : EditorAction()

@AiSchema("Delete a node at path")
data class DeleteAction(@AiParameter("JSON Pointer path to delete") val path: String) : EditorAction()
```

### 21. [ANNOTATION] Update @AiOperation/@AiParameter texts to explicitly require ASK or EXPLAIN when the requested source path doesn't exist or when intent is ambiguous (e.g., rename but no 'email' field).

**Current:** The @AiParameter description for the user's edit request is minimal and doesn't instruct the model how to behave when the source path doesn't exist (e.g., ask clarifying question, use SET+DELETE fallback).

**Rationale:** Annotations drive the system prompt that the model sees. Making the desired fallback/clarification behavior explicit reduces ambiguous outputs and validation failures.

```kotlin
interface JsonEditorApi {
  @AiOperation("Rename a field or perform requested edits; if a source path is missing, ASK the user before making changes.")
  fun respond(@AiParameter("Structured edit request. If operation is 'rename' and the source path does not exist, the model MUST emit an ASK action explaining the missing path.") value: EditRequest): JsonEditorResponse
}
```

### 22. [INTERCEPTOR] Add an interceptor that inspects the document and the user's instruction and adds explicit guidance into the prompt when requested source paths are missing.

**Current:** The PromptContext lacks pre-checks against the current document; the LLM is asked to perform a rename even though the document already contains contact_email and no 'email' field.

**Rationale:** Injecting document-aware hints prevents the model from blindly emitting invalid MOVE actions and encourages it to ASK or choose a safe fallback.

```kotlin
addInterceptor { ctx ->
  // run lightweight doc analysis and inject guidance
  val exists = checkPathExists(currentDocument, "/person/email")
  if (!exists && ctx.latestUserMessage.contains("rename")) {
    ctx.messages.add(systemMessage("NOTE: '/person/email' does not exist in the document. If you intended to rename, ASK the user to confirm or emit a SET+DELETE fallback."))
  }
  ctx
}
```

### 23. [RESILIENCE] Add validation rules that specifically check MOVE action fields and provide a fallback ASK response instead of silent retries.

**Current:** Retries failed due to validation errors; resilience config does not provide a targeted validator that detects missing 'to' paths specifically and offers a safe fallback action.

**Rationale:** A targeted validator prevents repeated useless retries and surfaces a human-friendly fallback when semantic preconditions fail.

```kotlin
resilience {
  validation { result ->
    // Ensure MoveAction always has non-empty 'from' and 'to'
    result.actions.all { action ->
      when(action) {
        is MoveAction -> action.from.isNotBlank() && action.to.isNotBlank()
        else -> true
      }
    }
  }
  fallback { error ->
    // Return an ASK action asking user to clarify
    JsonEditorResponse(actions = listOf(AskAction(message = "I cannot find the source path '/person/email'. Do you want to create a new 'contact_email' or rename an existing field?")))
  }
}
```

### 24. [TOOL] Expose a tool that checks JSON path existence and suggests safe edit patterns (SET+DELETE fallback). Allow the model to call it in the request flow.

**Current:** No tooling available to the model to verify or compute JSON Pointer existence. The model must guess document state from the prompt, increasing error risk.

**Rationale:** Exposing a pathExists tool allows the model to call a deterministic check and return a valid MOVE only when both paths are appropriate, or to ASK otherwise.

```kotlin
class JsonTools {
  fun pathExists(document: JsonNode, path: String): Boolean { /* implement */ }
  fun suggestRenameActions(from: String, to: String): List<EditorAction> = listOf(MoveAction(from, to))
}
// register with babelFit toolProvider(AnnotatedToolProvider(JsonTools()))

```

### 25. [CONVERSATION] Change conversational policy to require ASK when the requested edit references missing nodes, and reflect that in @AiOperation descriptions so the model sees it in the system prompt.

**Current:** The agent proceeded to produce edits rather than asking for clarification when the user's rename request targeted a non-existent 'email' field.

**Rationale:** Requiring clarification avoids making destructive or invalid edits and reduces validation failures.

```kotlin
Conversation guideline: If user requests rename and source path is absent in the provided document, the assistant MUST emit an ASK action with a one-line clarifying question before producing edit actions.

Example ASK action: AskAction(path="", message="I cannot find '/person/email' in the document. Do you want to rename a different field or add 'contact_email' with the same value?")
```

### 26. [SCHEMA] Add @AiSchema/@AiParameter annotations for EditorAction and the response type, defining required fields per action type; update method @AiOperation description to reference these concrete types.

**Current:** resultSchema is a single generic field { actions: [ ... ] } and EditorAction shape is not declared in the schema/annotations.

**Rationale:** Explicit schemas make the expected JSON structure unambiguous to the model and the SDK validator. The current generic 'actions' array leaves the model guessing which fields are required for each action type, causing validation errors (e.g., MOVE without from/to).

```kotlin
/* Kotlin: declare explicit schemas for EditorAction and JsonEditorResponse */
@AiSchema("EditorAction", "An action to edit or explain part of the JSON document")
data class EditorAction(
  @AiParameter("One of: SET, DELETE, MOVE, EXPLAIN, ASK") val type: String,
  @AiParameter("JSON Pointer path for the action target (e.g. /person/address)") val path: String?,
  @AiParameter("Required for MOVE: source path") val from: String?,
  @AiParameter("Required for MOVE: destination path") val to: String?,
  @AiParameter("JSON-encoded value for SET operations") val value: String?,
  @AiParameter("Human-facing message for EXPLAIN/ASK actions") val message: String?
)

@AiSchema("JsonEditorResponse", "Response containing an ordered list of EditorAction objects")
data class JsonEditorResponse(val actions: List<EditorAction>)
```

### 27. [INTERCEPTOR] Consolidate high-level instructions into the systemInstruction once (via interceptor) and keep per-call input minimal. Add a validation lambda that enforces required fields per action type so the SDK can reject invalid outputs before they propagate.

**Current:** System prompt and instruction text are long and repeated across attempts; validation errors show missing required fields for MOVE actions.

**Rationale:** Duplicated or verbose system prompts increase token usage and can lead to inconsistent instructions across attempts. Embedding validation rules in resilience prevents invalid attempts from being accepted and triggers retry/fallback logic earlier.

```kotlin
/* Kotlin: move static instructions to a single system instruction and add per-request concise prompt via interceptor */
babelFit<MyApi> {
  adapter(openAi)
  // single system instruction set once
  interceptor { ctx ->
    ctx.systemInstructions = "You are a JSON editor assistant. Return JsonEditorResponse(actions=[EditorAction...]). Follow the EditorAction schema exactly."
  }
  resilience {
    validation { result ->
      // example validation ensuring required fields for MOVE
      val actions = (result as JsonEditorResponse).actions
      actions.all { a ->
        when (a.type) {
          "MOVE" -> !a.from.isNullOrBlank() && !a.to.isNullOrBlank()
          "SET"  -> !a.path.isNullOrBlank() && a.value != null
          else -> true
        }
      }
    }
  }
}
```

### 28. [ANNOTATION] Clarify in @AiOperation that EXPLAIN/ASK messages must be plain text, avoid markdown, and enumerate required fields per action. This reduces formatting inconsistencies and validation failures.

**Current:** Method parameter description asks for 'structured edit request' but doesn't enumerate operation types or required fields per operation, so the model produces EXPLAIN content with Markdown and line breaks.

**Rationale:** Explicit operation descriptions reduce ambiguity about formatting (e.g., markdown vs plain text) and required fields. The model used bold formatting and extra markup which may not be desired by downstream consumers.

```kotlin
/* Kotlin: make @AiOperation description explicit and constrain formatting */
@AiOperation(
  summary = "Respond with JsonEditorResponse",
  description = "Return JsonEditorResponse. Allowed EditorAction.type values: SET, DELETE, MOVE, EXPLAIN, ASK. For EXPLAIN/ASK, message must be plain text (no markdown). For SET, value must be a JSON-encoded string. Do NOT include any fields beyond EditorAction."
)
fun respond(@AiParameter("User message controlling the edit or query") value: JsonEditorRequest): JsonEditorResponse
```

### 29. [ANNOTATION] Update the @AiOperation description to explicitly state the EXPLAIN/ASK requirement and common per-action invariants so the model is guided at the prompt level.

**Current:** The generated response correctly produced a DELETE EditorAction with path '/person/address/state', matching the user's intent to remove that field.

**Rationale:** Making the EXPLAIN/ASK requirement explicit in the operation annotation ensures the LLM and any generated prompts emphasize that rule. The description is part of the system prompt generated by BabelFit, so clarifying constraints here reduces ambiguous responses and validation retries.

```kotlin
/* Improve operation-level guidance in the API annotation */
@AiOperation(
  summary = "Edit JSON document",
  description = "Return a sequence of EditorAction objects. IMPORTANT: Every response MUST include at least one EXPLAIN or ASK action when changing the document, and action fields must respect per-type constraints (e.g., MOVE requires non-empty 'from' and 'to')."
)
fun respond(...): JsonEditorResponse
```

### 30. [SCHEMA] Refactor the EditorAction return types into explicit subclasses annotated with @AiSchema so the JSON schema enforces required fields per action. This reduces validation failures and unnecessary retries.

**Current:** The runtime schema for EditorAction is permissive, allowing actions without strong per-type validation (e.g., MOVE missing 'from'/'to' caused validation errors earlier).

**Rationale:** A stricter Kotlin model leads to a generated JSON schema that can be used by BabelFit to validate responses and reject malformed attempts before consuming retries. It prevents common mistakes like empty 'from'/'to' on MOVE actions.

```kotlin
/* Strengthen the Kotlin schema so generated JSON schema enforces per-action constraints */
@AiSchema("EditorAction with per-type constraints")
sealed class EditorAction {
  abstract val type: String
  abstract val path: String?
}

@AiSchema("Delete action")
data class DeleteAction(override val type: String = "DELETE", override val path: String) : EditorAction()

@AiSchema("Move action")
data class MoveAction(override val type: String = "MOVE", override val path: String?, val from: String, val to: String) : EditorAction()
```

### 31. [RESILIENCE] Add a resilience.validation block that checks for EXPLAIN/ASK presence and per-action required fields. Combine this with a helpful fallback to a structured error response if validation keeps failing.

**Current:** Resilience config retried requests when validation failed, but there was no targeted validation that flags missing EXPLAIN/ASK or per-action invariants early.

**Rationale:** A custom validation function prevents wasted attempts by rejecting responses that omit required EXPLAIN/ASK actions or violate per-action constraints, triggering a retry with clearer guidance to the model.

```kotlin
resilience {
  maxRetries = 2
  validation { result ->
    // Example validation: require at least one EXPLAIN or ASK when any non-EXPLAIN action present
    val actions = result.actions
    val hasExplainOrAsk = actions.any { it.type == "EXPLAIN" || it.type == "ASK" }
    val modifies = actions.any { it.type != "EXPLAIN" && it.type != "ASK" }
    hasExplainOrAsk || !modifies
  }
}
```

## Prompt Improvement Suggestions

### 1. SCHEMA_DESIGN

- **Current:** Use a single, generic JSON-Editor ruleset and rely on the model to infer required action fields (e.g., MOVE needs 'from' and 'to').
- **Suggested:** Make action schemas operation-specific and include minimal, concrete examples for each action type in the prompt. For MOVE, show an explicit example: {"type":"MOVE","path":"/person/contact_email","from":"/person/email","to":"/person/contact_email"}. Consider adding a dedicated 'RENAME' action to avoid MOVE ambiguity or require the model to emit SET+DELETE when renaming.
- **Rationale:** Validation failures repeatedly show the model emitted MOVE actions missing required 'from'/'to' values. The schema/rules are too generic and the model is not reliably inferring operation-specific required fields.
- **Impact:** HIGH — should substantially reduce validation failures and retries.

### 2. systemInstruction

- **Current:** Provide a long system prompt on every attempt and expect the model to follow many global rules (e.g., every response MUST include EXPLAIN/ASK; batch edits into one response).
- **Suggested:** Move heavy system-level instructions into a memorized or persistent system context (use @Memorize or a persistent preamble) and keep per-request prompts minimal. Only include rules that are strictly required for that request.
- **Rationale:** Trace shows duplicate prompts across attempts and overall token waste. Re-sending large system text on retries is inefficient and may confuse the model with repeated constraints.
- **Impact:** HIGH — reducing prompt size and duplications will lower token waste and improve latency.

### 3. rules

- **Current:** Enforce that every response MUST include an EXPLAIN or ASK action and that all edits be batched into a single response.
- **Suggested:** Make EXPLAIN/ASK conditional: require them only when the model needs user confirmation or when the requested edit is ambiguous. Permit multiple-step responses when edits are logically sequential instead of forcing all edits into one batch.
- **Rationale:** The mandatory EXPLAIN/ASK rule forces the model to produce explanatory actions even when not needed for a small edit. This increases complexity and chance of producing invalid action payloads.
- **Impact:** MEDIUM — clarifying when explanations/questions are necessary will reduce forced, potentially irrelevant EXPLAIN actions and make responses cleaner.

### 4. validation

- **Current:** Rely solely on model validation during attempts and retry a few times when validation fails.
- **Suggested:** Add a lightweight post-output validator/repairer that: (a) detects missing required fields for known action types and either injects them from context (e.g., infer 'from' path for renames) or converts ambiguous MOVE into safe SET+DELETE steps; (b) returns a clarifying ASK only when automatic repair is unsafe.
- **Rationale:** Several attempts failed due to simple structural omissions (missing 'from'/'to'). These could be fixed deterministically without full LLM retries.
- **Impact:** MEDIUM — pre-processing and automated correction will reduce retries and failed attempts.

### 5. prompt

- **Current:** Provide free-form user messages asking for operations like 'rename the "email" field to "contact_email"' without specifying current/target paths.
- **Suggested:** In per-request parameters include the focusPath or current path hint when asking for renames, or require the model to use JSON Pointer paths in its actions. Example: provide focusPath:"/person" and instruct: when renaming, emit from:"/person/email" to:"/person/contact_email".
- **Rationale:** Ambiguous natural-language instructions (rename email -> contact_email) led the model to choose MOVE but omit required fields or produce invalid moves because the document already used contact_email earlier. The model lacked an explicit mapping of field locations.
- **Impact:** MEDIUM — clearer operation-level guidance reduces model ambiguity.

## Agent Prompt

Use the following prompt with a coding agent or as a guide for manual fixes:

````
You are implementing fixes to the BabelFit JSON editor flow. Apply the following concrete, actionable changes so the SDK produces valid editor actions, reduces validation retries, and provides safe fallbacks.

1) Schema changes (high priority)
- Replace the loose 'actions' array schema with an explicit @AiSchema union/sealed-class (oneOf) for EditorAction variants: SetAction { type: 'SET', path: string, value: string (JSON-encoded), message?: string }, DeleteAction { type: 'DELETE', path: string, message?: string }, MoveAction { type: 'MOVE', from: string, to: string, message?: string }, ExplainAction { type: 'EXPLAIN', path?: string, message: string }, AskAction { type: 'ASK', path?: string, message: string }.
- In SetAction schema description require value to be a JSON-encoded string and include short examples: '"hello"', '42', '["a","b"]', '{"k":1}'. Make validators expect a quoted string containing JSON.
- Add conditional requirements: if type == 'MOVE' -> require both 'from' and 'to'. Provide an explicit example for each action in the @AiSchema descriptions.

2) Prompt / annotation improvements
- Update @AiOperation and @AiParameter descriptions to (a) include the machine-friendly EditorAction shape and examples near the top, (b) specify JSON Pointer rules (root allowed as '' or '/'), and (c) require ASK/EXPLAIN when the requested path is missing or ambiguous (e.g., rename request without explicit pointers).
- Make the 'every response MUST include EXPLAIN or ASK' guidance explicit but pragmatic: require ASK/EXPLAIN only when ambiguity/path-existence issues arise; otherwise allow a single EXPLAIN summarizing the batched edits.

3) Interceptor: persistent system instructions + machine spec
- Implement an interceptor that stores heavy systemInstructions once into PromptContext.systemInstructions (so attempts reuse it and duplicate prompts are eliminated). The interceptor should append a one-line, machine-readable EditorAction JSON schema and two short action examples into the per-call prompt rather than embedding the entire long rule set each attempt.
- Example appended line: "EditorAction shape: SetAction(value must be a JSON-encoded string), MoveAction(requires from,to), DeleteAction(path required). Examples: {\"type\":\"SET\",\"path\":\"/person/name\",\"value\":\"\"\"Alice\"\"\"}."

4) ToolProvider: path resolution & existence checks
- Add a small tool with functions: exists(pointer): bool, resolveByName(name): string[] (returns candidate paths), and bestResolveForRename(srcName, dstName): {from?:string, to:string}. Expose these tools so the model or an assistant routine can call them before emitting MOVE.
- Use this tool to automatically resolve ambiguous 'rename' requests; if the source path cannot be resolved unambiguously, produce an ASK action asking the user to confirm.

5) Response post-processing and automatic repairs (safe normalization)
- Implement a post-processor that validates model output and performs minimal, safe normalization:
  - JSON-encode SET.value when the model returned a native structure but it is unambiguous to encode.
  - If a MOVE misses 'from' or 'to' but a safe resolution exists via the tool (single candidate), fill them in automatically and add an EXPLAIN action noting the inferred paths.
  - If MOVE is ambiguous (multiple candidates) produce a single ASK action asking which path to move from, instead of retrying.
  - If MOVE seems like a rename and source path is missing but a safe fallback is to SET(dst) and DELETE(src), convert accordingly and document via EXPLAIN.
- Only perform normalization when changes are deterministic and safe; otherwise fail validation and emit ASK.

6) Resilience / validation config (enforce invariants and fallback)
- Add a resilience.validation lambda that enforces:
  - Each action matches its schema and required fields are present (MoveAction.from/to non-empty, DeleteAction.path non-root, etc.).
  - SET.value is a JSON-encoded string (validate by attempting parse of the string value into JSON).
  - At least one EXPLAIN or ASK present when the request includes ambiguous instructions or when the validator had to auto-fill fields.
- On validation failure, do not blindly retry the LLM multiple times. Instead return a single structured ASK action with a clear, machine-friendly question that the client can present to the user (e.g., "I couldn't find '/person/email'. Did you mean '/person/contact_email' or '/person/email'? Reply with the correct source path or confirm auto-rename.").
- Configure maxRetries to a small number (e.g., 1-2) with backoff. Treat validation failures as triggers to produce ASK rather than unbounded retries.

7) Annotation & developer ergonomics
- Add concrete examples to the Kotlin @AiSchema and @AiOperation annotations so generated system prompts are unambiguous. Keep the long-form human guidance in code comments and the short machine spec in the annotation text.
- Document the new tool functions and sample use cases (rename resolution, SET value encoding) in the project README and in the annotation descriptions.

8) Tests and rollout
- Add unit tests exercising: invalid MOVE (missing fields) -> ASK fallback; ambiguous rename -> ASK; SET with object literal -> post-processor encodes value; successful edit emits proper EXPLAIN summarizing edits.
- Gradually roll out: enable strict validation in dev mode first, review logs for ASK frequency, then tighten automation rules for safe auto-resolve cases.

Deliverables / code pointers to implement now:
- Update EditorAction @AiSchema definitions in the Kotlin model package (replace free-text actions schema with typed classes and examples).
- Implement the interceptor that persists system instructions and appends the compact machine spec to per-call prompt - place in babelFit{ addInterceptor(...) } setup.
- Add the path-resolution ToolProvider and expose it via babelFit{ toolProvider(...) } so the model or agent can call it.
- Implement resilience.validation lambda to assert per-action invariants and return structured ASK on failure; adjust maxRetries/backoff.
- Add a response post-processor in an interceptor or SDK layer to normalize SET.value and fill safe MOVE fields.

Use these changes together: typed schemas plus prompt-level machine spec reduce model ambiguity; tools and interceptors reduce path-resolution errors; validation + ASK fallback prevents wasted retries and token duplication; post-processing recovers from safe, minor model mistakes.
````

**Key Changes:**
- Refactor EditorAction schema into explicit, typed variants (SetAction, DeleteAction, MoveAction, ExplainAction, AskAction) and mark operation-specific required fields (e.g., MoveAction requires 'from' and 'to').
- Change SET.value to be a JSON-encoded string in the @AiSchema (string containing a JSON value) and add inline encoding examples in the schema description.
- Update @AiOperation/@AiParameter texts to include machine-friendly shape examples, explicit JSON Pointer guidance, and a rule: ASK/EXPLAIN must be emitted whenever an edit is ambiguous or a referenced path is missing.
- Add an interceptor that consolidates heavy systemInstructions once (persisted in PromptContext.systemInstructions) and appends a single-line machine-readable EditorAction shape and examples to each request.
- Add a ToolProvider exposing path-existence and pointer-resolution helpers the model can call (e.g., resolvePointer(document, fieldName) -> candidatePaths) and allow the model to use them in the prompt flow.
- Implement resilience.validation that enforces per-action invariants (e.g., MoveAction.from/to non-empty, SET.value is JSON-encoded string, at least one EXPLAIN/ASK present); on validation failure return a targeted ASK action instead of blind retries.
- Add a response post-processor (interceptor or SDK-layer) that normalizes/JSON-encodes SET.value when safely inferable and, where safe, converts incomplete MOVE into an explicit SET+DELETE pair or injects a clarifying ASK/EXPLAIN.
- Tighten retry policy (lower maxRetries, add short backoff) to avoid repeated invalid attempts and ensure validation failures lead to ASK fallback rather than repeated LLM retries.

