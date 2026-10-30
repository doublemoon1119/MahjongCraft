package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.platform.fabric.text.toDisplayText
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoArgument
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLine
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLineDisplayRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileEmojiRegistry
import net.minecraft.text.Text

/**
 * 將局況資料行與參數組成依用戶端語系解析的文字。
 *
 * @property roundInfoLineDisplayRegistry 局況行與規則訊息的翻譯定義來源。
 * @property tileDisplayNameRegistry 擴充牌種名稱查詢來源。
 * @property tileAssetRegistry 牌種素材 key 查詢來源。
 * @property tileEmojiRegistry 牌面 emoji 查詢來源。
 * @property warn 輸出一次性未知參數診斷的函式。
 */
class RoundInfoLineTextBuilder(
    private val roundInfoLineDisplayRegistry: RoundInfoLineDisplayRegistry,
    private val tileDisplayNameRegistry: TileDisplayNameRegistry,
    private val tileAssetRegistry: MinecraftTileAssetRegistry,
    private val tileEmojiRegistry: TileEmojiRegistry,
    private val warn: (String) -> Unit,
) {
    /** 已輸出缺少翻譯定義警告的訊息 ID。 */
    private val warnedMessageIds = mutableSetOf<String>()

    /** 是否已輸出無法辨識參數的警告。 */
    private var warnedUnrecognizedArgument = false

    /**
     * 依 [line] 的翻譯定義組成文字；缺少行定義時顯示原始行 ID。
     *
     * @param line 要顯示的局況資料行。
     * @return 依本機語系組成的文字或原始行 ID。
     */
    fun buildLineText(line: RoundInfoLine): Text {
        val display = roundInfoLineDisplayRegistry.find(line.key) ?: return Text.literal(line.key)
        val arguments = line.args.map(::buildArgumentText)
        return Text.translatable(display.translationKey, *arguments.toTypedArray())
    }

    /**
     * 將單一局況參數轉成 Minecraft 文字或整數。
     *
     * @param argument 要顯示的局況參數。
     * @return 翻譯文字、字面文字或整數。
     */
    private fun buildArgumentText(argument: RoundInfoArgument): Any = when (argument) {
        is RoundInfoArgument.Number -> argument.value
        is RoundInfoArgument.WindValue -> Text.translatable(argument.value.toMessageKey())
        is RoundInfoArgument.TileValue -> argument.tile.toDisplayText(
            tileDisplayNameRegistry,
            tileAssetRegistry,
            tileEmojiRegistry,
        )
        is RoundInfoArgument.Message -> roundInfoLineDisplayRegistry.findMessage(argument.id)
            ?.let { Text.translatable(it.translationKey) }
            ?: Text.literal(argument.id).also { warnMessageIdOnce(argument.id) }
        RoundInfoArgument.Unrecognized -> Text.literal("?").also { warnUnrecognizedArgumentOnce() }
    }

    /**
     * 場風對應的牌面顯示文字 key，跟手牌裡的風牌共用同一組翻譯。
     *
     * @return 對應風牌的翻譯 key。
     */
    private fun Wind.toMessageKey(): String = when (this) {
        Wind.EAST -> MinecraftMessageKeys.TILE_HONOR_EAST
        Wind.SOUTH -> MinecraftMessageKeys.TILE_HONOR_SOUTH
        Wind.WEST -> MinecraftMessageKeys.TILE_HONOR_WEST
        Wind.NORTH -> MinecraftMessageKeys.TILE_HONOR_NORTH
    }

    /**
     * 每個未登記規則訊息 ID 只輸出一次警告。
     *
     * @param id 未登記訊息的完整 ID。
     */
    private fun warnMessageIdOnce(id: String) {
        if (warnedMessageIds.add(id)) warn("No round info message registered for ID: $id")
    }

    /** 無法辨識參數只輸出一次警告。 */
    private fun warnUnrecognizedArgumentOnce() {
        if (warnedUnrecognizedArgument) return
        warnedUnrecognizedArgument = true
        warn("Received an unrecognized round info argument; displaying '?'")
    }
}
