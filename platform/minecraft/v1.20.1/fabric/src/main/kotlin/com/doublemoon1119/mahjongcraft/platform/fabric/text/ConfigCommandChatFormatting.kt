package com.doublemoon1119.mahjongcraft.platform.fabric.text

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import net.minecraft.text.ClickEvent
import net.minecraft.text.HoverEvent
import net.minecraft.text.MutableText
import net.minecraft.text.Style
import net.minecraft.text.Text
import net.minecraft.util.Formatting

/**
 * `config reload`／`config show` 這類設定診斷指令共用的聊天訊息格式，供
 * [com.doublemoon1119.mahjongcraft.platform.fabric.server.config.FabricServerConfigCommand]（server
 * 端）與
 * [com.doublemoon1119.mahjongcraft.platform.fabric.client.config.FabricClientConfigCommand]（client
 * 端）共用，讓兩邊指令效果完全一致，不各自維護一份相同的格式化邏輯。
 */

/** 建立統一帶有 mod 名稱前綴的 config 指令回饋。 */
fun prefixedConfigMessage(message: String, color: Formatting): MutableText = Text
    .literal("[${MinecraftModMetadata.MOD_NAME}] ")
    .formatted(Formatting.GOLD)
    .append(Text.literal(message).formatted(color))

/** 以 section、key 與 value 套用不同顏色格式化單行 TOML，空行則維持空白。 */
fun formatTomlLine(line: String): MutableText = when {
    line.isBlank() -> Text.empty()
    line.startsWith("[") -> Text.literal(line).formatted(Formatting.AQUA)
    " = " in line -> {
        val (key, value) = line.split(" = ", limit = 2)
        Text.literal(key).formatted(Formatting.GRAY)
            .append(Text.literal(" = ").formatted(Formatting.DARK_GRAY))
            .append(Text.literal(value).formatted(Formatting.GREEN))
    }
    else -> Text.literal(line)
}

/**
 * 依 vanilla「中括號 + 顏色 = 可 hover／可點擊」的慣例（例如成就、物品連結訊息），把 [label] 包成
 * `[標籤]`。中括號本身與 [label] 套用同一個 [Style]，確保滑鼠移到中括號上也會觸發 hover，不會只有文字
 * 本身才有效果；[clickEvent] 省略時只有 hover，沒有點擊行為；[color] 預設 AQUA，作為一般可互動文字的
 * 配色。
 */
fun bracketedInteractiveLabel(
    label: Text,
    hoverText: Text,
    clickEvent: ClickEvent? = null,
    color: Formatting = Formatting.AQUA,
): MutableText {
    var style = Style.EMPTY.withColor(color).withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText))
    if (clickEvent != null) {
        style = style.withClickEvent(clickEvent)
    }
    return Text.literal("[").setStyle(style)
        .append(label.copy().setStyle(style))
        .append(Text.literal("]").setStyle(style))
}

/**
 * 建立 `config show` 指令共用的 hover 內容：檔案路徑（灰色）+ 換行後依 [formatTomlLine] 逐行上色的
 * 完整 TOML，供 server／client 兩邊的 `show` 指令共用，把原本直接洗版聊天欄的多行輸出收進同一個
 * hover tooltip。
 */
fun configShowHoverText(displayPath: String, formattedToml: String): MutableText {
    var hoverText: MutableText = Text.literal("Path: $displayPath").formatted(Formatting.DARK_GRAY)
    formattedToml.lineSequence().forEach { line ->
        hoverText = hoverText.append("\n").append(formatTomlLine(line))
    }
    return hoverText
}
