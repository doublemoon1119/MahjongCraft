package com.doublemoon1119.mahjongcraft.platform.fabric.client.catalogue

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.MinecraftRuleCatalogueScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueBrowseStatus
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueConfigSource
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueEntry
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueLabel
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueTileGroupRole

/** 目錄文字的寬度測量與換行；畫面與測試各自提供實作。 */
internal interface RuleCatalogueTextMetrics {
    /**
     * 測量單行文字寬度。
     *
     * @param text 純文字。
     * @return 像素寬度。
     */
    fun width(text: String): Int

    /**
     * 依可用寬度換行。
     *
     * @param text 純文字。
     * @param maxWidth 每行可用寬度。
     * @return 至少一行的換行結果。
     */
    fun wrap(text: String, maxWidth: Int): List<String>

    /**
     * 截斷超出可用寬度的單行文字。
     *
     * @param text 純文字。
     * @param maxWidth 可用寬度。
     * @return 不超過可用寬度的文字。
     */
    fun trim(text: String, maxWidth: Int): String
}

/**
 * 範例牌面要使用的圖案。
 *
 * @property assetKey 實際繪製的牌面素材鍵；缺少圖案時為未知牌面。
 * @property missingName 缺少圖案時用於提示的牌種名稱；圖案存在時為 null。
 */
internal data class CatalogueTileArt(
    val assetKey: String,
    val missingName: String? = null,
)

/** 依牌種找出範例牌面圖案；畫面與測試各自提供實作。 */
internal fun interface RuleCatalogueTileArt {
    /**
     * 解析牌面圖案。
     *
     * @param tile 牌種。
     * @return 繪製用素材鍵與缺圖提示。
     */
    fun resolve(tile: Tile): CatalogueTileArt
}

/**
 * card 中的一行文字，座標相對於 card 左上角。
 *
 * @property text 這一行的文字。
 * @property x 左界。
 * @property y 上界。
 * @property width 文字實際寬度，繪製與命中測試共用。
 * @property color 文字顏色。
 * @property tooltip 指向這一行時顯示的提示；沒有提示時為空集合。
 */
internal data class CatalogueCardLine(
    val text: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val color: Int,
    val tooltip: List<String> = emptyList(),
)

/**
 * card 中的一張範例牌，座標相對於 card 左上角。
 *
 * @property placement 牌面位置。
 * @property assetKey 實際繪製的牌面素材鍵。
 * @property tooltip 缺少圖案時的提示；圖案存在時為空集合。
 */
internal data class CatalogueCardTile(
    val placement: RuleCatalogueTilePlacement,
    val assetKey: String,
    val tooltip: List<String>,
)

/**
 * 一個目錄條目的完整測量結果，繪製與命中測試共用。
 *
 * @property height card 高度。
 * @property lines 文字位置。
 * @property tiles 範例牌位置與圖案。
 */
internal data class CatalogueCard(
    val height: Int,
    val lines: List<CatalogueCardLine>,
    val tiles: List<CatalogueCardTile>,
)

/**
 * 把目錄條目排成 card：名稱、價值與限制標籤、說明、無法使用的原因，最後是各個範例的標題、情境說明與牌組。
 * 標籤有說明時，游標指到該標籤會顯示說明。
 *
 * 只處理文字與版面，不做任何規則判定；缺少翻譯時顯示翻譯鍵並附提示，缺少牌面圖案時使用未知牌面並附提示。
 *
 * @property translate 取得當前語言的翻譯；缺少翻譯時回傳 null。
 * @property metrics 文字寬度測量與換行。
 * @property tileArt 範例牌面圖案來源。
 */
