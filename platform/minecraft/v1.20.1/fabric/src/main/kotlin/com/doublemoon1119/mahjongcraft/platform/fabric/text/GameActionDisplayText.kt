package com.doublemoon1119.mahjongcraft.platform.fabric.text

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import net.minecraft.text.Text

/**
 * 將 [GameAction] 轉成人類可讀的顯示文字，供對局指令的候選 tooltip 與回饋訊息共用。放在 Fabric 模組
 * 的理由見 [toDisplayText]（[Tile] 版本）的 KDoc。
 *
 * 部分動作（[GameAction.Chi]／[GameAction.Pon]／[GameAction.Kan]／[GameAction.Ron]／
 * [GameAction.Discard]）本身只帶 tileId，不帶完整 [Tile]，需要呼叫端另外解析出對應的 [referenceTile]
 * 才能組出「吃 五筒」這種完整文字；解析不到時（理論上不會發生）退回顯示 `?`。
 *
 * [GameAction.GameStarted]／[GameAction.RoundStarted]／[GameAction.Draw]
 * 是系統廣播事件或全自動動作，不會透過對局指令觸發，這裡只是 exhaustive `when` 所需的防呆分支，
 * 不特別本地化。
 */
fun GameAction.toDisplayText(referenceTile: Tile?): Text = when (this) {
    is GameAction.Discard -> tileActionText(MinecraftMessageKeys.GAME_ACTION_DISCARD, referenceTile)
    GameAction.Riichi -> Text.translatable(MinecraftMessageKeys.GAME_ACTION_RIICHI)
    GameAction.Tsumo -> Text.translatable(MinecraftMessageKeys.GAME_ACTION_TSUMO)
    is GameAction.Chi -> tileActionText(MinecraftMessageKeys.GAME_ACTION_CHI, referenceTile)
    is GameAction.Pon -> tileActionText(MinecraftMessageKeys.GAME_ACTION_PON, referenceTile)
    is GameAction.Kan -> tileActionText(type.toMessageKey(), referenceTile)
    is GameAction.Ron -> tileActionText(MinecraftMessageKeys.GAME_ACTION_RON, referenceTile)
    GameAction.Pass -> Text.translatable(MinecraftMessageKeys.GAME_ACTION_PASS)
    is GameAction.ExhaustiveDraw -> exhaustiveDrawText()
    GameAction.GameStarted, GameAction.RoundStarted, GameAction.Draw -> Text.literal(this::class.simpleName ?: "")
}

/** 組出「動作 + 牌面」形式的顯示文字，[referenceTile] 為 null 時退回顯示 `?`。 */
private fun tileActionText(key: String, referenceTile: Tile?): Text = Text.translatable(
    key,
    referenceTile?.toDisplayText() ?: Text.literal("?"),
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
