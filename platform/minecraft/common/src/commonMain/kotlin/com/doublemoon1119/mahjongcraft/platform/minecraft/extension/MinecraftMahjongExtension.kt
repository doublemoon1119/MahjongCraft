package com.doublemoon1119.mahjongcraft.platform.minecraft.extension

import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.AiStrategyDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PlayerPortraitSourceRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PublicPlayerIndicatorDisplayRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.preparation.RoundPreparationDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.RoomMemberAppearanceSourceRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawReasonDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.MatchSettlementPresentationTemplateRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementPresentationTemplateRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.showcase.WinCelebrationShowcaseRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileEmojiRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabelRegistry

/**
 * 第三方 Minecraft mod 在 MahjongCraft runtime 啟動前登記 Minecraft 專屬整合的共用契約。
 *
 * 跟遊戲平台無關的規則邏輯本身（規則配置與計算、自訂牌種、網路／存檔層的資料轉換）屬於
 * `com.doublemoon1119.mahjongcraft.extension.MahjongExtension` 的職責，不在這裡登記；這裡只涵蓋
 * Minecraft 這個平台專屬的整合——[MinecraftTileAssetRegistry]／[AiStrategyDisplayNameRegistry] 這類
 * 概念屬於 Minecraft adapter，不應該出現在平台無關的 extension API 裡，才能讓規則邏輯保持可攜。
 *
 * 第三方類別若想同時登記規則層與 Minecraft 專屬整合，可以讓同一個類別同時實作兩個介面，不需要另外
 * 宣告第二個 Fabric entrypoint——loader adapter 只用 `MahjongExtension` 型別掃描 entrypoint，再從
 * 掃描結果中篩選出有實作此介面的部分；換句話說，只實作這個介面、沒有同時實作 `MahjongExtension`
 * 的類別不會被發現。
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

    /** 登記規則擴充動作在 Minecraft 中使用的本地化顯示名稱。 */
    fun registerGameActionDisplayNames(registry: GameActionDisplayNameRegistry) = Unit

    /** 登記第三方流局原因在 Minecraft 中使用的本地化顯示名稱。 */
    fun registerExhaustiveDrawReasonDisplayNames(registry: ExhaustiveDrawReasonDisplayNameRegistry) = Unit

    /** 登記第三方開局準備 step／option 的本地化顯示名稱。 */
    fun registerRoundPreparationDisplayNames(registry: RoundPreparationDisplayNameRegistry) = Unit

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

    /** 登記第三方宣告式胡牌 showcase 定義。 */
    fun registerWinCelebrationShowcases(registry: WinCelebrationShowcaseRegistry) = Unit

    /** 登記第三方胡牌結算欄位 provider 與完整宣告式模板。 */
    fun registerWinSettlementPresentationTemplates(registry: WinSettlementPresentationTemplateRegistry) = Unit

    /** 登記第三方終局排行面板模板。 */
    fun registerMatchSettlementPresentationTemplates(registry: MatchSettlementPresentationTemplateRegistry) = Unit

    /** 登記第三方玩家頭像來源；來源只能是 skin、牌面或 texture region。 */
    fun registerPlayerPortraitSources(registry: PlayerPortraitSourceRegistry) = Unit

    /** 登記規則公開 indicator／option 的本地化顯示。 */
    fun registerPublicPlayerIndicatorDisplays(registry: PublicPlayerIndicatorDisplayRegistry) = Unit

    /** 登記第三方規則的宣告式房間設定欄位。 */
    fun registerGameConfigPresentations(registry: GameConfigPresentationRegistry) = Unit

    /** 登記第三方房間成員外觀來源；完整 entity factory 留待 SeatActorProvider 階段。 */
    fun registerRoomMemberAppearanceSources(registry: RoomMemberAppearanceSourceRegistry) = Unit
}
