package com.doublemoon1119.mahjongcraft.platform.fabric.client.game

import com.doublemoon1119.mahjongcraft.flow.client.game.ClientDecisionTimerStateStore
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.DecisionTileOrientationDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.PlayerDecisionActionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.PlayerDecisionPromptDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.PlayerDecisionSelectionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.PlayerDecisionSelectionKindDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.RoundPreparationPromptDto
import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.MahjongClientConfigStore
import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.MahjongHudLayoutEditorScreen
import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.hudCoordinate
import com.doublemoon1119.mahjongcraft.platform.fabric.client.render.MahjongTileFaceRenderer
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileEntity
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
                } else if (canUsePhysicalTileDirectly()) {
                    ActionResult.PASS
                } else if (promptStore.prompt?.isInteractive == true) {
                    reopen()
                    ActionResult.SUCCESS
                } else {
                    ActionResult.PASS
                }
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

    /** 明確選擇立直後通知伺服器，並在本機啟用合法宣告牌高亮。 */
    fun beginRiichiSelection(prompt: PlayerDecisionPromptDto) {
        promptStore.beginRiichiSelection(prompt.decisionKey)
        submit(prompt, PlayerDecisionSelectionKindDto.BEGIN_RIICHI)
    }

    /** 玩家明確跳過自己回合的特殊動作後，進入普通實體出牌模式。 */
    fun beginDirectDiscard(prompt: PlayerDecisionPromptDto) {
        directDiscardDecisionKey = prompt.decisionKey
        dismissedDecisionKey = prompt.decisionKey
        MinecraftClient.getInstance().setScreen(null)
    }

    /** 只有已明確選擇立直或普通出牌時，右鍵才可傳到實體牌。 */
    private fun canUsePhysicalTileDirectly(): Boolean {
        val decisionKey = promptStore.prompt?.decisionKey ?: return false
        return directDiscardDecisionKey == decisionKey || promptStore.isRiichiSelectionActive()
    }

    /** 進入實體手牌 preparation 選取模式。 */
    fun beginPreparationTileSelection(prompt: PlayerDecisionPromptDto) {
        selectedPreparationTileIds.clear()
        preparationTileSelectionDecisionKey = prompt.decisionKey
        dismissedDecisionKey = prompt.decisionKey
        MinecraftClient.getInstance().setScreen(null)
    }

    /** 切換一張合法 preparation 手牌；達到 maxCount 時直接原子提交。 */
    private fun togglePreparationTile(entity: MahjongTileEntity): Boolean {
        val prompt = promptStore.prompt ?: return false
        if (preparationTileSelectionDecisionKey != prompt.decisionKey) return false
        val selection = prompt.preparation as? RoundPreparationPromptDto.TileSelection ?: return false
        val tileId = entity.uuid.toString()
        if (tileId !in selection.eligibleTileIds) return true
        if (!selectedPreparationTileIds.remove(tileId)) selectedPreparationTileIds.add(tileId)
        if (selectedPreparationTileIds.size == selection.maxCount) {
            val gameId = timerStore.state?.gameId ?: return true
            MahjongChannels.decisionSelection.sendToServer(
                json,
                PlayerDecisionSelectionDto(
                    gameId = gameId.toString(),
                    decisionKey = prompt.decisionKey,
                    kind = PlayerDecisionSelectionKindDto.PREPARATION_TILES,
                    tileIds = selectedPreparationTileIds.toList(),
                ),
            )
            selectedPreparationTileIds.clear()
            preparationTileSelectionDecisionKey = null
            directDiscardDecisionKey = null
        }
        return true
    }

    /** 新 prompt 第一次出現時自動開啟；普通出牌回合只保留精簡倒數。 */
    private fun tick(client: MinecraftClient) {
        val prompt = promptStore.prompt
        if (prompt == null) {
            if (client.currentScreen is PlayerDecisionScreen) client.setScreen(null)
            dismissedDecisionKey = null
            openedDecisionKey = null
            selectedPreparationTileIds.clear()
            preparationTileSelectionDecisionKey = null
            directDiscardDecisionKey = null
            return
        }
        if (preparationTileSelectionDecisionKey != null && preparationTileSelectionDecisionKey != prompt.decisionKey) {
            selectedPreparationTileIds.clear()
            preparationTileSelectionDecisionKey = null
        }
        val openScreen = client.currentScreen as? PlayerDecisionScreen
        if (openScreen != null && openScreen.decisionKey != prompt.decisionKey) {
            client.setScreen(null)
            openedDecisionKey = null
            selectedPreparationTileIds.clear()
            preparationTileSelectionDecisionKey = null
        }
        if (!prompt.isInteractive || client.currentScreen != null || prompt.decisionKey == openedDecisionKey) return
        openedDecisionKey = prompt.decisionKey
        dismissedDecisionKey = null
        client.setScreen(PlayerDecisionScreen(prompt, timerStore.state?.phase?.isReaction == true, this))
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

    /** 繪製一般遊戲畫面與聊天畫面共用的等待提示及倒數。 */
    private fun renderCompactDecisionHud(context: DrawContext) {
        val client = MinecraftClient.getInstance()
        if (client.options.hudHidden || timerStore.reading() == null) return
        val prompt = promptStore.prompt
        val groupWidth = COMPACT_HUD_WIDTH.coerceAtMost(context.scaledWindowWidth)
        val groupHeight = if (prompt != null && dismissedDecisionKey == prompt.decisionKey && prompt.isInteractive) {
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
        if (prompt != null && dismissedDecisionKey == prompt.decisionKey && prompt.isInteractive) {
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

    /** 依準星指向的手牌 UUID 選擇一份權威分析並繪製牌面格。 */
    private fun renderDiscardAnalysis(context: DrawContext, prompt: PlayerDecisionPromptDto, hit: net.minecraft.util.hit.HitResult?) {
        val tile = (hit as? EntityHitResult)?.entity as? MahjongTileEntity ?: return
        val analysis = prompt.discardAnalyses.firstOrNull { it.discardTileId == tile.uuid.toString() } ?: return
        val columns = minOf(MAX_WAIT_COLUMNS, analysis.waitingTiles.size.coerceAtLeast(1))
        val rowCount = (analysis.waitingTiles.size + columns - 1) / columns
        val statusHeight = if (analysis.statusIndicatorId == null) 0 else STATUS_HEIGHT
        val panelWidth = PADDING * 2 + columns * CELL_WIDTH
        val panelHeight = PADDING * 2 + statusHeight + rowCount * (TILE_HEIGHT + COUNT_HEIGHT)
        val left = (context.scaledWindowWidth - panelWidth) / 2
        val top = hudCoordinate(
            configStore.current.hudLayout.discardAnalysisY,
            context.scaledWindowHeight,
            panelHeight,
        )
        context.fill(left, top, left + panelWidth, top + panelHeight, 0xCC101820.toInt())
        analysis.statusIndicatorId?.let { indicator ->
            val text = Text.translatable(indicator.translationKey())
            context.drawCenteredTextWithShadow(
                MinecraftClient.getInstance().textRenderer,
                text,
                left + panelWidth / 2,
                top + PADDING,
                0xFF6B6B,
            )
            val dividerY = top + PADDING + STATUS_TEXT_HEIGHT + STATUS_DIVIDER_GAP
            context.fill(left + PADDING, dividerY, left + panelWidth - PADDING, dividerY + 1, STATUS_DIVIDER_COLOR)
        }
        analysis.waitingTiles.forEachIndexed { index, waiting ->
            val row = index / columns
            val column = index % columns
            val cellLeft = left + PADDING + column * CELL_WIDTH
            val tileX = cellLeft + (CELL_WIDTH - TILE_WIDTH) / 2
            val tileY = top + PADDING + statusHeight + row * (TILE_HEIGHT + COUNT_HEIGHT)
            tileFaceRenderer.renderGui(context, waiting.tileAssetKey, tileX, tileY, TILE_WIDTH, TILE_HEIGHT)
            val count = Text.translatable("mahjongcraft.hud.remaining_tiles", waiting.remainingCount)
            val color = when (waiting.remainingCount) {
                0 -> 0xAA4444
                1 -> 0xFFD54F
                else -> 0xFFFFFF
            }
            context.drawCenteredTextWithShadow(
                MinecraftClient.getInstance().textRenderer,
                count,
                cellLeft + CELL_WIDTH / 2,
                tileY + TILE_HEIGHT + 1,
                color,
            )
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

        private const val MAX_WAIT_COLUMNS = 7
        private const val PADDING = 6
        private const val CELL_WIDTH = 30
        private const val TILE_WIDTH = 18
        private const val TILE_HEIGHT = 24
        private const val TILE_TEXTURE_WIDTH = 48
        private const val TILE_TEXTURE_HEIGHT = 64
        private const val COUNT_HEIGHT = 11
        private const val STATUS_TEXT_HEIGHT = 9
        private const val STATUS_DIVIDER_GAP = 3
        private const val STATUS_TILE_GAP = 5
        private const val STATUS_HEIGHT = STATUS_TEXT_HEIGHT + STATUS_DIVIDER_GAP + 1 + STATUS_TILE_GAP
        private const val STATUS_DIVIDER_COLOR = 0x66708088
        private const val TIMER_SCALE = 1.5f
        private const val COMPACT_HUD_WIDTH = 220
        private const val COMPACT_HUD_TIMER_HEIGHT = 14
        private const val COMPACT_HUD_EXPANDED_HEIGHT = 38
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
            prompt.actions.filterNot { it.actionId == "mahjongcraft:pass" }.forEach { action ->
                add(
                    DisplayEntry(Text.translatable(action.actionId.translationKey()), action) {
                        controller.submit(prompt, PlayerDecisionSelectionKindDto.ACTION, action.token)
                    },
                )
            }
            if (prompt.riichiTileIds.isNotEmpty()) {
                add(
                    DisplayEntry(
                        Text.translatable("mahjongcraft.hud.action.riichi"),
                        previewTileAssetKeys = prompt.riichiTileAssetKeys,
                    ) { controller.beginRiichiSelection(prompt) },
                )
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
        horizontalScroll = horizontalScroll.coerceIn(0.0, maximumScroll())
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
                    val rowIndices = rowStart until rowEnd
                    val rowWidth = rowIndices.sumOf { tileDisplayWidth(entry.action, it) } + (rowIndices.count() - 1) * PREVIEW_TILE_GAP
                    var tileX = placement.x + (placement.width - rowWidth) / 2
                    rowIndices.takeWhile { it < tileIndex }.forEach { previous ->
                        tileX += tileDisplayWidth(entry.action, previous) + PREVIEW_TILE_GAP
                    }
                    val orientation = if (tileIndex == entry.action?.claimedTileIndex) {
                        entry.action.claimedTileOrientation
                    } else {
                        DecisionTileOrientationDto.UPRIGHT
                    }
                    val tileY = previewTop + row * (PREVIEW_TILE_HEIGHT + PREVIEW_TILE_GAP) +
                        if (orientation == DecisionTileOrientationDto.UPRIGHT) 0 else PREVIEW_TILE_HEIGHT - PREVIEW_TILE_WIDTH
                    drawTile(context, assetKey, tileX, tileY, orientation)
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

    /** 以 client player list 解析來源名稱，並使用完整本地化句型。 */
    private fun triggerText(): Text? {
        val playerId = prompt.triggerPlayerId ?: return null
        val uuid = runCatching { java.util.UUID.fromString(playerId) }.getOrNull()
        val playerName = prompt.triggerPlayerName
            ?: uuid?.let { MinecraftClient.getInstance().networkHandler?.getPlayerListEntry(it)?.profile?.name }
            ?: playerId.take(8)
        val relationKey = when (prompt.triggerPlayerRelation) {
            com.doublemoon1119.mahjongcraft.flow.network.dto.message.DecisionPlayerRelationDto.LEFT -> "mahjongcraft.hud.relation.left"
            com.doublemoon1119.mahjongcraft.flow.network.dto.message.DecisionPlayerRelationDto.ACROSS -> "mahjongcraft.hud.relation.across"
            com.doublemoon1119.mahjongcraft.flow.network.dto.message.DecisionPlayerRelationDto.RIGHT -> "mahjongcraft.hud.relation.right"
            null -> return null
        }
        val action = Text.translatable((prompt.triggerActionId ?: "mahjongcraft:discard").translationKey())
        return Text.translatable("mahjongcraft.hud.trigger", playerName, Text.translatable(relationKey), action)
    }

    /** 使用完整牌面 UV 等比例縮放預覽牌。 */
    private fun drawTile(
        context: DrawContext,
        assetKey: String,
        x: Int,
        y: Int,
        orientation: DecisionTileOrientationDto = DecisionTileOrientationDto.UPRIGHT,
    ) {
        controller.renderTileFace(context, assetKey, x, y, PREVIEW_TILE_WIDTH, PREVIEW_TILE_HEIGHT, orientation)
    }

    /** 橫置被鳴牌使用牌高作為畫面寬度，其餘牌使用正常牌寬。 */
    private fun tileDisplayWidth(action: PlayerDecisionActionDto?, tileIndex: Int): Int = if (tileIndex == action?.claimedTileIndex && action.claimedTileOrientation != DecisionTileOrientationDto.UPRIGHT) {
        PREVIEW_TILE_HEIGHT
    } else {
        PREVIEW_TILE_WIDTH
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
        return maxOf(MIN_CARD_HEIGHT, CARD_PADDING * 2 + tileRows * PREVIEW_TILE_HEIGHT + (tileRows - 1) * PREVIEW_TILE_GAP + BUTTON_HEIGHT)
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

    private fun scrollbarThumb(): ScrollbarThumb {
        val viewportWidth = viewportWidth()
        val thumbWidth = if (maximumScroll() <= 0.0) {
            viewportWidth
        } else {
            (viewportWidth.toDouble() * viewportWidth / contentWidth()).toInt().coerceIn(MIN_SCROLLBAR_THUMB_WIDTH, viewportWidth)
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
        const val SCROLLBAR_TRACK_COLOR = 0xFF26333D.toInt()
        const val SCROLLBAR_THUMB_COLOR = 0xFF8796A3.toInt()
    }
}

/** Prompt 是否包含需要玩家明確選擇的內容。 */
private val PlayerDecisionPromptDto.isInteractive: Boolean
    get() = actions.isNotEmpty() || riichiTileIds.isNotEmpty() || preparation != null

/** 將 namespaced ID 映射至內建語言鍵，未知 ID 仍以完整 ID 顯示。 */
internal fun String.translationKey(): String = when (this) {
    "mahjongcraft:discard_furiten" -> "mahjongcraft.hud.furiten.discard"
    "mahjongcraft:temporary_furiten" -> "mahjongcraft.hud.furiten.temporary"
    "mahjongcraft:permanent_furiten" -> "mahjongcraft.hud.furiten.permanent"
    else -> if (startsWith("mahjongcraft:")) "mahjongcraft.hud.action.${substringAfter(':')}" else this
}

/** 只有他家捨牌與搶槓視窗的跳過會提交正式 Pass。 */
private val com.doublemoon1119.mahjongcraft.flow.common.game.model.PlayerDecisionPhase.isReaction: Boolean
    get() = this == com.doublemoon1119.mahjongcraft.flow.common.game.model.PlayerDecisionPhase.DISCARD_REACTION ||
        this == com.doublemoon1119.mahjongcraft.flow.common.game.model.PlayerDecisionPhase.KAN_REACTION
