package com.doublemoon1119.mahjongcraft.buildlogic.detekt

import com.intellij.psi.PsiFile
import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import dev.detekt.api.RuleName
import java.net.URI
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

/**
 * 檢查多行 KDoc 的開頭是否單獨佔一行。
 *
 * @param config 規則的 detekt 設定。
 */
class KDocOpeningLineRule(
    config: Config = Config.empty,
) : Rule(config, DESCRIPTION, URI.create(URL)) {
    /** 規則在 MahjongCraft 規則集合中的名稱。 */
    override val ruleName: RuleName = RuleName("KDocOpeningLine")

    /** 檢查檔案內每個 Kotlin 宣告的 KDoc。 */
    override fun visitFile(file: PsiFile) {
        if (file !is KtFile) return
        file.accept(object : KtTreeVisitorVoid() {
            /** 檢查宣告附帶的 KDoc 開頭格式。 */
            override fun visitDeclaration(declaration: KtDeclaration) {
                declaration.docComment?.let { comment ->
                    val text = comment.text
                    if ('\n' in text && text.substringAfter("/**").substringBefore('\n').isNotBlank()) {
                        report(Finding(Entity.from(comment), MESSAGE))
                    }
                }
                super.visitDeclaration(declaration)
            }
        })
    }

    private companion object {
        /** 規則文件的專案位置。 */
        const val URL = "https://github.com/doublemoon1119/MahjongCraft/blob/refactor/v2/CONTRIBUTING.md#code-comments"

        /** 規則的用途說明。 */
        const val DESCRIPTION = "Checks that a multiline KDoc opening marker is on its own line."

        /** 多行 KDoc 開頭格式錯誤時使用的訊息。 */
        const val MESSAGE = "The opening /** of a multiline KDoc must be on its own line."
    }
}
