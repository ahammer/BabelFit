package ca.adamhammer.babelfit

import ca.adamhammer.babelfit.adapters.CachingAdapter
import ca.adamhammer.babelfit.context.DefaultContextBuilder
import ca.adamhammer.babelfit.context.InMemoryStore
import ca.adamhammer.babelfit.interfaces.ApiAdapter
import ca.adamhammer.babelfit.interfaces.ContextBuilder
import ca.adamhammer.babelfit.interfaces.ConversationManager
import ca.adamhammer.babelfit.interfaces.Interceptor
import ca.adamhammer.babelfit.interfaces.MemoryStore
import ca.adamhammer.babelfit.interfaces.RequestListener
import ca.adamhammer.babelfit.interfaces.SlidingWindowConversation
import ca.adamhammer.babelfit.interfaces.ToolProvider
import ca.adamhammer.babelfit.interfaces.TypeAdapter
import ca.adamhammer.babelfit.interfaces.UnboundedConversation
import ca.adamhammer.babelfit.model.PromptContext
import ca.adamhammer.babelfit.model.ResiliencePolicy
import ca.adamhammer.babelfit.model.BabelFitConfigurationException
import ca.adamhammer.babelfit.model.TypeAdapterRegistry
import java.lang.reflect.Proxy
import kotlin.reflect.KClass

/**
 * A configured BabelFit proxy instance.
 *
 * @param T the interface type this instance implements
 * @param api the dynamic proxy implementing [T], backed by the configured adapter
 * @param memoryStore the memory store for persisting results across calls
 * @param klass the [KClass] of the interface type
 */
class BabelFitInstance<T : Any>(
    val api: T,
    val memoryStore: MemoryStore,
    val klass: KClass<T>,
    val usage: UsageTracker = UsageTracker()
) {
    /**
     * Returns the memory store backing this instance.
     * Intended for intra-library use only (e.g., agent dispatchers that need
     * to share memory across multiple [BabelFitInstance]s).
     */
    fun writableMemory(): MemoryStore = memoryStore
}

@DslMarker
annotation class BabelFitDsl

/**
 * Builder for creating a BabelFit proxy. Supports both DSL and fluent Java-style APIs.
 *
 * DSL usage:
 * ```kotlin
 * val instance = babelFit<MyAPI> {
 *     adapter(OpenAiAdapter())
 *     resilience {
 *         maxRetries = 2
 *         timeoutMs = 30_000
 *     }
 *     interceptor { ctx -> ctx.copy(systemInstructions = ctx.systemInstructions + "\nBe concise.") }
 * }
 * ```
 */
@BabelFitDsl
class BabelFitBuilder<T : Any>(private val apiInterface: KClass<T>) {
    private var adapter: ApiAdapter? = null
    private var contextBuilder: ContextBuilder = DefaultContextBuilder()
    private val interceptors = mutableListOf<Interceptor>()
    private val toolProviders = mutableListOf<ToolProvider>()
    private val listeners = mutableListOf<RequestListener>()
    private var resiliencePolicy: ResiliencePolicy = ResiliencePolicy()
    private val typeAdapterRegistry = TypeAdapterRegistry()
    private var cacheConfig: CacheConfig? = null
    private var conversationManager: ConversationManager? = null

    // ── DSL-style configuration ─────────────────────────────────────────────

    /** Set the adapter instance. */
    fun adapter(adapter: ApiAdapter): BabelFitBuilder<T> {
        this.adapter = adapter
        return this
    }

    /** Set a routing adapter that chooses an adapter based on the prompt context. */
    fun adapter(router: (PromptContext) -> ApiAdapter): BabelFitBuilder<T> {
        this.adapter = ca.adamhammer.babelfit.adapters.RoutingAdapter(router)
        return this
    }

    /** Set a custom context builder. */
    fun contextBuilder(builder: ContextBuilder): BabelFitBuilder<T> {
        this.contextBuilder = builder
        return this
    }

    /** Add an interceptor via lambda. */
    fun interceptor(block: (PromptContext) -> PromptContext): BabelFitBuilder<T> {
        this.interceptors.add(Interceptor { block(it) })
        return this
    }

    /** Configure resilience policy via DSL block. */
    fun resilience(block: ResiliencePolicyBuilder.() -> Unit): BabelFitBuilder<T> {
        this.resiliencePolicy = ResiliencePolicyBuilder().apply(block).build()
        return this
    }

    /** Set a pre-built resilience policy. */
    fun resilience(policy: ResiliencePolicy): BabelFitBuilder<T> {
        this.resiliencePolicy = policy
        return this
    }

    /** Register a tool provider. Tools from all providers are made available to adapters. */
    fun toolProvider(provider: ToolProvider): BabelFitBuilder<T> {
        this.toolProviders.add(provider)
        return this
    }

    /** Register multiple tool providers. */
    fun toolProviders(providers: List<ToolProvider>): BabelFitBuilder<T> {
        this.toolProviders.addAll(providers)
        return this
    }

    /** Register a request lifecycle listener for metrics, logging, or cost tracking. */
    fun listener(listener: RequestListener): BabelFitBuilder<T> {
        this.listeners.add(listener)
        return this
    }

    /** Enable response caching with configurable TTL and max entries. */
    fun cache(block: CacheConfig.() -> Unit = {}): BabelFitBuilder<T> {
        this.cacheConfig = CacheConfig().apply(block)
        return this
    }

