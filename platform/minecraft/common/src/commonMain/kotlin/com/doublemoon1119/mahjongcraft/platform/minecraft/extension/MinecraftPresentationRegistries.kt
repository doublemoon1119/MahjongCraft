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
