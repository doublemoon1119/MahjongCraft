package com.doublemoon1119.mahjongcraft.platform.minecraft.sound

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameAction

/** Minecraft 呈現層播放規則動作語音所需的宣告式資料。 */
data class GameActionSoundPresentation(
    /** 已由 Minecraft sound registry 註冊的完整 namespaced ID。 */
    val soundId: String,
    /** 播放音量。 */
    val volume: Float = 1.0f,
    /** 播放音高。 */
    val pitch: Float = 1.0f,
) {
    init {
        require(':' in soundId) { "Sound ID must be namespaced: $soundId" }
        require(volume >= 0.0f) { "Sound volume must not be negative" }
        require(pitch > 0.0f) { "Sound pitch must be positive" }
    }
}

/** 單一規則模組動作對應的語音定義。 */
data class GameActionSoundDefinition(
    /** 提供此對應的規則模組完整 ID。 */
    val ruleModuleId: String,
    /** [GameAction.soundPresentationId] 回傳的穩定動作 ID。 */
    val actionId: String,
    /** 實際播放設定。 */
    val presentation: GameActionSoundPresentation,
) {
    init {
        require(':' in ruleModuleId) { "Rule module ID must be namespaced: $ruleModuleId" }
        require(':' in actionId) { "Game action ID must be namespaced: $actionId" }
    }
}

/** 管理規則模組動作與 Minecraft 語音呈現的凍結式 registry。 */
interface GameActionSoundPresentationRegistry {
    /** registry 是否已凍結。 */
    val isFrozen: Boolean

    /** 已登記的「規則模組／動作」組合。 */
    val definitionKeys: Set<Pair<String, String>>

    /** 登記一項宣告式動作語音。 */
    fun register(definition: GameActionSoundDefinition)

    /** 解析指定規則模組與權威動作；沒有對應時保持靜音。 */
    fun find(ruleModuleId: String, action: GameAction): GameActionSoundPresentation?

    /** 凍結 registry，禁止後續登記。 */
    fun freeze()
}

/** [GameActionSoundPresentationRegistry] 的記憶體實作。 */
class GameActionSoundPresentationRegistryImpl : GameActionSoundPresentationRegistry {
    /** 依規則模組與動作 ID 索引的呈現定義。 */
    private val definitions = mutableMapOf<Pair<String, String>, GameActionSoundPresentation>()

    override var isFrozen: Boolean = false
        private set

    override val definitionKeys: Set<Pair<String, String>> get() = definitions.keys

    override fun register(definition: GameActionSoundDefinition) {
        check(!isFrozen) { "Game action sound presentation registry is frozen" }
        val key = definition.ruleModuleId to definition.actionId
        require(definitions.putIfAbsent(key, definition.presentation) == null) {
            "Game action sound presentation already registered: ${definition.ruleModuleId}/${definition.actionId}"
        }
    }

    override fun find(ruleModuleId: String, action: GameAction): GameActionSoundPresentation? = action.soundPresentationId()?.let { actionId -> definitions[ruleModuleId to actionId] }

    override fun freeze() {
        isFrozen = true
    }
}

/** 取得動作在 Minecraft 宣告式聲音 registry 中使用的穩定 ID。 */
fun GameAction.soundPresentationId(): String? = when (this) {
    is GameAction.Chi -> BuiltInGameActionSoundIds.CHII
    is GameAction.Pon -> BuiltInGameActionSoundIds.PON
    is GameAction.Kan -> BuiltInGameActionSoundIds.KAN
    is GameAction.Ron -> BuiltInGameActionSoundIds.RON
    GameAction.Tsumo -> BuiltInGameActionSoundIds.TSUMO
    is GameAction.Extension -> value.id
    else -> null
}

/** MahjongCraft 內建動作語音使用的穩定 ID。 */
object BuiltInGameActionSoundIds {
    /** 吃牌。 */
    const val CHII: String = "mahjongcraft:chii"

    /** 碰牌。 */
    const val PON: String = "mahjongcraft:pon"

    /** 所有種類的槓牌。 */
    const val KAN: String = "mahjongcraft:kan"

    /** 榮和。 */
    const val RON: String = "mahjongcraft:ron"

    /** 自摸。 */
    const val TSUMO: String = "mahjongcraft:tsumo"
}

/** 登記內建日本麻將使用的六種暫用宣告語音。 */
fun GameActionSoundPresentationRegistry.registerBuiltInRiichiActionSounds() {
    val definitions = listOf(
        BuiltInGameActionSoundIds.CHII to BuiltInGameActionVoiceSoundIds.CHII,
        BuiltInGameActionSoundIds.PON to BuiltInGameActionVoiceSoundIds.PON,
        BuiltInGameActionSoundIds.KAN to BuiltInGameActionVoiceSoundIds.KAN,
        RiichiGameAction.Riichi.id to BuiltInGameActionVoiceSoundIds.RIICHI,
        BuiltInGameActionSoundIds.RON to BuiltInGameActionVoiceSoundIds.RON,
        BuiltInGameActionSoundIds.TSUMO to BuiltInGameActionVoiceSoundIds.TSUMO,
    )
    definitions.forEach { (actionId, soundId) ->
        register(
            GameActionSoundDefinition(
                ruleModuleId = BuiltInRuleModuleIds.RIICHI,
                actionId = actionId,
                presentation = GameActionSoundPresentation(soundId),
            ),
        )
    }
}

/** MahjongCraft 內建日本麻將宣告語音的 Minecraft sound ID。 */
object BuiltInGameActionVoiceSoundIds {
    /** 吃牌語音。 */
    const val CHII: String = "mahjongcraft:voice.chii"

    /** 碰牌語音。 */
    const val PON: String = "mahjongcraft:voice.pon"

    /** 槓牌語音。 */
    const val KAN: String = "mahjongcraft:voice.kan"

    /** 立直語音。 */
    const val RIICHI: String = "mahjongcraft:voice.riichi"

    /** 榮和語音。 */
    const val RON: String = "mahjongcraft:voice.ron"

    /** 自摸語音。 */
    const val TSUMO: String = "mahjongcraft:voice.tsumo"

    /** 全部內建宣告語音 ID。 */
    val ALL: List<String> = listOf(CHII, PON, KAN, RIICHI, RON, TSUMO)
}
