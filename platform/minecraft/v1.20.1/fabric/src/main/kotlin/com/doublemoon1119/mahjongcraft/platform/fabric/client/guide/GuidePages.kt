package com.doublemoon1119.mahjongcraft.platform.fabric.client.guide

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameAction
import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.previewAction
import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.previewPrompt
import com.doublemoon1119.mahjongcraft.platform.fabric.client.guide.scene.GuideScenes
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.BuiltInGameActionIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.guide.MinecraftGuideScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.guide.GuideAction
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.guide.GuideBlock
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.guide.GuideStep
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.guide.GuideTileItem
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.guide.scene.GuideScene
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.guide.scene.GuideSceneTileGlow
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.guide.scene.GuideViewer
import net.minecraft.network.chat.Component

/**
 * 「如何開始」的頁籤。
 *
 * @property titleKey 頁籤名稱的翻譯鍵。
 */
internal enum class GuideTab(val titleKey: String) {
    /** 準備桌椅、開局與回看對局。 */
    SETUP(MinecraftGuideScreenKeys.TAB_SETUP),

    /** 認識牌桌與對局中的操作。 */
    PLAY(MinecraftGuideScreenKeys.TAB_PLAY),

    /** 牌種、牌型、輪流摸打與鳴牌和牌。 */
    BASICS(MinecraftGuideScreenKeys.TAB_BASICS),
}

/**
 * 「如何開始」各頁的內容。
 *
 * 場景在建立時描述一次，之後只交給繪製器；文字一律使用翻譯鍵，繪製時才依目前語言解析。
 */
internal object GuidePages {
    /**
     * 頁籤的內容。
     *
     * @param tab 頁籤。
     * @param viewer 看著畫面的玩家，示意場景中以他代表自己的座位。
     * @return 由上而下的區塊。
     */
    fun blocks(tab: GuideTab, viewer: GuideViewer): List<GuideBlock> = when (tab) {
        GuideTab.SETUP -> setup()
        GuideTab.PLAY -> play(viewer)
        GuideTab.BASICS -> basics()
    }

    /** 準備與開局：合成配方、桌椅場景、開局步驟與回看對局。 */
    private fun setup(): List<GuideBlock> = listOf(
        heading(MinecraftGuideScreenKeys.SETUP_HEADING),
        GuideBlock.Recipe,
        GuideBlock.Scene(
            scene = GuideScenes.tableSetup(),
            heightRatio = SETUP_SCENE_RATIO,
            minimumHeight = SETUP_SCENE_MINIMUM,
            maximumHeight = SETUP_SCENE_MAXIMUM,
            captions = listOf(text(MinecraftGuideScreenKeys.SCENE_ROTATE_HINT)),
        ),
        GuideBlock.Steps(
            steps = listOf(
                GuideStep(text(MinecraftGuideScreenKeys.SETUP_STEP_TABLE), text(MinecraftGuideScreenKeys.SETUP_STEP_TABLE_HINT)),
                GuideStep(text(MinecraftGuideScreenKeys.SETUP_STEP_STOOLS), text(MinecraftGuideScreenKeys.SETUP_STEP_STOOLS_HINT)),
                GuideStep(text(MinecraftGuideScreenKeys.SETUP_STEP_JOIN), text(MinecraftGuideScreenKeys.SETUP_STEP_JOIN_HINT)),
                GuideStep(text(MinecraftGuideScreenKeys.SETUP_STEP_START)),
            ),
        ),
        hint(MinecraftGuideScreenKeys.SETUP_TIP),
        heading(MinecraftGuideScreenKeys.SETUP_HISTORY_HEADING),
        paragraph(MinecraftGuideScreenKeys.SETUP_HISTORY_TEXT),
        GuideBlock.Button(text(MinecraftGuideScreenKeys.SETUP_HISTORY_BUTTON), GuideAction.HISTORY),
    )

