package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryParticipantSummaryDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.TileFaceViewDto
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence
import kotlin.math.ceil

/** 牌譜卡片中的一列，例如事件頁的交易卡片與回放的結算卡片。 */
internal sealed interface HistoryCardRow {
    /** 此列完整高度。 */
    val height: Int

    /**
     * 已換行文字列。
     *
     * @property text 繪製文字。
     * @property tooltip 完整文字。
     * @property indent 文字縮排。
     * @property color 文字色。
     * @property participant 可選頭像。
     * @property portraitIndent 頭像縮排。
     */
    data class TextLine(
        val text: FormattedCharSequence,
        val tooltip: List<Component>,
        val indent: Int,
        val color: Int,
        val participant: HistoryParticipantSummaryDto?,
        val portraitIndent: Int,
    ) : HistoryCardRow {
        /** 共用文字列高度。 */
        override val height: Int = HistoryCardStyle.LINE_HEIGHT
    }

    /**
     * 已分組的一列牌。
     *
     * @property tiles 保留順序與重複的牌張。
     * @property indent 相對卡片左側的牌面縮排。
     */
    data class Tiles(
        val tiles: List<TileFaceViewDto>,
        val indent: Int = 16,
    ) : HistoryCardRow {
        /** 牌面及下方間距。 */
        override val height: Int = HistoryCardStyle.TILE_HEIGHT + 4
    }

    /**
     * 開頭帶玩家的一列牌，例如補花紀錄中「• 頭像 AI 3：」後接移出的花牌。
     *
     * @property label 牌前面的玩家名稱。
     * @property participant 玩家頭像來源；沒有對應玩家時為 null。
     * @property tiles 保留順序與重複的牌張。
     * @property bulletIndent 相對卡片左側的項目符號縮排。
     * @property portraitIndent 相對卡片左側的頭像縮排。
     * @property labelIndent 相對卡片左側的名稱縮排。
     * @property tileIndent 相對卡片左側的牌面縮排。
     */
    data class LabeledTiles(
        val label: Component,
        val participant: HistoryParticipantSummaryDto?,
        val tiles: List<TileFaceViewDto>,
        val bulletIndent: Int,
        val portraitIndent: Int,
        val labelIndent: Int,
        val tileIndent: Int,
    ) : HistoryCardRow {
        /** 牌面及下方間距。 */
        override val height: Int = HistoryCardStyle.TILE_HEIGHT + 4
    }

    /**
     * 段落間距。
     *
     * @property height 間距高度。
     */
    data class Space(
        override val height: Int,
    ) : HistoryCardRow

    /**
     * 交易標題列：左邊是交易序號與時間，右端是查看局面的放大鏡按鈕。
     *
     * @property text 標題文字。
     * @property index 已確認交易索引。
     */
    data class Heading(
        val text: Component,
        val index: Int,
    ) : HistoryCardRow {
        /** 容得下放大鏡按鈕的列高。 */
        override val height: Int = HistoryCardStyle.MINI_BUTTON_SIZE + 2
    }

    /**
     * 載入和牌手牌的按鈕列，留在事件頁展開手牌。
     *
     * @property index 已確認交易索引。
     */
    data class StateLink(
        val index: Int,
    ) : HistoryCardRow {
        /** 容得下按鈕的列高。 */
        override val height: Int = HistoryCardStyle.MINI_BUTTON_SIZE + 2

        /** 按鈕縮排。 */
        val indent: Int = 46
    }

    /**
     * 完整和牌手牌群組。
     *
     * @property layout 共用群組排列。
     * @property catalog 經驗證的同筆牌目錄。
     * @property notes 各牌的額外說明，例如副露被鳴的牌來自哪位玩家。
     */
    data class Hand(
        val layout: HistoryTileGroupLayout,
        val catalog: List<TileFaceViewDto>,
        val notes: Map<Int, List<HistoryTileNote>> = emptyMap(),
    ) : HistoryCardRow {
        /** 牌組完整高度與段落間距。 */
        override val height: Int = ceil(layout.height).toInt() + 8
    }
}
