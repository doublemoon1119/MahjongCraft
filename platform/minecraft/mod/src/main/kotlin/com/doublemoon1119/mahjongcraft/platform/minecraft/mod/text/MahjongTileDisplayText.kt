package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.text

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileFaceView
import com.doublemoon1119.mahjongcraft.logic.base.TileTypeId
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.notification.PlayerFeedbackPublisher
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileEmojiRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.findFor
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.toAssetKey
import net.minecraft.network.chat.Component

/**
 * 將 [Tile] 轉成人類可讀的顯示文字，供對局指令的手牌列表、候選 tooltip 與回饋訊息共用。
 *
 * 放在 Minecraft 版本模組（而非 `platform/minecraft/common`）——`net.minecraft.network.chat.Component` 只在
 * 依賴實際 Minecraft 版本的模組才看得到，`platform/minecraft/common` 刻意不依賴任何版本的 Minecraft，
 * 比照 [PlayerFeedbackPublisher]「共用模型在 common、實際組 Text 在版本模組」的既有分工。
 *
 * [Tile.Extension] 查 [displayNameRegistry]（第三方規則模組可自行登記自己的擴充牌種）；查不到時 fallback
 * 顯示原始 [TileTypeId] 字串。帶外觀樣式的牌面（例如赤五）改用 [TileFaceView.toDisplayText]。
 *
 * 翻譯文字前面會依 [tileAssetRegistry] 解出的 asset key，查 [tileEmojiRegistry] 加上對應的牌面 emoji
 * 字元（要實際顯示成貼圖，還需要有人提供對應的 `assets/minecraft/font/default.json` bitmap
 * provider，例如 `platform/minecraft/common/src/jvmMain/resources` 內建的那份）；查不到對應 emoji
 * （例如未登記的第三方牌種）時只顯示純文字，不強行湊一個不存在的圖案。
 *
 * @param displayNameRegistry 擴充牌種顯示名稱查詢來源。
 * @param tileAssetRegistry 牌種到貼圖資產鍵的轉換來源。
 * @param tileEmojiRegistry 牌面 emoji 查詢來源。
 * @return 包含可用牌面 emoji 與本地化名稱的文字。
 */
fun Tile.toDisplayText(
    displayNameRegistry: TileDisplayNameRegistry,
    tileAssetRegistry: MinecraftTileAssetRegistry,
    tileEmojiRegistry: TileEmojiRegistry,
): Component {
    val text = toNameText(displayNameRegistry)
    val emoji = tileEmojiRegistry.findFor(toAssetKey(tileAssetRegistry), tileAssetRegistry) ?: return text
    return Component.literal("$emoji ").append(text)
}

/**
 * 將 [Tile] 轉成本地化牌名，不加牌面 emoji，例如滑鼠提示中的「3萬」。
 *
 * [Tile.Extension] 查 [displayNameRegistry]；查不到時顯示原始 [TileTypeId] 字串。
 *
 * @param displayNameRegistry 擴充牌種顯示名稱查詢來源。
 * @return 本地化牌名。
 */
fun Tile.toNameText(displayNameRegistry: TileDisplayNameRegistry): Component = when (this) {
    is Tile.Numeric -> Component.translatable(suit.toMessageKey(), value)
    Tile.Honor.East -> Component.translatable(MinecraftMessageKeys.TILE_HONOR_EAST)
    Tile.Honor.South -> Component.translatable(MinecraftMessageKeys.TILE_HONOR_SOUTH)
    Tile.Honor.West -> Component.translatable(MinecraftMessageKeys.TILE_HONOR_WEST)
    Tile.Honor.North -> Component.translatable(MinecraftMessageKeys.TILE_HONOR_NORTH)
    Tile.Honor.Red -> Component.translatable(MinecraftMessageKeys.TILE_HONOR_RED)
    Tile.Honor.Green -> Component.translatable(MinecraftMessageKeys.TILE_HONOR_GREEN)
    Tile.Honor.White -> Component.translatable(MinecraftMessageKeys.TILE_HONOR_WHITE)
    is Tile.Extension -> displayNameRegistry.find(typeId)
        ?.let(Component::translatable)
        ?: Component.literal(typeId.toString())
}

/**
 * 將已知牌面轉成顯示文字：外觀樣式有登記名稱時使用該名稱，例如「赤五萬」，否則使用基本牌面的名稱。
 *
 * 牌面 emoji 依同一個外觀樣式解析出的素材 key 查詢。
 *
 * @param displayNameRegistry 牌種與外觀樣式的名稱查詢來源。
 * @param tileAssetRegistry 牌面與外觀樣式的素材 key 查詢來源。
 * @param tileEmojiRegistry 牌面 emoji 查詢來源。
 * @return 包含可用牌面 emoji 與本地化名稱的文字。
 */
fun TileFaceView.toDisplayText(
    displayNameRegistry: TileDisplayNameRegistry,
    tileAssetRegistry: MinecraftTileAssetRegistry,
    tileEmojiRegistry: TileEmojiRegistry,
): Component {
    val styleName = appearanceId?.let { displayNameRegistry.findStyle(it, tile) } ?: return tile.toDisplayText(displayNameRegistry, tileAssetRegistry, tileEmojiRegistry)
    val text = Component.translatable(styleName)
    val emoji = tileEmojiRegistry.findFor(toAssetKey(tileAssetRegistry), tileAssetRegistry) ?: return text
    return Component.literal("$emoji ").append(text)
}

/**
 * 將已知牌面轉成本地化牌名，不加牌面 emoji：外觀樣式有登記名稱時使用該名稱，例如「赤五萬」，否則使用基本牌面的名稱。
 *
 * @param displayNameRegistry 牌種與外觀樣式的名稱查詢來源。
 * @return 本地化牌名。
 */
fun TileFaceView.toNameText(displayNameRegistry: TileDisplayNameRegistry): Component = appearanceId
    ?.let { displayNameRegistry.findStyle(it, tile) }
    ?.let(Component::translatable)
    ?: tile.toNameText(displayNameRegistry)

/**
 * 將數牌花色映射到對應的翻譯 key。
 *
 * @return 此花色的本地化翻譯鍵。
 */
private fun Tile.Suit.toMessageKey(): String = when (this) {
    Tile.Suit.Character -> MinecraftMessageKeys.TILE_SUIT_CHARACTER
    Tile.Suit.Dot -> MinecraftMessageKeys.TILE_SUIT_DOT
    Tile.Suit.Bamboo -> MinecraftMessageKeys.TILE_SUIT_BAMBOO
}
