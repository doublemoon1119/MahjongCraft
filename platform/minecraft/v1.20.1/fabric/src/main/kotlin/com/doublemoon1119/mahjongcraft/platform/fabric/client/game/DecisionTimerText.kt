package com.doublemoon1119.mahjongcraft.platform.fabric.client.game

import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import kotlin.math.ceil

/**
 * 決策倒數的一段文字。
 *
 * @property text 這一段的文字。
 * @property color 這一段的文字顏色。
 */
internal data class DecisionTimerPart(
    val text: String,
    val color: Int,
)

/**
 * 基本時間、加號與較低對比的保留時間；保留時間開始消耗後依剩餘秒數轉為橘色與紅色。
 *
 * 兩段時間都已用完時為空清單。
 */
internal fun decisionTimerParts(baseSeconds: Int, reserveSeconds: Int): List<DecisionTimerPart> {
    val consumingReserve = baseSeconds <= 0 && reserveSeconds > 0
    return buildList {
        if (baseSeconds > 0) add(DecisionTimerPart(baseSeconds.toString(), BASE_COLOR))
        if (baseSeconds > 0 && reserveSeconds > 0) add(DecisionTimerPart(" + ", SEPARATOR_COLOR))
        if (reserveSeconds > 0) {
            val reserveColor = when {
                !consumingReserve -> RESERVE_COLOR
                reserveSeconds <= RESERVE_CRITICAL_SECONDS -> RESERVE_CRITICAL_COLOR
                else -> RESERVE_CONSUMING_COLOR
            }
            add(DecisionTimerPart(reserveSeconds.toString(), reserveColor))
        }
    }
}

/** [parts] 以 [scale] 縮放後的畫面寬度。 */
internal fun decisionTimerWidth(renderer: TextRenderer, parts: List<DecisionTimerPart>, scale: Float): Int = ceil(parts.sumOf { renderer.getWidth(it.text) } * scale).toInt()

/** 以 [centerX] 為水平中心、[y] 為上緣，依 [scale] 縮放畫出 [parts]。 */
internal fun DrawContext.drawDecisionTimer(
    renderer: TextRenderer,
    parts: List<DecisionTimerPart>,
    y: Int,
    centerX: Int,
    scale: Float,
) {
    if (parts.isEmpty()) return
    val width = parts.sumOf { renderer.getWidth(it.text) }
    matrices.push()
    matrices.scale(scale, scale, 1f)
    var x = centerX / scale - width / 2f
    parts.forEach { part ->
        drawTextWithShadow(renderer, part.text, x.toInt(), (y / scale).toInt(), part.color)
        x += renderer.getWidth(part.text)
    }
    matrices.pop()
}

private const val BASE_COLOR = 0xFFD54F
private const val SEPARATOR_COLOR = 0x888888
private const val RESERVE_COLOR = 0xB0B0B0
private const val RESERVE_CONSUMING_COLOR = 0xE69A45
private const val RESERVE_CRITICAL_COLOR = 0xE05252
private const val RESERVE_CRITICAL_SECONDS = 5
