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
import com.doublemoon1119.mahjongcraft.platform.minecraft.sound.GameActionSoundPresentationRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLineDisplayRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileEmojiRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabelRegistry

/**
 * 集中保存 Minecraft extension bootstrap 使用的強型別呈現 registry。
 *
 * 一般 renderer、screen 與 service 仍應只依賴實際需要的單一 registry；這個集合只用於 extension 註冊與
 * 統一凍結邊界。
 *
 * @property tileAssetRegistry 牌種 Minecraft asset registry。
 * @property tileDisplayNameRegistry 牌種顯示名稱 registry。
 * @property tileEmojiRegistry 牌面 emoji registry。
 * @property tileLabelRegistry 牌面輔助標籤 registry。
 * @property gameActionDisplayNameRegistry 遊戲動作顯示名稱 registry。
 * @property gameActionSoundPresentationRegistry 遊戲動作音效呈現 registry。
 * @property exhaustiveDrawReasonDisplayNameRegistry 流局原因顯示名稱 registry。
 * @property roundPreparationDisplayNameRegistry 開局準備顯示名稱 registry。
 * @property roundInfoLineDisplayRegistry 局況資訊行顯示 registry。
 * @property winCelebrationShowcaseRegistry 胡牌 showcase registry。
 * @property winSettlementTemplateRegistry 胡牌結算模板 registry。
 * @property matchSettlementTemplateRegistry 終局結算模板 registry。
 * @property aiStrategyDisplayNameRegistry AI 策略顯示名稱 registry。
 * @property ruleModuleDisplayNameRegistry 規則模組顯示名稱 registry。
 * @property playerPortraitSourceRegistry 玩家頭像來源 registry。
 * @property publicPlayerIndicatorDisplayRegistry 公開玩家 indicator 顯示 registry。
 * @property roomMemberAppearanceSourceRegistry 房間成員外觀來源 registry。
 * @property gameConfigPresentationRegistry 遊戲設定呈現 registry。
 */
