package com.doublemoon1119.mahjongcraft.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.ElementType
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import org.jetbrains.kotlin.com.intellij.lang.ASTNode

/**
 * 攔截宣告在主建構子、但類別內從未被讀取的 `private val`／`private var` 屬性。
 *
 * Kotlin 編譯器不會對這種孤兒依賴發出警告，完整 build 也不會失敗，因此每次把職責搬到新類別之後，原類別
 * 很容易留下已經沒有使用點的注入依賴。
 *
 * 判定只看同一個類別內的識別字節點，不掃描註解與 KDoc——`@property` 說明不算使用。刻意保留、尚未實作的
 * 佔位參數以 `@Suppress("ktlint:$RULE_SET_ID:no-unused-constructor-property")` 標註並說明理由。
 */
internal class NoUnusedConstructorPropertyRule :
    Rule(
        ruleId = RuleId("$RULE_SET_ID:no-unused-constructor-property"),
        about = About(maintainer = MAINTAINER),
    ),
    RuleAutocorrectApproveHandler {
    /** 逐一檢查類別主建構子宣告的 private 屬性。 */
    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision,
    ) {
        if (node.elementType != ElementType.CLASS) return
        val constructor = node.children().firstOrNull { it.elementType == ElementType.PRIMARY_CONSTRUCTOR } ?: return
        val parameterList = constructor.children()
            .firstOrNull { it.elementType == ElementType.VALUE_PARAMETER_LIST }
            ?: return
        val privateProperties = parameterList.children()
            .filter { it.elementType == ElementType.VALUE_PARAMETER && it.isPrivateProperty() }
            .toList()
        if (privateProperties.isEmpty()) return

        val usedNames = node.identifiersOutside(parameterList)
        privateProperties.forEach { parameter ->
            val name = parameter.propertyName() ?: return@forEach
            if (name in usedNames) return@forEach
            emit(
                parameter.startOffset,
                "Constructor property '$name' is never used; remove it or suppress with a stated reason",
                false,
            )
        }
    }

    /** 參數是否同時具有 `private` modifier 與 `val`／`var`。 */
    private fun ASTNode.isPrivateProperty(): Boolean {
        val modifiers = children().firstOrNull { it.elementType == ElementType.MODIFIER_LIST } ?: return false
        val isPrivate = modifiers.children().any { it.elementType == ElementType.PRIVATE_KEYWORD }
        val isProperty = children().any {
            it.elementType == ElementType.VAL_KEYWORD || it.elementType == ElementType.VAR_KEYWORD
        }
        return isPrivate && isProperty
    }

    /** 取得參數宣告的屬性名稱。 */
    private fun ASTNode.propertyName(): String? = children().firstOrNull { it.elementType == ElementType.IDENTIFIER }?.text

    /** 收集本節點底下、[excluded] 之外的全部識別字文字；註解與 KDoc 不產生識別字節點。 */
    private fun ASTNode.identifiersOutside(excluded: ASTNode): Set<String> {
        val names = mutableSetOf<String>()
        fun walk(current: ASTNode) {
            // KDoc 的 `@property` 名稱也解析成識別字，但說明不等於使用。
            if (current === excluded || current.elementType == ElementType.KDOC) return
            if (current.elementType == ElementType.IDENTIFIER || current.elementType == ElementType.REFERENCE_EXPRESSION) {
                names += current.text
            }
            current.children().forEach(::walk)
        }
        walk(this)
        return names
    }

    /** 逐一走訪子節點。 */
    private fun ASTNode.children(): Sequence<ASTNode> = generateSequence(firstChildNode) { it.treeNext }
}