    /**
     * 對局操作：認識牌桌、打牌、操作面板、宣告、倒數與設定。
     *
     * @param viewer 看著畫面的玩家。
     * @return 由上而下的區塊。
     */
    private fun play(viewer: GuideViewer): List<GuideBlock> = listOf(
        heading(MinecraftGuideScreenKeys.PLAY_TABLE_HEADING),
        paragraph(MinecraftGuideScreenKeys.PLAY_TABLE_TEXT),
        GuideBlock.Scene(
            scene = GuideScenes.tableOverview(handFaces = PLAY_HAND, viewer = viewer),
            heightRatio = OVERVIEW_SCENE_RATIO,
            minimumHeight = OVERVIEW_SCENE_MINIMUM,
            maximumHeight = OVERVIEW_SCENE_MAXIMUM,
            captions = listOf(text(MinecraftGuideScreenKeys.SCENE_EXPLORE_HINT), text(MinecraftGuideScreenKeys.SCENE_LAYOUT_NOTE)),
        ),
        heading(MinecraftGuideScreenKeys.PLAY_TURN_HEADING),
        paragraph(MinecraftGuideScreenKeys.PLAY_TURN_TEXT),
        paragraph(MinecraftGuideScreenKeys.PLAY_TURN_TARGET),
        handScene(GuideScenes.hand(PLAY_HAND, mapOf(TARGET_INDEX to GuideSceneTileGlow.TARGET))),
        paragraph(MinecraftGuideScreenKeys.PLAY_TURN_MATCHING),
        handScene(
            scene = GuideScenes.hand(
                faces = PLAY_HAND,
                glows = mapOf(
                    MATCHING_TARGET_INDEX to GuideSceneTileGlow.TARGET,
                    MATCHING_TARGET_INDEX + 1 to GuideSceneTileGlow.MATCHING,
                    MATCHING_TARGET_INDEX + 2 to GuideSceneTileGlow.MATCHING,
                ),
            ),
        ),
        heading(MinecraftGuideScreenKeys.PLAY_CALL_HEADING),
        paragraph(MinecraftGuideScreenKeys.PLAY_CALL_TEXT),
        hint(MinecraftGuideScreenKeys.PLAY_CALL_HINT),
        GuideBlock.DecisionPanel(CALL_PROMPT),
        heading(MinecraftGuideScreenKeys.PLAY_DECLARE_HEADING),
        paragraph(MinecraftGuideScreenKeys.PLAY_DECLARE_TEXT),
        hint(MinecraftGuideScreenKeys.PLAY_DECLARE_HINT),
        GuideBlock.DecisionPanel(DECLARE_PROMPT),
        heading(MinecraftGuideScreenKeys.PLAY_TIMER_HEADING),
        paragraph(MinecraftGuideScreenKeys.PLAY_TIMER_TEXT),
        hint(MinecraftGuideScreenKeys.PLAY_TIMER_HINT),
        heading(MinecraftGuideScreenKeys.PLAY_SETTINGS_HEADING),
        paragraph(MinecraftGuideScreenKeys.PLAY_SETTINGS_TEXT),
        hint(MinecraftGuideScreenKeys.PLAY_SETTINGS_HINT),
        GuideBlock.Button(text(MinecraftGuideScreenKeys.PLAY_SETTINGS_BUTTON), GuideAction.CLIENT_CONFIG),
    )

