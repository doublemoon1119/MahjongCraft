package com.doublemoon1119.mahjongcraft.platform.fabric.client.game

import com.doublemoon1119.mahjongcraft.flow.client.game.ClientDecisionTimerStateStore
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PlayerDecisionPhase
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.DecisionPlayerRelationDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.DecisionTileOrientationDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.PlayerDecisionActionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.PlayerDecisionPromptDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.PlayerDecisionSelectionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.PlayerDecisionSelectionKindDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.RoundPreparationPromptDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.WIN_AVAILABLE_ID
import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.MahjongClientConfigStore
import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.MahjongHudLayoutEditorScreen
import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.hudCoordinate
import com.doublemoon1119.mahjongcraft.platform.fabric.client.render.MahjongTileFaceRenderer
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileSelectionConfirmEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.network.MahjongChannels
import kotlinx.serialization.json.Json
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.text.Text
import net.minecraft.util.ActionResult
import net.minecraft.util.hit.EntityHitResult
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import kotlin.math.ceil
import kotlin.uuid.toKotlinUuid

/** 管理操作介面、精簡倒數與打牌分析 HUD 的共用客戶端生命週期。 */
@Single
class PlayerDecisionHudController(
    private val timerStore: ClientDecisionTimerStateStore,
    private val promptStore: ClientDecisionPromptStore,
    private val tileFaceRenderer: MahjongTileFaceRenderer,
    private val configStore: MahjongClientConfigStore,
    @Provided private val json: Json,
) {
    /** 玩家以 Esc 暫時收起的 decision key。 */
    private var dismissedDecisionKey: String? = null

    /** 最近已自動開啟過的 decision key。 */
    private var openedDecisionKey: String? = null

    /** 目前實體手牌 preparation 選取中的 tile UUID 字串。 */
    private val selectedPreparationTileIds = linkedSetOf<String>()

    /** 已由玩家明確點擊 preparation 選牌按鈕的 decision key。 */
    private var preparationTileSelectionDecisionKey: String? = null

    /** 目前實體手牌動作選取（例如立直宣告後選擇捨牌）中的 tile UUID 字串。 */
    private val selectedActionTileSelectionIds = linkedSetOf<String>()

    /** 已由玩家明確點擊、正在選取實體牌的動作 token。 */
    private var actionTileSelectionToken: String? = null

    /** 玩家明確點擊自己回合「跳過」後，才允許實體手牌直接出牌。 */
    private var directDiscardDecisionKey: String? = null

    /** 註冊 client tick、實體互動及聊天層前方的 HUD renderer bridge。 */
    fun registerEvents() {
        activeController = this
        ClientTickEvents.END_CLIENT_TICK.register(::tick)
        UseEntityCallback.EVENT.register { _, world, _, entity, _ ->
            if (world.isClient && entity is MahjongTileEntity && entity.managedByGame) {
                if (togglePreparationTile(entity)) {
                    ActionResult.SUCCESS
                } else if (toggleActionSelectionTile(entity)) {
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
        if (!prompt.isInteractive || client.currentScreen != null) return
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
        val gameId = timerStore.state?.gameId ?: return
        MahjongChannels.decisionSelection.sendToServer(
            json,
            PlayerDecisionSelectionDto(gameId.toString(), prompt.decisionKey, kind, token),
        )
        dismissedDecisionKey = prompt.decisionKey
        MinecraftClient.getInstance().setScreen(null)
    }

    /**
     * 明確點擊帶有選牌需求的動作卡片後，進入實體牌選取模式並啟用合法候選牌高亮；只有
     * `maxCount > 1` 才通知伺服器生成確認面板（[PlayerDecisionSelectionKindDto.BEGIN_TILE_SELECTION]）
     * ——`maxCount == 1` 維持右鍵合法牌直接自動送出，不需要面板。
     */
    fun beginActionTileSelection(prompt: PlayerDecisionPromptDto, action: PlayerDecisionActionDto) {
        selectedActionTileSelectionIds.clear()
        actionTileSelectionToken = action.token
        promptStore.beginTileSelection(prompt.decisionKey, action.token)
        dismissedDecisionKey = prompt.decisionKey
        MinecraftClient.getInstance().setScreen(null)
        if ((action.tileSelection?.maxCount ?: 1) > 1) {
            sendSelection(prompt, PlayerDecisionSelectionKindDto.BEGIN_TILE_SELECTION, action.token, emptyList())
        }
    }

    /** 玩家明確跳過自己回合的特殊動作後，進入普通實體出牌模式。 */
    fun beginDirectDiscard(prompt: PlayerDecisionPromptDto) {
        directDiscardDecisionKey = prompt.decisionKey
        dismissedDecisionKey = prompt.decisionKey
        MinecraftClient.getInstance().setScreen(null)
    }

    /** 只有已明確選擇普通出牌時，右鍵才可傳到實體牌——動作選牌（如立直）由 [toggleActionSelectionTile] 攔截。 */
    private fun canUsePhysicalTileDirectly(): Boolean {
        val decisionKey = promptStore.prompt?.decisionKey ?: return false
        return directDiscardDecisionKey == decisionKey
    }

    /**
     * 進入實體手牌 preparation 選取模式；只有 `maxCount > 1` 才通知伺服器生成確認面板，理由同
     * [beginActionTileSelection]。
     */
    fun beginPreparationTileSelection(prompt: PlayerDecisionPromptDto) {
        selectedPreparationTileIds.clear()
        preparationTileSelectionDecisionKey = prompt.decisionKey
        dismissedDecisionKey = prompt.decisionKey
        MinecraftClient.getInstance().setScreen(null)
        val selection = prompt.preparation as? RoundPreparationPromptDto.TileSelection
        if ((selection?.maxCount ?: 1) > 1) {
            sendSelection(prompt, PlayerDecisionSelectionKindDto.BEGIN_TILE_SELECTION, null, emptyList())
        }
    }

    /**
     * 切換一張合法 preparation 手牌；已選滿 `maxCount` 張時，右鍵其他尚未選取的牌不會有反應，必須先
     * 取消一張才能再選別的。只有 `maxCount == 1` 才會選中即自動送出（那種情境沒有確認面板，非送出
     * 不可）；`maxCount > 1` 一律要右鍵確認面板才會送出，即使剛好選滿 `maxCount` 張也一樣，見
     * [confirmTileSelection]。
     */
    private fun togglePreparationTile(entity: MahjongTileEntity): Boolean {
        val prompt = promptStore.prompt ?: return false
        if (preparationTileSelectionDecisionKey != prompt.decisionKey) return false
        val selection = prompt.preparation as? RoundPreparationPromptDto.TileSelection ?: return false
        val tileId = entity.uuid.toString()
        if (tileId !in selection.eligibleTileIds) return true
        if (!selectedPreparationTileIds.remove(tileId) && selectedPreparationTileIds.size < selection.maxCount) {
            selectedPreparationTileIds.add(tileId)
        }
        if (selection.maxCount == 1 && selectedPreparationTileIds.size == 1) {
            sendSelection(prompt, PlayerDecisionSelectionKindDto.PREPARATION_TILES, null, selectedPreparationTileIds.toList())
            selectedPreparationTileIds.clear()
            preparationTileSelectionDecisionKey = null
            directDiscardDecisionKey = null
        }
        return true
    }

    /**
     * 切換一張合法動作選牌手牌（例如立直宣告牌）；已選滿 `maxCount` 張時，右鍵其他尚未選取的牌不會有
     * 反應，理由同 [togglePreparationTile]。只有 `maxCount == 1` 才會選中即自動送出，理由也同
     * [togglePreparationTile]。
     */
    private fun toggleActionSelectionTile(entity: MahjongTileEntity): Boolean {
        val prompt = promptStore.prompt ?: return false
        val token = actionTileSelectionToken ?: return false
        if (!promptStore.isTileSelectionActive()) return false
        val selection = prompt.actions.firstOrNull { it.token == token }?.tileSelection ?: return false
        val tileId = entity.uuid.toString()
        if (tileId !in selection.eligibleTileIds) return true
        if (!selectedActionTileSelectionIds.remove(tileId) && selectedActionTileSelectionIds.size < selection.maxCount) {
            selectedActionTileSelectionIds.add(tileId)
        }
        if (selection.maxCount == 1 && selectedActionTileSelectionIds.size == 1) {
            sendSelection(prompt, PlayerDecisionSelectionKindDto.ACTION, token, selectedActionTileSelectionIds.toList())
            selectedActionTileSelectionIds.clear()
            actionTileSelectionToken = null
            promptStore.endTileSelection()
            directDiscardDecisionKey = null
        }
        return true
    }

    /**
     * 目前進行中選牌情境的已選數量與合法範圍（`minCount..maxCount`）；preparation 與 action 選牌互斥，
     * 依目前哪一個處於進行中判斷。沒有進行中的選牌時回傳 `null`。
     */
    private fun currentTileSelectionState(): TileSelectionState? {
        val prompt = promptStore.prompt ?: return null
        if (preparationTileSelectionDecisionKey == prompt.decisionKey) {
            val selection = prompt.preparation as? RoundPreparationPromptDto.TileSelection ?: return null
            return TileSelectionState(selectedPreparationTileIds.size, selection.minCount..selection.maxCount)
        }
        val token = actionTileSelectionToken
        if (token != null && promptStore.isTileSelectionActive()) {
            val selection = prompt.actions.firstOrNull { it.token == token }?.tileSelection ?: return null
            return TileSelectionState(selectedActionTileSelectionIds.size, selection.minCount..selection.maxCount)
        }
        return null
    }

    /** 目前選牌是否落在合法範圍內、右鍵確認面板會不會真的送出；沒有進行中的選牌時回傳 `null`。 */
    fun currentTileSelectionConfirmable(): Boolean? = currentTileSelectionState()?.let { it.selectedCount in it.validRange }

    /**
     * 右鍵多選確認面板：只有本機玩家就是這個面板的 [MahjongTileSelectionConfirmEntity.holderId]、且目前
     * 已選數量落在合法範圍內才送出；不在範圍內時忽略這次點擊（維持選取狀態，讓玩家繼續選）。回傳
     * `false`（面板不屬於本機玩家、或本機目前根本不在任何選牌情境）時，呼叫端應讓事件正常往下傳遞。
     */
    private fun confirmTileSelection(entity: MahjongTileSelectionConfirmEntity): Boolean {
        val prompt = promptStore.prompt ?: return false
        val localPlayerId = MinecraftClient.getInstance().player?.uuid?.toKotlinUuid() ?: return false
        if (entity.holderId != localPlayerId) return false
        if (currentTileSelectionState() == null) return false

        if (preparationTileSelectionDecisionKey == prompt.decisionKey) {
            val selection = prompt.preparation as? RoundPreparationPromptDto.TileSelection ?: return true
            if (selectedPreparationTileIds.size in selection.minCount..selection.maxCount) {
                sendSelection(prompt, PlayerDecisionSelectionKindDto.PREPARATION_TILES, null, selectedPreparationTileIds.toList())
                selectedPreparationTileIds.clear()
                preparationTileSelectionDecisionKey = null
                directDiscardDecisionKey = null
            }
            return true
        }

        val token = actionTileSelectionToken
        if (token != null && promptStore.isTileSelectionActive()) {
            val selection = prompt.actions.firstOrNull { it.token == token }?.tileSelection ?: return true
            if (selectedActionTileSelectionIds.size in selection.minCount..selection.maxCount) {
                sendSelection(prompt, PlayerDecisionSelectionKindDto.ACTION, token, selectedActionTileSelectionIds.toList())
                selectedActionTileSelectionIds.clear()
                actionTileSelectionToken = null
                promptStore.endTileSelection()
                directDiscardDecisionKey = null
            }
            return true
        }
        return false
    }

    /** [currentTileSelectionState] 的回傳形狀。 */
    private data class TileSelectionState(val selectedCount: Int, val validRange: IntRange)

    /** 送出一個以目前 decision key 約束的受控選擇，不觸發 [submit] 額外的關閉面板／dismiss 副作用。 */
    private fun sendSelection(prompt: PlayerDecisionPromptDto, kind: PlayerDecisionSelectionKindDto, token: String?, tileIds: List<String>) {
        val gameId = timerStore.state?.gameId ?: return
        MahjongChannels.decisionSelection.sendToServer(
            json,
            PlayerDecisionSelectionDto(gameId.toString(), prompt.decisionKey, kind, token, tileIds),
        )
    }

    /** 新 prompt 第一次出現時自動開啟；普通出牌回合只保留精簡倒數。 */
    private fun tick(client: MinecraftClient) {
        syncSelectionHighlights(client)
        val prompt = promptStore.prompt
        if (prompt == null) {
            if (client.currentScreen is PlayerDecisionScreen) client.setScreen(null)
            dismissedDecisionKey = null
            openedDecisionKey = null
            selectedPreparationTileIds.clear()
            preparationTileSelectionDecisionKey = null
            selectedActionTileSelectionIds.clear()
            actionTileSelectionToken = null
            directDiscardDecisionKey = null
            return
        }
        if (preparationTileSelectionDecisionKey != null && preparationTileSelectionDecisionKey != prompt.decisionKey) {
            selectedPreparationTileIds.clear()
            preparationTileSelectionDecisionKey = null
        }
        if (actionTileSelectionToken != null && !promptStore.isTileSelectionActive()) {
            selectedActionTileSelectionIds.clear()
            actionTileSelectionToken = null
        }
        val openScreen = client.currentScreen as? PlayerDecisionScreen
        if (openScreen != null && openScreen.decisionKey != prompt.decisionKey) {
            client.setScreen(null)
            openedDecisionKey = null
            selectedPreparationTileIds.clear()
            preparationTileSelectionDecisionKey = null
            selectedActionTileSelectionIds.clear()
            actionTileSelectionToken = null
        }
        if (!prompt.isInteractive || client.currentScreen != null || prompt.decisionKey == openedDecisionKey) return
        openedDecisionKey = prompt.decisionKey
        dismissedDecisionKey = null
        client.setScreen(PlayerDecisionScreen(prompt, timerStore.state?.phase?.isReaction == true, this))
    }

    /**
     * 逐 tick 依目前選取集合（[selectedPreparationTileIds]／[selectedActionTileSelectionIds]）同步管理中
     * 手牌的本地選取發光——先清除所有管理中手牌的選取發光，再對目前選取集合套用，比照
     * [MatchingTileHighlightController] 的「先清除再重算」手法，不用在每個新增／移除選取的分支各自
     * 手動維護高亮狀態，也不怕漏掉某條重置路徑（decisionKey 改變、prompt 消失等）忘記清除。
     */
    private fun syncSelectionHighlights(client: MinecraftClient) {
        val selectedIds = when {
            preparationTileSelectionDecisionKey != null -> selectedPreparationTileIds
            actionTileSelectionToken != null -> selectedActionTileSelectionIds
            else -> emptySet()
        }
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
        val groupWidth = COMPACT_HUD_WIDTH.coerceAtMost(context.scaledWindowWidth)
        val tileSelectionState = currentTileSelectionState()
        val showReopenReminder = prompt != null &&
            dismissedDecisionKey == prompt.decisionKey &&
            prompt.isInteractive &&
            !isPhysicalSelectionActive(prompt)
        val groupHeight = if (tileSelectionState != null || showReopenReminder) {
            COMPACT_HUD_EXPANDED_HEIGHT
        } else {
            COMPACT_HUD_TIMER_HEIGHT
        }
        val layout = configStore.current.hudLayout
        val groupLeft = hudCoordinate(layout.compactPromptX, context.scaledWindowWidth, groupWidth)
        val groupTop = hudCoordinate(layout.compactPromptY, context.scaledWindowHeight, groupHeight)
        val centerX = groupLeft + groupWidth / 2
        val timerY = groupTop + groupHeight - COMPACT_HUD_TIMER_HEIGHT
        renderTimerOverlay(context, timerY, centerX)
        if (tileSelectionState != null) {
            renderTileSelectionStatus(context, client, tileSelectionState, centerX, groupTop)
        } else if (showReopenReminder) {
            context.drawCenteredTextWithShadow(
                client.textRenderer,
                Text.translatable("mahjongcraft.hud.waiting_for_action"),
                centerX,
                groupTop,
                0xFFD54F,
            )
            context.drawCenteredTextWithShadow(
                client.textRenderer,
                Text.translatable("mahjongcraft.hud.reopen_action"),
                centerX,
                groupTop + 11,
                0xFFFFFF,
            )
        }
    }

    /**
     * 選牌進度提示：還沒選到 `minCount` 張時提示還缺幾張，落在合法範圍內（含剛好選滿 `maxCount`）時
     * 提示可以確認——選滿本身已經隱含「已達上限」，不需要額外的一次性提醒。
     */
    private fun renderTileSelectionStatus(
        context: DrawContext,
        client: MinecraftClient,
        state: TileSelectionState,
        centerX: Int,
        groupTop: Int,
    ) {
        context.drawCenteredTextWithShadow(
            client.textRenderer,
            Text.translatable("mahjongcraft.hud.tile_selection_in_progress"),
            centerX,
            groupTop,
            0xFFD54F,
        )
        val detail = if (state.selectedCount < state.validRange.first) {
            Text.translatable(
                "mahjongcraft.hud.tile_selection_need_more",
                state.validRange.first - state.selectedCount,
                state.selectedCount,
                state.validRange.last,
            )
        } else {
            Text.translatable("mahjongcraft.hud.tile_selection_ready", state.selectedCount, state.validRange.last)
        }
        context.drawCenteredTextWithShadow(client.textRenderer, detail, centerX, groupTop + 11, 0xFFFFFF)
    }

    /** 玩家已明確進入實體牌選擇階段時，不再顯示「重新開啟操作介面」提醒。 */
    private fun isPhysicalSelectionActive(prompt: PlayerDecisionPromptDto): Boolean = promptStore.isTileSelectionActive() ||
        preparationTileSelectionDecisionKey == prompt.decisionKey ||
        directDiscardDecisionKey == prompt.decisionKey

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

    /** 依準星指向的手牌 UUID 選擇一份權威分析並繪製牌面格；欄寬依實際文字寬度動態計算，避免不同語系下的
     * 剩餘張數／和牌資格文字互相碰撞。 */
    private fun renderDiscardAnalysis(context: DrawContext, prompt: PlayerDecisionPromptDto, hit: net.minecraft.util.hit.HitResult?) {
        if (!configStore.current.presentationVisibility.discardAnalysisEnabled) return
        val tile = (hit as? EntityHitResult)?.entity as? MahjongTileEntity ?: return
        val analyses = prompt.discardAnalysesForAction(actionTileSelectionToken)
        val analysis = analyses.firstOrNull { it.discardTileId == tile.uuid.toString() } ?: return
        val textRenderer = MinecraftClient.getInstance().textRenderer
        val columns = minOf(MAX_WAIT_COLUMNS, analysis.waitingTiles.size.coerceAtLeast(1))
        val rowCount = (analysis.waitingTiles.size + columns - 1) / columns
        val sharedAvailability = analysis.waitingTiles.map { it.winAvailability }.distinct().singleOrNull()
            ?.takeUnless { it == WIN_AVAILABLE_ID }
        val statusTexts = listOfNotNull(
            analysis.statusIndicatorId?.let { Text.translatable(it.translationKey()) },
            sharedAvailability?.let { Text.translatable(it.translationKey()) },
        )
        val statusHeight = if (statusTexts.isEmpty()) 0 else statusTexts.size * STATUS_TEXT_HEIGHT + STATUS_DIVIDER_GAP + 1 + STATUS_TILE_GAP
        val mixedAvailability = sharedAvailability == null &&
            analysis.waitingTiles.any {
                it.winAvailability != WIN_AVAILABLE_ID
            }
        val countTexts = analysis.waitingTiles.map { waiting -> Text.translatable("mahjongcraft.hud.remaining_tiles", waiting.remainingCount) }
        val availabilityTexts = analysis.waitingTiles.map { waiting ->
            if (mixedAvailability && waiting.winAvailability != WIN_AVAILABLE_ID) {
                Text.translatable(waiting.winAvailability.translationKey())
            } else {
                null
            }
        }
        val widestCellContent = maxOf(
            TILE_WIDTH,
            countTexts.maxOf(textRenderer::getWidth),
            availabilityTexts.maxOf { it?.let(textRenderer::getWidth) ?: 0 },
        )
        val cellWidth = widestCellContent + CELL_GAP
        val panelWidth = PADDING * 2 + columns * cellWidth
        val cellHeight = TILE_HEIGHT + COUNT_HEIGHT + if (mixedAvailability) AVAILABILITY_HEIGHT else 0
        val panelHeight = PADDING * 2 + statusHeight + rowCount * cellHeight
        val left = (context.scaledWindowWidth - panelWidth) / 2
        val top = hudCoordinate(
            configStore.current.hudLayout.discardAnalysisY,
            context.scaledWindowHeight,
            panelHeight,
        )
        context.fill(left, top, left + panelWidth, top + panelHeight, 0xCC101820.toInt())
        statusTexts.forEachIndexed { index, text ->
            context.drawCenteredTextWithShadow(
                textRenderer,
                text,
                left + panelWidth / 2,
                top + PADDING + index * STATUS_TEXT_HEIGHT,
                0xFF6B6B,
            )
        }
        if (statusTexts.isNotEmpty()) {
            val dividerY = top + PADDING + statusTexts.size * STATUS_TEXT_HEIGHT + STATUS_DIVIDER_GAP
            context.fill(left + PADDING, dividerY, left + panelWidth - PADDING, dividerY + 1, STATUS_DIVIDER_COLOR)
        }
        analysis.waitingTiles.forEachIndexed { index, waiting ->
            val row = index / columns
            val column = index % columns
            val cellLeft = left + PADDING + column * cellWidth
            val tileX = cellLeft + (cellWidth - TILE_WIDTH) / 2
            val tileY = top + PADDING + statusHeight + row * cellHeight
            tileFaceRenderer.renderGui(context, waiting.tileAssetKey, tileX, tileY, TILE_WIDTH, TILE_HEIGHT)
            val color = when (waiting.remainingCount) {
                0 -> 0xAA4444
                1 -> 0xFFD54F
                else -> 0xFFFFFF
            }
            context.drawCenteredTextWithShadow(
                textRenderer,
                countTexts[index],
                cellLeft + cellWidth / 2,
                tileY + TILE_HEIGHT + 1,
                color,
            )
            availabilityTexts[index]?.let { text ->
                context.drawCenteredTextWithShadow(
                    textRenderer,
                    text,
                    cellLeft + cellWidth / 2,
                    tileY + TILE_HEIGHT + COUNT_HEIGHT,
                    0xFFB05A,
                )
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

        private const val MAX_WAIT_COLUMNS = 7
        private const val PADDING = 6
        private const val CELL_GAP = 6
        private const val TILE_WIDTH = 18
        private const val TILE_HEIGHT = 24
        private const val AVAILABILITY_HEIGHT = 10
        private const val TILE_TEXTURE_WIDTH = 48
        private const val TILE_TEXTURE_HEIGHT = 64
        private const val COUNT_HEIGHT = 11
        private const val STATUS_TEXT_HEIGHT = 9
        private const val STATUS_DIVIDER_GAP = 3
        private const val STATUS_TILE_GAP = 5
        private const val STATUS_DIVIDER_COLOR = 0x66708088
        private const val TIMER_SCALE = 1.5f
        private const val COMPACT_HUD_WIDTH = 220
        private const val COMPACT_HUD_TIMER_HEIGHT = 14
        private const val COMPACT_HUD_EXPANDED_HEIGHT = 38

        /**
         * 多選選牌中已選取手牌的本地描邊色，跟 [MatchingTileHighlightController] 的青（準星目標）／
         * 橘黃（其他同種牌）刻意區隔開，選紫色系避免混淆。
         */
        private const val SELECTION_HIGHLIGHT_COLOR = 0x9D5DE8
    }
}

/** 透明且不暫停遊戲的權威操作選擇介面。 */
private class PlayerDecisionScreen(
    private val prompt: PlayerDecisionPromptDto,
    private val isReaction: Boolean,
    private val controller: PlayerDecisionHudController,
) : Screen(Text.translatable("mahjongcraft.hud.action_title")) {
    /** 畫面所呈現 prompt 的穩定 key，供生命週期協調器關閉過期畫面。 */
    val decisionKey: String
        get() = prompt.decisionKey

    /** 目前呈現的全部選項卡。 */
    private var visibleEntries: List<DisplayEntry> = emptyList()

    /** 跟隨橫向捲動內容移動的選項按鈕。 */
    private var cardButtons: List<ButtonWidget> = emptyList()

    /** 固定在 header 右側的跳過按鈕。 */
    private var skipButton: ButtonWidget? = null

    /** 目前操作卡片的水平捲動量。 */
    private var horizontalScroll = 0.0

    /** 是否已完成畫面開啟時的預設捲動位置；resize 造成的重建不應覆蓋玩家已手動調整的位置。 */
    private var horizontalScrollInitialized = false

    /** 是否正在拖曳 scrollbar thumb。 */
    private var draggingScrollbar = false

    /** 開始拖曳時的游標與捲動位置。 */
    private var scrollbarDragStartX = 0.0
    private var scrollbarDragStartScroll = 0.0

    /** 讓多人遊戲與 integrated server 在畫面開啟時持續推進。 */
    override fun shouldPause(): Boolean = false

    /** 建立固定單列、可水平捲動的半透明選項卡。 */
    override fun init() {
        val entries = buildList<DisplayEntry> {
            prompt.actions.filterNot { it.actionId == "mahjongcraft:pass" }
                .sortedBy { it.actionId.actionDisplayPriority() }
                .forEach { action ->
                    val onClick: () -> Unit = if (action.tileSelection != null) {
                        { controller.beginActionTileSelection(prompt, action) }
                    } else {
                        { controller.submit(prompt, PlayerDecisionSelectionKindDto.ACTION, action.token) }
                    }
                    add(DisplayEntry(Text.translatable(action.actionId.translationKey()), action, onClick = onClick))
                }
            when (val preparation = prompt.preparation) {
                RoundPreparationPromptDto.Confirmation -> add(
                    DisplayEntry(Text.translatable("mahjongcraft.hud.action.confirm")) {
                        controller.submit(prompt, PlayerDecisionSelectionKindDto.PREPARATION_CONFIRM)
                    },
                )
                is RoundPreparationPromptDto.SingleChoice -> preparation.optionIds.forEach { option ->
                    add(
                        DisplayEntry(Text.translatable(option.translationKey())) {
                            controller.submit(prompt, PlayerDecisionSelectionKindDto.PREPARATION_CHOICE, option)
                        },
                    )
                }
                is RoundPreparationPromptDto.TileSelection -> add(
                    DisplayEntry(
                        Text.translatable("mahjongcraft.hud.action.select_tiles", preparation.maxCount),
                        previewTileAssetKeys = preparation.eligibleTileAssetKeys,
                    ) {
                        controller.beginPreparationTileSelection(prompt)
                    },
                )
                else -> Unit
            }
        }
        visibleEntries = entries
        horizontalScroll = if (horizontalScrollInitialized) {
            horizontalScroll.coerceIn(0.0, maximumScroll())
        } else {
            horizontalScrollInitialized = true
            maximumScroll()
        }
        val placements = cardPlacements()
        cardButtons = visibleEntries.mapIndexed { index, entry ->
            val placement = placements[index]
            ButtonWidget.builder(entry.label) { entry.onClick() }
                .dimensions(
                    placement.x + CARD_PADDING,
                    placement.y + placement.height - BUTTON_HEIGHT - CARD_PADDING,
                    placement.width - CARD_PADDING * 2,
                    BUTTON_HEIGHT,
                )
                .build()
        }
        skipButton = if (prompt.preparation == null) {
            ButtonWidget.builder(Text.translatable("mahjongcraft.hud.action.skip")) {
                val pass = prompt.actions.firstOrNull { it.actionId == "mahjongcraft:pass" }
                if (isReaction && pass != null) {
                    controller.submit(prompt, PlayerDecisionSelectionKindDto.ACTION, pass.token)
                } else {
                    controller.beginDirectDiscard(prompt)
                }
            }.dimensions(
                panelRight() - SKIP_BUTTON_WIDTH - PANEL_PADDING,
                panelTop() + PANEL_PADDING,
                SKIP_BUTTON_WIDTH,
                BUTTON_HEIGHT,
            )
                .build()
        } else {
            null
        }
    }

    /** Esc 只暫時收起，不提交 Pass。 */
    override fun close() {
        controller.dismiss(prompt.decisionKey)
        client?.setScreen(null)
    }

    /** 操作區內的滾輪一律轉為水平捲動。 */
    override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        if (
            hasOverflow() &&
            mouseX in viewportLeft().toDouble()..viewportRight().toDouble() &&
            mouseY in panelTop().toDouble()..panelBottom().toDouble()
        ) {
            horizontalScroll = (horizontalScroll - amount * SCROLL_STEP).coerceIn(0.0, maximumScroll())
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, amount)
    }

    /** Scrollbar thumb 可直接點擊或開始拖曳；卡片按鈕只在裁切 viewport 內接收輸入。 */
    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (
            hasOverflow() &&
            button == 0 &&
            mouseX in viewportLeft().toDouble()..viewportRight().toDouble() &&
            mouseY in scrollbarTop().toDouble()..(scrollbarTop() + SCROLLBAR_HEIGHT).toDouble()
        ) {
            val thumb = scrollbarThumb()
            if (mouseX !in thumb.left.toDouble()..thumb.right.toDouble()) {
                horizontalScroll = scrollFromThumbLeft(mouseX - thumb.width / 2.0)
            }
            draggingScrollbar = true
            scrollbarDragStartX = mouseX
            scrollbarDragStartScroll = horizontalScroll
            return true
        }
        if (skipButton?.mouseClicked(mouseX, mouseY, button) == true) return true
        if (mouseX in viewportLeft().toDouble()..viewportRight().toDouble() && mouseY in cardTop().toDouble()..cardBottom().toDouble()) {
            return cardButtons.any { it.mouseClicked(mouseX, mouseY, button) }
        }
        return false
    }

    /** 拖曳 thumb 時依 track 的可移動比例更新內容 offset。 */
    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        if (draggingScrollbar && button == 0) {
            val thumb = scrollbarThumb()
            val travel = (viewportWidth() - thumb.width).coerceAtLeast(1)
            horizontalScroll = (scrollbarDragStartScroll + (mouseX - scrollbarDragStartX) / travel * maximumScroll())
                .coerceIn(0.0, maximumScroll())
            return true
        }
        return false
    }

    /** 放開左鍵後結束 scrollbar 拖曳。 */
    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0 && draggingScrollbar) {
            draggingScrollbar = false
            return true
        }
        skipButton?.mouseReleased(mouseX, mouseY, button)
        cardButtons.forEach { it.mouseReleased(mouseX, mouseY, button) }
        return false
    }

    /** 繪製觸發牌、完整副露預覽與半透明深色選項面板。 */
    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        val panelTop = panelTop()
        val panelHeight = panelHeight()
        val panelWidth = panelWidth()
        val panelLeft = (width - panelWidth) / 2
        context.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, 0xCC101820.toInt())
        context.drawCenteredTextWithShadow(
            textRenderer,
            title,
            width / 2,
            panelTop + PANEL_PADDING + (BUTTON_HEIGHT - textRenderer.fontHeight) / 2,
            0xFFD54F,
        )
        renderTriggerPanel(context, panelTop)
        val placements = cardPlacements()
        updateCardButtonPositions(placements)
        context.enableScissor(viewportLeft(), cardTop(), viewportRight(), cardBottom())
        visibleEntries.forEachIndexed { index, entry ->
            val placement = placements[index]
            val hovered = mouseX in placement.x until placement.x + placement.width &&
                mouseY in placement.y until placement.y + placement.height
            context.fill(
                placement.x,
                placement.y,
                placement.x + placement.width,
                placement.y + placement.height,
                if (hovered) CARD_HOVER_BACKGROUND else CARD_BACKGROUND,
            )
            val tiles = entry.previewTileAssetKeys.ifEmpty { entry.action?.previewTileAssetKeys.orEmpty() }
            if (tiles.isNotEmpty()) {
                val columns = previewColumns(placement.width)
                val rows = (tiles.size + columns - 1) / columns
                val previewHeight = rows * PREVIEW_TILE_HEIGHT + (rows - 1) * PREVIEW_TILE_GAP
                val buttonTop = placement.y + placement.height - BUTTON_HEIGHT - CARD_PADDING
                val previewTop = buttonTop - TILE_BUTTON_GAP - previewHeight
                tiles.forEachIndexed { tileIndex, assetKey ->
                    val row = tileIndex / columns
                    val rowStart = row * columns
                    val rowEnd = minOf(rowStart + columns, tiles.size)
                    val rowTileCount = rowEnd - rowStart
                    val rowWidth = rowTileCount * PREVIEW_TILE_WIDTH + (rowTileCount - 1) * PREVIEW_TILE_GAP
                    val column = tileIndex - rowStart
                    val tileX = placement.x + (placement.width - rowWidth) / 2 + column * (PREVIEW_TILE_WIDTH + PREVIEW_TILE_GAP)
                    val tileY = previewTop + row * (PREVIEW_TILE_HEIGHT + PREVIEW_TILE_GAP)
                    drawTile(context, assetKey, tileX, tileY)
                    if (tileIndex == entry.action?.claimedTileIndex) {
                        drawClaimedTileMarker(
                            context,
                            tileX + PREVIEW_TILE_WIDTH / 2,
                            tileY - CLAIMED_TILE_MARKER_GAP - CLAIMED_TILE_MARKER_ROW_WIDTHS.size,
                        )
                    }
                }
            }
        }
        cardButtons.forEach { it.render(context, mouseX, mouseY, delta) }
        context.disableScissor()
        skipButton?.render(context, mouseX, mouseY, delta)
        if (hasOverflow()) renderScrollbar(context)
        controller.renderTimerOverlay(
            context,
            panelTop + panelHeight + TIMER_PANEL_GAP,
        )
    }

    /** 完整顯示來源玩家、相對位置與動作；寬度不足時換行，不截斷資訊。 */
    private fun renderTriggerPanel(context: DrawContext, mainPanelTop: Int) {
        val assetKey = prompt.triggerTileAssetKey ?: return
        val text = triggerText()
        val maximumTextWidth = (width - SCREEN_MARGIN * 2 - TRIGGER_PADDING * 2).coerceAtLeast(1)
        val lines = text?.let { textRenderer.wrapLines(it, maximumTextWidth) }.orEmpty()
        val contentWidth = maxOf(PREVIEW_TILE_WIDTH, lines.maxOfOrNull(textRenderer::getWidth) ?: 0)
        val panelWidth = contentWidth + TRIGGER_PADDING * 2
        val textHeight = if (lines.isEmpty()) 0 else lines.size * TEXT_LINE_HEIGHT + TRIGGER_GAP
        val panelHeight = TRIGGER_PADDING * 2 + textHeight + PREVIEW_TILE_HEIGHT
        val left = (width - panelWidth) / 2
        val top = mainPanelTop - panelHeight - PANEL_GAP
        context.fill(left, top, left + panelWidth, top + panelHeight, 0xCC101820.toInt())
        lines.forEachIndexed { index, line ->
            context.drawCenteredTextWithShadow(textRenderer, line, width / 2, top + TRIGGER_PADDING + index * TEXT_LINE_HEIGHT, 0xFFFFFF)
        }
        drawTile(context, assetKey, width / 2 - PREVIEW_TILE_WIDTH / 2, top + TRIGGER_PADDING + textHeight)
    }

    /** 以 client player list 解析來源名稱，並使用完整本地化句型；自己回合摸牌沒有來源玩家，改顯示專用句型。 */
    private fun triggerText(): Text? {
        val playerId = prompt.triggerPlayerId
            ?: return if (prompt.triggerTileAssetKey != null) Text.translatable("mahjongcraft.hud.trigger.self_draw") else null
        val uuid = runCatching { java.util.UUID.fromString(playerId) }.getOrNull()
        val playerName = prompt.triggerPlayerName
            ?: uuid?.let { MinecraftClient.getInstance().networkHandler?.getPlayerListEntry(it)?.profile?.name }
            ?: playerId.take(8)
        val relationKey = when (prompt.triggerPlayerRelation) {
            DecisionPlayerRelationDto.LEFT -> "mahjongcraft.hud.relation.left"
            DecisionPlayerRelationDto.ACROSS -> "mahjongcraft.hud.relation.across"
            DecisionPlayerRelationDto.RIGHT -> "mahjongcraft.hud.relation.right"
            null -> return null
        }
        val action = Text.translatable((prompt.triggerActionId ?: "mahjongcraft:discard").translationKey())
        return Text.translatable("mahjongcraft.hud.trigger", playerName, Text.translatable(relationKey), action)
    }

    /** 使用完整牌面 UV 等比例縮放預覽牌；卡片預覽一律直立，不套用鳴牌後最終桌面朝向。 */
    private fun drawTile(context: DrawContext, assetKey: String, x: Int, y: Int) {
        controller.renderTileFace(context, assetKey, x, y, PREVIEW_TILE_WIDTH, PREVIEW_TILE_HEIGHT)
    }

    /**
     * 在 [centerX], [top] 位置畫一個寬扁的倒三角形指標，逐列縮減寬度來模擬三角形，
     * 不依賴字型字符，形狀比例可完全自訂。
     */
    private fun drawClaimedTileMarker(context: DrawContext, centerX: Int, top: Int) {
        CLAIMED_TILE_MARKER_ROW_WIDTHS.forEachIndexed { row, width ->
            val left = centerX - width / 2
            context.fill(left, top + row, left + width, top + row + 1, CLAIMED_TILE_MARKER_COLOR)
        }
    }

    /** 讓固定高度操作板位於 hotbar 上方並保留觸發牌空間。 */
    private fun panelTop(): Int = groupTop() + triggerAreaHeight()

    private fun panelBottom(): Int = panelTop() + panelHeight()

    /** 操作面板、觸發牌與倒數形成的完整群組上界。 */
    private fun groupTop(): Int = hudCoordinate(controller.hudLayout().decisionPanelY, height, groupHeight())

    /** 完整操作群組高度，確保拖曳設定後所有內容都留在畫面內。 */
    private fun groupHeight(): Int = triggerAreaHeight() + panelHeight() + TIMER_PANEL_GAP + TIMER_HEIGHT

    /** 觸發牌面板存在時保留其完整高度與間距。 */
    private fun triggerAreaHeight(): Int {
        val assetKey = prompt.triggerTileAssetKey ?: return 0
        if (assetKey.isEmpty()) return 0
        val lines = triggerText()?.let {
            textRenderer.wrapLines(it, (width - SCREEN_MARGIN * 2 - TRIGGER_PADDING * 2).coerceAtLeast(1))
        }.orEmpty()
        val textHeight = if (lines.isEmpty()) 0 else lines.size * TEXT_LINE_HEIGHT + TRIGGER_GAP
        return TRIGGER_PADDING * 2 + textHeight + PREVIEW_TILE_HEIGHT + PANEL_GAP
    }

    /** 選項數量只增加內容寬度，不再增加面板高度。 */
    private fun panelHeight(): Int = PANEL_PADDING * 2 + HEADER_HEIGHT + rowHeight() +
        if (hasOverflow()) SCROLLBAR_GAP + SCROLLBAR_HEIGHT else 0

    /** 依單一卡片寬度與預覽牌數增加高度，並只在螢幕不足時換行。 */
    private fun cardHeight(entry: DisplayEntry, cardWidth: Int): Int {
        val tileCount = entry.previewTileAssetKeys.ifEmpty { entry.action?.previewTileAssetKeys.orEmpty() }.size
        val tileRows = ((tileCount + previewColumns(cardWidth) - 1) / previewColumns(cardWidth)).coerceAtLeast(1)
        val markerHeight = if (entry.action?.claimedTileIndex != null) {
            CLAIMED_TILE_MARKER_ROW_WIDTHS.size + CLAIMED_TILE_MARKER_GAP
        } else {
            0
        }
        return maxOf(
            MIN_CARD_HEIGHT,
            CARD_PADDING * 2 + markerHeight + tileRows * PREVIEW_TILE_HEIGHT + (tileRows - 1) * PREVIEW_TILE_GAP + BUTTON_HEIGHT,
        )
    }

    /** 大量牌面預覽優先橫向擴張，只有畫面不足時才換行；普通動作維持緊湊卡片。 */
    private fun cardWidth(entry: DisplayEntry): Int {
        val tileCount = entry.previewTileAssetKeys.ifEmpty { entry.action?.previewTileAssetKeys.orEmpty() }.size
        if (tileCount <= COMPACT_PREVIEW_TILE_COUNT) return CARD_WIDTH
        val desired = CARD_PADDING * 2 + tileCount * PREVIEW_TILE_WIDTH + (tileCount - 1).coerceAtLeast(0) * PREVIEW_TILE_GAP
        return desired.coerceIn(CARD_WIDTH, (width - SCREEN_MARGIN * 2 - PANEL_PADDING * 2).coerceAtLeast(CARD_WIDTH))
    }

    private fun previewColumns(cardWidth: Int): Int = ((cardWidth - CARD_PADDING * 2 + PREVIEW_TILE_GAP) / (PREVIEW_TILE_WIDTH + PREVIEW_TILE_GAP)).coerceAtLeast(1)

    private fun rowHeight(): Int = visibleEntries.maxOfOrNull { cardHeight(it, cardWidth(it)) } ?: MIN_CARD_HEIGHT

    private fun contentWidth(): Int = visibleEntries.sumOf(::cardWidth) + (visibleEntries.size - 1).coerceAtLeast(0) * CARD_GAP

    /** 將全部可變寬卡片排成單列，並套用水平捲動 offset。 */
    private fun cardPlacements(): List<CardPlacement> {
        var x = if (hasOverflow()) {
            viewportLeft() - horizontalScroll.toInt()
        } else {
            viewportLeft() + (viewportWidth() - contentWidth()) / 2
        }
        return visibleEntries.map { entry ->
            val entryWidth = cardWidth(entry)
            CardPlacement(x, cardTop(), entryWidth, rowHeight()).also {
                x += entryWidth + CARD_GAP
            }
        }
    }

    /** 面板寬度在內容可容納時收合，溢出時使用整個安全畫面寬度。 */
    private fun panelWidth(): Int {
        val cardsWidth = contentWidth() + PANEL_PADDING * 2
        val headerWidth = textRenderer.getWidth(title) + HEADER_SIDE_WIDTH * 2 + PANEL_PADDING * 2
        return maxOf(cardsWidth, headerWidth).coerceAtMost((width - SCREEN_MARGIN * 2).coerceAtLeast(1))
    }

    private fun panelRight(): Int = (width + panelWidth()) / 2

    private fun viewportLeft(): Int = (width - panelWidth()) / 2 + PANEL_PADDING

    private fun viewportRight(): Int = (width + panelWidth()) / 2 - PANEL_PADDING

    private fun viewportWidth(): Int = viewportRight() - viewportLeft()

    private fun cardTop(): Int = panelTop() + PANEL_PADDING + HEADER_HEIGHT

    private fun cardBottom(): Int = cardTop() + rowHeight()

    private fun scrollbarTop(): Int = cardBottom() + SCROLLBAR_GAP

    private fun maximumScroll(): Double = (contentWidth() - viewportWidth()).coerceAtLeast(0).toDouble()

    private fun hasOverflow(): Boolean = contentWidth() > viewportWidth()

    /** 每幀同步因拖曳／滾輪移動後的原版按鈕座標。 */
    private fun updateCardButtonPositions(placements: List<CardPlacement>) {
        cardButtons.forEachIndexed { index, button ->
            val placement = placements[index]
            button.x = placement.x + CARD_PADDING
            button.y = placement.y + placement.height - BUTTON_HEIGHT - CARD_PADDING
        }
    }

    /** 滿寬 track 與依可見比例縮放的 thumb。 */
    private fun renderScrollbar(context: DrawContext) {
        context.fill(viewportLeft(), scrollbarTop(), viewportRight(), scrollbarTop() + SCROLLBAR_HEIGHT, SCROLLBAR_TRACK_COLOR)
        val thumb = scrollbarThumb()
        context.fill(thumb.left, scrollbarTop(), thumb.right, scrollbarTop() + SCROLLBAR_HEIGHT, SCROLLBAR_THUMB_COLOR)
    }

    /**
     * 高 GUI scale 搭配小解析度時 [viewportWidth] 可能比 [MIN_SCROLLBAR_THUMB_WIDTH] 還窄，此時最小
     * 寬度本身必須先讓給可見寬度，否則下界會大於上界（比照 [MahjongHudToolbarLayout.thumb] KDoc）。
     */
    private fun scrollbarThumb(): ScrollbarThumb {
        val viewportWidth = viewportWidth()
        val thumbWidth = if (maximumScroll() <= 0.0) {
            viewportWidth
        } else {
            (viewportWidth.toDouble() * viewportWidth / contentWidth()).toInt()
                .coerceIn(MIN_SCROLLBAR_THUMB_WIDTH.coerceAtMost(viewportWidth), viewportWidth)
        }
        val travel = viewportWidth - thumbWidth
        val left = viewportLeft() + if (maximumScroll() <= 0.0) 0 else (horizontalScroll / maximumScroll() * travel).toInt()
        return ScrollbarThumb(left, left + thumbWidth)
    }

    private fun scrollFromThumbLeft(thumbLeft: Double): Double {
        val thumb = scrollbarThumb()
        val travel = (viewportWidth() - thumb.width).coerceAtLeast(1)
        val relative = (thumbLeft - viewportLeft()).coerceIn(0.0, travel.toDouble())
        return relative / travel * maximumScroll()
    }

    /** 一張可點擊的受控動作卡。 */
    private data class DisplayEntry(
        val label: Text,
        val action: PlayerDecisionActionDto? = null,
        val previewTileAssetKeys: List<String> = emptyList(),
        val onClick: () -> Unit,
    )

    private data class CardPlacement(val x: Int, val y: Int, val width: Int, val height: Int)

    private data class ScrollbarThumb(val left: Int, val right: Int) {
        val width: Int
            get() = right - left
    }

    private companion object {
        const val CARD_WIDTH = 92
        const val COMPACT_PREVIEW_TILE_COUNT = 4
        const val MIN_CARD_HEIGHT = 62
        const val CARD_PADDING = 4
        const val CARD_GAP = 5
        const val BUTTON_HEIGHT = 20
        const val SKIP_BUTTON_WIDTH = 48
        const val PANEL_PADDING = 6
        const val HEADER_HEIGHT = 24
        const val HEADER_SIDE_WIDTH = 58
        const val PREVIEW_TILE_WIDTH = 18
        const val PREVIEW_TILE_HEIGHT = 24
        const val PREVIEW_TILE_GAP = 2
        const val TILE_BUTTON_GAP = 3
        const val TRIGGER_PADDING = 6
        const val TRIGGER_GAP = 3
        const val PANEL_GAP = 5
        const val SCREEN_MARGIN = 8
        const val TEXT_LINE_HEIGHT = 10
        const val TIMER_PANEL_GAP = 5
        const val TIMER_HEIGHT = 14
        const val SCROLLBAR_GAP = 4
        const val SCROLLBAR_HEIGHT = 4
        const val MIN_SCROLLBAR_THUMB_WIDTH = 18
        const val SCROLL_STEP = 48.0
        const val CARD_BACKGROUND = 0xCC2A3844.toInt()
        const val CARD_HOVER_BACKGROUND = 0xDD3A4B59.toInt()

        /**
         * 吃卡片標出鳴來那張牌的倒三角形指標，由上而下每列的寬度（像素，皆為奇數以確保左右對稱）；
         * 碰／槓不使用，牌面彼此完全相同，標記沒有辨識意義。
         */
        val CLAIMED_TILE_MARKER_ROW_WIDTHS = intArrayOf(9, 7, 5, 3, 1)

        /** 指標的顏色，用鮮明的紅色與牌面色調完全區隔，不受任何背景深淺影響辨識度。 */
        const val CLAIMED_TILE_MARKER_COLOR = 0xFFFF5555.toInt()

        /** 指標與牌面之間的間距（像素）。 */
        const val CLAIMED_TILE_MARKER_GAP = 3
        const val SCROLLBAR_TRACK_COLOR = 0xFF26333D.toInt()
        const val SCROLLBAR_THUMB_COLOR = 0xFF8796A3.toInt()
    }
}

