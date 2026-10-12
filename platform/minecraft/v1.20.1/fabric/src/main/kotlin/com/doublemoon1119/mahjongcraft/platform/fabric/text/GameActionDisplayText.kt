package com.doublemoon1119.mahjongcraft.platform.fabric.text

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabularyRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.vocabularyActionId
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawReasonDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileEmojiRegistry
import net.minecraft.text.Text

/**
 * 將 [GameAction] 轉成人類可讀的顯示文字，供對局指令的候選 tooltip 與回饋訊息共用。放在 Fabric 模組
 * 的理由見 [toDisplayText]（[Tile] 版本）的 KDoc。
 *
 * 動作的說法屬於規則（例如榮和與胡牌、捨牌與丟牌），因此文字一律向 [actionVocabularyRegistry] 以
 * 「[ruleModuleId] + 動作 ID」查詢，該規則沒有登記時退回中立預設。
 *
 * 部分動作（[GameAction.Chi]／[GameAction.Pon]／[GameAction.Kan]／[GameAction.Ron]／
 * [GameAction.Discard]）本身只帶 tileId，不帶完整 [Tile]，需要呼叫端另外解析出對應的 [referenceTile]
 * 才能組出「吃 五筒」這種完整文字；解析不到時（理論上不會發生）退回顯示 `?`。[displayNameRegistry]／
 * [tileAssetRegistry]／[tileEmojiRegistry] 轉交給 [Tile.toDisplayText] 解析 [referenceTile] 本身
 * （例如第三方牌種）的顯示名稱與牌面 emoji。
 *
 * [GameAction.GameStarted]／[GameAction.RoundStarted]／[GameAction.Draw]
 * 是系統廣播事件或全自動動作，不會透過對局指令觸發，這裡只是 exhaustive `when` 所需的防呆分支，
 * 不特別本地化。
 *
 * @param referenceTile 動作所指牌的已解析資料；不需要牌或無法解析時為 null。
 * @param ruleModuleId 使用中的規則模組 ID；無法取得時為 null。
 * @param actionVocabularyRegistry 動作用語查詢來源。
 * @param displayNameRegistry 牌種名稱查詢來源。
 * @param tileAssetRegistry 牌種資產鍵查詢來源。
 * @param tileEmojiRegistry 牌面 emoji 查詢來源。
 * @param exhaustiveDrawReasonDisplayNameRegistry 流局原因名稱查詢來源。
 * @return 對應動作的 Minecraft 顯示文字。
 */
fun GameAction.toDisplayText(
    referenceTile: Tile?,
    ruleModuleId: String?,
    actionVocabularyRegistry: GameActionVocabularyRegistry,
    displayNameRegistry: TileDisplayNameRegistry,
    tileAssetRegistry: MinecraftTileAssetRegistry,
    tileEmojiRegistry: TileEmojiRegistry,
    exhaustiveDrawReasonDisplayNameRegistry: ExhaustiveDrawReasonDisplayNameRegistry,
): Text {
    val actionId = vocabularyActionId()
    val messageKey = actionVocabularyRegistry.find(ruleModuleId, actionId)?.messageKey
    return when (this) {
        is GameAction.Discard, is GameAction.Chi, is GameAction.Pon, is GameAction.Kan, is GameAction.Ron ->
            messageKey
                ?.let { key ->
                    tileActionText(
                        key = key,
                        referenceTile = referenceTile,
                        displayNameRegistry = displayNameRegistry,
                        tileAssetRegistry = tileAssetRegistry,
                        tileEmojiRegistry = tileEmojiRegistry,
                    )
                }
                ?: Text.literal(actionId)
        GameAction.Tsumo, GameAction.Pass -> messageKey?.let(Text::translatable) ?: Text.literal(actionId)
        is GameAction.Extension -> messageKey?.let(Text::translatable) ?: Text.literal(value.id)
        is GameAction.ExhaustiveDraw -> exhaustiveDrawText(messageKey, exhaustiveDrawReasonDisplayNameRegistry)
        GameAction.MatchEnded -> Text.translatable(MinecraftMessageKeys.GAME_ACTION_MATCH_ENDED)
        is GameAction.DiceRolled -> Text.translatable(MinecraftMessageKeys.GAME_ACTION_DICE_ROLLED)
        GameAction.GameStarted, GameAction.RoundStarted, GameAction.Draw -> Text.literal(this::class.simpleName ?: "")
    }
}

/**
 * 組出「動作 + 牌面」形式的顯示文字，[referenceTile] 為 null 時退回顯示 `?`。
 *
 * @param key 動作翻譯鍵。
 * @param referenceTile 動作所指牌；缺少時顯示 `?`。
 * @param displayNameRegistry 牌種名稱查詢來源。
 * @param tileAssetRegistry 牌種資產鍵查詢來源。
 * @param tileEmojiRegistry 牌面 emoji 查詢來源。
 * @return 動作與牌面組成的顯示文字。
 */
private fun tileActionText(
    key: String,
    referenceTile: Tile?,
    displayNameRegistry: TileDisplayNameRegistry,
    tileAssetRegistry: MinecraftTileAssetRegistry,
    tileEmojiRegistry: TileEmojiRegistry,
): Text = Text.translatable(
    key,
    referenceTile?.toDisplayText(displayNameRegistry, tileAssetRegistry, tileEmojiRegistry) ?: Text.literal("?"),
)

/**
 * 解析流局宣告的顯示文字。
 *
 * 規則若已在動作用語中登記這個宣告（例如日麻的九種九牌），直接使用；否則查流局原因名稱，最後退回通用
 * 流局文字。
 *
 * @param messageKey 動作用語登記的翻譯鍵；未登記時為 null。
 * @param registry 流局原因名稱查詢來源。
 * @return 流局宣告文字。
 */
private fun GameAction.ExhaustiveDraw.exhaustiveDrawText(
    messageKey: String?,
    registry: ExhaustiveDrawReasonDisplayNameRegistry,
): Text = (messageKey ?: registry.find(reason.id))?.let(Text::translatable)
    ?: Text.translatable(MinecraftMessageKeys.GAME_ACTION_EXHAUSTIVE_DRAW)
