package com.doublemoon1119.mahjongcraft.platform.fabric.text

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileEmojiRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.toAssetKey
import net.minecraft.text.Text

/**
 * 將 [Tile] 轉成人類可讀的顯示文字，供對局指令的手牌列表、候選 tooltip 與回饋訊息共用。
 *
 * 放在 Fabric 模組（而非 `platform/minecraft/common`）——`net.minecraft.text.Text` 只在有實際
 * Minecraft 依賴的 loader 模組才看得到，`platform/minecraft/common` 刻意不依賴任何版本的 Minecraft，
 * 比照 [com.doublemoon1119.mahjongcraft.platform.fabric.server.notification.FabricPlayerFeedbackPublisher]
 * 「共用模型在 common、實際組 Text 在 Fabric」的既有分工。
 *
 * [Tile.Extension] 查 [displayNameRegistry]（內建日麻赤五已登記，第三方規則模組可自行登記自己的擴充
 * 牌種）；查不到時 fallback 顯示原始 [com.doublemoon1119.mahjongcraft.logic.base.TileTypeId] 字串。
 *
 * 翻譯文字前面會依 [tileAssetRegistry] 解出的 asset key，查 [tileEmojiRegistry] 加上對應的牌面 emoji
 * 字元（要實際顯示成貼圖，還需要有人提供對應的 `assets/minecraft/font/default.json` bitmap
 * provider，例如 `platform/minecraft/common/src/jvmMain/resources` 內建的那份）；查不到對應 emoji
 * （例如未登記的第三方牌種）時只顯示純文字，不強行湊一個不存在的圖案。
 */
fun Tile.toDisplayText(
    displayNameRegistry: TileDisplayNameRegistry,
    tileAssetRegistry: MinecraftTileAssetRegistry,
    tileEmojiRegistry: TileEmojiRegistry,
): Text {
    val text = when (this) {
        is Tile.Numeric -> Text.translatable(suit.toMessageKey(), value)
        Tile.Honor.East -> Text.translatable(MinecraftMessageKeys.TILE_HONOR_EAST)
        Tile.Honor.South -> Text.translatable(MinecraftMessageKeys.TILE_HONOR_SOUTH)
        Tile.Honor.West -> Text.translatable(MinecraftMessageKeys.TILE_HONOR_WEST)
        Tile.Honor.North -> Text.translatable(MinecraftMessageKeys.TILE_HONOR_NORTH)
        Tile.Honor.Red -> Text.translatable(MinecraftMessageKeys.TILE_HONOR_RED)
        Tile.Honor.Green -> Text.translatable(MinecraftMessageKeys.TILE_HONOR_GREEN)
        Tile.Honor.White -> Text.translatable(MinecraftMessageKeys.TILE_HONOR_WHITE)
        is Tile.Extension -> displayNameRegistry.find(typeId)
            ?.let(Text::translatable)
            ?: Text.literal(typeId.toString())
    }
    val emoji = tileEmojiRegistry.find(toAssetKey(tileAssetRegistry)) ?: return text
    return Text.literal("$emoji ").append(text)
}

/** 將數牌花色映射到對應的翻譯 key。 */
private fun Tile.Suit.toMessageKey(): String = when (this) {
    Tile.Suit.Character -> MinecraftMessageKeys.TILE_SUIT_CHARACTER
    Tile.Suit.Dot -> MinecraftMessageKeys.TILE_SUIT_DOT
    Tile.Suit.Bamboo -> MinecraftMessageKeys.TILE_SUIT_BAMBOO
}