    /** 麻將基礎：牌種、組牌、和牌牌型、輪流摸打、鳴牌和牌，最後連到規則一覽的各規則玩法。 */
    private fun basics(): List<GuideBlock> = listOf(
        heading(MinecraftGuideScreenKeys.BASICS_TILES_HEADING),
        paragraph(MinecraftGuideScreenKeys.BASICS_TILES_CHARACTERS),
        named((1..NUMBER_COUNT).map(::m)),
        paragraph(MinecraftGuideScreenKeys.BASICS_TILES_DOTS),
        named((1..NUMBER_COUNT).map(::p)),
        paragraph(MinecraftGuideScreenKeys.BASICS_TILES_BAMBOOS),
        named((1..NUMBER_COUNT).map(::s)),
        paragraph(MinecraftGuideScreenKeys.BASICS_TILES_WINDS),
        named(listOf(Tile.Honor.East, Tile.Honor.South, Tile.Honor.West, Tile.Honor.North)),
        paragraph(MinecraftGuideScreenKeys.BASICS_TILES_DRAGONS),
        named(listOf(Tile.Honor.White, Tile.Honor.Green, Tile.Honor.Red)),
        hint(MinecraftGuideScreenKeys.BASICS_TILES_HINT),
        heading(MinecraftGuideScreenKeys.BASICS_GROUPS_HEADING),
        paragraph(MinecraftGuideScreenKeys.BASICS_GROUPS_SEQUENCE),
        groups(listOf(m(2), m(3), m(4)), listOf(p(1), p(2), p(3)), listOf(s(7), s(8), s(9))),
        paragraph(MinecraftGuideScreenKeys.BASICS_GROUPS_TRIPLET),
        groups(List(3) { Tile.Honor.East }, List(3) { p(5) }, List(3) { m(7) }),
        paragraph(MinecraftGuideScreenKeys.BASICS_GROUPS_PAIR),
        groups(List(2) { Tile.Honor.Red }, List(2) { s(2) }, List(2) { p(9) }),
        heading(MinecraftGuideScreenKeys.BASICS_WIN_SHAPE_HEADING),
        groups(WIN_SEQUENCE_DOTS + WIN_SEQUENCE_BAMBOOS + WIN_TRIPLET + listOf(m(2), m(3), m(4)) + WIN_PAIR),
        paragraph(MinecraftGuideScreenKeys.BASICS_WIN_SHAPE_SPLIT),
        GuideBlock.Tiles(
            items = listOf(
                labelled(WIN_SEQUENCE_DOTS, MinecraftGuideScreenKeys.BASICS_SEQUENCE),
                labelled(WIN_SEQUENCE_BAMBOOS, MinecraftGuideScreenKeys.BASICS_SEQUENCE),
                labelled(WIN_TRIPLET, MinecraftGuideScreenKeys.BASICS_TRIPLET),
                labelled(listOf(m(2), m(3), m(4)), MinecraftGuideScreenKeys.BASICS_SEQUENCE),
                labelled(WIN_PAIR, MinecraftGuideScreenKeys.BASICS_PAIR),
            ),
        ),
        hint(MinecraftGuideScreenKeys.BASICS_WIN_SHAPE_HINT),
        heading(MinecraftGuideScreenKeys.BASICS_ROUND_HEADING),
        hint(MinecraftGuideScreenKeys.BASICS_ROUND_INTRO),
        GuideBlock.Card(
            title = text(MinecraftGuideScreenKeys.BASICS_ROUND_STEP_HAND),
            body = listOf(paragraph(MinecraftGuideScreenKeys.BASICS_ROUND_STEP_HAND_TEXT), groups(ROUND_START_HAND)),
        ),
        GuideBlock.Card(
            title = text(MinecraftGuideScreenKeys.BASICS_ROUND_STEP_DRAW),
            body = listOf(
                paragraph(MinecraftGuideScreenKeys.BASICS_ROUND_STEP_DRAW_TEXT),
                GuideBlock.Tiles(
                    items = listOf(
                        labelled(ROUND_START_HAND, MinecraftGuideScreenKeys.LABEL_HAND),
                        labelled(listOf(Tile.Honor.East), MinecraftGuideScreenKeys.LABEL_DRAWN),
                    ),
                ),
            ),
        ),
        GuideBlock.Card(
            title = text(MinecraftGuideScreenKeys.BASICS_ROUND_STEP_DISCARD),
            body = listOf(
                paragraph(MinecraftGuideScreenKeys.BASICS_ROUND_STEP_DISCARD_TEXT),
                GuideBlock.Tiles(
                    items = listOf(
                        labelled(ROUND_TENPAI_HAND, MinecraftGuideScreenKeys.LABEL_HAND),
                        labelled(listOf(Tile.Honor.North), MinecraftGuideScreenKeys.LABEL_RIVER),
                        labelled(listOf(m(1)), MinecraftGuideScreenKeys.LABEL_WAITING),
                        labelled(listOf(m(4)), MinecraftGuideScreenKeys.LABEL_OR),
                    ),
                ),
            ),
        ),
        GuideBlock.Card(
            title = text(MinecraftGuideScreenKeys.BASICS_ROUND_STEP_NEXT),
            body = listOf(paragraph(MinecraftGuideScreenKeys.BASICS_ROUND_STEP_NEXT_TEXT)),
        ),
        heading(MinecraftGuideScreenKeys.BASICS_MELDS_HEADING),
        hint(MinecraftGuideScreenKeys.BASICS_MELDS_INTRO),
        meld(
            titleKey = MinecraftGuideScreenKeys.BASICS_CHI,
            hintKey = MinecraftGuideScreenKeys.BASICS_CHI_HINT,
            source = labelled(listOf(m(4)), MinecraftGuideScreenKeys.LABEL_LEFT_DISCARD),
            existing = labelled(listOf(m(2), m(3)), MinecraftGuideScreenKeys.LABEL_IN_HAND),
            result = listOf(m(2), m(3), m(4)),
        ),
        meld(
            titleKey = MinecraftGuideScreenKeys.BASICS_PON,
            hintKey = MinecraftGuideScreenKeys.BASICS_PON_HINT,
            source = labelled(listOf(Tile.Honor.East), MinecraftGuideScreenKeys.LABEL_OTHER_DISCARD),
            existing = labelled(List(2) { Tile.Honor.East }, MinecraftGuideScreenKeys.LABEL_IN_HAND),
            result = List(3) { Tile.Honor.East },
        ),
        meld(
            titleKey = MinecraftGuideScreenKeys.BASICS_OPEN_KAN,
            hintKey = MinecraftGuideScreenKeys.BASICS_OPEN_KAN_HINT,
            source = labelled(listOf(Tile.Honor.Green), MinecraftGuideScreenKeys.LABEL_OTHER_DISCARD),
            existing = labelled(List(3) { Tile.Honor.Green }, MinecraftGuideScreenKeys.LABEL_IN_HAND),
            result = List(4) { Tile.Honor.Green },
        ),
        meld(
            titleKey = MinecraftGuideScreenKeys.BASICS_ADDED_KAN,
            hintKey = MinecraftGuideScreenKeys.BASICS_ADDED_KAN_HINT,
            source = labelled(listOf(Tile.Honor.Green), MinecraftGuideScreenKeys.LABEL_SELF_DRAWN),
            existing = labelled(List(3) { Tile.Honor.Green }, MinecraftGuideScreenKeys.LABEL_PREVIOUS_PON),
            result = List(4) { Tile.Honor.Green },
        ),
        meld(
            titleKey = MinecraftGuideScreenKeys.BASICS_CLOSED_KAN,
            hintKey = MinecraftGuideScreenKeys.BASICS_CLOSED_KAN_HINT,
            source = labelled(listOf(Tile.Honor.Green), MinecraftGuideScreenKeys.LABEL_SELF_DRAWN),
            existing = labelled(List(3) { Tile.Honor.Green }, MinecraftGuideScreenKeys.LABEL_CONCEALED),
            result = List(4) { Tile.Honor.Green },
        ),
        win(
            titleKey = MinecraftGuideScreenKeys.BASICS_RON,
            hintKey = MinecraftGuideScreenKeys.BASICS_RON_HINT,
            sourceKey = MinecraftGuideScreenKeys.LABEL_OTHER_DISCARD,
            resultKey = MinecraftGuideScreenKeys.BASICS_RESULT_WIN,
        ),
        win(
            titleKey = MinecraftGuideScreenKeys.BASICS_TSUMO,
            hintKey = MinecraftGuideScreenKeys.BASICS_TSUMO_HINT,
            sourceKey = MinecraftGuideScreenKeys.LABEL_SELF_DRAWN,
            resultKey = MinecraftGuideScreenKeys.BASICS_RESULT_SELF_DRAW,
        ),
        heading(MinecraftGuideScreenKeys.BASICS_VARIANTS_HEADING),
        paragraph(MinecraftGuideScreenKeys.BASICS_VARIANTS_TEXT),
        hint(MinecraftGuideScreenKeys.BASICS_VARIANTS_HINT),
        GuideBlock.Button(text(MinecraftGuideScreenKeys.BASICS_VARIANTS_BUTTON), GuideAction.RULE_BASICS),
    )

