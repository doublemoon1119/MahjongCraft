package com.doublemoon1119.mahjongcraft.platform.fabric.client.game

import com.doublemoon1119.mahjongcraft.flow.client.game.ClientDecisionTimerStateStore
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PlayerDecisionPhase
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.DecisionTileOrientationDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.PlayerDecisionActionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.PlayerDecisionPromptDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.PlayerDecisionSelectionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.PlayerDecisionSelectionKindDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.PlayerDecisionSubmissionResultDto
import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.MahjongClientConfigStore
import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.MahjongHudLayoutEditorScreen
import com.doublemoon1119.mahjongcraft.platform.fabric.client.render.MahjongTileFaceRenderer
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileSelectionConfirmEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.network.MahjongChannels
import kotlinx.serialization.json.Json
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.text.Text
import net.minecraft.util.ActionResult
import net.minecraft.util.hit.EntityHitResult
import net.minecraft.util.hit.HitResult
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import kotlin.math.ceil
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

/** 管理操作介面、精簡倒數與打牌分析 HUD 的共用客戶端生命週期。 */
@Single
class PlayerDecisionHudController(
    private val timerStore: ClientDecisionTimerStateStore,
    private val promptStore: ClientDecisionPromptStore,
    private val tileFaceRenderer: MahjongTileFaceRenderer,
    private val configStore: MahjongClientConfigStore,
    val decisionTexts: DecisionTextResolver,
    @Provided private val json: Json,
) {
    /** 玩家以 Esc 暫時收起的 decision key。 */
    private var dismissedDecisionKey: String? = null

    /** 最近已自動開啟過的 decision key。 */
    private var openedDecisionKey: String? = null

    /** 以實體手牌選牌時的本機狀態。 */
    private val tileSelection = DecisionTileSelectionState()

    /** 尚待伺服器 ACK 或後續權威 timer 更新確認的最終提交。 */
    private val submissionTracker = DecisionSubmissionTracker()

    /** 註冊 client tick、實體互動及聊天層前方的 HUD renderer bridge。 */
    fun registerEvents() {
        activeController = this
        ClientTickEvents.END_CLIENT_TICK.register(::tick)
        UseEntityCallback.EVENT.register { _, world, _, entity, _ ->
            if (world.isClient && entity is MahjongTileEntity && entity.managedByGame) {
                if (submissionTracker.isPending()) {
                    ActionResult.SUCCESS
                } else if (toggleTile(entity)) {
                    ActionResult.SUCCESS
                } else if (canUsePhysicalTileDirectly()) {
                    ActionResult.PASS
                } else if (promptStore.prompt?.isInteractive == true) {
                    reopen()
                    ActionResult.SUCCESS
                } else {
                    ActionResult.PASS
                }
            } else if (world.isClient && entity is MahjongTileSelectionConfirmEntity) {
                if (confirmTileSelection(entity)) ActionResult.SUCCESS else ActionResult.PASS
            } else {
                ActionResult.PASS
            }
        }
    }

    /** 右鍵任意自己的實體手牌時重新開啟仍有效的操作介面。 */
    fun reopen() {
        val client = MinecraftClient.getInstance()
        val prompt = promptStore.prompt ?: return
        if (!prompt.isInteractive || client.currentScreen != null || submissionTracker.isPending()) return
        dismissedDecisionKey = null
        client.setScreen(PlayerDecisionScreen(prompt, timerStore.state?.phase?.isReaction == true, this))
    }

    /** 操作介面被 Esc 收起時保留 prompt，改顯示等待提醒。 */
    fun dismiss(decisionKey: String) {
        dismissedDecisionKey = decisionKey
    }

    /** 讓操作 screen 使用與其他 HUD／showcase 相同的牌面及角落標籤 renderer。 */
    fun renderTileFace(
        context: DrawContext,
        assetKey: String,
        x: Int,
        y: Int,
        tileWidth: Int,
        tileHeight: Int,
        orientation: DecisionTileOrientationDto = DecisionTileOrientationDto.UPRIGHT,
    ) {
        tileFaceRenderer.renderGui(context, assetKey, x, y, tileWidth, tileHeight, orientation)
    }

    /** 取得目前已套用的 HUD 配置。 */
    fun hudLayout() = configStore.current.hudLayout

    /** 傳送一個以目前 decision key 約束的受控選擇。 */
    fun submit(prompt: PlayerDecisionPromptDto, kind: PlayerDecisionSelectionKindDto, token: String? = null) {
        sendFinalSelection(prompt, kind, token, emptyList())
    }

    /**
     * 明確點擊帶有選牌需求的動作卡片後，進入實體牌選取模式並啟用合法候選牌高亮；只有
     * `maxCount > 1` 才通知伺服器生成確認面板（[PlayerDecisionSelectionKindDto.BEGIN_TILE_SELECTION]）
     * ——`maxCount == 1` 維持右鍵合法牌直接自動送出，不需要面板。
     */
    fun beginActionTileSelection(prompt: PlayerDecisionPromptDto, action: PlayerDecisionActionDto) {
        val request = tileSelection.beginAction(action)
        promptStore.beginTileSelection(prompt.decisionKey, action.token)
        dismissedDecisionKey = prompt.decisionKey
        MinecraftClient.getInstance().setScreen(null)
        if (request != null) sendBeginTileSelection(prompt, request)
    }

    /** 玩家明確跳過自己回合的特殊動作後，進入普通實體出牌模式。 */
    fun beginDirectDiscard(prompt: PlayerDecisionPromptDto) {
        tileSelection.beginDirectDiscard(prompt.decisionKey)
        dismissedDecisionKey = prompt.decisionKey
        MinecraftClient.getInstance().setScreen(null)
    }

    /** 只有已明確選擇普通出牌時，右鍵才可傳到實體牌——選牌情境由 [toggleTile] 先行攔截。 */
    private fun canUsePhysicalTileDirectly(): Boolean {
        val decisionKey = promptStore.prompt?.decisionKey ?: return false
        return tileSelection.allowsDirectDiscard(decisionKey)
    }

    /**
     * 進入實體手牌 preparation 選取模式；只有 `maxCount > 1` 才通知伺服器生成確認面板，理由同
     * [beginActionTileSelection]。
     */
    fun beginPreparationTileSelection(prompt: PlayerDecisionPromptDto) {
        val request = tileSelection.beginPreparation(prompt)
        dismissedDecisionKey = prompt.decisionKey
        MinecraftClient.getInstance().setScreen(null)
        if (request != null) sendBeginTileSelection(prompt, request)
    }

    /** 右鍵一張管理中手牌；回傳是否已被進行中的選牌情境消化。 */
    private fun toggleTile(entity: MahjongTileEntity): Boolean {
        val prompt = promptStore.prompt ?: return false
        return applyOutcome(prompt, tileSelection.toggle(prompt, entity.uuid.toString(), promptStore.isTileSelectionActive()))
    }

    /** 目前選牌是否落在合法範圍內、右鍵確認面板會不會真的送出；沒有進行中的選牌時回傳 `null`。 */
    fun currentTileSelectionConfirmable(): Boolean? = currentTileSelectionProgress()?.let { it.selectedCount in it.validRange }

    /** 目前進行中選牌情境的進度；沒有進行中的選牌時回傳 `null`。 */
    private fun currentTileSelectionProgress(): DecisionTileSelectionState.Progress? {
        val prompt = promptStore.prompt ?: return null
        return tileSelection.progress(prompt, promptStore.isTileSelectionActive())
    }

    /**
     * 右鍵多選確認面板：只有本機玩家就是這個面板的 [MahjongTileSelectionConfirmEntity.holderId] 才處理。
     * 回傳 `false`（面板不屬於本機玩家、或本機目前根本不在任何選牌情境）時，呼叫端應讓事件正常往下傳遞。
     */
    private fun confirmTileSelection(entity: MahjongTileSelectionConfirmEntity): Boolean {
        if (submissionTracker.isPending()) return true
        val prompt = promptStore.prompt ?: return false
        val localPlayerId = MinecraftClient.getInstance().player?.uuid?.toKotlinUuid() ?: return false
        if (entity.holderId != localPlayerId) return false
        return applyOutcome(prompt, tileSelection.confirm(prompt, promptStore.isTileSelectionActive()))
    }

    /** 送出狀態機要求的最終選擇，並回傳這次互動是否已被消化。 */
    private fun applyOutcome(prompt: PlayerDecisionPromptDto, outcome: DecisionTileSelectionState.Outcome): Boolean = when (outcome) {
        DecisionTileSelectionState.Outcome.Ignored -> false
        is DecisionTileSelectionState.Outcome.Consumed -> {
            outcome.selection?.let { sendFinalSelection(prompt, it.kind, it.token, it.tileIds) }
            true
        }
    }

    /** 通知伺服器為需要多選的情境生成確認面板。 */
    private fun sendBeginTileSelection(prompt: PlayerDecisionPromptDto, request: DecisionTileSelectionState.BeginRequest) {
        sendSelection(prompt, PlayerDecisionSelectionKindDto.BEGIN_TILE_SELECTION, request.token, emptyList())
    }

    /** 送出一個以目前 decision key 約束的受控選擇，不觸發 [submit] 額外的關閉面板／dismiss 副作用。 */
    private fun sendSelection(prompt: PlayerDecisionPromptDto, kind: PlayerDecisionSelectionKindDto, token: String?, tileIds: List<String>) {
        val gameId = timerStore.state?.gameId ?: return
        MahjongChannels.decisionSelection.sendToServer(
            json,
            PlayerDecisionSelectionDto(gameId.toString(), prompt.decisionKey, kind, token, tileIds),
        )
    }

    /** 送出具有唯一 ID 的最終選擇，並在收到 ACK 前保留所有畫面與實體選牌狀態。 */
    private fun sendFinalSelection(
        prompt: PlayerDecisionPromptDto,
        kind: PlayerDecisionSelectionKindDto,
        token: String?,
        tileIds: List<String>,
    ) {
        if (submissionTracker.isPending()) return
        val gameId = timerStore.state?.gameId ?: return
        val submissionId = Uuid.random().toString()
        if (!submissionTracker.begin(gameId.toString(), prompt.decisionKey, submissionId)) return
        MahjongChannels.decisionSelection.sendToServer(
            json,
            PlayerDecisionSelectionDto(gameId.toString(), prompt.decisionKey, kind, token, tileIds, submissionId),
        )
        (MinecraftClient.getInstance().currentScreen as? PlayerDecisionScreen)?.refreshSubmissionState()
    }

    /** 套用 server ACK；過期或不屬於目前提交的回覆一律忽略。 */
    fun handleSubmissionResult(result: PlayerDecisionSubmissionResultDto) {
        if (submissionTracker.acknowledge(result) == AcknowledgementEffect.UNLOCKED) {
            (MinecraftClient.getInstance().currentScreen as? PlayerDecisionScreen)?.refreshSubmissionState()
        }
    }

    /** 世界離線時同步清除提交鎖與選牌資料。 */
    fun clear() {
        submissionTracker.clear()
        clearLocalDecisionState()
    }

    /** 是否正等待目前 decision 的最終提交結果。 */
    fun isSubmissionPending(decisionKey: String): Boolean = submissionTracker.isPending(decisionKey)

    /** 新 prompt 第一次出現時自動開啟；普通出牌回合只保留精簡倒數。 */
    private fun tick(client: MinecraftClient) {
        syncSelectionHighlights(client)
        val prompt = promptStore.prompt
        if (prompt == null) {
            if (client.currentScreen is PlayerDecisionScreen) client.setScreen(null)
            submissionTracker.applyAuthoritativeDecision(null)
            dismissedDecisionKey = null
            openedDecisionKey = null
            clearLocalDecisionState()
            return
        }
        if (submissionTracker.isPending() && !submissionTracker.isPending(prompt.decisionKey)) {
            submissionTracker.applyAuthoritativeDecision(prompt.decisionKey)
            clearLocalDecisionState()
        }
        tileSelection.syncTo(prompt.decisionKey, promptStore.isTileSelectionActive())
        val openScreen = client.currentScreen as? PlayerDecisionScreen
        if (openScreen != null && openScreen.decisionKey != prompt.decisionKey) {
            client.setScreen(null)
            openedDecisionKey = null
            tileSelection.clearSelections()
        }
        if (!prompt.isInteractive || client.currentScreen != null || prompt.decisionKey == openedDecisionKey) return
        openedDecisionKey = prompt.decisionKey
        dismissedDecisionKey = null
        client.setScreen(PlayerDecisionScreen(prompt, timerStore.state?.phase?.isReaction == true, this))
    }

    /** 清除只屬於上一個 decision 的本機互動狀態。 */
    private fun clearLocalDecisionState() {
        tileSelection.clear()
        promptStore.endTileSelection()
    }

    /**
     * 逐 tick 依 [DecisionTileSelectionState.highlightedTileIds] 同步管理中手牌的本地選取發光——先清除
     * 所有管理中手牌的選取發光，再對目前選取集合套用，比照 [MatchingTileHighlightController] 的「先清除
     * 再重算」手法，不用在每個新增／移除選取的分支各自手動維護高亮狀態，也不怕漏掉某條重置路徑
     * （decisionKey 改變、prompt 消失等）忘記清除。
     */
    private fun syncSelectionHighlights(client: MinecraftClient) {
        val selectedIds = tileSelection.highlightedTileIds()
        val tiles = client.world?.entities?.filterIsInstance<MahjongTileEntity>().orEmpty()
        tiles.forEach { tile ->
            if (tile.uuid.toString() in selectedIds) {
                tile.setSelectionHighlight(SELECTION_HIGHLIGHT_COLOR)
            } else {
                tile.clearSelectionHighlight()
            }
        }
    }

    /** 在原版聊天欄之前繪製被動提示，讓聊天背景保有自然的半透明覆蓋效果。 */
    private fun renderBeforeChat(context: DrawContext) {
        val client = MinecraftClient.getInstance()
        if (client.options.hudHidden) return
        if (client.currentScreen is MahjongHudLayoutEditorScreen) return
        if (client.currentScreen !is PlayerDecisionScreen) {
            renderCompactDecisionHud(context)
        }
        val prompt = promptStore.prompt
        if (client.currentScreen == null && prompt != null) renderDiscardAnalysis(context, prompt, client.crosshairTarget)
    }

    /** 繪製一般遊戲畫面與聊天畫面共用的等待提示及倒數；正在多選選牌時改顯示選牌進度提示。 */
    private fun renderCompactDecisionHud(context: DrawContext) {
        val client = MinecraftClient.getInstance()
        if (!configStore.current.presentationVisibility.compactPromptEnabled) return
        if (client.options.hudHidden || timerStore.reading() == null) return
        val prompt = promptStore.prompt
        val content = compactDecisionHudContent(
            prompt = prompt,
            dismissedDecisionKey = dismissedDecisionKey,
            isPhysicalSelectionActive = prompt != null && isPhysicalSelectionActive(prompt),
            tileSelectionProgress = currentTileSelectionProgress(),
        )
        val hudLayout = configStore.current.hudLayout
        val layout = CompactDecisionHudLayout(
            screenWidth = context.scaledWindowWidth,
            screenHeight = context.scaledWindowHeight,
            ratioX = hudLayout.compactPromptX,
            ratioY = hudLayout.compactPromptY,
            expanded = content != CompactDecisionHudContent.TimerOnly,
        )
        renderTimerOverlay(context, layout.timerTop, layout.centerX)
        when (content) {
            CompactDecisionHudContent.TimerOnly -> Unit
            is CompactDecisionHudContent.TileSelection -> renderCompactLines(
                context,
                layout,
                Text.translatable("mahjongcraft.hud.tile_selection_in_progress"),
                tileSelectionDetailText(content.progress),
            )
            CompactDecisionHudContent.ReopenReminder -> renderCompactLines(
                context,
                layout,
                Text.translatable("mahjongcraft.hud.waiting_for_action"),
                Text.translatable("mahjongcraft.hud.reopen_action"),
            )
        }
    }

    /** 繪製精簡 HUD 倒數上方的兩行提示。 */
    private fun renderCompactLines(context: DrawContext, layout: CompactDecisionHudLayout, title: Text, detail: Text) {
        val renderer = MinecraftClient.getInstance().textRenderer
        context.drawCenteredTextWithShadow(renderer, title, layout.centerX, layout.groupTop, COMPACT_HUD_TITLE_COLOR)
        context.drawCenteredTextWithShadow(renderer, detail, layout.centerX, layout.detailTextTop, COMPACT_HUD_DETAIL_COLOR)
    }

    /** 玩家已明確進入實體牌選擇階段時，不再顯示「重新開啟操作介面」提醒。 */
    private fun isPhysicalSelectionActive(prompt: PlayerDecisionPromptDto): Boolean = tileSelection.isPhysicalSelectionActive(prompt.decisionKey, promptStore.isTileSelectionActive())

    /** 在一般 HUD 或操作畫面的最上層繪製同一份權威倒數，避免被 Screen 背景遮住。 */
    fun renderTimerOverlay(context: DrawContext, y: Int, centerX: Int = context.scaledWindowWidth / 2) {
        val reading = timerStore.reading() ?: return
        renderDecisionTimer(
            context,
            ceil(reading.baseRemainingMillis / 1_000.0).toInt(),
            ceil(reading.reserveRemainingMillis / 1_000.0).toInt(),
            y,
            centerX,
        )
    }

    /** 將基本時間、加號與較低對比的保留時間分段放大並靠右下排列。 */
    private fun renderDecisionTimer(
        context: DrawContext,
        baseSeconds: Int,
        reserveSeconds: Int,
        y: Int,
        centerX: Int,
    ) {
        val renderer = MinecraftClient.getInstance().textRenderer
        val consumingReserve = baseSeconds <= 0 && reserveSeconds > 0
        val parts = buildList {
            if (baseSeconds > 0) add(baseSeconds.toString() to 0xFFD54F)
            if (baseSeconds > 0 && reserveSeconds > 0) add(" + " to 0x888888)
            if (reserveSeconds > 0) {
                val reserveColor = when {
                    !consumingReserve -> 0xB0B0B0
                    reserveSeconds <= 5 -> 0xE05252
                    else -> 0xE69A45
                }
                add(reserveSeconds.toString() to reserveColor)
            }
        }
        if (parts.isEmpty()) return
        val width = parts.sumOf { renderer.getWidth(it.first) }
        context.matrices.push()
        context.matrices.scale(TIMER_SCALE, TIMER_SCALE, 1f)
        var x = centerX / TIMER_SCALE - width / 2f
        parts.forEach { (text, color) ->
            context.drawTextWithShadow(renderer, text, x.toInt(), (y / TIMER_SCALE).toInt(), color)
            x += renderer.getWidth(text)
        }
        context.matrices.pop()
    }

    /**
     * 依準星指向的手牌 UUID 選擇一份權威分析並繪製牌面格。
     *
     * 欄寬依實際文字寬度動態計算，避免不同語系下的剩餘張數與和牌資格文字互相碰撞，因此在這裡量測後交給
     * [DiscardAnalysisLayout]。
     */
    private fun renderDiscardAnalysis(context: DrawContext, prompt: PlayerDecisionPromptDto, hit: HitResult?) {
        if (!configStore.current.presentationVisibility.discardAnalysisEnabled) return
        val tile = (hit as? EntityHitResult)?.entity as? MahjongTileEntity ?: return
        val analyses = prompt.discardAnalysesForAction(tileSelection.activeActionToken)
        val analysis = analyses.firstOrNull { it.discardTileId == tile.uuid.toString() } ?: return
        val renderer = MinecraftClient.getInstance().textRenderer
        val content = discardAnalysisContent(decisionTexts, prompt.ruleModuleId, analysis)
        val layout = DiscardAnalysisLayout(
            screenWidth = context.scaledWindowWidth,
            screenHeight = context.scaledWindowHeight,
            cellCount = content.cells.size,
            statusLineCount = content.statusTexts.size,
            widestCellContentWidth = content.cells.maxOfOrNull { cell ->
                maxOf(
                    DiscardAnalysisLayout.TILE_WIDTH,
                    renderer.getWidth(cell.countText),
                    cell.availabilityText?.let(renderer::getWidth) ?: 0,
                )
            } ?: DiscardAnalysisLayout.TILE_WIDTH,
            hasAvailabilityRow = content.hasAvailabilityRow,
            ratioY = configStore.current.hudLayout.discardAnalysisY,
        )
        context.fill(
            layout.panelLeft,
            layout.panelTop,
            layout.panelLeft + layout.panelWidth,
            layout.panelTop + layout.panelHeight,
            ANALYSIS_PANEL_BACKGROUND,
        )
        content.statusTexts.forEachIndexed { index, text ->
            context.drawCenteredTextWithShadow(renderer, text, layout.panelCenterX, layout.statusTextTop(index), ANALYSIS_STATUS_COLOR)
        }
        if (content.statusTexts.isNotEmpty()) {
            context.fill(
                layout.panelLeft + DiscardAnalysisLayout.PADDING,
                layout.dividerTop,
                layout.panelLeft + layout.panelWidth - DiscardAnalysisLayout.PADDING,
                layout.dividerTop + 1,
                ANALYSIS_DIVIDER_COLOR,
            )
        }
        content.cells.forEachIndexed { index, cell ->
            val bounds = layout.tileBounds(index)
            tileFaceRenderer.renderGui(context, cell.tileAssetKey, bounds.x, bounds.y, bounds.width, bounds.height)
            val centerX = layout.cellCenterX(index)
            context.drawCenteredTextWithShadow(renderer, cell.countText, centerX, layout.countTextTop(index), cell.countColor)
            cell.availabilityText?.let { text ->
                context.drawCenteredTextWithShadow(renderer, text, centerX, layout.availabilityTextTop(index), ANALYSIS_AVAILABILITY_COLOR)
            }
        }
    }

    companion object {
        /** 由客戶端初始化後提供給版本限定 mixin 的唯一 controller。 */
        @Volatile
        private var activeController: PlayerDecisionHudController? = null

        /** Minecraft 1.20.1 缺少可排序 HUD layer API，故由精準 mixin 在聊天欄前呼叫。 */
        @JvmStatic
        fun renderPassiveHudBeforeChat(context: DrawContext) {
            activeController?.renderBeforeChat(context)
        }

        /** 供 [MahjongTileSelectionConfirmEntityRenderer] 查詢目前選牌是否可送出，決定面板文字顏色。 */
        @JvmStatic
        fun isTileSelectionConfirmable(): Boolean? = activeController?.currentTileSelectionConfirmable()

        private const val TIMER_SCALE = 1.5f
        private const val COMPACT_HUD_TITLE_COLOR = 0xFFD54F
        private const val COMPACT_HUD_DETAIL_COLOR = 0xFFFFFF
        private const val ANALYSIS_PANEL_BACKGROUND = 0xCC101820.toInt()
        private const val ANALYSIS_STATUS_COLOR = 0xFF6B6B
        private const val ANALYSIS_AVAILABILITY_COLOR = 0xFFB05A
        private const val ANALYSIS_DIVIDER_COLOR = 0x66708088

        /**
         * 多選選牌中已選取手牌的本地描邊色，跟 [MatchingTileHighlightController] 的青（準星目標）／
         * 橘黃（其他同種牌）刻意區隔開，選紫色系避免混淆。
         */
        private const val SELECTION_HIGHLIGHT_COLOR = 0x9D5DE8
    }
}

/**
 * 取得目前動作選牌情境的捨牌分析；該動作沒有專屬分析或已離開選牌情境時，沿用 prompt 的一般分析。
 */
internal fun PlayerDecisionPromptDto.discardAnalysesForAction(actionToken: String?) = actionToken
    ?.let { token -> actions.firstOrNull { it.token == token } }
    ?.tileSelection
    ?.discardAnalyses
    .orEmpty()
    .ifEmpty { discardAnalyses }

/** 只有他家捨牌與搶槓視窗的跳過會提交正式 Pass。 */
private val PlayerDecisionPhase.isReaction: Boolean
    get() = this == PlayerDecisionPhase.DISCARD_REACTION || this == PlayerDecisionPhase.KAN_REACTION
