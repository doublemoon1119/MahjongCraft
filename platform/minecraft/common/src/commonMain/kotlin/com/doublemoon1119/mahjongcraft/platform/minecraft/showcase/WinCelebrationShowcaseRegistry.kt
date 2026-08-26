package com.doublemoon1119.mahjongcraft.platform.minecraft.showcase

import com.doublemoon1119.mahjongcraft.flow.common.game.model.BuiltInWinCelebrationCueIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftShowcaseKeys

/** 宣告式胡牌展示定義註冊中心。 */
interface WinCelebrationShowcaseRegistry {
    /** registry 是否已凍結。 */
    val isFrozen: Boolean

    /** 目前已註冊 cue key 的唯讀快照。 */
    val cueKeys: Set<String>

    /** 登記一個 cue；重複 key 會失敗。 */
    fun register(definition: WinCelebrationShowcaseDefinition)

    /** 凍結後禁止註冊。 */
    fun freeze()

    /** 依 cue key 取得定義。 */
    fun find(cueKey: String): WinCelebrationShowcaseDefinition?
}

/** [WinCelebrationShowcaseRegistry] 的記憶體實作。 */
class WinCelebrationShowcaseRegistryImpl : WinCelebrationShowcaseRegistry {
    private val definitions = mutableMapOf<String, WinCelebrationShowcaseDefinition>()
    override var isFrozen: Boolean = false
        private set
    override val cueKeys: Set<String> get() = definitions.keys.toSet()

    override fun register(definition: WinCelebrationShowcaseDefinition) {
        check(!isFrozen) { "Win celebration showcase registry is frozen" }
        require(definitions.putIfAbsent(definition.cueKey, definition) == null) {
            "Duplicate win celebration showcase cue: ${definition.cueKey}"
        }
    }

    override fun freeze() {
        isFrozen = true
    }

    override fun find(cueKey: String): WinCelebrationShowcaseDefinition? = definitions[cueKey]
}

/** 註冊所有內建役滿展示定義。 */
fun WinCelebrationShowcaseRegistry.registerBuiltInWinCelebrationShowcases() {
    BUILT_IN_CUES.forEach { cue ->
        register(
            WinCelebrationShowcaseDefinition(
                cueKey = BuiltInWinCelebrationCueIds.riichiYakuman(cue),
                titleTranslationKey = MinecraftShowcaseKeys.fromCuePath(cue),
                titleImageResourceId = "${MinecraftModMetadata.MOD_ID}:textures/showcase/$cue.png",
                palette = ShowcasePalette(primary = 0xFFFFD45A.toInt(), secondary = 0xFFC32128.toInt(), accent = 0xFFFFFFFF.toInt()),
            ),
        )
    }
}

/** 內建役滿 cue 的固定優先順序與素材名稱。 */
private val BUILT_IN_CUES = listOf(
    "kokushi_musou_13",
    "churen_poto_9",
    "suuankou_tanki",
    "daisuushii",
    "kokushi_musou",
    "churen_poto",
    "tsuuiisou",
    "ryuuuiisou",
    "suuankou",
    "sukantsu",
    "shousuushi",
    "daisangen",
    "chinroutou",
    "tenhou",
    "chiihou",
)
