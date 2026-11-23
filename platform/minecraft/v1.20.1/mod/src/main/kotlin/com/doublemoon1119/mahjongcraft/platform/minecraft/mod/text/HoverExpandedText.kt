package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.text

import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentContents
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.contents.LiteralContents

/**
 * 依指令來源調整訊息：玩家照原樣收到滑鼠提示；伺服器主控台、RCON 與指令方塊看不到提示，改收到 [withHoverTextExpanded] 的結果。
 *
 * @param source 收到訊息的指令來源。
 * @return 適合該來源閱讀的訊息。
 */
fun Component.readableBy(source: CommandSourceStack): Component = if (source.isPlayer) this else withHoverTextExpanded()

/**
 * 把滑鼠提示的內容直接寫在對應的標籤後面，並移除提示與點擊動作。
 *
 * 單行提示接在標籤同一行；多行提示從下一行開始、縮排列出。多行提示的第一行若只是重複標籤或標籤前的文字，例如分類名稱，
 * 展開時略過這一行。
 *
 * @return 不需要滑鼠提示也能讀到全部內容的訊息。
 */
fun Component.withHoverTextExpanded(): MutableComponent = expand(node = this, inheritedHover = null, precedingText = "")

/**
 * 展開一個節點與它的子節點。
 *
 * @param node 要展開的節點。
 * @param inheritedHover 上層節點已展開的提示；子節點帶著同一個提示時不再重複展開。
 * @param precedingText 同一層中這個節點之前最後一段非空白的文字。
 * @return 展開後的節點。
 */
private fun expand(node: Component, inheritedHover: HoverEvent?, precedingText: String): MutableComponent {
    val hover = node.style.hoverEvent
    val ownsHover = hover != null && hover != inheritedHover
    val copy = MutableComponent.create(node.contents).setStyle(node.style.withHoverEvent(null).withClickEvent(null))
    var lastText = ""
    node.siblings.forEach { child ->
        copy.append(expand(node = child, inheritedHover = hover ?: inheritedHover, precedingText = lastText))
        child.string.trim().takeIf(String::isNotEmpty)?.let { lastText = it }
    }
    val hoverText = hover?.getValue(HoverEvent.Action.SHOW_TEXT)
    if (!ownsHover || hoverText == null) return copy
    return Component.empty().append(copy).append(expandedHoverText(hoverText, label = node.string.trim().removeSurrounding("[", "]"), precedingText = precedingText))
}

/**
 * 把提示內容排成接在標籤後面的文字。
 *
 * @param hoverText 提示內容。
 * @param label 標籤文字，不含中括號。
 * @param precedingText 標籤前最後一段非空白的文字。
 * @return 單行時以空格接在標籤後，多行時從下一行開始縮排列出。
 */
private fun expandedHoverText(hoverText: Component, label: String, precedingText: String): MutableComponent {
    val text = hoverText.withHoverTextExpanded()
    if ('\n' !in text.string) return Component.literal(" ").append(text)
    val firstLine = text.string.substringBefore('\n').trim()
    val repeatsLabel = firstLine == label || firstLine == precedingText
    val body = if (repeatsLabel && text.contents.isOwnLine(firstLine)) withoutOwnContents(text) else Component.literal("\n").append(text)
    return indented(body)
}

/**
 * 判斷節點自己的內容（不含子節點）是否正好是第一行。
 *
 * @param firstLine 第一行文字。
 * @return 自己的內容就是第一行時為 true。
 */
private fun ComponentContents.isOwnLine(firstLine: String): Boolean = MutableComponent.create(this).string.trim() == firstLine

/**
 * 只保留節點的子節點，去掉節點自己的內容。
 *
 * @param node 要處理的節點。
 * @return 帶相同樣式、只含原本子節點的節點。
 */
private fun withoutOwnContents(node: Component): MutableComponent = Component.empty().setStyle(node.style).also { copy ->
    node.siblings.forEach(copy::append)
}

/**
 * 在每個換行後加上縮排。
 *
 * @param node 要縮排的節點。
 * @return 每一行都縮排的副本。
 */
private fun indented(node: Component): MutableComponent {
    val contents = node.contents
    val copy = if (contents is LiteralContents) {
        Component.literal(contents.text().replace("\n", "\n$HOVER_INDENT")).setStyle(node.style)
    } else {
        MutableComponent.create(contents).setStyle(node.style)
    }
    node.siblings.forEach { copy.append(indented(it)) }
    return copy
}

/** 展開的多行提示每一行前面的縮排。 */
private const val HOVER_INDENT = "      "
