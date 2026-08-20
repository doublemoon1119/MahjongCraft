package com.doublemoon1119.mahjongcraft.platform.minecraft.extension

import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.AiStrategyDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileEmojiRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabelRegistry

/**
 * 第三方 Minecraft mod 在 MahjongCraft runtime 啟動前登記 Minecraft 專屬整合的共用契約。
 *
 * 與平台無關的 `com.doublemoon1119.mahjongcraft.extension.MahjongExtension` 分開定義，因為
 * [MinecraftTileAssetRegistry]／[AiStrategyDisplayNameRegistry] 屬於 Minecraft adapter 概念，不應
 * 出現在平台無關的 extension API。第三方類別若想同時登記規則層與 Minecraft 專屬整合，可以讓同一個
 * 類別同時實作兩個介面；loader adapter 會從既有 extension 發現結果中篩選出有實作此介面的部分，
 * 不需要額外宣告第二個 entrypoint。
 */
interface MinecraftMahjongExtension {
    /** 第三方 extension 的穩定識別字串，用於診斷註冊錯誤。 */
    val id: String

    /**
     * 登記第三方牌種對應的 Minecraft asset key。
     *
     * 預設不註冊任何映射，使只提供規則層整合的 extension 不必加入空實作。
     */
    fun registerTileAssets(registry: MinecraftTileAssetRegistry) = Unit

    /**
     * 登記第三方 AI 策略對應的顯示名稱。
     *
     * 預設不註冊任何映射，使不提供 AI 策略的 extension 不必加入空實作。
     */
    fun registerAiStrategyDisplayNames(registry: AiStrategyDisplayNameRegistry) = Unit

    /**
     * 登記第三方牌種對應的顯示名稱。
     *
     * 預設不註冊任何映射，使不提供自訂牌種顯示名稱的 extension 不必加入空實作。
     */
    fun registerTileDisplayNames(registry: TileDisplayNameRegistry) = Unit

    /**
     * 登記第三方規則模組對應的顯示名稱。
     *
     * 預設不註冊任何映射，使不提供自訂規則模組顯示名稱的 extension 不必加入空實作。
     */
    fun registerRuleModuleDisplayNames(registry: RuleModuleDisplayNameRegistry) = Unit

    /**
     * 登記第三方牌種 asset key 對應的顯示 emoji 字元。
     *
     * 預設不註冊任何映射，使不提供自訂牌面 emoji 的 extension 不必加入空實作。字元本身要能顯示成
     * 貼圖，還需要第三方 mod／資源包另外提供對應的 `assets/minecraft/font/default.json` bitmap
     * provider；這個 registry 只負責保存字元對照，不管字型檔案。
     */
    fun registerTileEmojis(registry: TileEmojiRegistry) = Unit

    /**
     * 登記第三方牌種 asset key 對應的牌面角落標籤（非中文圈玩家可切換的輔助標籤）。
     *
     * 預設不註冊任何映射，使不提供自訂標籤的 extension 不必加入空實作；未註冊的 asset key 呈現端會
     * 視為不顯示標籤，不是錯誤。
     */
    fun registerTileLabels(registry: TileLabelRegistry) = Unit
}
