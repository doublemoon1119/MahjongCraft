package com.doublemoon1119.mahjongcraft.buildlogic.detekt

import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import dev.detekt.api.RuleName
import java.net.URI
import org.jetbrains.kotlin.kdoc.psi.impl.KDocSection
import org.jetbrains.kotlin.kdoc.psi.impl.KDocTag
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

/**
 * 檢查 KDoc 描述與第一個區塊標籤之間是否保留空白行。
 *
 * @param config 規則的 detekt 設定。
 */
class KDocTagSpacingRule(
    config: Config = Config.empty,
) : Rule(config, DESCRIPTION, URI.create(URL)) {
    /** 規則在 MahjongCraft 規則集合中的名稱。 */
    override val ruleName: RuleName = RuleName("KDocTagSpacing")

    /** 檢查檔案內每個 Kotlin 宣告的 KDoc。 */
    override fun visitFile(file: PsiFile) {
        if (file !is KtFile) return
        file.accept(object : KtTreeVisitorVoid() {
            /** 檢查宣告附帶的 KDoc 標籤間距。 */
            override fun visitDeclaration(declaration: KtDeclaration) {
                declaration.docComment?.let { comment ->
                    val defaultSection = comment.getDefaultSection()
                    if (defaultSection.getContent().trim().isNotEmpty()) {
                        val firstTag = PsiTreeUtil.collectElementsOfType(comment, KDocTag::class.java)
                            .filter { it !is KDocSection && it.getName() != null }
                            .minByOrNull { it.textOffset }
                        if (firstTag != null && !hasBlankLineBefore(comment.text, firstTag.textOffset - comment.textOffset)) {
                            report(Finding(Entity.from(firstTag), MESSAGE))
                        }
                    }
                }
                super.visitDeclaration(declaration)
            }
        })
    }

    /**
     * 判斷第一個區塊標籤前是否有獨立的空白註解行。
     *
     * @param commentText KDoc 的完整文字。
     * @param tagOffset 第一個區塊標籤相對於 KDoc 起點的字元位置。
     */
    private fun hasBlankLineBefore(commentText: String, tagOffset: Int): Boolean {
        val prefix = commentText.substring(0, tagOffset.coerceIn(0, commentText.length))
        val lines = prefix.split('\n')
        if (lines.size < 2) return false
        return lines[lines.lastIndex - 1].removePrefix("\r").trim().removePrefix("*").trim().isEmpty()
    }

    private companion object {
        /** 規則文件的專案位置。 */
        const val URL = "https://github.com/doublemoon1119/MahjongCraft/blob/refactor/v2/CONTRIBUTING.md#code-comments"

        /** 規則的用途說明。 */
        const val DESCRIPTION = "Checks that a KDoc description is separated from its first block tag."

        /** KDoc 描述與第一個區塊標籤未分隔時使用的訊息。 */
        const val MESSAGE = "A KDoc description must be separated from its first block tag by a blank comment line."
    }
}