class MinecraftPresentationRegistries(
    // 牌面呈現
    val tileAssetRegistry: MinecraftTileAssetRegistry,
    val tileDisplayNameRegistry: TileDisplayNameRegistry,
    val tileEmojiRegistry: TileEmojiRegistry,
    val tileLabelRegistry: TileLabelRegistry,
    // 動作與局況呈現
    val gameActionDisplayNameRegistry: GameActionDisplayNameRegistry,
    val gameActionSoundPresentationRegistry: GameActionSoundPresentationRegistry,
    val exhaustiveDrawReasonDisplayNameRegistry: ExhaustiveDrawReasonDisplayNameRegistry,
    val roundPreparationDisplayNameRegistry: RoundPreparationDisplayNameRegistry,
    val roundInfoLineDisplayRegistry: RoundInfoLineDisplayRegistry,
    // 胡牌與結算呈現
    val winCelebrationShowcaseRegistry: WinCelebrationShowcaseRegistry,
    val winSettlementTemplateRegistry: WinSettlementPresentationTemplateRegistry,
    val matchSettlementTemplateRegistry: MatchSettlementPresentationTemplateRegistry,
    // 玩家與房間呈現
    val aiStrategyDisplayNameRegistry: AiStrategyDisplayNameRegistry,
    val ruleModuleDisplayNameRegistry: RuleModuleDisplayNameRegistry,
    val playerPortraitSourceRegistry: PlayerPortraitSourceRegistry,
    val publicPlayerIndicatorDisplayRegistry: PublicPlayerIndicatorDisplayRegistry,
    val roomMemberAppearanceSourceRegistry: RoomMemberAppearanceSourceRegistry,
    val gameConfigPresentationRegistry: GameConfigPresentationRegistry,
) {
    /** 取得目前所有 Minecraft presentation registry 的不可變診斷快照。 */
    fun registrationSnapshot(): MinecraftPresentationRegistrationSnapshot = MinecraftPresentationRegistrationSnapshot(
        listOf(
            snapshotCategory("mahjongcraft:tile_asset", "Tile Asset", tileAssetRegistry.registrationKeys),
            snapshotCategory("mahjongcraft:tile_display_name", "Tile Display Name", tileDisplayNameRegistry.registrationKeys),
            snapshotCategory("mahjongcraft:tile_emoji", "Tile Emoji", tileEmojiRegistry.registrationKeys),
            snapshotCategory("mahjongcraft:tile_label", "Tile Label", tileLabelRegistry.registrationKeys),
            snapshotCategory("mahjongcraft:game_action_display_name", "Game Action Display Name", gameActionDisplayNameRegistry.registrationKeys),
            snapshotCategory("mahjongcraft:game_action_sound", "Game Action Sound", gameActionSoundPresentationRegistry.registrationKeys),
            snapshotCategory("mahjongcraft:exhaustive_draw_reason_display_name", "Exhaustive Draw Reason Display Name", exhaustiveDrawReasonDisplayNameRegistry.registrationKeys),
            snapshotCategory("mahjongcraft:round_preparation_display_name", "Round Preparation Display Name", roundPreparationDisplayNameRegistry.registrationKeys),
            snapshotCategory("mahjongcraft:round_info_line_display", "Round Info Line Display", roundInfoLineDisplayRegistry.registrationKeys),
            snapshotCategory("mahjongcraft:win_celebration_showcase", "Win Celebration Showcase", winCelebrationShowcaseRegistry.registrationKeys),
            snapshotCategory("mahjongcraft:win_settlement_presentation", "Win Settlement Presentation", winSettlementTemplateRegistry.registrationKeys),
            snapshotCategory("mahjongcraft:match_settlement_template", "Match Settlement Template", matchSettlementTemplateRegistry.registrationKeys),
            snapshotCategory("mahjongcraft:ai_strategy_display_name", "AI Strategy Display Name", aiStrategyDisplayNameRegistry.registrationKeys),
            snapshotCategory("mahjongcraft:rule_module_display_name", "Rule Module Display Name", ruleModuleDisplayNameRegistry.registrationKeys),
            snapshotCategory("mahjongcraft:player_portrait_source", "Player Portrait Source", playerPortraitSourceRegistry.registrationKeys),
            snapshotCategory("mahjongcraft:public_player_indicator_display", "Public Player Indicator Display", publicPlayerIndicatorDisplayRegistry.registrationKeys),
            snapshotCategory("mahjongcraft:room_member_appearance_source", "Room Member Appearance Source", roomMemberAppearanceSourceRegistry.registrationKeys),
            snapshotCategory("mahjongcraft:game_config_presentation", "Game Config Presentation", gameConfigPresentationRegistry.registrationKeys),
        ),
    )

    /** 依固定分類順序凍結集合內所有 registry。 */
    fun freezeAll() {
        tileAssetRegistry.freeze()
        tileDisplayNameRegistry.freeze()
        tileEmojiRegistry.freeze()
        tileLabelRegistry.freeze()
        gameActionDisplayNameRegistry.freeze()
        gameActionSoundPresentationRegistry.freeze()
        exhaustiveDrawReasonDisplayNameRegistry.freeze()
        roundPreparationDisplayNameRegistry.freeze()
        roundInfoLineDisplayRegistry.freeze()
        winCelebrationShowcaseRegistry.freeze()
        winSettlementTemplateRegistry.freeze()
        matchSettlementTemplateRegistry.freeze()
        aiStrategyDisplayNameRegistry.freeze()
        ruleModuleDisplayNameRegistry.freeze()
        playerPortraitSourceRegistry.freeze()
        publicPlayerIndicatorDisplayRegistry.freeze()
        roomMemberAppearanceSourceRegistry.freeze()
        gameConfigPresentationRegistry.freeze()
    }
}

/** 建立單一 Minecraft presentation registry 的診斷快照類別。 */
private fun snapshotCategory(
    id: String,
    displayName: String,
    registrationKeys: Iterable<String>,
): MinecraftPresentationRegistrationSnapshotCategory = MinecraftPresentationRegistrationSnapshotCategory(
    id,
    displayName,
    registrationKeys.toSet(),
)

/** 保存所有 Minecraft 呈現 registry 在單一時間點的註冊內容。 */
data class MinecraftPresentationRegistrationSnapshot(
    val categories: List<MinecraftPresentationRegistrationSnapshotCategory>,
) {
    /** 與 [current] 比較並回傳新增的註冊內容。 */
    fun additionsSince(current: MinecraftPresentationRegistrationSnapshot): List<MinecraftPresentationRegistrationSnapshotCategory> {
        val baselineById = categories.associateBy { it.id }
        return current.categories.map { category ->
            category.copy(registrationKeys = category.registrationKeys - baselineById[category.id].orEmptyKeys())
        }
    }
}

/** 保存單一 Minecraft 呈現 registry 的名稱與註冊 key。 */
data class MinecraftPresentationRegistrationSnapshotCategory(
    val id: String,
    val displayName: String,
    val registrationKeys: Set<String>,
)

/** 取得 nullable 快照分類的註冊 key。 */
private fun MinecraftPresentationRegistrationSnapshotCategory?.orEmptyKeys(): Set<String> = this?.registrationKeys.orEmpty()
