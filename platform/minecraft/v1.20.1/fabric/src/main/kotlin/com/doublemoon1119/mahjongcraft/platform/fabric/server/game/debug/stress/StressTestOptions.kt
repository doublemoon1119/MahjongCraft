package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.stress

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