    /**
     * 鳴牌的一段：來源 ＋ 已有 → 組成，下方是補充說明。
     *
     * @param titleKey 標題的翻譯鍵。
     * @param hintKey 補充說明的翻譯鍵。
     * @param source 來源牌組。
     * @param existing 已有的牌組。
     * @param result 組成的牌。
     * @return 鳴牌的一段。
     */
    private fun meld(
        titleKey: String,
        hintKey: String,
        source: GuideTileItem,
        existing: GuideTileItem,
        result: List<Tile>,
    ): GuideBlock = GuideBlock.Section(
        title = text(titleKey),
        body = listOf(
            GuideBlock.Tiles(
                items = listOf(
                    source,
                    GuideTileItem.Operator(Component.literal(PLUS)),
                    existing,
                    GuideTileItem.Operator(Component.literal(ARROW)),
                    labelled(result, MinecraftGuideScreenKeys.LABEL_RESULT),
                ),
            ),
            hint(hintKey),
        ),
    )

    /**
     * 和牌的一段：手牌 ＋ 最後一張 → 結果，兩種和牌使用同一手牌。
     *
     * @param titleKey 標題的翻譯鍵。
     * @param hintKey 補充說明的翻譯鍵。
     * @param sourceKey 最後一張牌來源標籤的翻譯鍵。
     * @param resultKey 結果文字的翻譯鍵。
     * @return 和牌的一段。
     */
    private fun win(
        titleKey: String,
        hintKey: String,
        sourceKey: String,
        resultKey: String,
    ): GuideBlock = GuideBlock.Section(
        title = text(titleKey),
        body = listOf(
            GuideBlock.Tiles(
                items = listOf(
                    labelled(WIN_SEQUENCE_DOTS, MinecraftGuideScreenKeys.LABEL_HAND),
                    GuideTileItem.Group(WIN_SEQUENCE_BAMBOOS),
                    GuideTileItem.Group(WIN_TRIPLET),
                    GuideTileItem.Group(WIN_PAIR),
                    GuideTileItem.Group(listOf(m(2), m(3))),
                    GuideTileItem.Operator(Component.literal(PLUS)),
                    labelled(listOf(m(4)), sourceKey),
                    GuideTileItem.Operator(Component.literal(ARROW)),
                    GuideTileItem.Result(text(resultKey)),
                ),
            ),
            hint(hintKey),
        ),
    )

