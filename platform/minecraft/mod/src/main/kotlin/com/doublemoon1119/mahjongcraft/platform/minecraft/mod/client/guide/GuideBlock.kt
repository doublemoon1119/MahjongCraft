package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.guide

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.PlayerDecisionPromptDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.guide.scene.GuideScene
import net.minecraft.network.chat.Component

/**
 * 「如何開始」內容區中由上而下排列的一個區塊。
 *
 * 區塊只描述內容；位置與高度由 [GuideContentLayout] 依可用寬度計算，繪製與互動由畫面負責。
 */
internal sealed interface GuideBlock {
    /**
     * 段落標題，以標題色呈現；不是頁面第一個區塊時上方多留一段間距。
     *
     * @property text 標題文字。
     */
    data class Heading(val text: Component) : GuideBlock

    /**
     * 一段會自動換行的文字。
     *
     * @property text 文字；以換行分隔句子。
     * @property hint 是否以灰色的補充說明呈現。
     */
    data class Paragraph(val text: Component, val hint: Boolean = false) : GuideBlock

    /**
     * 編號步驟，編號從 1 開始。
     *
     * @property steps 依順序排列的步驟。
     */
    data class Steps(val steps: List<GuideStep>) : GuideBlock

    /**
     * 以遊戲繪製器畫出的示意場景，寬度與內容區相同。
     *
     * @property scene 場景。
     * @property heightRatio 場景高度相對於寬度的比例。
     * @property minimumHeight 場景高度下限。
     * @property maximumHeight 場景高度上限。
     * @property captions 場景下方置中的灰色說明。
     */
    data class Scene(
        val scene: GuideScene,
        val heightRatio: Float,
        val minimumHeight: Int,
        val maximumHeight: Int,
        val captions: List<Component> = emptyList(),
    ) : GuideBlock

    /**
     * 一列平面牌面，放不下時換行。
     *
     * @property items 由左到右的牌組、符號或附名稱的牌。
     */
    data class Tiles(val items: List<GuideTileItem>) : GuideBlock

    /**
     * 上方有分隔線的一段，標題以標題色放在上方、內容在下，例如鳴牌的每一種。
     *
     * @property title 標題。
     * @property body 標題下方由上而下的區塊。
     */
    data class Section(val title: Component, val body: List<GuideBlock>) : GuideBlock

    /**
     * 有底色的卡片，標題在上、內容在下，例如輪流摸牌、打牌的每一步。
     *
     * @property title 標題。
     * @property body 標題下方由上而下的區塊。
     */
    data class Card(val title: Component, val body: List<GuideBlock>) : GuideBlock

    /**
     * 開啟其他畫面的按鈕，靠左放置。
     *
     * @property label 按鈕文字。
     * @property action 按下後開啟的畫面。
     */
    data class Button(val label: Component, val action: GuideAction) : GuideBlock

    /** 麻將桌與麻將凳的合成配方輪播；找不到配方時不佔空間。 */
    data object Recipe : GuideBlock

    /**
     * 以遊戲中操作面板的繪製畫出的範例面板，水平置中。
     *
     * @property prompt 範例決策提示。
     */
    data class DecisionPanel(val prompt: PlayerDecisionPromptDto) : GuideBlock
}

/**
 * 編號步驟中的一步。
 *
 * @property text 步驟本身。
 * @property hint 步驟下方的灰色補充；沒有時為 null。
 */
internal data class GuideStep(val text: Component, val hint: Component? = null)

/** 「如何開始」按鈕開啟的畫面。 */
internal enum class GuideAction {
    /** 對局歷史。 */
    HISTORY,

    /** 客戶端設定。 */
    CLIENT_CONFIG,

    /** 規則一覽的「基本玩法」分類。 */
    RULE_BASICS,
}

/** 平面牌列中的一個項目。 */
internal sealed interface GuideTileItem {
    /**
     * 一組相連的牌，上方可附標籤。
     *
     * @property tiles 由左到右的牌。
     * @property label 牌組上方的小標籤；沒有時為 null。
     */
    data class Group(val tiles: List<Tile>, val label: Component? = null) : GuideTileItem

    /**
     * 牌組之間的符號，例如加號與箭頭，與牌面垂直置中；換行時跟著後面的項目移到下一行。
     *
     * @property symbol 符號文字。
     */
    data class Operator(val symbol: Component) : GuideTileItem

    /**
     * 一張牌，牌的下方附牌名。
     *
     * @property tile 牌。
     */
    data class Named(val tile: Tile) : GuideTileItem

    /**
     * 一段強調色文字，與牌面垂直置中，例如「和牌」。
     *
     * @property text 文字。
     */
    data class Result(val text: Component) : GuideTileItem
}
