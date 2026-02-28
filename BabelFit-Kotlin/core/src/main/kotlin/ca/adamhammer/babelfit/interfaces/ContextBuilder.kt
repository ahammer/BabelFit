package ca.adamhammer.babelfit.interfaces

import ca.adamhammer.babelfit.model.PromptContext
import ca.adamhammer.babelfit.model.BabelFitRequest

/**
 * Builds a [PromptContext] from a raw [BabelFitRequest].
 *
 * The default implementation ([ca.adamhammer.babelfit.context.DefaultContextBuilder])
 * assembles a system preamble and JSON method invocation from annotations.
 * Replace it to fully control how prompts are constructed.
 */
interface ContextBuilder {
    fun build(request: BabelFitRequest): PromptContext
}
