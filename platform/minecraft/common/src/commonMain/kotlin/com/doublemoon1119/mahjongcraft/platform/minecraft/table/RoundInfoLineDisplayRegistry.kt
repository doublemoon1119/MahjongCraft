package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys

/** 局況顯示單一數值參數在翻譯句子裡代表的意義。 */
enum class RoundInfoLineArgumentKind {
    /** 直接當數字代入翻譯參數。 */
    NUMBER,

    /** 當作 `Wind.ordinal` 解讀，呈現端需另外換算成對應風位的翻譯文字再代入，不能直接顯示成數字。 */
    WIND,
}

/**
 * 規則專屬局況顯示行（`MahjongRuleModule.getRoundInfoLines` 產生的 `RoundInfoLine`）對應的翻譯資訊。
 *
 * @property translationKey 這一行文字使用的翻譯 key。
 * @property argumentKinds `RoundInfoLine.args` 每個數值依序代表的意義；未列出的多餘參數視為
 * [RoundInfoLineArgumentKind.NUMBER]。
 */
data class RoundInfoLineDisplay(
    val translationKey: String,
    val argumentKinds: List<RoundInfoLineArgumentKind> = emptyList(),
)

/** 將 `RoundInfoLine.key` 映射至對應翻譯資訊，供呈現端組出實際文字，不需要認得特定規則模組。 */
interface RoundInfoLineDisplayRegistry {
    /** registry 是否已凍結。 */
    val isFrozen: Boolean

    /** 登記一個局況顯示行 key 的翻譯資訊。 */
    fun register(key: String, display: RoundInfoLineDisplay)

    /** 查詢指定 key 的翻譯資訊；未登記時回傳 null。 */
    fun find(key: String): RoundInfoLineDisplay?

    /** 凍結 registry，禁止後續登記。 */
    fun freeze()
}

/** [RoundInfoLineDisplayRegistry] 的記憶體實作。 */
class RoundInfoLineDisplayRegistryImpl : RoundInfoLineDisplayRegistry {
    /** 依局況顯示行 key 索引的翻譯資訊。 */
    private val displays = mutableMapOf<String, RoundInfoLineDisplay>()

    override var isFrozen: Boolean = false
        private set

    override fun register(key: String, display: RoundInfoLineDisplay) {
        check(!isFrozen) { "Round info line display registry is frozen" }
        require(key.isNotBlank()) { "Round info line key must not be blank" }
        require(displays.putIfAbsent(key, display) == null) { "Duplicate round info line display: $key" }
    }

    override fun find(key: String): RoundInfoLineDisplay? = displays[key]

    override fun freeze() {
        isFrozen = true
    }
}

/** 登記內建日麻的局況顯示行。 */
fun RoundInfoLineDisplayRegistry.registerBuiltInRiichiRoundInfoLineDisplays() {
    register(
        RiichiRuleModule.TITLE_KEY,
        RoundInfoLineDisplay(
            MinecraftMessageKeys.ROUND_INFO_TITLE,
            listOf(RoundInfoLineArgumentKind.WIND, RoundInfoLineArgumentKind.NUMBER, RoundInfoLineArgumentKind.NUMBER),
        ),
    )
    register(RiichiRuleModule.WALL_REMAINING_KEY, RoundInfoLineDisplay(MinecraftMessageKeys.ROUND_INFO_WALL_REMAINING))
    register(RiichiRuleModule.STICK_POT_KEY, RoundInfoLineDisplay(MinecraftMessageKeys.ROUND_INFO_STICK_POT))
}
