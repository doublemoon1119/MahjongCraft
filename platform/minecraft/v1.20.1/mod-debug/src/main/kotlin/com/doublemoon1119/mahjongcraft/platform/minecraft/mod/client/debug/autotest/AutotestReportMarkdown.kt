package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.debug.autotest

/**
 * 把報告轉成給人閱讀的 Markdown：先列整體結果與各步驟摘要，再列每個步驟的檢查、截圖與聲音。
 *
 * @param report 自動測試報告。
 * @return Markdown 文字。
 */
internal fun autotestReportMarkdown(report: AutotestReport): String = buildString {
    appendLine("# Autotest report")
    appendLine()
    appendLine("- Target: ${report.target}")
    appendLine("- Started: ${report.startedAt}")
    appendLine("- Duration: ${report.durationMillis} ms")
    appendLine("- Result: ${report.status.label()}")
    appendLine()
    report.steps.forEach { step -> appendLine("- ${step.status.label()} `${step.id}` (${step.durationMillis} ms)") }
    report.steps.filter { it.status != AutotestStatus.SKIPPED }.forEach { step ->
        appendLine()
        appendLine("## ${step.id}: ${step.status.label()}")
        if (step.checks.isNotEmpty()) {
            appendLine()
            step.checks.forEach { check ->
                val detail = check.detail?.let { " - $it" }.orEmpty()
                appendLine("- [${if (check.passed) "x" else " "}] ${check.name}$detail")
            }
        }
        if (step.screenshots.isNotEmpty()) {
            appendLine()
            appendLine("Screenshots:")
            step.screenshots.forEach { appendLine("- [$it]($it)") }
        }
        if (step.sounds.isNotEmpty()) {
            appendLine()
            appendLine("Sounds:")
            step.sounds.forEach { appendLine("- ${it.atMillis} ms `${it.soundId}`") }
        }
        step.error?.let {
            appendLine()
            appendLine("Error:")
            appendLine()
            appendLine("```text")
            appendLine(it)
            appendLine("```")
        }
    }
}

/** 結果在報告中的寫法。 */
private fun AutotestStatus.label(): String = when (this) {
    AutotestStatus.PASSED -> "PASSED"
    AutotestStatus.FAILED -> "FAILED"
    AutotestStatus.SKIPPED -> "SKIPPED"
    AutotestStatus.ABORTED -> "ABORTED"
}