internal class RuleCataloguePresenter(
    private val translate: (String) -> String?,
    private val metrics: RuleCatalogueTextMetrics,
    private val tileArt: RuleCatalogueTileArt,
) {
    /**
     * 取得翻譯，缺少或空白時回傳 null。
     *
     * @param key 翻譯鍵。
     * @return 當前語言的文字，或 null。
     */
    fun translation(key: String): String? = translate(key)?.takeIf(String::isNotBlank)

    /**
     * 取得可顯示的文字，缺少翻譯時顯示翻譯鍵。
     *
     * @param key 翻譯鍵。
     * @return 可顯示的文字。
     */
    fun text(key: String): String = translation(key) ?: key

    /**
     * 取得帶一個參數的文字。
     *
     * @param key 含 `%s` 的翻譯鍵。
     * @param argument 參數。
     * @return 代入參數後的文字；缺少翻譯時為翻譯鍵與參數。
     */
    fun format(key: String, argument: String): String = translation(key)?.replace("%s", argument) ?: "$key: $argument"

    /**
     * 測量一個條目的 card。
     *
     * @param entry 目錄條目。
     * @param width card 寬度。
     * @return 文字、牌面位置與 card 高度。
     */
    fun measure(entry: RuleCatalogueEntry, width: Int): CatalogueCard {
        val innerWidth = (width - PADDING * 2).coerceAtLeast(1)
        val lines = mutableListOf<CatalogueCardLine>()
        val tiles = mutableListOf<CatalogueCardTile>()
        var y = PADDING

        /**
         * 加入一段可換行的文字。
         *
         * @param content 段落文字。
         * @param color 段落顏色。
         * @param tooltip 指向段落時的提示。
         */
        fun paragraph(
            content: String,
            color: Int,
            tooltip: List<String> = emptyList(),
        ) {
            metrics.wrap(content, innerWidth).forEach { line ->
                lines += CatalogueCardLine(
                    text = line,
                    x = PADDING,
                    y = y,
                    width = metrics.width(line),
                    color = color,
                    tooltip = tooltip,
                )
                y += LINE_HEIGHT
            }
        }

        /**
         * 加入一段來自翻譯鍵的文字，缺少翻譯時附上提示。
         *
         * @param key 翻譯鍵。
         * @param color 段落顏色。
         */
        fun translated(key: String, color: Int) = paragraph(
            content = text(key),
            color = color,
            tooltip = if (translation(key) == null) listOf(format(MinecraftRuleCatalogueScreenKeys.MISSING_TRANSLATION, key)) else emptyList(),
        )

        /**
         * 由左往右排列標籤並以分隔符號隔開，放不下時整個標籤換到下一行；每個標籤各自帶提示。
         *
         * @param labels 條目標籤。
         */
        fun labelRow(labels: List<RuleCatalogueLabel>) {
            val separatorWidth = metrics.width(LABEL_SEPARATOR)
            var x = 0
            labels.forEachIndexed { index, label ->
                val name = text(label.nameTranslationKey)
                val nameWidth = metrics.width(name)
                if (index > 0) {
                    if (x + separatorWidth + nameWidth <= innerWidth) {
                        lines += CatalogueCardLine(
                            text = LABEL_SEPARATOR,
                            x = PADDING + x,
                            y = y,
                            width = separatorWidth,
                            color = DESCRIPTION_COLOR,
                        )
                        x += separatorWidth
                    } else {
                        y += LINE_HEIGHT
                        x = 0
                    }
                }
                val parts = if (nameWidth <= innerWidth - x) listOf(name) else metrics.wrap(name, innerWidth)
                parts.forEachIndexed { partIndex, part ->
                    if (partIndex > 0) {
                        y += LINE_HEIGHT
                        x = 0
                    }
                    val partWidth = metrics.width(part)
                    lines += CatalogueCardLine(
                        text = part,
                        x = PADDING + x,
                        y = y,
                        width = partWidth,
                        color = LABEL_COLOR,
                        tooltip = labelTooltip(label),
                    )
                    x += partWidth
                }
            }
            y += LINE_HEIGHT
        }

        translated(key = entry.nameTranslationKey, color = NAME_COLOR)
        if (entry.labels.isNotEmpty()) {
            y += LINE_GAP
            labelRow(entry.labels)
        }
        y += PARAGRAPH_GAP
        translated(key = entry.descriptionTranslationKey, color = DESCRIPTION_COLOR)
        entry.unavailableReasonTranslationKey?.let { key ->
            y += PARAGRAPH_GAP
            translated(key = key, color = UNAVAILABLE_COLOR)
        }
        entry.examples.forEach { example ->
            y += EXAMPLE_GAP
            val titleKey = if (example.completeHand) MinecraftRuleCatalogueScreenKeys.EXAMPLE_COMPLETE else MinecraftRuleCatalogueScreenKeys.EXAMPLE_PARTIAL
            translated(key = titleKey, color = EXAMPLE_TITLE_COLOR)
            example.descriptionTranslationKey?.let { translated(key = it, color = DESCRIPTION_COLOR) }
            y += LINE_GAP
            val tileLayout = RuleCatalogueTileLayout.measure(
                groups = example.groups,
                width = innerWidth,
                headingWidth = { role -> metrics.width(text(roleKey(role))) },
            )
            tileLayout.headings.forEach { heading ->
                val label = metrics.trim(text(roleKey(heading.role)), heading.maxWidth)
                lines += CatalogueCardLine(
                    text = label,
                    x = PADDING + heading.x,
                    y = y + heading.y,
                    width = metrics.width(label),
                    color = HEADING_COLOR,
                )
            }
            tileLayout.placements.forEach { placement ->
                val art = tileArt.resolve(placement.tile)
                tiles += CatalogueCardTile(
                    placement = placement.copy(x = PADDING + placement.x, y = y + placement.y),
                    assetKey = art.assetKey,
                    tooltip = art.missingName?.let { listOf(format(MinecraftRuleCatalogueScreenKeys.MISSING_ASSET, it)) }.orEmpty(),
                )
            }
            y += tileLayout.height
        }
        return CatalogueCard(height = y + PADDING, lines = lines, tiles = tiles)
    }

    /**
     * 標籤的提示：標籤說明，以及缺少翻譯時的提示。
     *
     * @param label 條目標籤。
     * @return 提示的每一行；沒有提示時為空集合。
     */
    private fun labelTooltip(label: RuleCatalogueLabel): List<String> = buildList {
        if (translation(label.nameTranslationKey) == null) add(format(MinecraftRuleCatalogueScreenKeys.MISSING_TRANSLATION, label.nameTranslationKey))
        label.descriptionTranslationKey?.let { key ->
            add(translation(key) ?: format(MinecraftRuleCatalogueScreenKeys.MISSING_TRANSLATION, key))
        }
    }

    /** card 的尺寸與文字層次。 */
    internal companion object {
        /** card 四側留白。 */
        const val PADDING: Int = 6

        /** 文字行高。 */
        const val LINE_HEIGHT: Int = 10

        /** 名稱與標籤、範例文字與牌組之間的小間距。 */
        const val LINE_GAP: Int = 2

        /** 段落之間的間距。 */
        const val PARAGRAPH_GAP: Int = 4

        /** 範例之間的間距。 */
        const val EXAMPLE_GAP: Int = 8

        /** 多個標籤之間的分隔。 */
        const val LABEL_SEPARATOR: String = " · "

        /** 條目名稱。 */
        const val NAME_COLOR: Int = 0xffffff

        /** 價值與限制標籤。 */
        const val LABEL_COLOR: Int = 0xffdd88

        /** 說明與情境。 */
        const val DESCRIPTION_COLOR: Int = 0xaaaaaa

        /** 無法使用的原因。 */
        const val UNAVAILABLE_COLOR: Int = 0xff9a8a

        /** 範例標題。 */
        const val EXAMPLE_TITLE_COLOR: Int = 0x8ed5df

        /** 牌組標題。 */
        const val HEADING_COLOR: Int = 0x8a8a8a
    }
}

