package com.doublemoon1119.mahjongcraft.logic.module

/**
 * 桌面局況顯示的規則自訂延伸項目，見 [MahjongRuleModule.getRoundInfoExtras]。
 *
 * @property key 呈現層事先登記、認得才顯示的穩定字串；呈現層看到不認得的 key 時應該略過該行
 * （forward-compatible），不是報錯。
 * @property value 要代入翻譯字串的整數參數。
 */
data class RoundInfoExtra(val key: String, val value: Int)