/** Prompt 是否包含需要玩家明確選擇的內容。 */
private val PlayerDecisionPromptDto.isInteractive: Boolean
    get() = actions.isNotEmpty() || preparation != null

/**
 * 取得目前動作選牌情境的捨牌分析；該動作沒有專屬分析或已離開選牌情境時，沿用 prompt 的一般分析。
 */
internal fun PlayerDecisionPromptDto.discardAnalysesForAction(actionToken: String?) = actionToken
    ?.let { token -> actions.firstOrNull { it.token == token } }
    ?.tileSelection
    ?.discardAnalyses
    .orEmpty()
    .ifEmpty { discardAnalyses }

/** 操作卡由左至右的顯示順序；未列出的 ID（含第三方規則模組的特殊動作）維持原始相對順序排在最後。 */
internal fun String.actionDisplayPriority(): Int = when (this) {
    "mahjongcraft:chi" -> 0
    "mahjongcraft:pon" -> 1
    "mahjongcraft:kan_open", "mahjongcraft:kan_closed", "mahjongcraft:kan_added" -> 2
    "mahjongcraft:ron" -> 3
    "mahjongcraft:tsumo" -> 4
    else -> 5
}

/** 將 namespaced ID 映射至內建語言鍵，未知 ID 仍以完整 ID 顯示。 */
internal fun String.translationKey(): String = when (this) {
    "mahjongcraft:discard_furiten" -> "mahjongcraft.hud.furiten.discard"
    "mahjongcraft:temporary_furiten" -> "mahjongcraft.hud.furiten.temporary"
    "mahjongcraft:permanent_furiten" -> "mahjongcraft.hud.furiten.permanent"
    "mahjongcraft:win_available" -> "mahjongcraft.hud.win_availability.available"
    "mahjongcraft:win_tsumo_only" -> "mahjongcraft.hud.win_availability.tsumo_only"
    "mahjongcraft:win_no_yaku" -> "mahjongcraft.hud.win_availability.no_yaku"
    "mahjongcraft:win_below_minimum" -> "mahjongcraft.hud.win_availability.below_minimum"
    else -> if (startsWith("mahjongcraft:")) "mahjongcraft.hud.action.${substringAfter(':')}" else this
}

/** 只有他家捨牌與搶槓視窗的跳過會提交正式 Pass。 */
private val PlayerDecisionPhase.isReaction: Boolean
    get() = this == PlayerDecisionPhase.DISCARD_REACTION || this == PlayerDecisionPhase.KAN_REACTION
