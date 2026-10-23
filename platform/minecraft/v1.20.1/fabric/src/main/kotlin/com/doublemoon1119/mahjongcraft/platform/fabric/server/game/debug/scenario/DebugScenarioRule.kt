package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario

/** debug 情境載入後使用的規則模組。 */
internal enum class DebugScenarioRule {
    /** 依這一桌的人數使用一般的四人或三人日麻。 */
    STANDARD,

    /** 開發用的自動拔北三人日麻；只適用三人日麻的桌子。 */
    AUTO_NORTH,

    /** 開發用的和牌者坐莊、每局換位四人日麻；只適用四人日麻的桌子。 */
    WINNER_DEALER,

    /** 開發用的可宣告不聽四人日麻；只適用四人日麻的桌子。 */
    NOTEN_DECLARATION,
}
