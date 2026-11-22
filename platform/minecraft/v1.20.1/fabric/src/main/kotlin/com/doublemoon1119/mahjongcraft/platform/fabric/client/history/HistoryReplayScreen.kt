package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryParticipantSummaryDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayIdentityDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayTransactionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundEventsDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundPositionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundStateDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.TileFaceViewDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.WindDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDomain
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.tile.component.TilePose
import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongStoolBlock
import com.doublemoon1119.mahjongcraft.platform.fabric.client.character.ClientAiCharacterPreviews
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.ControlTooltip
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.RestartableMarqueeButtonWidget
import com.doublemoon1119.mahjongcraft.platform.fabric.client.guide.scene.GuideSceneRenderer
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModBlocks
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionReplayMarker
import com.doublemoon1119.mahjongcraft.platform.minecraft.animation.AnimationStep
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.table.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.AiCharacterSnapshot
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.HandReveal
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.HistoryReplayArrangement
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.HistoryReplayCharacterAppearance
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.HistoryReplayHandReveals
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.HistoryReplayHighlights
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.HistoryReplayWinEffects
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.MinecraftHistoryScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.ReplayTableNotes
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.CardBackground
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.DropdownOption
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.DropdownOverlay
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.FormScreenLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.PixelIcon
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.ScreenRendering
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.ScrollbarLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.VerticalScrollbar
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.measuringTimeNow
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.guide.scene.GuideScene
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.guide.scene.GuideSceneEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.guide.scene.GuideSceneFigure
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.guide.scene.GuideSceneRenderGuard
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.guide.scene.GuideSceneTileGlow
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.guide.scene.GuideSceneView
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history.HistoryBrowseReplayState
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history.HistoryBrowseStatus
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history.HistoryPageFrame
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history.HistoryPaging
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history.HistoryReplayAnimation
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history.HistoryReplayHandRevealMotion
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history.HistoryReplayLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history.HistoryReplayPlayerEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history.HistoryReplaySpeed
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history.HistoryScreenLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.entity.MahjongStoolSeatGeometry
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.minecraft.seating.MahjongSeatingTableLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongTableSeatLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.ArrangedTile
import com.mojang.authlib.GameProfile
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.locale.Language
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.LivingEntity
import org.lwjgl.glfw.GLFW
import java.util.Objects
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

/**
 * 牌譜回放畫面：把一局的每一筆交易依序擺成牌桌場景，可以播放、暫停、逐筆前後移動、拖曳進度條與換局。
 *
 * 牌面與事件由瀏覽控制器在背景讀取；本畫面只負責呈現、播放計時與輸入。
 *
 * @property session 共用查詢、導航及呈現來源。
 */
