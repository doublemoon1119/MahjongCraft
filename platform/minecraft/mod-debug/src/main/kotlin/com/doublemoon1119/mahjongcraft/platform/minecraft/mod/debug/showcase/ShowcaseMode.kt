package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.showcase

import java.nio.file.Path
import kotlin.io.path.Path

/**
 * 宣傳截圖模式的設定，由開發執行的系統屬性決定。
 *
 * - [PROPERTY] 為 `true`（不分大小寫）時開啟：遊戲啟動後自動拍攝宣傳截圖並關閉遊戲。
 * - [TITLE_PROPERTY] 指定後製時疊在 GitHub 預覽圖上的標題圖片。
 *
 * @property titleImage 標題圖片的位置。沒有指定時為 null，後製不產生疊上標題的版本。
 */
internal data class ShowcaseMode(val titleImage: Path?) {
    /** 由系統屬性解析宣傳截圖模式。 */
    companion object {
        /** 開發執行以這個系統屬性開啟宣傳截圖模式。 */
        const val PROPERTY: String = "mahjongcraft.showcase"

        /** 開發執行以這個系統屬性指定標題圖片。 */
        const val TITLE_PROPERTY: String = "mahjongcraft.showcase.title"

        /**
         * 解析屬性值。
         *
         * @param enabled [PROPERTY] 的值。不存在時為 null。
         * @param title [TITLE_PROPERTY] 的值。不存在時為 null。
         * @return 宣傳截圖模式。沒有開啟時為 null。
         */
        fun parse(enabled: String?, title: String?): ShowcaseMode? {
            if (!enabled?.trim().equals("true", ignoreCase = true)) return null
            return ShowcaseMode(titleImage = title?.trim()?.takeIf(String::isNotEmpty)?.let(::Path))
        }

        /** 目前這次執行的宣傳截圖模式，沒有開啟時為 null。 */
        val current: ShowcaseMode? by lazy { parse(System.getProperty(PROPERTY), System.getProperty(TITLE_PROPERTY)) }
    }
}
