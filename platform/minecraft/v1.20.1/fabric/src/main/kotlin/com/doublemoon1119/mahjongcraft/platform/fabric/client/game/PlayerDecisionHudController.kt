package com.doublemoon1119.mahjongcraft.platform.fabric.client.game

import com.doublemoon1119.mahjongcraft.flow.client.game.ClientDecisionTimerStateStore
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PlayerDecisionPhase
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkJson
import com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic.AutomaticControlStatusHudRenderer
import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.MahjongClientConfigStore
import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.MahjongHudLayoutEditorScreen
import com.doublemoon1119.mahjongcraft.platform.fabric.client.player.ClientPlayerDisplayNameResolver
import com.doublemoon1119.mahjongcraft.platform.fabric.client.render.MahjongTileFaceRenderer
import com.doublemoon1119.mahjongcraft.platform.fabric.client.render.MahjongTileSelectionConfirmEntityRenderer
import com.doublemoon1119.mahjongcraft.platform.fabric.client.state.ClientMahjongStateStore
import com.doublemoon1119.mahjongcraft.platform.fabric.client.tile.MatchingTileHighlightController
import com.doublemoon1119.mahjongcraft.platform.fabric.client.time.PauseAwareClientMonotonicClock
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileSelectionConfirmEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.network.MahjongChannels
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.DecisionTileOrientationDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.PlayerDecisionActionDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.PlayerDecisionPromptDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.PlayerDecisionSelectionDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.PlayerDecisionSelectionKindDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.PlayerDecisionSubmissionResultDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.toAssetKey
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.text.Text
import net.minecraft.util.ActionResult
import net.minecraft.util.hit.EntityHitResult
import net.minecraft.util.hit.HitResult
import kotlin.math.ceil
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

/**
 * 管理操作介面、精簡倒數與手牌分析 HUD 的共用客戶端生命週期。
 *
 * @property timerStore 權威決策倒數狀態。
 * @property timerDisplay 依本地時間產生的倒數顯示。
 * @property promptStore 玩家目前的操作提示。
 * @property tileFaceRenderer 共用牌面繪製器。
 * @property tileAssetRegistry 牌種對應的牌面素材。
 * @property configStore 客戶端呈現設定。
 * @property stateStore 玩家可見的房間與桌況。
 * @property automaticControlStatusHudRenderer 自動操作狀態繪製器。
 * @property playerNames 共用真人普通名稱與 AI 名稱解析器。
 * @property decisionTexts 規則提供的動作文字解析器。
 * @property json 決策提交的封包序列化設定。
 * @param clock 判斷提交等待確認多久的客戶端時間。
 */