    /**
     * 手牌場景，寬度與內容區相同、高度固定在一手牌的範圍。
     *
     * @param scene 手牌場景。
     * @return 場景區塊。
     */
    private fun handScene(scene: GuideScene): GuideBlock = GuideBlock.Scene(
        scene = scene,
        heightRatio = HAND_SCENE_RATIO,
        minimumHeight = HAND_SCENE_MINIMUM,
        maximumHeight = HAND_SCENE_MAXIMUM,
    )

    /**
     * 段落標題。
     *
     * @param key 翻譯鍵。
     * @return 標題區塊。
     */
    private fun heading(key: String): GuideBlock = GuideBlock.Heading(text(key))

    /**
     * 一般段落。
     *
     * @param key 翻譯鍵。
     * @return 段落區塊。
     */
    private fun paragraph(key: String): GuideBlock = GuideBlock.Paragraph(text(key))

    /**
     * 灰色補充段落。
     *
     * @param key 翻譯鍵。
     * @return 段落區塊。
     */
    private fun hint(key: String): GuideBlock = GuideBlock.Paragraph(text(key), hint = true)

    /**
     * 一列附牌名的牌。
     *
     * @param tiles 牌。
     * @return 牌列區塊。
     */
    private fun named(tiles: List<Tile>): GuideBlock = GuideBlock.Tiles(tiles.map(GuideTileItem::Named))

    /**
     * 一列沒有標籤的牌組。
     *
     * @param groups 各組的牌。
     * @return 牌列區塊。
     */
    private fun groups(vararg groups: List<Tile>): GuideBlock = GuideBlock.Tiles(groups.map { GuideTileItem.Group(it) })

    /**
     * 上方有標籤的牌組。
     *
     * @param tiles 牌。
     * @param labelKey 標籤的翻譯鍵。
     * @return 牌組。
     */
    private fun labelled(tiles: List<Tile>, labelKey: String): GuideTileItem = GuideTileItem.Group(tiles, text(labelKey))

    /**
     * 翻譯文字。
     *
     * @param key 翻譯鍵。
     * @return 繪製時依目前語言解析的文字。
     */
    private fun text(key: String): Component = Component.translatable(key)

    /**
     * 萬子。
     *
     * @param value 數字。
     * @return 牌。
     */
    private fun m(value: Int): Tile = Tile.Numeric(Tile.Suit.Character, value)

    /**
     * 筒子。
     *
     * @param value 數字。
     * @return 牌。
     */
    private fun p(value: Int): Tile = Tile.Numeric(Tile.Suit.Dot, value)

    /**
     * 索子。
     *
     * @param value 數字。
     * @return 牌。
     */
    private fun s(value: Int): Tile = Tile.Numeric(Tile.Suit.Bamboo, value)

    /** 數牌每類的張數。 */
    private const val NUMBER_COUNT = 9

