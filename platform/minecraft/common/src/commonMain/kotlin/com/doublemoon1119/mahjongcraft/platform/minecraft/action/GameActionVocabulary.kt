package com.doublemoon1119.mahjongcraft.platform.minecraft.action

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftResourceIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys

/** 所有規則共用的核心動作識別碼，與規則擴充動作使用同一套命名空間慣例。 */
object BuiltInGameActionIds {
    /** 吃。 */
    const val CHI: String = "mahjongcraft:chi"

    /** 碰。 */
    const val PON: String = "mahjongcraft:pon"

    /** 明槓。 */
    const val KAN_OPEN: String = "mahjongcraft:kan_open"

    /** 暗槓。 */
    const val KAN_CLOSED: String = "mahjongcraft:kan_closed"

    /** 加槓。 */
    const val KAN_ADDED: String = "mahjongcraft:kan_added"

    /** 榮和。 */
    const val RON: String = "mahjongcraft:ron"

    /** 自摸。 */
    const val TSUMO: String = "mahjongcraft:tsumo"

    /** 跳過。 */
    const val PASS: String = "mahjongcraft:pass"

    /** 捨牌。 */
    const val DISCARD: String = "mahjongcraft:discard"

    /** 不屬於上列任何一種的動作。 */
    const val UNKNOWN: String = "mahjongcraft:action"
}

/**
 * 一個動作在某個規則下的顯示用語。
 *
 * 同一個動作在不同規則可能有不同說法（例如榮和與胡牌、捨牌與丟牌），因此用語屬於規則，不寫在呈現程式裡。
 *
 * @property labelKey 操作卡與標題用的短名稱 translation key，不帶參數。
 * @property messageKey 聊天訊息用的 translation key，可帶牌面參數；預設沿用 [labelKey]。
 * @property order 操作卡由左至右的順序；`null` 代表排在所有已登記的動作之後，並維持原始相對順序。
 */
data class GameActionVocabulary(
    val labelKey: String,
    val messageKey: String = labelKey,
    val order: Int? = null,
) {
    init {
        require(labelKey.isNotBlank()) { "Game action label key must not be blank" }
        require(messageKey.isNotBlank()) { "Game action message key must not be blank" }
    }
}

/**
 * 管理各規則動作用語的凍結式 registry。
 *
 * 查詢以「規則模組 ID + 動作 ID」為準，該規則沒有登記時退回中立預設；兩者都沒有時由呼叫端顯示原始 ID。
 */
interface GameActionVocabularyRegistry {
    /** 目前已登記組合的穩定 key 快照，格式為 `規則模組 ID/動作 ID`，中立預設以 [DEFAULT_KEY] 表示。 */
    val registrationKeys: Set<String>

    /** registry 是否已凍結。 */
    val isFrozen: Boolean

    /** 登記所有規則共用的中立預設。 */
    fun registerDefault(actionId: String, vocabulary: GameActionVocabulary)

    /** 登記指定規則模組專屬的用語，覆寫同一個動作的中立預設。 */
    fun register(ruleModuleId: String, actionId: String, vocabulary: GameActionVocabulary)

    /** 查詢用語：先查 [ruleModuleId] 的登記，再查中立預設；都沒有時回傳 null。 */
    fun find(ruleModuleId: String?, actionId: String): GameActionVocabulary?

    /** 凍結 registry，禁止後續登記。 */
    fun freeze()

    companion object {
        /** [registrationKeys] 中代表中立預設的規則位置。 */
        const val DEFAULT_KEY: String = "default"
    }
}

/** [GameActionVocabularyRegistry] 的記憶體實作。 */
class GameActionVocabularyRegistryImpl : GameActionVocabularyRegistry {
    /** 依動作 ID 索引的中立預設。 */
    private val defaults = mutableMapOf<String, GameActionVocabulary>()

    /** 依規則模組與動作 ID 索引的規則專屬用語。 */
    private val byRuleModule = mutableMapOf<Pair<String, String>, GameActionVocabulary>()

    override val registrationKeys: Set<String>
        get() = defaults.keys.mapTo(mutableSetOf()) { actionId -> "${GameActionVocabularyRegistry.DEFAULT_KEY}/$actionId" } +
            byRuleModule.keys.map { (ruleModuleId, actionId) -> "$ruleModuleId/$actionId" }

    override var isFrozen: Boolean = false
        private set

    override fun registerDefault(actionId: String, vocabulary: GameActionVocabulary) {
        check(!isFrozen) { "Game action vocabulary registry is frozen" }
        MinecraftResourceIds.requireValid(actionId) { "Game action ID must be namespaced: $actionId" }
        require(defaults.putIfAbsent(actionId, vocabulary) == null) {
            "Default game action vocabulary already registered: $actionId"
        }
    }

