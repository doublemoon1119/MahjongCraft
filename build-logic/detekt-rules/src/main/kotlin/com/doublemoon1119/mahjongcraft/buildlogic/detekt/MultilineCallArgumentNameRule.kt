package com.doublemoon1119.mahjongcraft.buildlogic.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.RequiresAnalysisApi
import dev.detekt.api.Rule
import dev.detekt.api.RuleName
import java.net.URI
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtSuperTypeCallEntry
import org.jetbrains.kotlin.psi.KtValueArgument

/**
 * 檢查參數清單跨多行的呼叫是否每個參數都寫了參數名稱。
 *
 * 只檢查 Kotlin 函式與建構子的呼叫；Java 函式與函式型別的呼叫不允許寫參數名稱，可變數量參數與寫在括號外的 lambda
 * 也不檢查。`require`、`check` 這類條件檢查函式與 `error` 只接一個條件或訊息，名稱沒有額外資訊，同樣不檢查。
 * 訊息附上參數名稱，方便依報告補上。
 *
 * @param config 規則的 detekt 設定。
 */
class MultilineCallArgumentNameRule(
    config: Config = Config.empty,
) : Rule(config, DESCRIPTION, URI.create(URL)),
    RequiresAnalysisApi {
    /** 規則在 MahjongCraft 規則集合中的名稱。 */
    override val ruleName: RuleName = RuleName("MultilineCallArgumentName")

    /** 檢查一般函式與建構子呼叫。 */
    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        reportUnnamedArguments(expression)
    }

    /** 檢查類別標頭中對父類別建構子的呼叫。 */
    override fun visitSuperTypeCallEntry(call: KtSuperTypeCallEntry) {
        super.visitSuperTypeCallEntry(call)
        reportUnnamedArguments(call)
    }

    /**
     * 參數清單跨多行時，回報每個沒寫名稱、可以寫名稱的參數。
     *
     * @param element 要檢查的呼叫。
     */
    private fun reportUnnamedArguments(element: KtCallElement) {
        val argumentList = element.valueArgumentList ?: return
        if (!argumentList.text.contains('\n')) return
        val unnamed = element.valueArguments.filterIsInstance<KtValueArgument>().filter { it !is KtLambdaArgument && !it.isNamed() }
        if (unnamed.isEmpty()) return
        val parameterNames = analyze(element) {
            val call = element.resolveToCall()?.singleFunctionCallOrNull() ?: return
            if (!call.symbol.hasStableParameterNames) return
            if (call.symbol.callableId?.asSingleFqName()?.asString() in SELF_EXPLANATORY_CALLS) return
            call.valueArgumentMapping.mapNotNull { (expression, signature) ->
                if (signature.symbol.isVararg) null else expression to signature.name.asString()
            }.toMap()
        }
        unnamed.forEach { argument ->
            val name = argument.getArgumentExpression()?.let(parameterNames::get) ?: return@forEach
            report(Finding(Entity.from(argument), "$MESSAGE_PREFIX`$name`."))
        }
    }

    private companion object {
        /** 只接一個條件或訊息、參數名稱沒有額外資訊的標準函式。 */
        val SELF_EXPLANATORY_CALLS: Set<String> = setOf(
            "kotlin.require",
            "kotlin.requireNotNull",
            "kotlin.check",
            "kotlin.checkNotNull",
            "kotlin.error",
        )

        /** 規則說明。 */
        const val DESCRIPTION: String = "Arguments of a call that spans several lines should be named."

        /** 回報訊息的開頭，後接參數名稱。 */
        const val MESSAGE_PREFIX: String = "Name this argument of a multi-line call: "

        /** 規則說明文件。 */
        const val URL: String = "https://github.com/doublemoon1119/MahjongCraft/blob/refactor/v2/docs/contributing/code-style.md"
    }
}
