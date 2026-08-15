package com.doublemoon1119.mahjongcraft.platform.fabric.text

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileTypes
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import net.minecraft.text.Text

/**
 * 將 [Tile] 轉成人類可讀的顯示文字，供對局指令的手牌列表、候選 tooltip 與回饋訊息共用。
 *
 * 放在 Fabric 模組（而非 `platform/minecraft/common`）——`net.minecraft.text.Text` 只在有實際
 * Minecraft 依賴的 loader 模組才看得到，`platform/minecraft/common` 刻意不依賴任何版本的 Minecraft，
 * 比照 [com.doublemoon1119.mahjongcraft.platform.fabric.server.notification.FabricPlayerFeedbackPublisher]
 * 「共用模型在 common、實際組 Text 在 Fabric」的既有分工。
 *
 * [Tile.Extension] 內建日麻赤五（[RiichiTileTypes]）額外特判顯示成「赤 + 對應花色的 5」；其餘（第三方
 * 規則模組的擴充牌種）沒有顯示名稱對照表，fallback 顯示原始 [com.doublemoon1119.mahjongcraft.logic.base.TileTypeId]
 * 字串——跟遊戲規則 hover text 目前先顯示技術欄位名稱、之後再補顯示名稱對照的做法一致，不是新模式。
 *
 * TODO: 之後若要幫其他 Extension 牌種（尤其是第三方規則模組）補上翻譯後的顯示名稱，應該仿照
 *   `AiStrategyDisplayNameRegistry` 的開放註冊模式新增一個對照 registry，而不是在這裡窮舉每個牌種。
 */
fun Tile.toDisplayText(): Text = when (this) {
    is Tile.Numeric -> Text.translatable(suit.toMessageKey(), value)
    Tile.Honor.East -> Text.translatable(MinecraftMessageKeys.TILE_HONOR_EAST)
    Tile.Honor.South -> Text.translatable(MinecraftMessageKeys.TILE_HONOR_SOUTH)
    Tile.Honor.West -> Text.translatable(MinecraftMessageKeys.TILE_HONOR_WEST)
    Tile.Honor.North -> Text.translatable(MinecraftMessageKeys.TILE_HONOR_NORTH)
    Tile.Honor.Red -> Text.translatable(MinecraftMessageKeys.TILE_HONOR_RED)
    Tile.Honor.Green -> Text.translatable(MinecraftMessageKeys.TILE_HONOR_GREEN)
    Tile.Honor.White -> Text.translatable(MinecraftMessageKeys.TILE_HONOR_WHITE)
    is Tile.Extension -> when (typeId) {
        RiichiTileTypes.RED_FIVE_CHARACTER -> redFiveDisplayText(Tile.Suit.Character)
        RiichiTileTypes.RED_FIVE_DOT -> redFiveDisplayText(Tile.Suit.Dot)
        RiichiTileTypes.RED_FIVE_BAMBOO -> redFiveDisplayText(Tile.Suit.Bamboo)
        else -> Text.literal(typeId.toString())
    }
}

/** 組出「赤 + 該花色的 5」顯示文字，供內建日麻赤五共用。 */
private fun redFiveDisplayText(suit: Tile.Suit): Text = Text.translatable(MinecraftMessageKeys.TILE_RED_FIVE_PREFIX)
    .append(Tile.Numeric(suit, 5).toDisplayText())

/** 將數牌花色映射到對應的翻譯 key。 */
private fun Tile.Suit.toMessageKey(): String = when (this) {
    Tile.Suit.Character -> MinecraftMessageKeys.TILE_SUIT_CHARACTER
    Tile.Suit.Dot -> MinecraftMessageKeys.TILE_SUIT_DOT
    Tile.Suit.Bamboo -> MinecraftMessageKeys.TILE_SUIT_BAMBOO
}
