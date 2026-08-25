package com.doublemoon1119.mahjongcraft.platform.minecraft.action

import com.doublemoon1119.mahjongcraft.logic.base.ExtensionGameAction
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameAction
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys

/** 管理規則擴充動作 ID 對應之 Minecraft 翻譯 key。 */
interface GameActionDisplayNameRegistry {
    /** registry 是否已凍結。 */
    val isFrozen: Boolean

    /** 登記一個擴充動作的翻譯 key。 */
    fun register(actionId: String, translationKey: String)

    /** 查詢指定擴充動作的翻譯 key；未登記時回傳 null。 */
    fun find(action: ExtensionGameAction): String?

    /** 凍結 registry，禁止後續登記。 */
    fun freeze()
}

/** [GameActionDisplayNameRegistry] 的記憶體實作。 */
class GameActionDisplayNameRegistryImpl : GameActionDisplayNameRegistry {
    /** 依擴充動作 ID 索引的翻譯 key。 */
    private val translationKeys = mutableMapOf<String, String>()

    /** 是否已禁止後續登記。 */
    override var isFrozen: Boolean = false
        private set

    override fun register(actionId: String, translationKey: String) {
        check(!isFrozen) { "Game action display-name registry is frozen" }
        require(actionId.isNotBlank()) { "Game action id must not be blank" }
        require(translationKey.isNotBlank()) { "Game action translation key must not be blank" }
        require(translationKeys.putIfAbsent(actionId, translationKey) == null) {
            "Game action display name already registered: $actionId"
        }
    }

    override fun find(action: ExtensionGameAction): String? = translationKeys[action.id]

    override fun freeze() {
        isFrozen = true
    }
}

/** 登記 MahjongCraft 內建規則擴充動作的顯示名稱。 */
fun GameActionDisplayNameRegistry.registerRiichiGameActionDisplayName() {
    register(RiichiGameAction.Riichi.id, MinecraftMessageKeys.GAME_ACTION_RIICHI)
}