class PlayerDecisionHudController(
    private val timerStore: ClientDecisionTimerStateStore,
    private val timerDisplay: DecisionTimerDisplay,
    private val promptStore: ClientDecisionPromptStore,
    private val tileFaceRenderer: MahjongTileFaceRenderer,
    private val tileAssetRegistry: MinecraftTileAssetRegistry,
    private val configStore: MahjongClientConfigStore,
    private val stateStore: ClientMahjongStateStore,
    private val automaticControlStatusHudRenderer: AutomaticControlStatusHudRenderer,
    private val playerNames: ClientPlayerDisplayNameResolver,
    val decisionTexts: DecisionTextResolver,
    private val json: NetworkJson,
    clock: PauseAwareClientMonotonicClock,
) {
    /**
     * 使用共用名稱來源解析決策的真人來源，不另行查詢外部服務。
     *
     * @param playerId 來源玩家的 UUID 字串。
     * @return 已知普通名稱或本地化未知玩家名稱。
     */
    fun resolveTriggerPlayerName(playerId: String): String = playerNames.resolve(null, playerId, false)

    /** 玩家以 Esc 暫時收起的 decision key。 */
    private var dismissedDecisionKey: String? = null

    /** 最近已自動開啟過的 decision key。 */
    private var openedDecisionKey: String? = null

    /** 以實體手牌選牌時的本機狀態。 */
    private val tileSelection = DecisionTileSelectionState()

    /** 尚待伺服器 ACK 或後續權威 timer 更新確認的最終提交。 */
    private val submissionTracker = DecisionSubmissionTracker(clock)

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

    /**
     * 操作介面被 Esc 收起時保留提示，改顯示等待提醒。
     *
     * @param decisionKey 被收起的決策識別碼。
     */
    fun dismiss(decisionKey: String) {
        dismissedDecisionKey = decisionKey
    }

    /**
     * 讓操作畫面使用與其他 HUD 及牌面展示相同的繪製器。
     *
     * @param context 繪製環境。
     * @param assetKey 牌面素材識別碼。
     * @param x 牌面左界。
     * @param y 牌面上界。
     * @param tileWidth 牌面寬度。
     * @param tileHeight 牌面高度。
     * @param orientation 牌面朝向。
     */
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

    /**
     * 傳送以決策識別碼約束的最終選擇；已有待確認提交時不重複送出。
     *
     * @param prompt 選擇所屬的權威決策提示。
     * @param kind 選擇種類。
     * @param token 動作或開局準備選項的識別；不需要時為 null。
     */
    fun submit(prompt: PlayerDecisionPromptDto, kind: PlayerDecisionSelectionKindDto, token: String? = null) {
        sendFinalSelection(prompt, kind, token, emptyList())
    }

    /**
     * 明確點擊帶有選牌需求的動作卡片後，進入實體牌選取模式並啟用合法候選牌高亮；只有
     * `maxCount > 1` 才通知伺服器生成確認面板（[PlayerDecisionSelectionKindDto.BEGIN_TILE_SELECTION]）
     * ——`maxCount == 1` 維持右鍵合法牌直接自動送出，不需要面板。
     *
     * @param prompt 動作所屬的權威決策提示。
     * @param action 帶有選牌需求的動作。
     */
    fun beginActionTileSelection(prompt: PlayerDecisionPromptDto, action: PlayerDecisionActionDto) {
        val request = tileSelection.beginAction(action)
        promptStore.beginTileSelection(prompt.decisionKey, action.token)
        dismissedDecisionKey = prompt.decisionKey
        MinecraftClient.getInstance().setScreen(null)
        if (request != null) sendBeginTileSelection(prompt, request)
    }

    /**
     * 玩家明確跳過自己回合的特殊動作後，進入普通實體出牌模式。
     *
     * @param prompt 目前自己回合的決策提示。
     */
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
     *
     * @param prompt 帶有開局準備選牌需求的決策提示。
     */
    fun beginPreparationTileSelection(prompt: PlayerDecisionPromptDto) {
        val request = tileSelection.beginPreparation(prompt)
        dismissedDecisionKey = prompt.decisionKey
        MinecraftClient.getInstance().setScreen(null)
        if (request != null) sendBeginTileSelection(prompt, request)
    }

    /**
     * 將右鍵牌面交給目前選牌狀態處理。
     *
     * @param entity 被右鍵的麻將牌實體。
     * @return 是否已處理此互動；沒有決策提示時為 false。
     */
    private fun toggleTile(entity: MahjongTileEntity): Boolean {
        val prompt = promptStore.prompt ?: return false
        return applyOutcome(prompt, tileSelection.toggle(prompt, entity.uuid.toString(), promptStore.isTileSelectionActive()))
    }

    /** 目前選牌是否落在合法範圍內、右鍵確認面板會不會真的送出；沒有進行中的選牌時回傳 `null`。 */
    fun currentTileSelectionConfirmable(): Boolean? = currentTileSelectionProgress()?.let { it.selectedCount in it.validRange }

    /**
     * 取得目前動作選牌的說明。
     *
     * @param prompt 用於尋找目前選牌動作的決策提示。
     * @return 動作說明；沒有對應動作或說明時為 null。
     */
    private fun activeActionDescription(prompt: PlayerDecisionPromptDto): Text? {
        val token = tileSelection.activeActionToken ?: return null
        val action = prompt.actions.firstOrNull { it.token == token } ?: return null
        return decisionTexts.actionDescription(prompt.ruleModuleId, action.actionId)
    }

    /** 目前進行中選牌情境的進度；沒有進行中的選牌時回傳 `null`。 */
    private fun currentTileSelectionProgress(): DecisionTileSelectionState.Progress? {
        val prompt = promptStore.prompt ?: return null
        return tileSelection.progress(prompt, promptStore.isTileSelectionActive())
    }

    /**
     * 處理右鍵多選確認面板；沒有待確認提交時，只處理屬於本機玩家的面板。
     *
     * @param entity 被右鍵的確認面板。
     * @return 是否消費互動；已有待確認提交時直接消費，其他未處理情況回傳 false 供事件繼續傳遞。
     */
    private fun confirmTileSelection(entity: MahjongTileSelectionConfirmEntity): Boolean {
        if (submissionTracker.isPending()) return true
        val prompt = promptStore.prompt ?: return false
        val localPlayerId = MinecraftClient.getInstance().player?.uuid?.toKotlinUuid() ?: return false
        if (entity.holderId != localPlayerId) return false
        return applyOutcome(prompt, tileSelection.confirm(prompt, promptStore.isTileSelectionActive()))
    }

    /**
     * 送出選牌狀態機要求的最終選擇。
     *
     * @param prompt 選擇所屬的決策提示。
     * @param outcome 選牌狀態機的處理結果。
     * @return 狀態機是否已消費互動，不代表封包已成功送出。
     */
    private fun applyOutcome(prompt: PlayerDecisionPromptDto, outcome: DecisionTileSelectionState.Outcome): Boolean = when (outcome) {
        DecisionTileSelectionState.Outcome.Ignored -> false
        is DecisionTileSelectionState.Outcome.Consumed -> {
            outcome.selection?.let { sendFinalSelection(prompt, it.kind, it.token, it.tileIds) }
            true
        }
    }

    /**
     * 通知伺服器為需要多選的情境生成確認面板。
     *
     * @param prompt 選牌所屬的決策提示。
     * @param request 選牌狀態機產生的開始要求。
     */
    private fun sendBeginTileSelection(prompt: PlayerDecisionPromptDto, request: DecisionTileSelectionState.BeginRequest) {
        sendSelection(prompt, PlayerDecisionSelectionKindDto.BEGIN_TILE_SELECTION, request.token, emptyList())
    }

    /**
     * 傳送不追蹤最終提交確認的選擇；沒有有效遊戲計時狀態時不送出。
     *
     * @param prompt 選擇所屬的決策提示。
     * @param kind 選擇種類。
     * @param token 動作識別；不需要時為 null。
     * @param tileIds 選取的牌實體 UUID 字串。
     */
    private fun sendSelection(prompt: PlayerDecisionPromptDto, kind: PlayerDecisionSelectionKindDto, token: String?, tileIds: List<String>) {
        val gameId = timerStore.state?.gameId ?: return
        MahjongChannels.decisionSelection.sendToServer(
            json,
            PlayerDecisionSelectionDto(gameId.toString(), prompt.decisionKey, kind, token, tileIds, submissionId = ""),
        )
    }

    /**
     * 傳送具有唯一識別碼的最終選擇，並保留選牌狀態等待伺服器確認或權威決策更新。
     * 已有待確認提交或沒有有效遊戲計時狀態時不送出。
     *
     * @param prompt 選擇所屬的決策提示。
     * @param kind 選擇種類。
     * @param token 動作或準備選項識別；不需要時為 null。
     * @param tileIds 選取的牌實體 UUID 字串。
     */
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

    /**
     * 套用伺服器提交確認；過期或不屬於目前提交的回覆忽略。
     *
     * @param result 伺服器回傳的提交結果。
     */
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

    /**
     * 判斷指定決策是否仍在等待最終提交結果。
     *
     * @param decisionKey 要查詢的決策識別碼。
     * @return 是否有屬於該決策的待確認提交。
     */
    fun isSubmissionPending(decisionKey: String): Boolean = submissionTracker.isPending(decisionKey)

    /**
     * 同步選牌與提交狀態，並在新的可互動提示首次出現且沒有其他畫面時自動開啟。
     *
     * @param client 目前客戶端。
     */
    private fun tick(client: MinecraftClient) {
        syncSelectionHighlights(client)
        val prompt = promptStore.prompt
        if (prompt == null) {
            if (client.currentScreen is PlayerDecisionScreen) client.setScreen(null)
            submissionTracker.applyAuthoritativeDecision(null, promptStore.updateSequence)
            dismissedDecisionKey = null
            openedDecisionKey = null
            clearLocalDecisionState()
            return
        }
        val pendingForOtherDecision = submissionTracker.isPending() && !submissionTracker.isPending(prompt.decisionKey)
        submissionTracker.applyAuthoritativeDecision(prompt.decisionKey, promptStore.updateSequence)
        if (pendingForOtherDecision) clearLocalDecisionState()
        applySubmissionTimeout(client)
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

    /**
     * 提交一直沒有收到確認時提示玩家；解除鎖定後讓操作介面恢復可選。
     *
     * @param client 目前客戶端。
     */
    private fun applySubmissionTimeout(client: MinecraftClient) {
        val messageKey = when (submissionTracker.checkTimeout()) {
            TimeoutEffect.NONE -> return
            TimeoutEffect.RESYNCING -> MinecraftMessageKeys.DECISION_CONFIRMATION_RESYNCING
            TimeoutEffect.UNLOCKED -> MinecraftMessageKeys.DECISION_CONFIRMATION_RETRY
        }
        client.player?.sendMessage(Text.translatable(messageKey), true)
        (client.currentScreen as? PlayerDecisionScreen)?.refreshSubmissionState()
    }

    /** 清除只屬於上一個 decision 的本機互動狀態。 */
    private fun clearLocalDecisionState() {
        tileSelection.clear()
        promptStore.endTileSelection()
    }

    /**
     * 每個客戶端 tick 重新核對世界中的麻將牌實體：選取集合內套用描邊，其餘清除選取描邊。
     *
     * @param client 提供目前世界與牌實體的客戶端。
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

    /**
     * 在原版聊天欄之前繪製被動提示；隱藏 HUD 或編輯 HUD 位置時不繪製。
     *
     * @param context 本幀繪製環境。
     */
    private fun renderBeforeChat(context: DrawContext) {
        val client = MinecraftClient.getInstance()
        if (client.options.hudHidden) return
        if (client.currentScreen is MahjongHudLayoutEditorScreen) return
        automaticControlStatusHudRenderer.render(context)
        if (client.currentScreen !is PlayerDecisionScreen) {
            renderCompactDecisionHud(context)
        }
        if (client.currentScreen == null) renderHandAnalysis(context, promptStore.prompt, client.crosshairTarget)
    }

    /**
     * 繪製共用的等待提示及倒數；正在選牌時改顯示選牌進度。
     *
     * @param context 本幀繪製環境。
     */
    private fun renderCompactDecisionHud(context: DrawContext) {
        val client = MinecraftClient.getInstance()
        if (!configStore.current.presentationVisibility.compactPromptEnabled) return
        if (client.options.hudHidden) return
        val timerParts = currentTimerParts() ?: return
        val prompt = promptStore.prompt
        val content = compactDecisionHudContent(
            prompt = prompt,
            dismissedDecisionKey = dismissedDecisionKey,
            isPhysicalSelectionActive = prompt != null && isPhysicalSelectionActive(prompt),
            tileSelectionProgress = currentTileSelectionProgress(),
            tileSelectionDescription = prompt?.let(::activeActionDescription),
        )
        val hudLayout = configStore.current.hudLayout
        renderCompactHud(
            context = context,
            content = content,
            timerParts = timerParts,
            ratioX = hudLayout.compactPromptX,
            ratioY = hudLayout.compactPromptY,
        )
    }

    /**
     * 在指定位置畫出精簡 HUD；HUD 位置編輯器的預覽也使用此方法。
     *
     * @param context 繪製環境。
     * @param content 精簡提示內容。
     * @param timerParts 倒數文字片段。
     * @param ratioX 水平位置比例。
     * @param ratioY 垂直位置比例。
     */
    internal fun renderCompactHud(
        context: DrawContext,
        content: CompactDecisionHudContent,
        timerParts: List<DecisionTimerPart>,
        ratioX: Double,
        ratioY: Double,
    ) {
        val renderer = MinecraftClient.getInstance().textRenderer
        val descriptionLines = compactDescriptionLines(content, context.scaledWindowWidth)
        val layout = compactHudLayout(
            screenWidth = context.scaledWindowWidth,
            screenHeight = context.scaledWindowHeight,
            content = content,
            ratioX = ratioX,
            ratioY = ratioY,
        )
        context.drawDecisionTimer(renderer, timerParts, layout.timerTop, layout.centerX, TIMER_SCALE)
        when (content) {
            CompactDecisionHudContent.TimerOnly -> Unit
            is CompactDecisionHudContent.TileSelection -> {
                renderCompactLines(
                    context,
                    layout,
                    Text.translatable("mahjongcraft.hud.tile_selection_in_progress"),
                    tileSelectionDetailText(content.progress),
                )
                descriptionLines.forEachIndexed { index, line ->
                    context.drawCenteredTextWithShadow(renderer, line, layout.centerX, layout.descriptionLineTop(index), COMPACT_HUD_DETAIL_COLOR)
                }
            }
            CompactDecisionHudContent.ReopenReminder -> renderCompactLines(
                context,
                layout,
                Text.translatable("mahjongcraft.hud.waiting_for_action"),
                Text.translatable("mahjongcraft.hud.reopen_action"),
            )
        }
    }

    /**
     * 計算精簡 HUD 版位；說明行數依實際換行結果計算。
     *
     * @param screenWidth 畫面寬度。
     * @param screenHeight 畫面高度。
     * @param content 精簡提示內容。
     * @param ratioX 水平位置比例。
     * @param ratioY 垂直位置比例。
     * @return 精簡 HUD 的版面資訊。
     */
    internal fun compactHudLayout(
        screenWidth: Int,
        screenHeight: Int,
        content: CompactDecisionHudContent,
        ratioX: Double,
        ratioY: Double,
    ): CompactDecisionHudLayout = CompactDecisionHudLayout(
        screenWidth = screenWidth,
        screenHeight = screenHeight,
        ratioX = ratioX,
        ratioY = ratioY,
        expanded = content != CompactDecisionHudContent.TimerOnly,
        descriptionLineCount = compactDescriptionLines(content, screenWidth).size,
    )

    /**
     * 將選牌進度的動作說明依精簡 HUD 寬度換行。
     *
     * @param content 精簡提示內容。
     * @param screenWidth 畫面寬度，用於限制換行寬度。
     * @return 說明文字行；非選牌進度或沒有說明時為空清單。
     */
    private fun compactDescriptionLines(content: CompactDecisionHudContent, screenWidth: Int) = (content as? CompactDecisionHudContent.TileSelection)?.description
        ?.let { MinecraftClient.getInstance().textRenderer.wrapLines(it, CompactDecisionHudLayout.GROUP_WIDTH.coerceAtMost(screenWidth)) }
        .orEmpty()

    /**
     * 繪製精簡 HUD 倒數上方的兩行提示。
     *
     * @param context 繪製環境。
     * @param layout 精簡 HUD 的版面資訊。
     * @param title 第一行提示。
     * @param detail 第二行提示。
     */
    private fun renderCompactLines(context: DrawContext, layout: CompactDecisionHudLayout, title: Text, detail: Text) {
        val renderer = MinecraftClient.getInstance().textRenderer
        context.drawCenteredTextWithShadow(renderer, title, layout.centerX, layout.groupTop, COMPACT_HUD_TITLE_COLOR)
        context.drawCenteredTextWithShadow(renderer, detail, layout.centerX, layout.detailTextTop, COMPACT_HUD_DETAIL_COLOR)
    }

    /**
     * 判斷玩家是否已進入目前決策的實體選牌階段。
     *
     * @param prompt 目前決策提示。
     * @return 是否處於實體選牌階段，供呼叫端決定是否隱藏重新開啟提醒。
     */
    private fun isPhysicalSelectionActive(prompt: PlayerDecisionPromptDto): Boolean = tileSelection.isPhysicalSelectionActive(prompt.decisionKey, promptStore.isTileSelectionActive())

    /**
     * 在一般 HUD 或操作畫面的最上層繪製同一份權威倒數，避免被 Screen 背景遮住。
     *
     * @param context 繪製環境。
     * @param y 倒數文字上界。
     * @param centerX 倒數文字水平中心。
     * @param scale 文字縮放；單獨顯示的精簡倒數放大以便辨識，操作面板標題列內與標題同尺寸。
     */
    fun renderTimerOverlay(
        context: DrawContext,
        y: Int,
        centerX: Int = context.scaledWindowWidth / 2,
        scale: Float = TIMER_SCALE,
    ) {
        val parts = currentTimerParts() ?: return
        context.drawDecisionTimer(MinecraftClient.getInstance().textRenderer, parts, y, centerX, scale)
    }

    /**
     * 量測目前倒數文字縮放後的寬度。
     *
     * @param scale 文字縮放比例。
     * @return 倒數寬度；沒有有效計時時為 null。
     */
    fun timerOverlayWidth(scale: Float = TIMER_SCALE): Int? = currentTimerParts()?.let { parts ->
        decisionTimerWidth(MinecraftClient.getInstance().textRenderer, parts, scale)
    }

    /** 目前倒數的文字片段；沒有有效計時時為 null，兩段時間都已用完時為空清單。 */
    private fun currentTimerParts(): List<DecisionTimerPart>? {
        val reading = timerDisplay.reading() ?: return null
        return decisionTimerParts(
            baseSeconds = ceil(reading.baseRemainingMillis / 1_000.0).toInt(),
            reserveSeconds = ceil(reading.reserveRemainingMillis / 1_000.0).toInt(),
        )
    }

    /**
     * 優先依準星指向的手牌 UUID 顯示捨牌後預測，沒有合法候選時顯示目前手牌的權威分析。
     *
     * @param context 繪製環境。
     * @param prompt 目前決策提示，沒有時為 null。
     * @param hit 準星命中結果，沒有時為 null。
     */
    private fun renderHandAnalysis(context: DrawContext, prompt: PlayerDecisionPromptDto?, hit: HitResult?) {
        if (!configStore.current.presentationVisibility.discardAnalysisEnabled) return
        val pointedTile = (hit as? EntityHitResult)?.entity as? MahjongTileEntity
        val client = MinecraftClient.getInstance()
        val playerId = client.player?.uuid?.toKotlinUuid()
        val tableId = playerId?.let(stateStore::findTableWhereSeated)
        val selection = selectHandAnalysis(
            prompt = prompt,
            activeActionToken = tileSelection.activeActionToken,
            pointedTileId = pointedTile?.uuid?.toString(),
            currentAnalysis = tableId?.let(stateStore::handReadinessAnalysis),
        ) ?: return
        val content = when (selection) {
            is HandAnalysisSelection.AfterDiscard ->
                discardAnalysisContent(decisionTexts, selection.ruleModuleId, selection.analysis)
            is HandAnalysisSelection.Current -> handAnalysisContent(decisionTexts, selection.analysis)
        }
        if (content.cells.isEmpty()) return
        renderAnalysisPanel(context, content, configStore.current.hudLayout.discardAnalysisY)
    }

    /**
     * [content] 在指定畫面上的手牌分析面板版位。
     *
     * 欄寬與狀態列寬度都依實際文字寬度動態計算：前者避免不同語系下的剩餘張數與和牌資格文字互相碰撞，
     * 後者讓較長的狀態列不會超出面板背景。
     *
     * @param screenWidth 畫面寬度。
     * @param screenHeight 畫面高度。
     * @param content 手牌分析內容。
     * @param ratioY 垂直位置比例。
     * @return 手牌分析面板的版面資訊。
     */
    internal fun analysisLayout(
        screenWidth: Int,
        screenHeight: Int,
        content: DiscardAnalysisContent,
        ratioY: Double,
    ): DiscardAnalysisLayout {
        val renderer = MinecraftClient.getInstance().textRenderer
        return DiscardAnalysisLayout(
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            cellCount = content.cells.size,
            statusLineCount = content.statusTexts.size,
            widestCellContentWidth = content.cells.maxOfOrNull { cell ->
                maxOf(
                    DiscardAnalysisLayout.TILE_WIDTH,
                    renderer.getWidth(cell.countText),
                    cell.availabilityText?.let(renderer::getWidth) ?: 0,
                )
            } ?: DiscardAnalysisLayout.TILE_WIDTH,
            widestStatusTextWidth = content.statusTexts.maxOfOrNull(renderer::getWidth) ?: 0,
            hasAvailabilityRow = content.hasAvailabilityRow,
            ratioY = ratioY,
        )
    }

    /**
     * 在指定位置畫出手牌分析面板；HUD 位置編輯器的預覽也使用此方法。
     *
     * @param context 繪製環境。
     * @param content 手牌分析內容。
     * @param ratioY 垂直位置比例。
     */
    internal fun renderAnalysisPanel(context: DrawContext, content: DiscardAnalysisContent, ratioY: Double) {
        val renderer = MinecraftClient.getInstance().textRenderer
        val layout = analysisLayout(context.scaledWindowWidth, context.scaledWindowHeight, content, ratioY)
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
            tileFaceRenderer.renderGui(context, cell.tile.toDomain().toAssetKey(tileAssetRegistry), bounds.x, bounds.y, bounds.width, bounds.height)
            val centerX = layout.cellCenterX(index)
            context.drawCenteredTextWithShadow(renderer, cell.countText, centerX, layout.countTextTop(index), cell.countColor)
            cell.availabilityText?.let { text ->
                context.drawCenteredTextWithShadow(renderer, text, centerX, layout.availabilityTextTop(index), ANALYSIS_AVAILABILITY_COLOR)
            }
        }
    }

    /** 提供 HUD 橋接入口與共用繪製常數。 */
    companion object {
        /** 由客戶端初始化後提供給版本限定 mixin 的唯一 controller。 */
        @Volatile
        private var activeController: PlayerDecisionHudController? = null

        /**
         * 供聊天欄前方的 mixin 繪製被動 HUD；尚未註冊控制器時不繪製。
         *
         * @param context 繪製環境。
         */
        @JvmStatic
        fun renderPassiveHudBeforeChat(context: DrawContext) {
            activeController?.renderBeforeChat(context)
        }

        /** 供 [MahjongTileSelectionConfirmEntityRenderer] 查詢目前選牌是否可送出，決定面板文字顏色。 */
        @JvmStatic
        fun isTileSelectionConfirmable(): Boolean? = activeController?.currentTileSelectionConfirmable()

        /** 單獨顯示倒數時的文字縮放比例。 */
        private const val TIMER_SCALE = 1.5f

        /** 精簡提示標題的 RGB 色值。 */
        private const val COMPACT_HUD_TITLE_COLOR = 0xFFD54F

        /** 精簡提示細節的 RGB 色值。 */
        private const val COMPACT_HUD_DETAIL_COLOR = 0xFFFFFF

        /** 手牌分析面板背景的 ARGB 色值。 */
        private const val ANALYSIS_PANEL_BACKGROUND = 0xCC101820.toInt()

        /** 手牌分析狀態文字的 RGB 色值。 */
        private const val ANALYSIS_STATUS_COLOR = 0xFF6B6B

        /** 和牌資格文字的 RGB 色值。 */
        private const val ANALYSIS_AVAILABILITY_COLOR = 0xFFB05A

        /** 手牌分析分隔線的 ARGB 色值。 */
        private const val ANALYSIS_DIVIDER_COLOR = 0x66708088

        /**
         * 多選選牌中已選取手牌的本地描邊色，跟 [MatchingTileHighlightController] 的青（準星目標）／
         * 橘黃（其他同種牌）刻意區隔開，選紫色系避免混淆。
         */
        private const val SELECTION_HIGHLIGHT_COLOR = 0x9D5DE8
    }
}

/**
 * 取得指定動作的捨牌分析；動作不存在或沒有專屬分析時，沿用提示的一般分析。
 * 此函式不判斷目前是否仍在選牌情境。
 *
 * @param actionToken 要尋找的動作識別；null 時使用一般分析。
 * @return 動作專屬分析或一般分析清單。
 */
internal fun PlayerDecisionPromptDto.discardAnalysesForAction(actionToken: String?) = actionToken
    ?.let { token -> actions.firstOrNull { it.token == token } }
    ?.tileSelection
    ?.discardAnalyses
    .orEmpty()
    .ifEmpty { discardAnalyses }

/** 只有他家捨牌與搶和視窗的跳過會提交正式 Pass。 */
private val PlayerDecisionPhase.isReaction: Boolean
    get() = this == PlayerDecisionPhase.DISCARD_REACTION || this == PlayerDecisionPhase.ROBBING_REACTION