    /** 加號。 */
    private const val PLUS = "＋"

    /** 箭頭。 */
    private const val ARROW = "→"

    /** 準備桌椅場景的高寬比。 */
    private const val SETUP_SCENE_RATIO = 0.55f

    /** 準備桌椅場景的高度下限。 */
    private const val SETUP_SCENE_MINIMUM = 120

    /** 準備桌椅場景的高度上限。 */
    private const val SETUP_SCENE_MAXIMUM = 240

    /** 認識牌桌場景的高寬比。 */
    private const val OVERVIEW_SCENE_RATIO = 0.75f

    /** 認識牌桌場景的高度下限。 */
    private const val OVERVIEW_SCENE_MINIMUM = 160

    /** 認識牌桌場景的高度上限。 */
    private const val OVERVIEW_SCENE_MAXIMUM = 340

    /** 手牌場景的高寬比。 */
    private const val HAND_SCENE_RATIO = 0.14f

    /** 手牌場景的高度下限。 */
    private const val HAND_SCENE_MINIMUM = 40

    /** 手牌場景的高度上限。 */
    private const val HAND_SCENE_MAXIMUM = 64

    /** 對局操作頁的示意手牌，由左到右。 */
    private val PLAY_HAND: List<String> = listOf("m2", "m3", "m4", "p5", "p6", "p7", "s3", "s3", "east", "east", "east", "red_dragon", "red_dragon")

    /** 準星指著的牌在示意手牌中的位置。 */
    private const val TARGET_INDEX = 2

    /** 同種牌範例中準星指著的東在示意手牌中的位置，後面兩張是相同的東。 */
    private const val MATCHING_TARGET_INDEX = 8

    /** 和牌牌型與和牌範例共用的一二三筒。 */
    private val WIN_SEQUENCE_DOTS: List<Tile> = listOf(p(1), p(2), p(3))

    /** 和牌牌型與和牌範例共用的七八九索。 */
    private val WIN_SEQUENCE_BAMBOOS: List<Tile> = listOf(s(7), s(8), s(9))

    /** 和牌牌型與和牌範例共用的中中中。 */
    private val WIN_TRIPLET: List<Tile> = List(3) { Tile.Honor.Red }

    /** 和牌牌型與和牌範例共用的一對東。 */
    private val WIN_PAIR: List<Tile> = List(2) { Tile.Honor.East }

    /** 輪流摸打範例開始時的手牌。 */
    private val ROUND_START_HAND: List<Tile> = WIN_SEQUENCE_DOTS + WIN_SEQUENCE_BAMBOOS + WIN_TRIPLET + listOf(m(2), m(3), Tile.Honor.East, Tile.Honor.North)

    /** 輪流摸打範例打出北之後的手牌。 */
    private val ROUND_TENPAI_HAND: List<Tile> = WIN_SEQUENCE_DOTS + WIN_SEQUENCE_BAMBOOS + WIN_TRIPLET + listOf(m(2), m(3)) + WIN_PAIR

    /** 別人打牌時的範例面板：上家打出五索，可以吃、碰、槓、和或跳過；文字不依任何規則。 */
    private val CALL_PROMPT = previewPrompt(
        triggerTileAssetKey = "s5",
        fromLeftPlayer = true,
        actions = listOf(
            previewAction(BuiltInGameActionIds.CHI, listOf("s4", "s5", "s6"), claimedTileIndex = 1),
            previewAction(BuiltInGameActionIds.PON, listOf("s5", "s5", "s5")),
            previewAction(BuiltInGameActionIds.KAN_OPEN, listOf("s5", "s5", "s5", "s5")),
            previewAction(BuiltInGameActionIds.RON, listOf("s5")),
            previewAction(BuiltInGameActionIds.PASS, emptyList()),
        ),
        ruleModuleId = null,
    )

    /** 宣告時指定要打的牌的範例面板：日本麻將的立直，可以打出四萬或三索。 */
    private val DECLARE_PROMPT = previewPrompt(
        triggerTileAssetKey = "m4",
        fromLeftPlayer = false,
        actions = listOf(
            previewAction(RiichiGameAction.Riichi.id, listOf("m4", "s3")),
            previewAction(BuiltInGameActionIds.PASS, emptyList()),
        ),
        ruleModuleId = BuiltInRuleModuleIds.RIICHI,
    )
}