    /** Set a conversation manager for multi-turn history tracking. */
    fun conversation(manager: ConversationManager): BabelFitBuilder<T> {
        this.conversationManager = manager
        return this
    }

    /** Configure conversation management via DSL block. */
    fun conversation(block: ConversationConfig.() -> Unit): BabelFitBuilder<T> {
        this.conversationManager = ConversationConfig().apply(block).build()
        return this
    }

    /**
     * Register a [TypeAdapter] that bridges an external POJO to a `@Serializable` mirror type.
     *
     * When BabelFit encounters the POJO as a parameter or return type, it will
     * transparently swap it for the mirror type during schema generation, serialization,
     * and deserialization, then convert back to the POJO for the caller.
     */
    fun typeAdapter(adapter: TypeAdapter<*, *>): BabelFitBuilder<T> {
        typeAdapterRegistry.register(adapter)
        return this
    }

    // ── Legacy Java-style API (kept for backward compatibility) ─────────────

    fun <U : ApiAdapter> setAdapterClass(adapterClass: KClass<U>): BabelFitBuilder<T> {
        val ctor = adapterClass.constructors.firstOrNull { it.parameters.all { p -> p.isOptional } }
            ?: throw IllegalArgumentException(
                "${adapterClass.simpleName} has no no-arg or all-optional constructor"
            )
        this.adapter = ctor.callBy(emptyMap())
        return this
    }

    fun setAdapterDirect(adapter: ApiAdapter): BabelFitBuilder<T> = adapter(adapter)

    fun setContextBuilder(builder: ContextBuilder): BabelFitBuilder<T> = contextBuilder(builder)

    fun addInterceptor(interceptor: Interceptor): BabelFitBuilder<T> {
        this.interceptors.add(interceptor)
        return this
    }

    fun setResiliencePolicy(policy: ResiliencePolicy): BabelFitBuilder<T> = resilience(policy)

    // ── Build ───────────────────────────────────────────────────────────────

    fun build(): BabelFitInstance<T> {
        var resolvedAdapter = adapter
            ?: throw BabelFitConfigurationException("Adapter must be provided. Use adapter(...) or setAdapterDirect(...)")

        cacheConfig?.let { config ->
            resolvedAdapter = CachingAdapter(resolvedAdapter, config.ttlMs, config.maxEntries)
        }

        // Conversation manager is prepended as an interceptor so history is injected first
        val allInterceptors = buildList {
            conversationManager?.let { add(it) }
            addAll(interceptors)
        }

        val memoryStore = InMemoryStore()
        val usageTracker = UsageTracker()
        val allListeners = listeners.toList() + usageTracker

        val babelfit = BabelFit(
            resolvedAdapter, contextBuilder, allInterceptors,
            resiliencePolicy, toolProviders.toList(), memoryStore, apiInterface,
            allListeners, typeAdapterRegistry, conversationManager
        )

        val proxyInstance = Proxy.newProxyInstance(
            apiInterface.java.classLoader,
            arrayOf(apiInterface.java),
            babelfit
        )

        return BabelFitInstance(apiInterface.java.cast(proxyInstance), memoryStore, apiInterface, usageTracker)
    }
}

/**
 * DSL builder for [ResiliencePolicy].
 */
@BabelFitDsl
class ResiliencePolicyBuilder {
    var maxRetries: Int = 0
    var retryDelayMs: Long = 1000
    var backoffMultiplier: Double = 2.0
    var timeoutMs: Long = 0
    var resultValidator: ((Any) -> Any)? = null
    var fallbackAdapter: ApiAdapter? = null
    var maxConcurrentRequests: Int = 0
    var maxRequestsPerMinute: Int = 0

    fun build() = ResiliencePolicy(
        maxRetries = maxRetries,
        retryDelayMs = retryDelayMs,
        backoffMultiplier = backoffMultiplier,
        timeoutMs = timeoutMs,
        resultValidator = resultValidator,
        fallbackAdapter = fallbackAdapter,
        maxConcurrentRequests = maxConcurrentRequests,
        maxRequestsPerMinute = maxRequestsPerMinute
    )
}

/**
 * Configuration for response caching.
 *
 * @property ttlMs time-to-live for cache entries in milliseconds (default: 5 minutes)
 * @property maxEntries maximum number of cached entries (default: 100)
 */
@BabelFitDsl
class CacheConfig {
    var ttlMs: Long = 300_000
    var maxEntries: Int = 100
}

/**
 * DSL for configuring conversation management.
 *
 * ```kotlin
 * babelFit<MyAPI> {
 *     adapter(myAdapter)
 *     conversation {
 *         maxMessages = 20  // sliding window; omit for unbounded
 *     }
 * }
 * ```
 */
@BabelFitDsl
class ConversationConfig {
    /** Maximum number of messages to retain. `0` means unbounded. */
    var maxMessages: Int = 0

    internal fun build(): ConversationManager =
        if (maxMessages > 0) SlidingWindowConversation(maxMessages)
        else UnboundedConversation()
}

/**
 * Top-level DSL entry point for creating a BabelFit proxy.
 *
 * ```kotlin
 * val instance = babelFit<MyAPI> {
 *     adapter(OpenAiAdapter())
 *     resilience { maxRetries = 2 }
 * }
 * val api = instance.api
 * ```
 */
inline fun <reified T : Any> babelFit(block: BabelFitBuilder<T>.() -> Unit): BabelFitInstance<T> {
    return BabelFitBuilder(T::class).apply(block).build()
}
