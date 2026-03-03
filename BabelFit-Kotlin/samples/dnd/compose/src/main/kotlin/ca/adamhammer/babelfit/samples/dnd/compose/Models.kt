package ca.adamhammer.babelfit.samples.dnd.compose

typealias ModelOption = ca.adamhammer.babelfit.samples.common.ModelOption
typealias Vendor = ca.adamhammer.babelfit.samples.common.Vendor

enum class AppScreen {
    SETUP,
    PLAYING
}

data class CharacterDraft(
    val name: String = "",
    val race: String = "",
    val characterClass: String = "",
    val manualBackstory: Boolean = false,
    val backstory: String = ""
)

data class SetupState(
    val genre: String = "",
    val premise: String = "",
    val partySize: Int = 2,
    val maxRounds: Int = 3,
    val enableImages: Boolean = false,
    val artStyle: String = "Anime",
    val textVendor: Vendor = Vendor.OPENAI,
    val textModel: String = Vendor.OPENAI.defaultModel,
    val imageVendor: Vendor = Vendor.OPENAI,
    val drafts: List<CharacterDraft> = List(2) { CharacterDraft() }
)
