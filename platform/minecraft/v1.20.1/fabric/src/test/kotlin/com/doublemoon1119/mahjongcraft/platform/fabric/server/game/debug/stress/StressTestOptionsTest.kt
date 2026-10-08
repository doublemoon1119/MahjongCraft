package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.stress

import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證壓力測試指令具名選項的解析與補全。 */
class StressTestOptionsTest {
    /** 沒有寫任何選項時使用預設設定。 */
    @Test
    fun `empty options use the defaults`() {
        assertEquals(StressOptionsParseResult.Parsed(StressRunOptions.DEFAULT), parseStressRunOptions(""))
        assertEquals(StressOptionsParseResult.Parsed(StressRunOptions.DEFAULT), parseStressRunOptions("   "))
    }

    /** 選項順序不限、只寫需要的，其餘沿用預設。 */
    @Test
    fun `options can be given in any order`() {
        val expected = StressRunOptions.DEFAULT.copy(historyMode = StressHistoryMode.OFF, warmupSeconds = 30)

        assertEquals(StressOptionsParseResult.Parsed(expected), parseStressRunOptions("warmup 30 history off"))
        assertEquals(StressOptionsParseResult.Parsed(expected), parseStressRunOptions("history  off   warmup 30"))
    }

    /** 每個選項都能設定。 */
    @Test
    fun `every option is applied`() {
        val parsed = parseStressRunOptions("pace fast history encode max_mspt 250 warmup 0")

        assertEquals(
            StressOptionsParseResult.Parsed(
                StressRunOptions(pace = StressTestPace.FAST, historyMode = StressHistoryMode.ENCODE, stutterLimitMillis = 250, warmupSeconds = 0),
            ),
            parsed,
        )
    }

    /** 名稱寫錯、缺少值、重複或值無效時回報第一個錯誤。 */
    @Test
    fun `malformed options report the first error`() {
        assertEquals(StressOptionsParseResult.Invalid(StressOptionError.UnknownOption("speed")), parseStressRunOptions("speed fast"))
        assertEquals(StressOptionsParseResult.Invalid(StressOptionError.MissingValue(StressOption.WARMUP)), parseStressRunOptions("history off warmup"))
        assertEquals(StressOptionsParseResult.Invalid(StressOptionError.DuplicateOption(StressOption.PACE)), parseStressRunOptions("pace fast pace realtime"))
        assertEquals(StressOptionsParseResult.Invalid(StressOptionError.InvalidValue(StressOption.HISTORY, "none")), parseStressRunOptions("history none"))
        assertEquals(StressOptionsParseResult.Invalid(StressOptionError.InvalidValue(StressOption.WARMUP, "601")), parseStressRunOptions("warmup 601"))
        assertEquals(StressOptionsParseResult.Invalid(StressOptionError.InvalidValue(StressOption.MAX_MSPT, "0")), parseStressRunOptions("max_mspt 0"))
        assertEquals(StressOptionsParseResult.Invalid(StressOptionError.InvalidValue(StressOption.MAX_MSPT, "abc")), parseStressRunOptions("max_mspt abc"))
    }

    /** 暖機秒數換算成 tick。 */
    @Test
    fun `warm-up seconds convert to ticks`() {
        assertEquals(600L, StressRunOptions.DEFAULT.copy(warmupSeconds = 30).warmupTicks)
        assertEquals(0L, StressRunOptions.DEFAULT.copy(warmupSeconds = 0).warmupTicks)
    }

    /** 輪到名稱時補全尚未使用的選項，輪到值時補全該選項可用的值；位置從最後一個字開始。 */
    @Test
    fun `suggestions follow the position in the options`() {
        assertEquals(0 to listOf("pace", "history", "max_mspt", "warmup"), stressOptionSuggestions(""))
        assertEquals(8 to listOf("write", "encode", "off"), stressOptionSuggestions("history "))
        assertEquals(8 to listOf("write", "encode", "off"), stressOptionSuggestions("history en"))
        assertEquals(12 to listOf("pace", "max_mspt", "warmup"), stressOptionSuggestions("history off "))
        assertEquals(6 to emptyList<String>(), stressOptionSuggestions("speed "), "An unknown option has no values to suggest.")
    }
}