    override fun register(ruleModuleId: String, actionId: String, vocabulary: GameActionVocabulary) {
        check(!isFrozen) { "Game action vocabulary registry is frozen" }
        MinecraftResourceIds.requireValid(ruleModuleId) { "Rule module ID must be namespaced: $ruleModuleId" }
        MinecraftResourceIds.requireValid(actionId) { "Game action ID must be namespaced: $actionId" }
        require(byRuleModule.putIfAbsent(ruleModuleId to actionId, vocabulary) == null) {
            "Game action vocabulary already registered: $ruleModuleId/$actionId"
        }
    }

    override fun find(ruleModuleId: String?, actionId: String): GameActionVocabulary? = ruleModuleId?.let { byRuleModule[it to actionId] } ?: defaults[actionId]

    override fun freeze() {
        isFrozen = true
    }
}

/**
 * 取得動作在用語 registry 中使用的穩定 ID。
 *
 * 核心動作為 [BuiltInGameActionIds] 的固定 ID；流局宣告用流局原因 ID，規則擴充動作用動作自己的 ID，
 * 兩者都由規則決定。
 */
fun GameAction.vocabularyActionId(): String = when (this) {
    is GameAction.Chi -> BuiltInGameActionIds.CHI
    is GameAction.Pon -> BuiltInGameActionIds.PON
    is GameAction.Kan -> when (type) {
        GameAction.KanType.OPEN_KAN -> BuiltInGameActionIds.KAN_OPEN
        GameAction.KanType.CLOSED_KAN -> BuiltInGameActionIds.KAN_CLOSED
        GameAction.KanType.ADDED_KAN -> BuiltInGameActionIds.KAN_ADDED
    }
    is GameAction.Ron -> BuiltInGameActionIds.RON
    GameAction.Tsumo -> BuiltInGameActionIds.TSUMO
    GameAction.Pass -> BuiltInGameActionIds.PASS
    is GameAction.Discard -> BuiltInGameActionIds.DISCARD
    is GameAction.ExhaustiveDraw -> reason.id
    is GameAction.Extension -> value.id
    else -> BuiltInGameActionIds.UNKNOWN
}

/** HUD 操作卡短名稱的 translation key 前綴。 */
private const val HUD_ACTION_PREFIX: String = MinecraftModMetadata.MOD_ID + ".hud.action."

/**
 * 登記核心動作的中立預設用語與操作卡順序。
 *
 * 預設沿用目前的用語；說法不同的規則登記同一個動作 ID 即可覆寫，不需要改動呈現程式。
 */
fun GameActionVocabularyRegistry.registerBuiltInGameActionVocabulary() {
    registerDefault(
        BuiltInGameActionIds.CHI,
        GameActionVocabulary(HUD_ACTION_PREFIX + "chi", MinecraftMessageKeys.GAME_ACTION_CHI, order = 0),
    )
    registerDefault(
        BuiltInGameActionIds.PON,
        GameActionVocabulary(HUD_ACTION_PREFIX + "pon", MinecraftMessageKeys.GAME_ACTION_PON, order = 1),
    )
    registerDefault(
        BuiltInGameActionIds.KAN_OPEN,
        GameActionVocabulary(HUD_ACTION_PREFIX + "kan_open", MinecraftMessageKeys.GAME_ACTION_KAN_OPEN, order = 2),
    )
    registerDefault(
        BuiltInGameActionIds.KAN_CLOSED,
        GameActionVocabulary(HUD_ACTION_PREFIX + "kan_closed", MinecraftMessageKeys.GAME_ACTION_KAN_CLOSED, order = 2),
    )
    registerDefault(
        BuiltInGameActionIds.KAN_ADDED,
        GameActionVocabulary(HUD_ACTION_PREFIX + "kan_added", MinecraftMessageKeys.GAME_ACTION_KAN_ADDED, order = 2),
    )
    registerDefault(
        BuiltInGameActionIds.RON,
        GameActionVocabulary(HUD_ACTION_PREFIX + "ron", MinecraftMessageKeys.GAME_ACTION_RON, order = 3),
    )
    registerDefault(
        BuiltInGameActionIds.TSUMO,
        GameActionVocabulary(HUD_ACTION_PREFIX + "tsumo", MinecraftMessageKeys.GAME_ACTION_TSUMO, order = 4),
    )
    registerDefault(
        BuiltInGameActionIds.PASS,
        GameActionVocabulary(HUD_ACTION_PREFIX + "pass", MinecraftMessageKeys.GAME_ACTION_PASS),
    )
    registerDefault(
        BuiltInGameActionIds.DISCARD,
        GameActionVocabulary(HUD_ACTION_PREFIX + "discard", MinecraftMessageKeys.GAME_ACTION_DISCARD),
    )
}
