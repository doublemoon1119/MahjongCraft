package com.doublemoon1119.mahjongcraft.logic.module

/**
 * 桌面局況顯示的一行內容，見 [MahjongRuleModule.getRoundInfoLines]——完全由規則自己決定要不要
 * 提供、提供幾行、用什麼參數，呈現層固定欄位不預設任何內容。
 *
 * @property key 呈現層事先登記、認得才顯示的穩定字串；呈現層看到不認得的 key 時應該略過該行
 * （forward-compatible），不是報錯。
 * @property args 依序代入翻譯字串的整數參數列表。
 */
data class RoundInfoLine(val key: String, val args: List<Int> = emptyList())