/**
 * 取得牌組角色的標題翻譯鍵。
 *
 * @param role 牌組角色。
 * @return 翻譯鍵。
 */
internal fun roleKey(role: RuleCatalogueTileGroupRole): String = when (role) {
    RuleCatalogueTileGroupRole.HAND -> MinecraftRuleCatalogueScreenKeys.GROUP_HAND
    RuleCatalogueTileGroupRole.WINNING_TILE -> MinecraftRuleCatalogueScreenKeys.GROUP_WINNING_TILE
    RuleCatalogueTileGroupRole.OPEN_MELD -> MinecraftRuleCatalogueScreenKeys.GROUP_OPEN_MELD
    RuleCatalogueTileGroupRole.OPEN_KAN -> MinecraftRuleCatalogueScreenKeys.GROUP_OPEN_KAN
    RuleCatalogueTileGroupRole.CLOSED_KAN -> MinecraftRuleCatalogueScreenKeys.GROUP_CLOSED_KAN
    RuleCatalogueTileGroupRole.ILLUSTRATION -> MinecraftRuleCatalogueScreenKeys.GROUP_ILLUSTRATION
}

/**
 * 取得瀏覽狀態的說明文字鍵。
 *
 * @param status 瀏覽狀態。
 * @return 說明文字鍵；有內容可顯示時為 null。
 */
internal fun catalogueStatusKey(status: RuleCatalogueBrowseStatus): String? = when (status) {
    RuleCatalogueBrowseStatus.NO_RULES -> MinecraftRuleCatalogueScreenKeys.STATUS_NO_RULES
    RuleCatalogueBrowseStatus.CONFIG_UNAVAILABLE -> MinecraftRuleCatalogueScreenKeys.STATUS_CONFIG_UNAVAILABLE
    RuleCatalogueBrowseStatus.MISSING_PROVIDER -> MinecraftRuleCatalogueScreenKeys.STATUS_MISSING_PROVIDER
    RuleCatalogueBrowseStatus.UNSUPPORTED_CONFIG -> MinecraftRuleCatalogueScreenKeys.STATUS_UNSUPPORTED_CONFIG
    RuleCatalogueBrowseStatus.EMPTY_CATALOGUE -> MinecraftRuleCatalogueScreenKeys.STATUS_EMPTY_CATALOGUE
    RuleCatalogueBrowseStatus.NO_RESULTS -> MinecraftRuleCatalogueScreenKeys.STATUS_NO_RESULTS
    RuleCatalogueBrowseStatus.AVAILABLE -> null
}

/**
 * 取得設定來源的提示文字鍵。
 *
 * @param source 設定來源。
 * @return 提示文字鍵。
 */
internal fun catalogueSourceKey(source: RuleCatalogueConfigSource): String = when (source) {
    RuleCatalogueConfigSource.GENERAL -> MinecraftRuleCatalogueScreenKeys.SOURCE_GENERAL
    RuleCatalogueConfigSource.ROOM -> MinecraftRuleCatalogueScreenKeys.SOURCE_ROOM
    RuleCatalogueConfigSource.ROOM_DRAFT -> MinecraftRuleCatalogueScreenKeys.SOURCE_ROOM_DRAFT
    RuleCatalogueConfigSource.HISTORY -> MinecraftRuleCatalogueScreenKeys.SOURCE_HISTORY
}
