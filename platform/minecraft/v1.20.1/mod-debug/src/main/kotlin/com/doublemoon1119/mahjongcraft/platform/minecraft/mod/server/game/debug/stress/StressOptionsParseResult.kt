package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug.stress

import com.doublemoon1119.mahjongcraft.platform.minecraft.server.game.debug.stress.StressOptionError

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
