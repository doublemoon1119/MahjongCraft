package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.stress

/** 解析選項的結果。 */
sealed interface StressOptionsParseResult {
    /**
     * 解析成功。
     *
     * @property options 沒有指定的選項沿用 [StressRunOptions.DEFAULT]。
     */
    data class Parsed(val options: StressRunOptions) : StressOptionsParseResult

    /**
     * 解析失敗。
     *
     * @property error 第一個遇到的錯誤。
     */
    data class Invalid(val error: StressOptionError) : StressOptionsParseResult
}
