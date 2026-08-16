package com.doublemoon1119.mahjongcraft.platform.fabric.text

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileEmojiRegistry
import net.minecraft.text.Text

/**
 * 將 [GameAction] 轉成人類可讀的顯示文字，供對局指令的候選 tooltip 與回饋訊息共用。放在 Fabric 模組
 * 的理由見 [toDisplayText]（[Tile] 版本）的 KDoc。
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
 */
fun GameAction.toDisplayText(
    referenceTile: Tile?,
    displayNameRegistry: TileDisplayNameRegistry,
    tileAssetRegistry: MinecraftTileAssetRegistry,
    tileEmojiRegistry: TileEmojiRegistry,
): Text = when (this) {
    is GameAction.Discard -> tileActionText(
        key = MinecraftMessageKeys.GAME_ACTION_DISCARD,
        referenceTile = referenceTile,
        displayNameRegistry = displayNameRegistry,
        tileAssetRegistry = tileAssetRegistry,
        tileEmojiRegistry = tileEmojiRegistry,
    )
    GameAction.Riichi -> Text.translatable(MinecraftMessageKeys.GAME_ACTION_RIICHI)
    GameAction.Tsumo -> Text.translatable(MinecraftMessageKeys.GAME_ACTION_TSUMO)
    is GameAction.Chi -> tileActionText(
        key = MinecraftMessageKeys.GAME_ACTION_CHI,
        referenceTile = referenceTile,
        displayNameRegistry = displayNameRegistry,
        tileAssetRegistry = tileAssetRegistry,
        tileEmojiRegistry = tileEmojiRegistry,
    )
    is GameAction.Pon -> tileActionText(
        key = MinecraftMessageKeys.GAME_ACTION_PON,
        referenceTile = referenceTile,
        displayNameRegistry = displayNameRegistry,
        tileAssetRegistry = tileAssetRegistry,
        tileEmojiRegistry = tileEmojiRegistry,
    )
    is GameAction.Kan -> tileActionText(
        key = type.toMessageKey(),
        referenceTile = referenceTile,
        displayNameRegistry = displayNameRegistry,
        tileAssetRegistry = tileAssetRegistry,
        tileEmojiRegistry = tileEmojiRegistry,
    )
    is GameAction.Ron -> tileActionText(
        key = MinecraftMessageKeys.GAME_ACTION_RON,
        referenceTile = referenceTile,
        displayNameRegistry = displayNameRegistry,
        tileAssetRegistry = tileAssetRegistry,
        tileEmojiRegistry = tileEmojiRegistry,
    )
    GameAction.Pass -> Text.translatable(MinecraftMessageKeys.GAME_ACTION_PASS)
    is GameAction.ExhaustiveDraw -> exhaustiveDrawText()
    GameAction.MatchEnded -> Text.translatable(MinecraftMessageKeys.GAME_ACTION_MATCH_ENDED)
    is GameAction.DiceRolled -> Text.translatable(MinecraftMessageKeys.GAME_ACTION_DICE_ROLLED)
    GameAction.GameStarted, GameAction.RoundStarted, GameAction.Draw -> Text.literal(this::class.simpleName ?: "")
}

/** 組出「動作 + 牌面」形式的顯示文字，[referenceTile] 為 null 時退回顯示 `?`。 */
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

/** 將槓牌種類映射到對應的翻譯 key。 */
private fun GameAction.KanType.toMessageKey(): String = when (this) {
    GameAction.KanType.OPEN_KAN -> MinecraftMessageKeys.GAME_ACTION_KAN_OPEN
    GameAction.KanType.CLOSED_KAN -> MinecraftMessageKeys.GAME_ACTION_KAN_CLOSED
    GameAction.KanType.ADDED_KAN -> MinecraftMessageKeys.GAME_ACTION_KAN_ADDED
}

/** 九種九牌顯示專屬文字，其餘規則專屬流局原因退回通用 fallback。 */
private fun GameAction.ExhaustiveDraw.exhaustiveDrawText(): Text = if (reason == RiichiExhaustiveDrawReason.KyuushuKyuuhai) {
    Text.translatable(MinecraftMessageKeys.GAME_ACTION_KYUUSHU_KYUUHAI)
} else {
    Text.translatable(MinecraftMessageKeys.GAME_ACTION_EXHAUSTIVE_DRAW)
}
