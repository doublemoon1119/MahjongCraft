package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.stress

/**
 * 開始壓力測試時可在指令中指定的設定。
 *
 * @property pace 每桌推進節奏。
 * @property historyMode 歷史處理方式。
 * @property stutterLimitMillis 單一 tick 耗時超過這個毫秒數就算一次卡頓。
 * @property warmupSeconds 開始後不列入統計與卡頓判斷的秒數；0 表示不暖機。
 */
data class StressRunOptions(
    val pace: StressTestPace,
    val historyMode: StressHistoryMode,
    val stutterLimitMillis: Int,
    val warmupSeconds: Int,
) {
    init {
        require(stutterLimitMillis in requireNotNull(StressOption.MAX_MSPT.range)) { "Stutter limit is out of range" }
        require(warmupSeconds in requireNotNull(StressOption.WARMUP.range)) { "Warm-up seconds are out of range" }
    }

    /** 暖機的 tick 數。 */
    val warmupTicks: Long get() = warmupSeconds.toLong() * TICKS_PER_SECOND

    /** 依這些設定建立的安全閥門檻。 */
    fun thresholds(): StressSafetyThresholds = StressSafetyThresholds(stutterLimitMillis = stutterLimitMillis.toDouble())

    companion object {
        /** 指令中沒有指定任何選項時的設定。 */
        val DEFAULT: StressRunOptions = StressRunOptions(
            pace = StressTestPace.REALTIME,
            historyMode = StressHistoryMode.WRITE,
            stutterLimitMillis = 100,
            warmupSeconds = 60,
        )

        /** 一秒的伺服器 tick 數。 */
        private const val TICKS_PER_SECOND = 20L
    }
}

/**
 * 指令中的具名選項；以「名稱 值」成對出現，順序不限、每個最多一次。
 *
 * @property commandName 指令中使用的名稱。
 * @property range 數值選項接受的範圍；非數值選項為 null。
 */
enum class StressOption(val commandName: String, val range: IntRange?) {
    /** 推進節奏：`realtime` 或 `fast`。 */
    PACE("pace", null),

    /** 歷史處理方式：`write`、`encode` 或 `off`。 */
    HISTORY("history", null),

    /** 判定卡頓的單一 tick 耗時毫秒數。 */
    MAX_MSPT("max_mspt", 1..10_000),

    /** 暖機秒數。 */
    WARMUP("warmup", 0..600),
}

/** 解析選項時的錯誤。 */
sealed interface StressOptionError {
    /**
     * 不認得的選項名稱。
     *
     * @property token 寫錯的名稱。
     */
    data class UnknownOption(val token: String) : StressOptionError

    /**
     * 選項後面沒有值。
     *
     * @property option 缺少值的選項。
     */
    data class MissingValue(val option: StressOption) : StressOptionError

    /**
     * 同一個選項寫了兩次。
     *
     * @property option 重複的選項。
     */
    data class DuplicateOption(val option: StressOption) : StressOptionError

    /**
     * 選項的值不在可用的名稱或範圍內。
     *
     * @property option 值無效的選項。
     * @property value 寫錯的值。
     */
    data class InvalidValue(val option: StressOption, val value: String) : StressOptionError
}

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

/**
 * 解析指令中「名稱 值」成對的選項文字，例如 `history off warmup 30`；空白文字得到 [StressRunOptions.DEFAULT]。
 *
 * @param text 選項文字。
 * @return 解析結果；有多個錯誤時回報第一個。
 */
fun parseStressRunOptions(text: String): StressOptionsParseResult {
    val tokens = text.trim().split(WHITESPACE).filter(String::isNotEmpty)
    var options = StressRunOptions.DEFAULT
    val seen = mutableSetOf<StressOption>()
    var index = 0
    while (index < tokens.size) {
        val option = StressOption.entries.firstOrNull { it.commandName == tokens[index] }
            ?: return StressOptionsParseResult.Invalid(StressOptionError.UnknownOption(tokens[index]))
        if (!seen.add(option)) return StressOptionsParseResult.Invalid(StressOptionError.DuplicateOption(option))
        val value = tokens.getOrNull(index + 1) ?: return StressOptionsParseResult.Invalid(StressOptionError.MissingValue(option))
        options = options.with(option, value) ?: return StressOptionsParseResult.Invalid(StressOptionError.InvalidValue(option, value))
        index += 2
    }
    return StressOptionsParseResult.Parsed(options)
}

/**
 * 依目前輸入到一半的選項文字，列出最後一個字可以補全的候選：輪到名稱時為尚未使用的選項名稱，輪到值時為該選項可用的值。
 *
 * @param text 目前已輸入的選項文字。
 * @return 候選清單與最後一個字在 [text] 中的起點。
 */
fun stressOptionSuggestions(text: String): Pair<Int, List<String>> {
    val lastStart = text.lastIndexOfAny(charArrayOf(' ', '\t')) + 1
    val completed = text.substring(0, lastStart).trim().split(WHITESPACE).filter(String::isNotEmpty)
    val candidates = if (completed.size % 2 == 0) {
        val used = completed.filterIndexed { index, _ -> index % 2 == 0 }.toSet()
        StressOption.entries.map { it.commandName }.filterNot { it in used }
    } else {
        when (StressOption.entries.firstOrNull { it.commandName == completed.last() }) {
            StressOption.PACE -> StressTestPace.entries.map { it.commandName }
            StressOption.HISTORY -> StressHistoryMode.entries.map { it.commandName }
            StressOption.MAX_MSPT -> listOf(StressRunOptions.DEFAULT.stutterLimitMillis.toString())
            StressOption.WARMUP -> WARMUP_SUGGESTIONS
            null -> emptyList()
        }
    }
    return lastStart to candidates
}

/** 套用一個選項的值；值無效時回傳 null。 */
private fun StressRunOptions.with(option: StressOption, value: String): StressRunOptions? = when (option) {
    StressOption.PACE -> StressTestPace.entries.firstOrNull { it.commandName == value }?.let { copy(pace = it) }
    StressOption.HISTORY -> StressHistoryMode.entries.firstOrNull { it.commandName == value }?.let { copy(historyMode = it) }
    StressOption.MAX_MSPT -> value.toBoundedInt(option)?.let { copy(stutterLimitMillis = it) }
    StressOption.WARMUP -> value.toBoundedInt(option)?.let { copy(warmupSeconds = it) }
}

/** 解析成 [option] 範圍內的整數；不是整數或超出範圍時回傳 null。 */
private fun String.toBoundedInt(option: StressOption): Int? = toIntOrNull()?.takeIf { it in requireNotNull(option.range) }

/** 分隔選項的空白。 */
private val WHITESPACE = Regex("\\s+")

/** 暖機秒數的補全候選。 */
private val WARMUP_SUGGESTIONS: List<String> = listOf("0", "30", "60")
