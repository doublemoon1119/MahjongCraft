package com.doublemoon1119.mahjongcraft.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.ElementType
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.psi.tree.IElementType

/**
 * 攔截 Kotlin 程式碼中可避免的專案完整套件名稱引用，要求改用 file-level `import` 加短名稱。
 *
 * 只檢查真正的程式碼引用：型別位置的 `USER_TYPE` 與運算式位置的 `DOT_QUALIFIED_EXPRESSION`。`package`
 * 與 `import` directive、字串常值、註解與 KDoc 都不是這兩種節點，因此天然不會被判為違規——`@ComponentScan`
 * 這類把套件名稱當字串保存的 component scanning 契約，以及內層模組說明外層呼叫端時寫在註解裡的跨依賴
 * 邊界引用，都不受影響。
 *
 * 巢狀節點只回報最外層一次：`com.example.Foo.Bar` 的內層限定式不重複回報。
 *
 * 規則只回報，不自動修正——自動插入 import 需要處理同名衝突、巢狀宣告與 alias，風險不屬於格式化工具。
 */
internal class NoFullyQualifiedProjectReferenceRule :
    Rule(
        ruleId = RuleId("$RULE_SET_ID:no-fully-qualified-project-reference"),
        about = About(maintainer = MAINTAINER),
    ),
    RuleAutocorrectApproveHandler {
    /** 檢查型別與運算式位置的限定引用。 */
    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision,
    ) {
        if (node.elementType != ElementType.USER_TYPE && node.elementType != ElementType.DOT_QUALIFIED_EXPRESSION) {
            return
        }
        val text = node.text
        if (!text.startsWith(PROJECT_PACKAGE_PREFIX)) return
        // package／import directive 的限定名稱也解析成同樣的節點型別，但它們正是短名稱的來源。
        if (node.hasAncestor(ElementType.PACKAGE_DIRECTIVE, ElementType.IMPORT_DIRECTIVE)) return
        // 同型別的外層節點已涵蓋本節點，只回報最外層一次。
        if (node.treeParent?.elementType == node.elementType) return
        emit(
            node.startOffset,
            "Use a file-level import and the short name instead of the fully qualified $text",
            false,
        )
    }

    /** 節點是否位於任一指定型別的祖先底下。 */
    private fun ASTNode.hasAncestor(vararg elementTypes: IElementType): Boolean = generateSequence(treeParent) { it.treeParent }.any { it.elementType in elementTypes }

    private companion object {
        /** 觸發規則的專案套件前綴。 */
        const val PROJECT_PACKAGE_PREFIX: String = "com.doublemoon1119.mahjongcraft."
    }
}