internal class HistoryReplayScreen(
    private val session: HistoryBrowseSession,
) : Screen(Component.translatable(MinecraftHistoryScreenKeys.REPLAY_TITLE)) {
    /** 記錄擺不出牌桌的原因。 */
    private val logger = mahjongCraftLogger(HistoryReplayScreen::class)

    /** 版面。 */
    private var layout = HistoryReplayLayout.measure(width = 0, height = 0, labelWidth = 0, speedSelectorWidth = 0)

    /** 播放狀態，同一次瀏覽共用。 */
    private val playback = session.replayPlayback

    /** 場景繪製器。 */
    private val renderer = GuideSceneRenderer()

    /** 下拉清單。 */
    private val dropdown = DropdownOverlay()

    /** 視角：觀看的玩家初始座位；自由視角為 null。 */
    private var viewSeat: Int? = null

    /** 手牌是否面朝上攤開；否則和對局中一樣立著。 */
    private var handsOpen = false

    /** 自由視角時玩家拖曳轉動的角度。 */
    private var yawOffset = 0f

    /** 是否正在拖曳轉動場景。 */
    private var draggingScene = false

    /** 場景在放得下桌椅的大小上再放大的倍數。 */
    private var zoom = 1f

    /** 放大後場景往右移動的像素。 */
    private var panX = 0f

    /** 放大後場景往下移動的像素。 */
    private var panY = 0f

    /** 是否正在拖曳平移場景。 */
    private var draggingPan = false

    /** 場景是否全螢幕。 */
    private var fullscreen = false

    /** 全螢幕時最近一次移動滑鼠或換步的時間，決定控制區何時淡出。 */
    private var lastActivity = Duration.ZERO

    /** 上一幀的游標位置，用來偵測滑鼠移動。 */
    private var lastPointer: Pair<Int, Int>? = null

    /** 控制區目前的不透明度；一般版面固定為 1。 */
    private var controlsAlpha = 1f

    /** 是否正在拖曳進度條。 */
    private var draggingProgress = false

    /** 是否正在拖曳交易列表捲軸。 */
    private var draggingListBar = false

    /** 拖曳捲軸時滑塊內的抓取位置。 */
    private var listGrabOffset = 0.0

    /** 交易列表的捲動位置。 */
    private var listScroll = 0.0

    /** 窄視窗是否叫出交易列表。 */
    private var listOpen = false

    /** 上次建立場景的條件與結果。 */
    private var sceneCache: Pair<SceneKey, GuideScene?>? = null

    /** 真人玩家的模型，依玩家識別碼保留；換世界時重建。 */
    private val playerEntities = mutableMapOf<String, LivingEntity>()

    /** 依牌譜記下的資料畫出的本局資訊、玩家資訊與桌上物件。 */
    private val tableDisplays = HistoryReplayTableDisplays(session)

    /** 停在胡牌或流局那一步時疊在場景上的結算卡片。 */
    private val settlementCard by lazy {
        val tileGroups = HistoryTileGroupRenderer(
            faces = session.tileFaces,
            assets = session.tileAssets,
            markerDisplays = session.discardMarkers,
            displayNames = session.tileDisplayNames,
            networkRegistries = session.networkRegistries,
        )
        HistoryReplaySettlementCard(
            session = session,
            textRenderer = font,
            outcomeRows = HistoryOutcomeCardRows(session.eventPresenter, session.settlementTemplates, session.setAsideDisplays),
            rowRenderer = HistoryCardRowRenderer(session, font, session.tileFaces, session.tileAssets, tileGroups),
            eventPresenter = session.eventPresenter,
            actorText = { seat -> session.controller.state.value.replay?.state?.identity?.let { actorText(it, seat) } ?: Component.translatable(MinecraftHistoryScreenKeys.ROUND_SEAT, seat + 1) },
            participant = { seat -> session.controller.state.value.replay?.state?.identity?.let { participant(it, seat) } },
        )
    }

    /** 收起結算卡片的按鈕。 */
    private lateinit var settlementHideButton: RestartableMarqueeButtonWidget

    /** 收起後重新顯示結算卡片的按鈕。 */
    private lateinit var settlementShowButton: RestartableMarqueeButtonWidget

    /** 最近擺好的牌桌，依條件索引；只留動畫前後兩步需要的份量。 */
    private val arrangements = LinkedHashMap<ArrangementKey, Map<Int, ArrangedTile>?>()

    /** 正在播放的牌動畫；沒有時為 null。 */
    private var animation: HistoryReplayAnimation? = null

    /** 上一幀顯示的局序號與步數。 */
    private var shownStep: Pair<Int, Int>? = null

    /** 上一幀顯示的牌面。 */
    private var shownState: HistoryRoundStateDto? = null

    /** 上一幀的時間，用來推進動畫。 */
    private var lastFrameAt: Duration? = null

    /** 上次整理的交易列與它所根據的事件頁。 */
    private var rowsCache: Pair<List<HistoryRoundEventsDto>, List<TransactionRow>>? = null

    /** 播放控制按鈕：第一筆、上一筆、播放、下一筆、最後一筆。 */
    private val controlButtons = mutableListOf<RestartableMarqueeButtonWidget>()

    /** 速度選擇器。 */
    private lateinit var speedButton: RestartableMarqueeButtonWidget

    /** 視角選擇器。 */
    private lateinit var viewButton: RestartableMarqueeButtonWidget

    /** 手牌選擇器。 */
    private lateinit var handButton: RestartableMarqueeButtonWidget

    /** 開啟可見性子頁的按鈕。 */
    private lateinit var visibilityButton: RestartableMarqueeButtonWidget

    /** 各家人物目前的不透明度，依初始座位；切換顯示方式或轉動鏡頭時往新的值淡入淡出。 */
    private val figureAlpha = mutableMapOf<Int, Float>()

    /** 上一幀到這一幀經過的時間，供淡入淡出使用。 */
    private var frameElapsed = Duration.ZERO

    /** 窄視窗叫出交易列表的按鈕。 */
    private var listButton: RestartableMarqueeButtonWidget? = null

    /** 上一局按鈕。 */
    private lateinit var previousRoundButton: RestartableMarqueeButtonWidget

    /** 下一局按鈕。 */
    private lateinit var nextRoundButton: RestartableMarqueeButtonWidget

    /** 讀取或回放失敗時的重試按鈕。 */
    private lateinit var retryButton: RestartableMarqueeButtonWidget

    /** 場景右上角切換全螢幕的按鈕。 */
    private lateinit var fullscreenButton: RestartableMarqueeButtonWidget

    /** 建立控制項。 */
    override fun init() {
        val labelWidth = listOf(MinecraftHistoryScreenKeys.REPLAY_VIEW, MinecraftHistoryScreenKeys.REPLAY_HAND).maxOf { font.width(Component.translatable(it)) }
        val speedWidth = RestartableMarqueeButtonWidget.selectorWidth(HistoryReplaySpeed.entries.maxOf { font.width(speedText(it)) })
        layout = if (fullscreen) {
            HistoryReplayLayout.fullscreen(width = width, height = height, speedSelectorWidth = speedWidth)
        } else {
            HistoryReplayLayout.measure(width = width, height = height, labelWidth = labelWidth, speedSelectorWidth = speedWidth)
        }
        clampPan()
        dropdown.close()
        clearWidgets()
        controlButtons.clear()
        val actions = listOf(::first, ::previous, ::togglePlay, ::next, ::last)
        val icons = listOf(PixelIcon.SKIP_FIRST, PixelIcon.STEP_BACKWARD, PixelIcon.PLAY, PixelIcon.STEP_FORWARD, PixelIcon.SKIP_LAST)
        actions.forEachIndexed { index, action ->
            val bounds = layout.controlButton(index)
            controlButtons += addRenderableWidget(
                button(Component.empty(), bounds) { action() }.also { it.icon = icons[index] },
            )
        }
        speedButton = addRenderableWidget(button(speedText(playback.speed), layout.speedSelector()) { toggleSpeedList() })
        speedButton.selectorExpanded = { dropdown.isOpenFor(SPEED_KEY) }
        viewButton = addRenderableWidget(button(Component.empty(), layout.viewSelector) { toggleViewList() })
        viewButton.selectorExpanded = { dropdown.isOpenFor(VIEW_KEY) }
        handButton = addRenderableWidget(button(Component.empty(), layout.handSelector) { toggleHandList() })
        handButton.selectorExpanded = { dropdown.isOpenFor(HAND_KEY) }
        visibilityButton = addRenderableWidget(button(Component.translatable(MinecraftHistoryScreenKeys.REPLAY_VISIBILITY), layout.visibilityButton) { openVisibility() })
        visibilityButton.tooltip = Tooltip.create(Component.translatable(MinecraftHistoryScreenKeys.REPLAY_VISIBILITY_TOOLTIP))
        listButton = layout.listToggle?.let { bounds ->
            addRenderableWidget(button(Component.translatable(MinecraftHistoryScreenKeys.REPLAY_LIST), bounds) { listOpen = !listOpen })
        }
        val frame = HistoryPageFrame(windowWidth = width, windowHeight = height)
        previousRoundButton = addRenderableWidget(button(Component.translatable(MinecraftHistoryScreenKeys.REPLAY_PREVIOUS_ROUND), frame.footerSlot(0)) { changeRound(-1) })
        nextRoundButton = addRenderableWidget(button(Component.translatable(MinecraftHistoryScreenKeys.REPLAY_NEXT_ROUND), frame.footerSlot(2)) { changeRound(1) })
        val backButton = addRenderableWidget(button(Component.translatable(MinecraftHistoryScreenKeys.BACK), frame.footerSlot(HistoryPageFrame.LAST_SLOT)) { session.backFromReplay() })
        retryButton = addRenderableWidget(button(Component.translatable(MinecraftHistoryScreenKeys.RETRY), HistoryScreenLayout.Bounds(0, 0, RETRY_WIDTH, FormScreenLayout.BUTTON_HEIGHT)) { session.controller.retry() })
        retryButton.visible = false
        settlementHideButton = addRenderableWidget(
            button(Component.translatable(MinecraftHistoryScreenKeys.REPLAY_SETTLEMENT_HIDE), HistoryScreenLayout.Bounds(0, 0, SETTLEMENT_BUTTON_WIDTH, SETTLEMENT_BUTTON_HEIGHT)) {
                settlementCard.collapsed = true
            },
        )
        settlementHideButton.tooltip = Tooltip.create(Component.translatable(MinecraftHistoryScreenKeys.REPLAY_SETTLEMENT_HIDE_TOOLTIP))
        settlementShowButton = addRenderableWidget(
            button(Component.translatable(MinecraftHistoryScreenKeys.REPLAY_SETTLEMENT), HistoryScreenLayout.Bounds(0, 0, SETTLEMENT_BUTTON_WIDTH, SETTLEMENT_BUTTON_HEIGHT)) {
                settlementCard.collapsed = false
            },
        )
        settlementShowButton.tooltip = Tooltip.create(Component.translatable(MinecraftHistoryScreenKeys.REPLAY_SETTLEMENT_SHOW_TOOLTIP))
        settlementHideButton.visible = false
        settlementShowButton.visible = false
        fullscreenButton = addRenderableWidget(button(Component.empty(), layout.fullscreenToggle) { toggleFullscreen() })
        if (fullscreen) {
            // 全螢幕只留播放控制；視角、手牌、交易列表與底部按鈕回到一般版面才出現。
            listOfNotNull(viewButton, handButton, visibilityButton, listButton, previousRoundButton, nextRoundButton, backButton).forEach { it.visible = false }
        }
    }

    /** 切換全螢幕，重新排版；進入時控制區先顯示，滑鼠一段時間沒動才淡出。 */
    private fun toggleFullscreen() {
        fullscreen = !fullscreen
        listOpen = false
        lastActivity = measuringTimeNow()
        rebuildWidgets()
    }

    /**
     * 建立一顆按鈕。
     *
     * @param text 按鈕文字。
     * @param bounds 按鈕範圍。
     * @param onPress 按下時的動作。
     * @return 按鈕。
     */
    private fun button(text: Component, bounds: HistoryScreenLayout.Bounds, onPress: () -> Unit): RestartableMarqueeButtonWidget = RestartableMarqueeButtonWidget.builder(text) { onPress() }.dimensions(bounds.x, bounds.y, bounds.width, bounds.height).build()

    /** 歷史瀏覽不暫停整合伺服器。 */
    override fun isPauseScreen(): Boolean = false

    /** Esc 回到事件頁。 */
    override fun onClose() {
        session.backFromReplay()
    }

    /** 釋放場景繪製器，正常子頁切換不結束共用 session。 */
    override fun removed() {
        renderer.close()
        session.removed(this)
        super.removed()
    }

    /** 更新連線生命週期。 */
    override fun tick() {
        session.tick()
        super.tick()
    }

    /**
     * 推進播放、繪製場景、說明、交易列表、進度條與控制列。
     *
     * @param context 繪製上下文。
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @param delta 幀內插值。
     */
    override fun render(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context)
        advancePlayback()
        val now = measuringTimeNow()
        if (lastPointer != mouseX to mouseY) {
            lastPointer = mouseX to mouseY
            lastActivity = now
        }
        controlsAlpha = controlsAlpha(now, mouseX, mouseY)
        val replay = session.controller.state.value.replay
        updateAnimation(replay, now)
        if (!layout.fullscreen) {
            val heading = title.copy().append(TITLE_SEPARATOR).append(Component.translatable(MinecraftHistoryScreenKeys.ROUND_NUMBER, replay?.roundNumber ?: "—"))
            ScreenRendering.title(context, font, heading, width)
        }
        val rows = replay?.let(::rows).orEmpty()
        val pointerX = dropdown.pointer(mouseX)
        val pointerY = dropdown.pointer(mouseY)
        var tooltip: List<Component>? = null
        updateControls(replay)
        renderScene(context, replay, pointerX, pointerY)
        renderSettlement(context, replay, pointerX, pointerY)?.let { tooltip = it }
        layout.controlBar?.let { bar -> context.fill(bar.x, bar.y, bar.right, bar.bottom, faded(CONTROL_BAR_BACKGROUND)) }
        if (!layout.fullscreen) {
            renderCaption(context, replay, rows)
            if (layout.wide || listOpen) tooltip = renderList(context, replay, rows, pointerX, pointerY)
            renderSelectorLabels(context)
            renderRoundNumber(context, replay)
        }
        if (controlsShown()) {
            renderSpeedLabel(context)
            renderProgress(context, replay, rows, pointerX, pointerY)?.let { tooltip = it }
            renderCounter(context, replay)
        }
        super.render(context, pointerX, pointerY, delta)
        dropdown.render(context, font, height, mouseX, mouseY)
        if (!dropdown.isOpen && !draggingProgress && !draggingListBar) tooltip?.let { context.renderComponentTooltip(font, it, mouseX, mouseY) }
    }

    /**
     * 控制區的不透明度：一般版面固定顯示；全螢幕時暫停中、游標在控制區或全螢幕按鈕上、拖曳進度條或清單展開時
     * 一直顯示，否則滑鼠一段時間沒動就淡出。
     *
     * @param now 目前時間。
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @return 0 到 1 的不透明度。
     */
    private fun controlsAlpha(now: Duration, mouseX: Int, mouseY: Int): Float {
        if (!layout.fullscreen) return 1f
        val bar = layout.controlBar
        val toggle = layout.fullscreenToggle
        val overControls = (bar != null && mouseY >= bar.y) ||
            (mouseX >= toggle.x && mouseX < toggle.right && mouseY >= toggle.y && mouseY < toggle.bottom)
        if (!playback.playing || draggingProgress || dropdown.isOpen || overControls) return 1f
        val idle = now - lastActivity
        return ((CONTROLS_HIDE_DELAY + CONTROLS_FADE - idle) / CONTROLS_FADE).toFloat().coerceIn(0f, 1f)
    }

    /**
     * 控制區是否看得見、可以操作。
     *
     * @return 不透明度高到看得見時為 true。
     */
    private fun controlsShown(): Boolean = controlsAlpha >= MIN_VISIBLE_ALPHA

    /**
     * 依控制區的不透明度淡化一個顏色。
     *
     * @param argb 原本的 ARGB 顏色；沒有 alpha 位元時視為不透明。
     * @return 淡化後的 ARGB 顏色。
     */
    private fun faded(argb: Int): Int {
        val alpha = if (argb ushr ALPHA_SHIFT == 0) FULL_ALPHA else argb ushr ALPHA_SHIFT
        return ((alpha * controlsAlpha).toInt() shl ALPHA_SHIFT) or (argb and RGB_MASK)
    }

    /** 播放中時間到了就前進一步。 */
    private fun advancePlayback() {
        val replay = session.controller.state.value.replay ?: return
        val stepCount = replay.stepCount ?: return
        val next = playback.advance(
            step = replay.step,
            stepCount = stepCount,
            now = measuringTimeNow(),
            nextReady = session.controller.isReplayStepReady(replay.step + 1),
            marked = markerAt(replay, replay.step) != null,
        ) ?: return
        session.controller.replayTo(next)
    }

    /**
     * 更新按鈕的文字、圖示與可用狀態。
     *
     * @param replay 目前回放；不在回放時為 null。
     */
    private fun updateControls(replay: HistoryBrowseReplayState?) {
        // 還不知道總步數時當成沒有步數，所有移動按鈕停用。
        val stepCount = replay?.stepCount ?: 0
        val step = replay?.step ?: 0
        controlButtons[0].active = stepCount > 0 && step > 0
        controlButtons[1].active = stepCount > 0 && step > 0
        controlButtons[2].active = stepCount > 1
        controlButtons[3].active = step < stepCount - 1
        controlButtons[4].active = step < stepCount - 1
        controlButtons[2].icon = if (playback.playing) PixelIcon.PAUSE else PixelIcon.PLAY
        (controlButtons + speedButton + fullscreenButton).forEach {
            it.visible = controlsShown()
            it.setAlpha(controlsAlpha)
        }
        fullscreenButton.icon = if (fullscreen) PixelIcon.FULLSCREEN_EXIT else PixelIcon.FULLSCREEN_ENTER
        val fullscreenLabel = Component.translatable(if (fullscreen) MinecraftHistoryScreenKeys.REPLAY_EXIT_FULLSCREEN else MinecraftHistoryScreenKeys.REPLAY_FULLSCREEN)
        fullscreenButton.message = fullscreenLabel
        fullscreenButton.tooltip = Tooltip.create(ControlTooltip.description(fullscreenLabel))
        val labels = listOf(
            MinecraftHistoryScreenKeys.REPLAY_FIRST,
            MinecraftHistoryScreenKeys.REPLAY_PREVIOUS,
            if (playback.playing) MinecraftHistoryScreenKeys.REPLAY_PAUSE else MinecraftHistoryScreenKeys.REPLAY_PLAY,
            MinecraftHistoryScreenKeys.REPLAY_NEXT,
            MinecraftHistoryScreenKeys.REPLAY_LAST,
        )
        controlButtons.forEachIndexed { index, button ->
            button.message = Component.translatable(labels[index])
            button.tooltip = Tooltip.create(ControlTooltip.description(Component.translatable(labels[index])))
        }
        val speeds = HistoryReplaySpeed.entries
        speedButton.message = speedText(playback.speed)
        speedButton.tooltip = Tooltip.create(
            ControlTooltip.options(
                description = Component.translatable(MinecraftHistoryScreenKeys.REPLAY_SPEED),
                labels = speeds.map(::speedText),
                currentIndex = speeds.indexOf(playback.speed),
            ),
        )
        val players = replay?.state?.players.orEmpty()
        if (viewSeat != null && players.isNotEmpty() && players.none { it.initialSeatIndex == viewSeat }) viewSeat = null
        visibilityButton.active = players.isNotEmpty()
        viewButton.message = viewText(replay?.state, viewSeat)
        viewButton.active = players.isNotEmpty()
        val seats = viewSeats(replay?.state)
        viewButton.tooltip = Tooltip.create(
            ControlTooltip.options(
                description = Component.translatable(MinecraftHistoryScreenKeys.REPLAY_VIEW),
                labels = seats.map { viewText(replay?.state, it) },
                currentIndex = seats.indexOf(viewSeat),
            ),
        )
        handButton.message = Component.translatable(HAND_KEYS[if (handsOpen) 1 else 0])
        handButton.tooltip = Tooltip.create(
            ControlTooltip.options(
                description = Component.translatable(MinecraftHistoryScreenKeys.REPLAY_HAND),
                labels = HAND_KEYS.map { Component.translatable(it) },
                currentIndex = if (handsOpen) 1 else 0,
            ),
        )
        listButton?.outlined = listOpen
        val rounds = roundNumbers()
        val index = rounds.indexOf(replay?.roundNumber)
        previousRoundButton.active = index > 0
        nextRoundButton.active = index >= 0 && index < rounds.lastIndex
        val failed = replay?.status is HistoryBrowseStatus.Failed || replay?.eventsStatus is HistoryBrowseStatus.Failed
        retryButton.visible = failed
        retryButton.active = failed && session.controller.canRetry()
    }

    /**
     * 繪製場景；牌面還沒取得或無法擺牌時改寫說明。
     *
     * @param context 繪製上下文。
     * @param replay 目前回放。
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     */
    private fun renderScene(context: GuiGraphics, replay: HistoryBrowseReplayState?, mouseX: Int, mouseY: Int) {
        val bounds = layout.scene
        context.fill(bounds.x, bounds.y, bounds.right, bounds.bottom, SCENE_BACKGROUND)
        val state = replay?.state
        val scene = state?.let(::scene)
        val notice = when {
            replay == null -> Component.translatable(MinecraftHistoryScreenKeys.LOADING)
            replay.status is HistoryBrowseStatus.Failed -> Component.translatable(replay.status.reason.translationKey)
            state == null -> Component.translatable(MinecraftHistoryScreenKeys.LOADING)
            scene == null -> sceneNotice(state)
            else -> null
        }
        if (scene != null) {
            renderer.render(
                context = context,
                scene = scene,
                left = bounds.x,
                top = bounds.y,
                width = bounds.width,
                height = bounds.height,
                mouseX = mouseX,
                mouseY = mouseY,
                yawOffset = yawOffset,
                zoom = zoom,
                panX = panX,
                panY = panY,
            )
        }
        if (notice != null) {
            val lines = font.split(notice, (bounds.width - NOTICE_PADDING * 2).coerceAtLeast(1))
            val top = bounds.y + (bounds.height - lines.size * LINE_HEIGHT) / 2
            lines.forEachIndexed { index, line ->
                context.drawCenteredString(font, line, bounds.x + bounds.width / 2, top + index * LINE_HEIGHT, TEXT_COLOR)
            }
            if (retryButton.visible) {
                retryButton.x = bounds.x + (bounds.width - retryButton.width) / 2
                retryButton.y = top + lines.size * LINE_HEIGHT + NOTICE_PADDING
            }
        } else if (retryButton.visible) {
            // 場景右上角是全螢幕按鈕，重試按鈕排在它左邊。
            retryButton.x = layout.fullscreenToggle.x - NOTICE_PADDING - retryButton.width
            retryButton.y = layout.fullscreenToggle.y
        }
    }

    /**
     * 取得牌面後仍擺不出牌桌時的說明，例如規則還在讀取、這台電腦沒有裝這套規則或這一步擺不出來。
     *
     * @param state 目前牌面。
     * @return 說明文字。
     */
    private fun sceneNotice(state: HistoryRoundStateDto): Component {
        val browse = session.controller.state.value
        val key = HistoryReplaySceneNotice.key(
            ruleInstalled = session.ruleInstalled(browse.summary?.detail?.summary?.ruleId),
            rulesStatus = browse.ruleSettings?.takeIf { it.matchId == state.identity.matchId }?.status,
            moduleResolved = session.ruleModule(state) != null,
            hasNotes = tableDisplays.notes(state) != null,
        )
        return Component.translatable(key)
    }

    /**
     * 依目前牌面、視角與手牌選擇建立場景；牌在動畫中時每一幀依動畫重畫，否則條件相同時沿用上次的結果。
     *
     * @param state 目前牌面。
     * @return 場景；規則設定還沒讀到或牌譜缺少擺牌資料時為 null。
     */
    private fun scene(state: HistoryRoundStateDto): GuideScene? {
        val arranged = arrangement(state) ?: return null
        val (figures, players) = seatedPlayers(state)
        val entities = players + tableDisplays.tableProps(state, arranged)
        animation?.let { running ->
            val effects = running.winEffects().takeIf { session.replayVisibility.winEffect }.orEmpty()
            val built = HistoryReplayScene.build(state.players.size, arranged, running.samples(), figures, entities, effects) { index -> assetKey(state, index) }
            return withGlows(withPanels(withView(built, state), state), state, arranged)
        }
        val key = SceneKey(state = state, arranged = arranged, viewSeat = viewSeat, figures = figures, entities = entities)
        val built = sceneCache?.takeIf { it.first == key }?.second
            ?: withView(HistoryReplayScene.build(state.players.size, arranged, emptyMap(), figures, entities, emptyList()) { index -> assetKey(state, index) }, state)
        sceneCache = key to built
        return withGlows(withPanels(built, state), state, arranged)
    }

    /**
     * 讓牌發光，條件與顏色與對局中相同：
     *
     * - 最後打出、仍留在牌河的牌：規則開啟這項標示、玩家的顯示設定也開著時，牌落地後持續發光。
     * - 滑鼠指著的牌與相同的牌：玩家的同種牌提示開著時以呼吸光發亮；看不到牌面的牌不算，拖曳場景時不亮。
     *
     * 動畫播放中只有正在移動的牌不參與，其他牌照常發光，播放時不會每一步閃一下。
     *
     * @param scene 場景。
     * @param state 目前牌面。
     * @param arranged 擺好的牌桌。
     * @return 套上發光的場景。
     */
    private fun withGlows(scene: GuideScene, state: HistoryRoundStateDto, arranged: Map<Int, ArrangedTile>): GuideScene {
        val module = session.ruleModule(state) ?: return scene
        val visibility = session.clientConfig.current.presentationVisibility
        val moving = animation?.movingTiles.orEmpty()
        val latest = HistoryReplayHighlights.latestDiscard(
            discardOrder = state.discardOrder,
            discards = state.players.flatMap { it.discards },
            roundOver = state.outcome != null,
        )?.takeIf { it !in moving && visibility.latestDiscardHighlightEnabled && session.tileLayoutProfiles.profileFor(module.id).highlightsLatestDiscard }
        val target = renderer.hoveredTileKey.takeIf {
            !draggingScene && !draggingPan && !dropdown.isOpen && visibility.matchingTileHighlightEnabled
        }
        val interpretation = module.createTileInterpretationPolicy()
        val visible = arranged.filterValues { it.pose != TilePose.FACE_DOWN }.keys - moving
        val canonical = { index: Int ->
            state.tileCatalog.getOrNull(index)?.takeIf { it.isDisplayableHistoryFace() }?.let { interpretation.canonicalize(it.tile.toDomain()) }
        }
        val matching = target?.takeIf { it in visible }?.let { HistoryReplayHighlights.matching(it, visible, canonical) }
        if (latest == null && matching == null) return scene
        val lit = target?.takeIf { matching != null && canonical(it) != null }
        return scene.copy(
            tiles = scene.tiles.map { tile ->
                val glow = when (tile.key) {
                    null -> null
                    lit -> GuideSceneTileGlow.TARGET
                    in matching.orEmpty() -> GuideSceneTileGlow.MATCHING
                    latest -> GuideSceneTileGlow.LATEST_DISCARD
                    else -> null
                }
                if (glow == null) tile else tile.copy(glow = glow)
            },
        )
    }

    /**
     * 加上本局資訊與玩家資訊；選某一家視角時不畫那一家的玩家資訊，免得擋住視線。
     *
     * @param scene 場景。
     * @param state 目前牌面。
     * @return 加上面板的場景。
     */
    private fun withPanels(scene: GuideScene, state: HistoryRoundStateDto): GuideScene {
        val hiddenSeat = state.players.indexOfFirst { it.initialSeatIndex == viewSeat }.takeIf { it >= 0 }
        return scene.copy(
            roundInfo = tableDisplays.roundInfo(state),
            playerInfo = tableDisplays.playerInfo(state, hiddenSeat) { seat -> actorText(state.identity, seat) },
        )
    }

    /**
     * 坐在桌邊的各家，都坐在自己的凳子上面向桌子。
     *
     * AI 照該場保存的角色外觀畫出：內建機器人用保存的牌面；其他角色用該角色的預覽模型，模型還沒建立、沒有對應的預覽能力或
     * 繪製失敗時改畫內建機器人。真人玩家以玩家模型畫出。這一步打出牌的玩家在牌的動畫開頭揮一次手。
     *
     * @param state 目前牌面。
     * @return 內建機器人外觀的角色與以原版繪製器畫出的模型。
     */
    private fun seatedPlayers(state: HistoryRoundStateDto): Pair<List<GuideSceneFigure>, List<GuideSceneEntity>> {
        val world = minecraft?.level ?: return emptyList<GuideSceneFigure>() to emptyList()
        if (playerEntities.values.any { it.level() !== world }) playerEntities.clear()
        val seats = MahjongSeatingTableLayout.seatPlacements(
            layout = MahjongTableSeatLayout.forPlayerCount(state.players.size),
            controllerX = 0,
            controllerY = 0,
            controllerZ = 0,
            tableFacing = MahjongTableFacing.NORTH,
        )
        val feetY = (ModBlocks.woodenMahjongStool as MahjongStoolBlock).design.seatHeight - MahjongStoolSeatGeometry.SEATED_BOTTOM_HEIGHT
        val summary = session.controller.state.value.summary?.detail?.summary
        val figures = mutableListOf<GuideSceneFigure>()
        val entities = mutableListOf<GuideSceneEntity>()
        state.players.forEachIndexed { index, player ->
            val seat = seats.getOrNull(index) ?: return@forEachIndexed
            val swing = animation?.swingProgress(player.initialSeatIndex) ?: 0f
            val alpha = figureAlpha(player.initialSeatIndex, seat.yaw)
            val playerId = state.identity.players.firstOrNull { it.initialSeatIndex == player.initialSeatIndex }?.playerId
            val participant = playerId?.let { id -> summary?.participants?.firstOrNull { it.playerId.equals(id, ignoreCase = true) } }
            val uuid = playerId?.let { runCatching { Uuid.parse(it) }.getOrNull() }
            if (participant?.aiStrategyId != null || uuid == null) {
                val character = uuid?.let { session.participants.character(it, summary?.matchId) }
                val model = character?.takeIf { HistoryReplayCharacterAppearance.usesPreviewModel(it.definition) }?.let(session.characterPreviews::sceneModel)
                if (character != null && model != null) {
                    entities += GuideSceneEntity(
                        entity = model,
                        x = seat.x,
                        y = feetY,
                        z = seat.z,
                        yaw = seat.yaw,
                        seated = true,
                        guard = CharacterModelGuard(session.characterPreviews, character),
                        swingProgress = swing,
                        alpha = alpha,
                    )
                } else {
                    val face = HistoryReplayCharacterAppearance.botFace(character?.definition)
                    figures += GuideSceneFigure(
                        faceAssetKey = face,
                        x = seat.x,
                        y = feetY,
                        z = seat.z,
                        yaw = seat.yaw,
                        seated = true,
                        swingProgress = swing,
                        alpha = alpha,
                    )
                }
            } else {
                val entity = playerEntities.getOrPut(playerId) { playerEntity(world, uuid) }
                entities += GuideSceneEntity(
                    entity = entity,
                    x = seat.x,
                    y = feetY,
                    z = seat.z,
                    yaw = seat.yaw,
                    seated = true,
                    swingProgress = swing,
                    alpha = alpha,
                )
            }
        }
        return figures to entities
    }

    /**
     * 一家人物這一幀的不透明度：依可見性設定、視角與鏡頭方向得到目標值，再從上一幀的值淡入淡出過去。
     *
     * @param initialSeat 初始座位。
     * @param seatYaw 座位面向桌子的朝向。
     * @return 0 到 1 之間的不透明度。
     */
    private fun figureAlpha(initialSeat: Int, seatYaw: Float): Float {
        val target = HistoryReplayFigureOpacity.target(
            visibility = session.replayVisibility.figure(initialSeat),
            viewed = initialSeat == viewSeat,
            seatYaw = seatYaw,
            cameraYaw = cameraYaw(),
        )
        val alpha = figureAlpha[initialSeat]?.let { HistoryReplayFigureOpacity.approach(it, target, frameElapsed) } ?: target
        figureAlpha[initialSeat] = alpha
        return alpha
    }

    /**
     * 場景鏡頭目前的水平角度：選某一家視角時是那一家座位朝向轉半圈，自由視角是預設角度加上拖曳轉動的角度。
     *
     * @return 水平角度。
     */
    private fun cameraYaw(): Float {
        val state = session.controller.state.value.replay?.state ?: return HistoryReplayScene.VIEW_YAW + yawOffset
        val index = state.players.indexOfFirst { it.initialSeatIndex == viewSeat }
        if (index < 0) return HistoryReplayScene.VIEW_YAW + yawOffset
        val seats = MahjongSeatingTableLayout.seatPlacements(
            layout = MahjongTableSeatLayout.forPlayerCount(state.players.size),
            controllerX = 0,
            controllerY = 0,
            controllerZ = 0,
            tableFacing = MahjongTableFacing.NORTH,
        )
        return seats.getOrNull(index)?.yaw?.minus(HALF_TURN) ?: (HistoryReplayScene.VIEW_YAW + yawOffset)
    }

    /** 開啟可見性子頁，帶入目前各家的名稱與視角。 */
    private fun openVisibility() {
        val state = session.controller.state.value.replay?.state ?: return
        val seats = state.players.sortedBy { it.initialSeatIndex }.map { player ->
            HistoryReplayVisibilityContext.Seat(initialSeat = player.initialSeatIndex, label = viewText(state, player.initialSeatIndex))
        }
        session.openReplayVisibility(
            replay = this,
            context = HistoryReplayVisibilityContext(seats = seats, viewedSeat = viewSeat, viewLabel = viewText(state, viewSeat)),
        )
    }

    /**
     * 建立不在世界中的玩家模型，外觀與牌譜頭像相同，並開始載入玩家的皮膚。
     *
     * @param world 目前客戶端世界。
     * @param playerId 玩家識別碼。
     * @return 玩家模型。
     */
    private fun playerEntity(world: ClientLevel, playerId: Uuid): LivingEntity {
        val profiles = session.playerProfiles
        profiles.requestResolve(playerId)
        val profile = profiles.knownProfile(playerId) ?: GameProfile(playerId.toJavaUuid(), UNKNOWN_PROFILE_NAME)
        return HistoryReplayPlayerEntity(world, playerId, profile, profiles)
    }

    /**
     * 局內牌索引對應的牌面素材。
     *
     * @param state 目前牌面。
     * @param index 局內牌索引。
     * @return 素材；看不到牌面時為 null。
     */
    private fun assetKey(state: HistoryRoundStateDto, index: Int): String? = state.tileCatalog.getOrNull(index)?.toHistoryAssetKey(session.tileAssets, session.networkRegistries)

    /**
     * 擺出整桌的牌：手牌依規則排好，依目前的手牌選擇立著或攤開，攤牌的玩家照對局中倒下或蓋下；最近兩份牌面的結果留著，
     * 動畫的前後兩步不必重算。
     *
     * @param state 牌面。
     * @return 依局內牌索引的擺放；規則設定還沒讀到或牌譜缺少擺牌資料時為 null。
     */
    private fun arrangement(state: HistoryRoundStateDto): Map<Int, ArrangedTile>? {
        val module = session.ruleModule(state) ?: return null
        val key = ArrangementKey(state = state, module = module, handsOpen = handsOpen, eventPages = session.controller.state.value.replay?.eventPages)
        if (key in arrangements) return arrangements[key]
        val sorted = session.replayState(state)
        val notes = tableDisplays.notes(state)
        val handPoses = notes?.let { HistoryReplayHandReveals.handPoses(sorted, reveals(sorted, it)) }.orEmpty()
        val built = if (notes == null) {
            null
        } else {
            runCatching {
                HistoryReplayArrangement.arrange(
                    state = sorted,
                    notes = notes,
                    module = module,
                    tileLayout = session.tileLayoutProfiles.profileFor(module.id),
                    openHands = handsOpen,
                    sidewaysMarker = { session.discardMarkers.find(it)?.sideways == true },
                    handPoses = handPoses,
                )
            }.onFailure { logger.warn("Replay table arrangement failed for round {} at {}", state.roundNumber, state.position, it) }.getOrNull()
        }
        arrangements[key] = built
        while (arrangements.size > ARRANGEMENT_CACHE_SIZE) arrangements.remove(arrangements.keys.first())
        return built
    }

    /**
     * 套用視角：選某一家時鏡頭在那個座位後方朝桌子中央看。
     *
     * @param scene 場景。
     * @param state 目前牌面。
     * @return 套用視角後的場景。
     */
    private fun withView(scene: GuideScene, state: HistoryRoundStateDto): GuideScene {
        val seatIndex = state.players.indexOfFirst { it.initialSeatIndex == viewSeat }
        if (seatIndex < 0) return scene
        val seats = MahjongSeatingTableLayout.seatPlacements(
            layout = MahjongTableSeatLayout.forPlayerCount(state.players.size),
            controllerX = 0,
            controllerY = 0,
            controllerZ = 0,
            tableFacing = MahjongTableFacing.NORTH,
        )
        val seatYaw = seats.getOrNull(seatIndex)?.yaw ?: return scene
        // 鏡頭在座位後方朝桌子中央看：座位面向桌子的朝向轉半圈，就是讓這個座位落在畫面下方的場景角度。
        return scene.copy(view = GuideSceneView(yawDegrees = seatYaw - HALF_TURN, pitchDegrees = SEAT_VIEW_PITCH), rotatable = false)
    }

    /**
     * 推進牌的動畫：顯示的牌面往後一步時播放這一步的動畫，其他換步直接切換；動畫中播放計時暫停，動畫播完才開始停留。
     *
     * @param replay 目前回放。
     * @param now 目前時間。
     */
    private fun updateAnimation(replay: HistoryBrowseReplayState?, now: Duration) {
        val elapsed = lastFrameAt?.let { now - it } ?: Duration.ZERO
        lastFrameAt = now
        frameElapsed = elapsed
        animation?.let { running ->
            running.advance(elapsed.toDouble(DurationUnit.SECONDS) * TICKS_PER_SECOND * playback.speed.multiplier).forEach(::playSound)
            if (running.finished || running.roundNumber != replay?.roundNumber || running.toStep != replay.step) animation = null
        }
        val state = replay?.state ?: return
        val shown = replay.roundNumber to replay.step
        val previous = shownState
        if (shown != shownStep) {
            val forward = shownStep?.first == replay.roundNumber && replay.step == (shownStep?.second ?: 0) + 1
            animation = previous?.takeIf { forward }?.let { startAnimation(replay.roundNumber, replay.step, it, state) }
            shownStep = shown
            shownState = state
        }
        if (animation != null) playback.hold(now)
    }

    /**
     * 建立往後一步的動畫。
     *
     * @param roundNumber 局序號。
     * @param toStep 動畫結束時的步數。
     * @param before 這一步開始時的牌面。
     * @param after 這一步結束時的牌面。
     * @return 動畫；擺不出牌桌或沒有牌要動時為 null。
     */
    private fun startAnimation(roundNumber: Int, toStep: Int, before: HistoryRoundStateDto, after: HistoryRoundStateDto): HistoryReplayAnimation? {
        val from = arrangement(before) ?: return null
        val to = arrangement(after) ?: return null
        return HistoryReplayAnimation(
            roundNumber = roundNumber,
            toStep = toStep,
            before = from,
            after = to,
            reveals = newReveals(before, after),
            winningTiles = if (session.replayVisibility.winEffect) HistoryReplayWinEffects.winningTiles(transactions(after), toStep) else emptyList(),
        ).takeIf { it.durationTicks > 0 }
    }

    /**
     * 每一家手牌在這一步的攤牌方式。
     *
     * @param state 依規則排好手牌的牌面。
     * @param notes 這一步的呈現附註。
     * @return 依玩家在牌面中的位置；沒有攤牌的玩家不列入。
     */
    private fun reveals(state: HistoryRoundStateDto, notes: ReplayTableNotes): Map<Int, HandReveal> {
        val step = when (val position = state.position) {
            HistoryRoundPositionDto.Initial -> 0
            is HistoryRoundPositionDto.AfterTransaction -> position.index + 1
        }
        return HistoryReplayHandReveals.reveals(state, notes, HistoryReplayHandReveals.revealedWinners(transactions(state), step))
    }

    /**
     * 牌面所屬那一局目前讀到的交易。
     *
     * @param state 牌面。
     * @return 依序的交易；回放已經換到別局時為空。
     */
    private fun transactions(state: HistoryRoundStateDto): List<HistoryReplayTransactionDto> = session.controller.state.value.replay
        ?.takeIf { it.state?.identity == state.identity }
        ?.eventPages?.flatMap { it.transactions }.orEmpty()

    /**
     * 往後一步時開始攤牌的手牌。
     *
     * @param before 這一步開始時的牌面。
     * @param after 這一步結束時的牌面。
     * @return 開始攤牌的手牌；沒有時為空。
     */
    private fun newReveals(before: HistoryRoundStateDto, after: HistoryRoundStateDto): List<HistoryReplayHandRevealMotion> {
        val sortedBefore = session.replayState(before)
        val sortedAfter = session.replayState(after)
        val notesBefore = tableDisplays.notes(before) ?: return emptyList()
        val notesAfter = tableDisplays.notes(after) ?: return emptyList()
        val previous = reveals(sortedBefore, notesBefore)
        return reveals(sortedAfter, notesAfter).filter { (index, reveal) -> previous[index] != reveal }.map { (index, reveal) ->
            val player = sortedAfter.players[index]
            HistoryReplayHandRevealMotion(tiles = player.handTiles + listOfNotNull(player.lastDrawn), reveal = reveal)
        }
    }

    /**
     * 在客戶端播放動畫的聲音，不屬於任何位置。
     *
     * @param sound 聲音步驟。
     */
    private fun playSound(sound: AnimationStep.PlaySound) {
        val event = ResourceLocation.tryParse(sound.soundId)?.let(BuiltInRegistries.SOUND_EVENT::get) ?: return
        minecraft?.soundManager?.play(SimpleSoundInstance.forUI(event, sound.pitch, sound.volume))
    }

    /**
     * 繪製場景下緣的交易說明。
     *
     * @param context 繪製上下文。
     * @param replay 目前回放。
     * @param rows 整局的交易列。
     */
    private fun renderCaption(context: GuiGraphics, replay: HistoryBrowseReplayState?, rows: List<TransactionRow>) {
        val bounds = layout.caption
        replay ?: return
        val row = rows.getOrNull(replay.step - 1)
        val label = if (replay.step == 0) {
            Component.translatable(MinecraftHistoryScreenKeys.STATE_INITIAL).withStyle { it.withColor(ACCENT_COLOR) }
        } else {
            Component.translatable(MinecraftHistoryScreenKeys.REPLAY_CAPTION, replay.step).withStyle { it.withColor(ACCENT_COLOR) }
        }
        val summary = row?.summary
        val text = if (summary == null) label else label.copy().append("  ").append(summary)
        val tile = row?.tile
        val tileSpace = if (tile != null) SMALL_TILE_WIDTH + TILE_GAP else 0
        val available = (bounds.width - NOTICE_PADDING * 2 - tileSpace).coerceAtLeast(1)
        val line = font.substrByWidth(text, available)
        val lineWidth = font.width(line)
        val left = bounds.x + (bounds.width - lineWidth - tileSpace) / 2
        context.drawString(font, Language.getInstance().getVisualOrder(line), left, bounds.y + (bounds.height - FormScreenLayout.VISIBLE_TEXT_HEIGHT) / 2, TEXT_COLOR)
        tile?.let { renderTile(context, it, left + lineWidth + TILE_GAP, bounds.y + (bounds.height - SMALL_TILE_HEIGHT) / 2) }
    }

    /**
     * 繪製交易列表；跟隨中時讓目前這筆保持可見。
     *
     * @param context 繪製上下文。
     * @param replay 目前回放。
     * @param rows 整局的交易列。
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @return 游標指著的交易提示；沒有時為 null。
     */
    private fun renderList(context: GuiGraphics, replay: HistoryBrowseReplayState?, rows: List<TransactionRow>, mouseX: Int, mouseY: Int): List<Component>? {
        val bounds = layout.list
        replay ?: return null
        val contentHeight = rows.size * ROW_STEP
        if (playback.following && replay.step > 0) {
            val rowTop = (replay.step - 1) * ROW_STEP
            if (rowTop < listScroll || rowTop + ROW_HEIGHT > listScroll + bounds.height) listScroll = (rowTop - (bounds.height - ROW_HEIGHT) / 2).toDouble()
        }
        listScroll = listScroll.coerceIn(0.0, (contentHeight - bounds.height).coerceAtLeast(0).toDouble())
        var tooltip: List<Component>? = null
        context.enableScissor(bounds.x, bounds.y, bounds.right, bounds.bottom)
        rows.forEachIndexed { index, row ->
            val top = bounds.y + index * ROW_STEP - listScroll.toInt()
            if (top + ROW_HEIGHT < bounds.y || top > bounds.bottom) return@forEachIndexed
            val step = index + 1
            val hovered = mouseX >= bounds.x && mouseX < bounds.right && mouseY >= maxOf(top, bounds.y) && mouseY < minOf(top + ROW_HEIGHT, bounds.bottom)
            context.fill(bounds.x, top, bounds.right, top + ROW_HEIGHT, if (hovered) CardBackground.NEUTRAL_HOVER_COLOR else CardBackground.NEUTRAL_COLOR)
            val future = step > replay.step
            context.drawString(font, font.substrByWidth(row.heading, bounds.width - ROW_PADDING * 2).let(Language.getInstance()::getVisualOrder), bounds.x + ROW_PADDING, top + ROW_PADDING, if (future) FUTURE_HEADING_COLOR else ACCENT_COLOR)
            val tileSpace = if (row.tile != null) SMALL_TILE_WIDTH + TILE_GAP else 0
            val summary = row.summary?.let { font.substrByWidth(it, (bounds.width - ROW_PADDING * 2 - tileSpace).coerceAtLeast(1)) }
            val summaryY = top + ROW_PADDING + LINE_HEIGHT
            summary?.let { context.drawString(font, Language.getInstance().getVisualOrder(it), bounds.x + ROW_PADDING, summaryY, if (future) FUTURE_TEXT_COLOR else TEXT_COLOR) }
            row.tile?.let { tile ->
                val tileX = bounds.x + ROW_PADDING + (summary?.let(font::width) ?: 0) + TILE_GAP
                renderTile(context, tile, tileX, summaryY - (SMALL_TILE_HEIGHT - FormScreenLayout.VISIBLE_TEXT_HEIGHT) / 2)
            }
            row.marker?.let { context.fill(bounds.right - MARKER_STRIP_WIDTH, top, bounds.right, top + ROW_HEIGHT, OPAQUE or markerColor(it)) }
            if (step == replay.step) context.renderOutline(bounds.x, top, bounds.width, ROW_HEIGHT, CURRENT_OUTLINE)
            if (hovered) tooltip = listOfNotNull(row.heading, row.summary)
        }
        context.disableScissor()
        VerticalScrollbar.render(context, layout.listScrollbarX, listScrollbar(contentHeight))
        return tooltip
    }

    /**
     * 交易列表捲軸的位置。
     *
     * @param contentHeight 全部交易列的高度。
     * @return 捲軸幾何。
     */
    private fun listScrollbar(contentHeight: Int): ScrollbarLayout = ScrollbarLayout(
        trackTop = layout.list.y,
        trackBottom = layout.list.bottom,
        itemCount = contentHeight,
        visibleItemCount = layout.list.height,
        scrollIndex = listScroll.toInt(),
        minimumThumbHeight = VerticalScrollbar.MINIMUM_THUMB_HEIGHT,
    )

    /**
     * 繪製視角與手牌選擇器左側的標籤。
     *
     * @param context 繪製上下文。
     */
    private fun renderSelectorLabels(context: GuiGraphics) {
        listOf(MinecraftHistoryScreenKeys.REPLAY_VIEW to layout.viewSelector, MinecraftHistoryScreenKeys.REPLAY_HAND to layout.handSelector).forEach { (key, bounds) ->
            val label = Component.translatable(key)
            val y = bounds.y + (bounds.height - FormScreenLayout.VISIBLE_TEXT_HEIGHT) / 2
            context.drawString(font, label, bounds.x - layout.selectorLabelWidth - LABEL_GAP, y, MUTED_COLOR)
        }
    }

    /**
     * 繪製速度選擇器左側的標籤，隨控制區淡出。
     *
     * @param context 繪製上下文。
     */
    private fun renderSpeedLabel(context: GuiGraphics) {
        val speed = layout.speedSelector()
        val speedLabel = Component.translatable(MinecraftHistoryScreenKeys.REPLAY_SPEED)
        context.drawString(font, speedLabel, speed.x - LABEL_GAP - font.width(speedLabel), speed.y + (speed.height - FormScreenLayout.VISIBLE_TEXT_HEIGHT) / 2, faded(MUTED_COLOR))
    }

    /**
     * 繪製進度條、已播放的部分、標記與目前位置。
     *
     * @param context 繪製上下文。
     * @param replay 目前回放。
     * @param rows 整局的交易列。
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @return 游標指著進度條時那一步的說明；沒有時為 null。
     */
    private fun renderProgress(context: GuiGraphics, replay: HistoryBrowseReplayState?, rows: List<TransactionRow>, mouseX: Int, mouseY: Int): List<Component>? {
        val bounds = layout.progress
        val trackTop = bounds.y + (bounds.height - TRACK_HEIGHT) / 2
        context.fill(bounds.x, trackTop, bounds.right, trackTop + TRACK_HEIGHT, faded(TRACK_COLOR))
        val stepCount = replay?.stepCount ?: return null
        val lastStep = (stepCount - 1).coerceAtLeast(1)
        val currentX = bounds.x + bounds.width * replay.step / lastStep
        context.fill(bounds.x, trackTop, currentX, trackTop + TRACK_HEIGHT, faded(PLAYED_COLOR))
        rows.forEachIndexed { index, row ->
            val marker = row.marker ?: return@forEachIndexed
            val x = bounds.x + bounds.width * (index + 1) / lastStep
            // 鳴牌最常出現，只畫在軌道上的短刻度；宣告與和牌畫滿進度條的高度，不靠顏色也分得出來。
            val (top, bottom) = if (marker == GameActionReplayMarker.MELD) {
                trackTop - SHORT_MARKER_OVERHANG to trackTop + TRACK_HEIGHT + SHORT_MARKER_OVERHANG
            } else {
                bounds.y to bounds.bottom
            }
            context.fill(x - 1, top, x + 1, bottom, faded(markerColor(marker)))
        }
        context.fill(currentX - KNOB_HALF_WIDTH, bounds.y, currentX + KNOB_HALF_WIDTH, bounds.bottom, faded(KNOB_BORDER))
        context.fill(currentX - KNOB_HALF_WIDTH + 1, bounds.y + 1, currentX + KNOB_HALF_WIDTH - 1, bounds.bottom - 1, faded(KNOB_COLOR))
        if (mouseY < bounds.y - PROGRESS_HIT_MARGIN || mouseY >= bounds.bottom + PROGRESS_HIT_MARGIN || mouseX < bounds.x || mouseX > bounds.right) return null
        val step = stepAt(mouseX.toDouble(), stepCount)
        val row = rows.getOrNull(step - 1)
        return if (step == 0) {
            listOf(Component.translatable(MinecraftHistoryScreenKeys.STATE_INITIAL))
        } else {
            listOfNotNull(row?.heading ?: Component.translatable(MinecraftHistoryScreenKeys.REPLAY_CAPTION, step), row?.summary)
        }
    }

    /**
     * 繪製控制列上的目前交易與總交易數。
     *
     * @param context 繪製上下文。
     * @param replay 目前回放。
     */
    private fun renderCounter(context: GuiGraphics, replay: HistoryBrowseReplayState?) {
        val stepCount = replay?.stepCount ?: return
        val text = Component.translatable(MinecraftHistoryScreenKeys.REPLAY_COUNTER, replay.step, stepCount - 1)
        val right = layout.speedSelector().x - LABEL_GAP * 2 - font.width(Component.translatable(MinecraftHistoryScreenKeys.REPLAY_SPEED))
        val available = (right - layout.counterX).coerceAtLeast(0)
        if (available <= 0) return
        val line = font.substrByWidth(text, available)
        context.drawString(font, Language.getInstance().getVisualOrder(line), layout.counterX, layout.controls.y + (layout.controls.height - FormScreenLayout.VISIBLE_TEXT_HEIGHT) / 2, faded(TEXT_COLOR))
    }

    /**
     * 在底部中間格繪製局數與總局數。
     *
     * @param context 繪製上下文。
     * @param replay 目前回放。
     */
    private fun renderRoundNumber(context: GuiGraphics, replay: HistoryBrowseReplayState?) {
        val bounds = HistoryPageFrame(windowWidth = width, windowHeight = height).footerSlot(1)
        HistoryPaging.renderRange(
            context = context,
            textRenderer = font,
            bounds = bounds,
            primary = Component.translatable(MinecraftHistoryScreenKeys.ROUND_NUMBER, replay?.roundNumber ?: "—"),
            secondary = Component.translatable(MinecraftHistoryScreenKeys.REPLAY_ROUND_TOTAL, roundNumbers().size),
        )
    }

    /**
     * 繪製一張小牌。
     *
     * @param context 繪製上下文。
     * @param tile 牌面。
     * @param x 左界。
     * @param y 上界。
     */
    private fun renderTile(context: GuiGraphics, tile: TileFaceViewDto, x: Int, y: Int) {
        val asset = tile.toHistoryAssetKey(registry = session.tileAssets, registries = session.networkRegistries)
        session.tileFaces.renderGui(context, asset, x, y, SMALL_TILE_WIDTH, SMALL_TILE_HEIGHT, appearance = session.tileAppearance())
    }

    /**
     * 整理整局的交易列；事件頁沒變時沿用上次的結果。
     *
     * @param replay 目前回放。
     * @return 依交易順序排列的列。
     */
    private fun rows(replay: HistoryBrowseReplayState): List<TransactionRow> {
        rowsCache?.takeIf { it.first == replay.eventPages }?.let { return it.second }
        val ruleId = session.controller.state.value.summary?.detail?.summary?.ruleId
        val markers = session.eventPresenter.replayMarkers(ruleId, replay.eventPages.flatMap { it.transactions })
        val built = replay.eventPages.flatMap { page ->
            session.eventPresenter.present(page, ruleId).transactions.map { transaction -> page.identity to transaction }
        }.mapIndexed { index, (identity, transaction) ->
            val fact = transaction.facts.firstOrNull { it.actorSeat != null } ?: transaction.facts.firstOrNull()
            val summary = fact?.let { found -> found.actorSeat?.let { actorText(identity, it).copy().append(" ").append(found.text) } ?: found.text }
            TransactionRow(
                heading = Component.translatable(MinecraftHistoryScreenKeys.ROUND_TRANSACTION, transaction.index + 1, HistoryScreenText.timeOfDay(transaction.occurredAtEpochMillis)),
                summary = summary,
                tile = fact?.let { it.directTiles.firstOrNull() ?: it.revealedTiles.firstOrNull() },
                marker = markers[index],
            )
        }
        rowsCache = replay.eventPages to built
        return built
    }

    /**
     * 取得某一步那筆交易的標記種類。
     *
     * @param replay 目前回放。
     * @param step 步數；0 是初始牌面，沒有交易。
     * @return 標記種類；沒有標記或事件還沒讀到時為 null。
     */
    private fun markerAt(replay: HistoryBrowseReplayState, step: Int): GameActionReplayMarker? = if (step <= 0) null else rows(replay).getOrNull(step - 1)?.marker

    /**
     * 取得玩家名稱或從 1 開始的座位名稱。
     *
     * @param identity 初始玩家身分。
     * @param seat 初始座位。
     * @return 玩家名稱。
     */
    private fun actorText(identity: HistoryReplayIdentityDto, seat: Int): Component {
        val summary = session.controller.state.value.summary?.detail?.summary
        val id = identity.players.firstOrNull { it.initialSeatIndex == seat }?.playerId
        val participant = id?.let { playerId -> summary?.participants?.firstOrNull { it.playerId.equals(playerId, ignoreCase = true) } }
        return participant?.let { session.participants.name(it, summary?.participants.orEmpty(), summary?.matchId) } ?: Component.translatable(MinecraftHistoryScreenKeys.ROUND_SEAT, seat + 1)
    }

    /**
     * 視角選擇器顯示的文字。
     *
     * @param state 目前牌面；還沒取得時為 null。
     * @param seat 觀看的玩家初始座位；自由視角為 null。
     * @return 文字。
     */
    private fun viewText(state: HistoryRoundStateDto?, seat: Int?): Component {
        val player = state?.players?.firstOrNull { it.initialSeatIndex == seat } ?: return Component.translatable(MinecraftHistoryScreenKeys.REPLAY_VIEW_FREE)
        return Component.translatable(MinecraftHistoryScreenKeys.REPLAY_VIEW_SEAT, windText(player.seatWind), actorText(state.identity, player.initialSeatIndex))
    }

    /**
     * 座位風的名稱。
     *
     * @param wind 座位風。
     * @return 名稱。
     */
    private fun windText(wind: WindDto): Component = Component.translatable(
        when (wind) {
            WindDto.EAST -> MinecraftHistoryScreenKeys.REPLAY_WIND_EAST
            WindDto.SOUTH -> MinecraftHistoryScreenKeys.REPLAY_WIND_SOUTH
            WindDto.WEST -> MinecraftHistoryScreenKeys.REPLAY_WIND_WEST
            WindDto.NORTH -> MinecraftHistoryScreenKeys.REPLAY_WIND_NORTH
        },
    )

    /**
     * 速度選擇器顯示的文字。
     *
     * @param speed 速度。
     * @return 文字。
     */
    private fun speedText(speed: HistoryReplaySpeed): Component = Component.translatable(
        MinecraftHistoryScreenKeys.REPLAY_SPEED_VALUE,
        speed.multiplier.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() },
    )

    /**
     * 視角清單的選項：自由，接著依初始座位排列的每一家。
     *
     * @param state 目前牌面；還沒取得時只有自由。
     * @return 觀看的玩家初始座位；自由為 null。
     */
    private fun viewSeats(state: HistoryRoundStateDto?): List<Int?> = listOf<Int?>(null) + state?.players?.sortedBy { it.initialSeatIndex }?.map { it.initialSeatIndex }.orEmpty()

    /** 展開或收合視角清單。 */
    private fun toggleViewList() {
        val state = session.controller.state.value.replay?.state ?: return
        val seats = viewSeats(state)
        dropdown.toggle(
            key = VIEW_KEY,
            anchor = viewButton,
            options = seats.map { DropdownOption(label = viewText(state, it)) },
            selectedIndex = seats.indexOf(viewSeat).takeIf { it >= 0 },
        ) { index -> viewSeat = seats[index] }
    }

    /** 展開或收合手牌清單。 */
    private fun toggleHandList() {
        dropdown.toggle(
            key = HAND_KEY,
            anchor = handButton,
            options = HAND_KEYS.map { DropdownOption(label = Component.translatable(it)) },
            selectedIndex = if (handsOpen) 1 else 0,
        ) { index ->
            handsOpen = index == 1
            animation = null
        }
    }

    /** 展開或收合速度清單。 */
    private fun toggleSpeedList() {
        val speeds = HistoryReplaySpeed.entries
        dropdown.toggle(
            key = SPEED_KEY,
            anchor = speedButton,
            options = speeds.map { DropdownOption(label = speedText(it)) },
            selectedIndex = speeds.indexOf(playback.speed),
        ) { index -> playback.speed = speeds[index] }
    }

    /** 播放或暫停。 */
    private fun togglePlay() {
        val replay = session.controller.state.value.replay ?: return
        val stepCount = replay.stepCount ?: return
        if (playback.playing) {
            playback.pause()
            return
        }
        lastActivity = measuringTimeNow()
        val start = playback.play(step = replay.step, stepCount = stepCount, now = lastActivity)
        if (start != replay.step) session.controller.replayTo(start)
    }

    /** 回到初始牌面。 */
    private fun first() = moveTo(0)

    /** 上一筆交易。 */
    private fun previous() = moveBy(-1)

    /** 下一筆交易。 */
    private fun next() = moveBy(1)

    /** 最後一筆交易。 */
    private fun last() {
        val stepCount = session.controller.state.value.replay?.stepCount ?: return
        moveTo(stepCount - 1)
    }

    /**
     * 相對目前步數移動。
     *
     * @param delta 移動的步數。
     */
    private fun moveBy(delta: Int) {
        val replay = session.controller.state.value.replay ?: return
        moveTo(replay.step + delta)
    }

    /**
     * 移到指定步數；播放中會重新計時，交易列表恢復跟隨。
     *
     * @param step 新的步數。
     */
    private fun moveTo(step: Int) {
        val stepCount = session.controller.state.value.replay?.stepCount ?: return
        if (session.controller.replayTo(step.coerceIn(0, stepCount - 1))) playback.moved(measuringTimeNow())
    }

    /**
     * 換到前一局或後一局。
     *
     * @param direction 往前為 -1，往後為 1。
     */
    private fun changeRound(direction: Int) {
        val rounds = roundNumbers()
        val current = rounds.indexOf(session.controller.state.value.replay?.roundNumber)
        val target = rounds.getOrNull(current + direction) ?: return
        if (current < 0 || !session.controller.replayRound(target)) return
        playback.roundChanged()
        listScroll = 0.0
    }

    /**
     * 摘要列出的局序號。
     *
     * @return 依順序排列的局序號。
     */
    private fun roundNumbers(): List<Int> = session.controller.state.value.summary?.detail?.rounds?.map { it.roundNumber }.orEmpty()

    /**
     * 游標水平位置對應的步數。
     *
     * @param mouseX 游標水平座標。
     * @param stepCount 這一局的總步數。
     * @return 步數。
     */
    private fun stepAt(mouseX: Double, stepCount: Int): Int {
        val bounds = layout.progress
        val fraction = ((mouseX - bounds.x) / bounds.width.coerceAtLeast(1)).coerceIn(0.0, 1.0)
        return (fraction * (stepCount - 1)).roundToInt()
    }

    /**
     * 處理下拉清單、進度條、交易列表與場景的點擊。
     *
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @param button 按鍵。
     * @return 是否處理。
     */
    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (dropdown.mouseClicked(font, height, mouseX, mouseY, button)) return true
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && zoom > 1f && isOverScene(mouseX, mouseY)) {
            draggingPan = true
            return true
        }
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button)
        if (super.mouseClicked(mouseX, mouseY, button)) return true
        if (settlementCard.mouseClicked(mouseX, mouseY)) return true
        val replay = session.controller.state.value.replay ?: return false
        val progress = layout.progress
        val stepCount = replay.stepCount
        if (stepCount != null && controlsShown() && mouseX >= progress.x && mouseX <= progress.right && mouseY >= progress.y - PROGRESS_HIT_MARGIN && mouseY < progress.bottom + PROGRESS_HIT_MARGIN) {
            draggingProgress = true
            moveTo(stepAt(mouseX, stepCount))
            return true
        }
        if (!layout.fullscreen && (layout.wide || listOpen)) {
            val list = layout.list
            val contentHeight = rows(replay).size * ROW_STEP
            val bar = listScrollbar(contentHeight)
            if (bar.maximumScroll > 0 && mouseX >= layout.listScrollbarX - 1 && mouseX < layout.listScrollbarX + VerticalScrollbar.WIDTH + 1 && mouseY >= list.y && mouseY < list.bottom) {
                draggingListBar = true
                listGrabOffset = bar.grabOffset(mouseY)
                dragListBar(mouseY, contentHeight)
                return true
            }
            if (mouseX >= list.x && mouseX < list.right && mouseY >= list.y && mouseY < list.bottom) {
                val index = ((mouseY - list.y + listScroll) / ROW_STEP).toInt()
                if (index in rows(replay).indices) moveTo(index + 1)
                return true
            }
        }
        if (viewSeat == null && isOverScene(mouseX, mouseY)) {
            draggingScene = true
            return true
        }
        return false
    }

    /**
     * 游標是否在場景範圍內。
     *
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @return 在場景內時為 true。
     */
    private fun isOverScene(mouseX: Double, mouseY: Double): Boolean {
        val scene = layout.scene
        return mouseX >= scene.x && mouseX < scene.right && mouseY >= scene.y && mouseY < scene.bottom && !settlementCard.contains(mouseX, mouseY)
    }

    /**
     * 這一局有結算之後，在場景中間偏下畫出到目前這一步為止最近的一筆結算；預設收起，場景下方放一顆展開的按鈕。牌的動畫
     * 播放中不畫。
     *
     * @param context 繪製上下文。
     * @param replay 目前回放。
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @return 游標指著的內容提示；沒有時為 null。
     */
    private fun renderSettlement(context: GuiGraphics, replay: HistoryBrowseReplayState?, mouseX: Int, mouseY: Int): List<Component>? {
        val scene = layout.scene
        val bottom = layout.controlBar?.y ?: scene.bottom
        val showing = animation == null && settlementCard.available(replay)
        val tooltip = if (showing) settlementCard.render(context, scene, bottom, replay, mouseX, mouseY) else null
        val card = settlementCard.bounds.takeIf { showing }
        settlementHideButton.visible = card != null
        card?.let {
            settlementHideButton.x = it.right - SETTLEMENT_BUTTON_MARGIN - settlementHideButton.width
            settlementHideButton.y = it.y + SETTLEMENT_BUTTON_MARGIN
        }
        settlementShowButton.visible = showing && settlementCard.collapsed
        settlementShowButton.x = scene.x + (scene.width - settlementShowButton.width) / 2
        settlementShowButton.y = bottom - SETTLEMENT_BUTTON_MARGIN * 2 - settlementShowButton.height
        return tooltip
    }

    /**
     * 依 replay 身分對應摘要玩家，供結算卡片畫頭像。
     *
     * @param identity 初始玩家身分。
     * @param seat 初始座位。
     * @return 對應的參與者；沒有時為 null。
     */
    private fun participant(identity: HistoryReplayIdentityDto, seat: Int): HistoryParticipantSummaryDto? {
        val id = identity.players.firstOrNull { it.initialSeatIndex == seat }?.playerId ?: return null
        return session.controller.state.value.summary?.detail?.summary?.participants?.firstOrNull { it.playerId.equals(id, ignoreCase = true) }
    }

    /**
     * 調整放大倍數；回到原本大小時平移歸零，並讓平移不超出放大後多出來的範圍。
     *
     * @param value 新的放大倍數，超出範圍時夾到最小或最大倍數。
     */
    private fun setZoom(value: Float) {
        zoom = value.coerceIn(MIN_ZOOM, MAX_ZOOM)
        clampPan()
    }

    /** 讓平移不超出放大後多出來的範圍。 */
    private fun clampPan() {
        val maxX = layout.scene.width * (zoom - 1f) / 2f
        val maxY = layout.scene.height * (zoom - 1f) / 2f
        panX = panX.coerceIn(-maxX, maxX)
        panY = panY.coerceIn(-maxY, maxY)
    }

    /**
     * 拖曳進度條、交易列表捲軸或轉動場景。
     *
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @param button 按鍵。
     * @param deltaX 水平位移。
     * @param deltaY 垂直位移。
     * @return 是否處理。
     */
    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        if (settlementCard.mouseDragged(mouseY)) return true
        val replay = session.controller.state.value.replay
        when {
            draggingProgress -> replay?.stepCount?.let { stepCount ->
                val step = stepAt(mouseX, stepCount)
                if (step != replay.step) moveTo(step)
            }
            draggingListBar -> replay?.let {
                dragListBar(mouseY, rows(it).size * ROW_STEP)
                playback.scrolled()
            }
            draggingScene -> yawOffset += deltaX.toFloat() * DRAG_DEGREES_PER_PIXEL
            draggingPan -> {
                panX += deltaX.toFloat()
                panY += deltaY.toFloat()
                clampPan()
            }
            else -> return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
        }
        return true
    }

    /**
     * 依捲軸拖曳位置更新交易列表的捲動。
     *
     * @param mouseY 游標垂直座標。
     * @param contentHeight 全部交易列的高度。
     */
    private fun dragListBar(mouseY: Double, contentHeight: Int) {
        listScroll = listScrollbar(contentHeight).scrollIndexFor(mouseY, listGrabOffset).toDouble()
        playback.scrolled()
    }

    /**
     * 結束拖曳。
     *
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @param button 按鍵。
     * @return 是否處理。
     */
    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (settlementCard.mouseReleased()) return true
        val handled = draggingProgress || draggingListBar || draggingScene || draggingPan
        draggingProgress = false
        draggingListBar = false
        draggingScene = false
        draggingPan = false
        return handled || super.mouseReleased(mouseX, mouseY, button)
    }

    /**
     * 捲動交易列表；玩家自己捲動後列表停止跟隨。
     *
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @param amount 捲動量。
     * @return 是否處理。
     */
    override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        if (dropdown.mouseScrolled(font, height, amount)) return true
        val list = layout.list
        if (!layout.fullscreen && (layout.wide || listOpen) && mouseX >= list.x && mouseX < layout.listScrollbarX + VerticalScrollbar.WIDTH && mouseY >= list.y && mouseY < list.bottom) {
            listScroll -= amount * ROW_STEP
            playback.scrolled()
            return true
        }
        if (settlementCard.mouseScrolled(mouseX, mouseY, amount)) return true
        if (isOverScene(mouseX, mouseY)) {
            setZoom(zoom * ZOOM_STEP.pow(amount.toFloat()))
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, amount)
    }

    /**
     * 空白鍵播放或暫停，左右方向鍵上一筆、下一筆，Home、End 第一筆、最後一筆，F 切換全螢幕，全螢幕時 Esc 只結束
     * 全螢幕；先於按鈕焦點處理。
     *
     * @param keyCode 按鍵碼。
     * @param scanCode 掃描碼。
     * @param modifiers 修飾鍵。
     * @return 是否處理。
     */
    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (dropdown.keyPressed(font, height, keyCode)) return true
        when (keyCode) {
            GLFW.GLFW_KEY_SPACE -> togglePlay()
            GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_HOME, GLFW.GLFW_KEY_END -> {
                // 換步時讓全螢幕的控制區短暫出現，看得到進度。
                lastActivity = measuringTimeNow()
                when (keyCode) {
                    GLFW.GLFW_KEY_LEFT -> previous()
                    GLFW.GLFW_KEY_RIGHT -> next()
                    GLFW.GLFW_KEY_HOME -> first()
                    else -> last()
                }
            }
            GLFW.GLFW_KEY_F -> toggleFullscreen()
            GLFW.GLFW_KEY_ESCAPE -> if (fullscreen) toggleFullscreen() else return super.keyPressed(keyCode, scanCode, modifiers)
            else -> return super.keyPressed(keyCode, scanCode, modifiers)
        }
        return true
    }

    /**
     * 決定場景內容的條件；相同時沿用上次建立的場景。
     *
     * @property state 牌面。
     * @property arranged 擺好的牌桌。
     * @property viewSeat 觀看的玩家初始座位；自由視角為 null。
     * @property figures 內建機器人外觀的角色。
     * @property entities 以原版繪製器畫出的模型。
     */
    private data class SceneKey(
        val state: HistoryRoundStateDto,
        val arranged: Map<Int, ArrangedTile>,
        val viewSeat: Int?,
        val figures: List<GuideSceneFigure>,
        val entities: List<GuideSceneEntity>,
    )

    /**
     * 隔離角色預覽模型的繪製失敗；失敗後該角色在等待重試期間改畫內建機器人。
     *
     * @property previews 角色預覽模型來源。
     * @property character 模型所屬的外觀。
     */
    private data class CharacterModelGuard(
        private val previews: ClientAiCharacterPreviews,
        private val character: AiCharacterSnapshot,
    ) : GuideSceneRenderGuard {
        override fun run(draw: () -> Unit) {
            previews.guardSceneModel(character, draw)
        }
    }

    /**
     * 決定牌桌擺法的條件；牌面、規則模組與事件頁以同一個物件比對，不逐項比較內容，每一幀查詢時不必走過整份牌面。
     *
     * @property state 牌面。
     * @property module 規則模組。
     * @property handsOpen 手牌是否面朝上攤開。
     * @property eventPages 已讀到的事件頁，決定哪些贏家攤牌；不在回放時為 null。
     */
    private class ArrangementKey(
        val state: HistoryRoundStateDto,
        val module: MahjongRuleModule<*>,
        val handsOpen: Boolean,
        val eventPages: List<HistoryRoundEventsDto>?,
    ) {
        /**
         * 是否為同樣的擺法條件。
         *
         * @param other 比較對象。
         * @return 牌面、規則模組與事件頁都是同一個物件，手牌選擇也相同時為 true。
         */
        override fun equals(other: Any?): Boolean = other is ArrangementKey &&
            other.state === state &&
            other.module === module &&
            other.handsOpen == handsOpen &&
            other.eventPages === eventPages

        /**
         * 依物件身分計算的雜湊值。
         *
         * @return 雜湊值。
         */
        override fun hashCode(): Int = Objects.hash(System.identityHashCode(state), System.identityHashCode(module), handsOpen, System.identityHashCode(eventPages))
    }

    /**
     * 交易列表的一列。
     *
     * @property heading 交易編號與時間。
     * @property summary 這筆交易做了什麼；沒有內容時為 null。
     * @property tile 跟在說明後面的牌；沒有時為 null。
     * @property marker 回放標記種類；不標記時為 null。
     */
    private data class TransactionRow(
        val heading: Component,
        val summary: Component?,
        val tile: TileFaceViewDto?,
        val marker: GameActionReplayMarker?,
    )

    /** 版面尺寸與配色。 */
    private companion object {
        /** 視角清單的識別值。 */
        const val VIEW_KEY = "view"

        /** 手牌清單的識別值。 */
        const val HAND_KEY = "hand"

        /** 手牌清單的選項：立著、攤開。 */
        val HAND_KEYS = listOf(MinecraftHistoryScreenKeys.REPLAY_HAND_STANDING, MinecraftHistoryScreenKeys.REPLAY_HAND_OPEN)

        /** 速度清單的識別值。 */
        const val SPEED_KEY = "speed"

        /** 標題與局數之間的分隔。 */
        const val TITLE_SEPARATOR = " · "

        /** 重試按鈕寬度。 */
        const val RETRY_WIDTH = 80

        /** 文字列高。 */
        const val LINE_HEIGHT = 10

        /** 說明文字的內距。 */
        const val NOTICE_PADDING = 6

        /** 交易列高度。 */
        const val ROW_HEIGHT = 24

        /** 交易列的間距與高度合計。 */
        const val ROW_STEP = 26

        /** 交易列的內距。 */
        const val ROW_PADDING = 3

        /** 交易列右側標記色條的寬度。 */
        const val MARKER_STRIP_WIDTH = 2

        /** 小牌寬度。 */
        const val SMALL_TILE_WIDTH = 8

        /** 小牌高度。 */
        const val SMALL_TILE_HEIGHT = 11

        /** 文字與小牌之間的距離。 */
        const val TILE_GAP = 3

        /** 選擇器標籤與選擇器之間的距離。 */
        const val LABEL_GAP = 4

        /** 進度條軌道的高度。 */
        const val TRACK_HEIGHT = 2

        /** 鳴牌短刻度超出軌道上下緣的像素。 */
        const val SHORT_MARKER_OVERHANG = 1

        /** 目前位置標示的半寬。 */
        const val KNOB_HALF_WIDTH = 3

        /** 結算卡片收起與顯示按鈕的寬度。 */
        const val SETTLEMENT_BUTTON_WIDTH = 44

        /** 結算卡片收起與顯示按鈕的高度。 */
        const val SETTLEMENT_BUTTON_HEIGHT = 14

        /** 結算按鈕與卡片邊緣的距離。 */
        const val SETTLEMENT_BUTTON_MARGIN = 4

        /** 進度條上下可點擊的額外範圍。 */
        const val PROGRESS_HIT_MARGIN = 3

        /** 還不知道玩家名稱時，模型 profile 使用的名稱；名牌不會畫出，只用來建立模型。 */
        const val UNKNOWN_PROFILE_NAME = "player"

        /** 一秒的遊戲 tick 數，動畫依此換算時間。 */
        const val TICKS_PER_SECOND = 20.0

        /** 留著的擺好牌桌份數：動畫前後兩步。 */
        const val ARRANGEMENT_CACHE_SIZE = 2

        /** 全螢幕時滑鼠沒動多久後控制區開始淡出。 */
        val CONTROLS_HIDE_DELAY: Duration = 2.seconds

        /** 控制區淡出所需的時間。 */
        val CONTROLS_FADE: Duration = 250.milliseconds

        /** 控制區看得見、可以操作的最低不透明度。 */
        const val MIN_VISIBLE_ALPHA = 0.05f

        /** ARGB 顏色中 alpha 位元的位移。 */
        const val ALPHA_SHIFT = 24

        /** 完全不透明的 alpha 值。 */
        const val FULL_ALPHA = 0xFF

        /** ARGB 顏色中 RGB 的位元。 */
        const val RGB_MASK = 0xFFFFFF

        /** 全螢幕控制區的底色。 */
        const val CONTROL_BAR_BACKGROUND = 0x99000000.toInt()

        /** 最小放大倍數：剛好放得下桌椅。 */
        const val MIN_ZOOM = 1f

        /** 最大放大倍數。 */
        const val MAX_ZOOM = 4f

        /** 滾輪一格放大的倍數。 */
        const val ZOOM_STEP = 1.15f

        /** 拖曳一個像素轉動的角度。 */
        const val DRAG_DEGREES_PER_PIXEL = 0.5f

        /** 半圈的角度。 */
        const val HALF_TURN = 180f

        /** 某一家視角的俯視角度。 */
        const val SEAT_VIEW_PITCH = 45f

        /** 場景底色：立體場景直接疊在背景上時邊界不清楚，加一層底讓場景範圍分明。 */
        const val SCENE_BACKGROUND = 0x40000000

        /** 一般文字。 */
        const val TEXT_COLOR = 0xFFFFFF

        /** 次要文字。 */
        const val MUTED_COLOR = 0xBBBBBB

        /** 強調文字。 */
        const val ACCENT_COLOR = 0x8ED5DF

        /** 還沒播到的交易標題。 */
        const val FUTURE_HEADING_COLOR = 0x6F8F94

        /** 還沒播到的交易說明。 */
        const val FUTURE_TEXT_COLOR = 0x9A9A9A

        /** 鳴牌標記：最常出現，用低彩度的青灰色，不搶過宣告與和牌。 */
        const val MELD_COLOR = 0x7FA39A

        /** 宣告標記。 */
        const val DECLARATION_COLOR = 0x55AAFF

        /** 和牌標記。 */
        const val WIN_COLOR = 0xFFD54F

        /** 將 RGB 配色轉為不透明 ARGB 的 alpha 位元。 */
        const val OPAQUE = 0xFF000000.toInt()

        /** 目前交易的外框。 */
        const val CURRENT_OUTLINE = 0xFFFFFFFF.toInt()

        /** 進度條軌道。 */
        const val TRACK_COLOR = 0xCC505050.toInt()

        /** 已播放的部分。 */
        const val PLAYED_COLOR = 0xFFD0D0D0.toInt()

        /** 目前位置標示的外框。 */
        const val KNOB_BORDER = 0xFF000000.toInt()

        /** 目前位置標示。 */
        const val KNOB_COLOR = 0xFFFFFFFF.toInt()

        /**
         * 回放標記的顏色。
         *
         * @param marker 標記種類。
         * @return RGB 顏色。
         */
        fun markerColor(marker: GameActionReplayMarker): Int = when (marker) {
            GameActionReplayMarker.MELD -> MELD_COLOR
            GameActionReplayMarker.DECLARATION -> DECLARATION_COLOR
            GameActionReplayMarker.WIN -> WIN_COLOR
        }
    }
}
