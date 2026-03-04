package ca.adamhammer.babelfit.debug.trace

import ca.adamhammer.babelfit.context.TraceContextKeys
import ca.adamhammer.babelfit.interfaces.ApiAdapter
import ca.adamhammer.babelfit.interfaces.ToolProvider
import ca.adamhammer.babelfit.model.PromptContext
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

class TracingAdapter(
    private val delegate: ApiAdapter,
    private val session: TraceSession
) : ApiAdapter {

    private fun getAttemptId(context: PromptContext): String {
        val requestId = context[TraceContextKeys.CURRENT_SPAN_ID] ?: "unknown-request"
        val attempt = context[TraceContextKeys.CURRENT_ATTEMPT] ?: 1
        return "$requestId-attempt-$attempt"
    }

    override suspend fun <R : Any> handleRequest(context: PromptContext, resultClass: KClass<R>): R {
        return handleRequest(context, resultClass, emptyList())
    }

    override suspend fun <R : Any> handleRequest(
        context: PromptContext,
        resultClass: KClass<R>,
        toolProviders: List<ToolProvider>
    ): R {
        val attemptId = getAttemptId(context)
        val wrappedProviders = toolProviders.map { TracingToolProvider(it, session, attemptId) }
        return delegate.handleRequest(context, resultClass, wrappedProviders)
    }

    override fun handleRequestStreaming(context: PromptContext): Flow<String> {
        return handleRequestStreaming(context, emptyList())
    }

    override fun handleRequestStreaming(
        context: PromptContext,
        toolProviders: List<ToolProvider>
    ): Flow<String> {
        val attemptId = getAttemptId(context)
        val wrappedProviders = toolProviders.map { TracingToolProvider(it, session, attemptId) }
        return delegate.handleRequestStreaming(context, wrappedProviders)
    }
}
