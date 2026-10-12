package com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic

import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.MahjongClientConfigStore
import com.doublemoon1119.mahjongcraft.platform.fabric.client.state.ClientMahjongStateStore
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.ChatScreen
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

/**
 * 在一般遊戲或聊天畫面繪製本局已確認的自動操作狀態。
 *
 * @property configStore HUD 顯示與位置設定的來源。
 * @property stateStore 查詢本機玩家目前入座對局的狀態來源。
 * @property coordinator 已確認自動操作快照的來源。
 * @property displays 控制項名稱與顯示順序的解析器。
 */
@Single
class AutomaticControlStatusHudRenderer(
    private val configStore: MahjongClientConfigStore,
    private val stateStore: ClientMahjongStateStore,
    private val coordinator: ClientAutomaticControlUpdateCoordinator,
    private val displays: AutomaticControlDisplayResolver,
) {
    /**
     * 僅在一般遊戲畫面或聊天畫面呈現，不顯示尚未確認的設定草稿。
     *
     * @param context 本次 GUI 繪製環境。
     */
    fun render(context: DrawContext) {
        val client = MinecraftClient.getInstance()
        if (client.options.hudHidden || !configStore.current.presentationVisibility.automaticControlStatusEnabled) return
        if (client.currentScreen != null && client.currentScreen !is ChatScreen) return
        val playerId = client.player?.uuid?.toKotlinUuid() ?: return
        val snapshot = coordinator.snapshot() ?: return
        val gameId = runCatching { Uuid.parse(snapshot.gameId) }.getOrNull() ?: return
        if (stateStore.findTableWhereSeated(playerId) != gameId) return
        val game = stateStore.gameSnapshot(gameId) ?: return
        if (game.id != gameId || game.players.none { it.id == playerId }) return
        val rows = automaticControlStatusRows(
            activeGameId = gameId.toString(),
            snapshot = snapshot,
            autoSortHandEnabled = configStore.current.autoSortHandEnabled,
            displays = displays,
        )
        if (rows.isEmpty()) return
        val hudLayout = configStore.current.hudLayout
        renderRows(context, rows, hudLayout.automaticControlStatusX, hudLayout.automaticControlStatusY)
    }

    /**
     * 在指定比例位置繪製狀態列；HUD 位置編輯器的預覽也使用這裡。
     *
     * @param context 本次 GUI 繪製環境。
     * @param rows 依顯示順序排列的控制項狀態。
     * @param ratioX 水平位置比例。
     * @param ratioY 垂直位置比例。
     */
    internal fun renderRows(
        context: DrawContext,
        rows: List<AutomaticControlStatusRow>,
        ratioX: Double,
        ratioY: Double,
    ) {
        val renderer = MinecraftClient.getInstance().textRenderer
        val dotWidth = AutomaticControlStatusHudText.dotWidth(renderer)
        val layout = automaticControlStatusHudLayout(
            rowWidths = AutomaticControlStatusHudText.rowWidths(renderer, rows.map { it.label }),
            textHeight = AutomaticControlStatusHudText.textHeight(renderer),
            screenWidth = context.scaledWindowWidth,
            screenHeight = context.scaledWindowHeight,
            ratioX = ratioX,
            ratioY = ratioY,
            summaryWidth = { AutomaticControlStatusHudText.summaryWidth(renderer, it) },
        ) ?: return

        context.matrices.push()
        context.matrices.translate(layout.bounds.left.toDouble(), layout.bounds.top.toDouble(), 0.0)
        context.matrices.scale(layout.scale, layout.scale, 1f)
        context.fill(0, 0, layout.rawWidth, layout.rawHeight, BACKGROUND_COLOR)
        layout.placements.forEach { placement ->
            val index = placement.rowIndex
            if (index == null) {
                context.drawTextWithShadow(
                    renderer,
                    AutomaticControlStatusHudText.summaryText(layout.hiddenCount),
                    placement.x,
                    placement.y,
                    SUMMARY_COLOR,
                )
            } else {
                val row = rows[index]
                context.drawTextWithShadow(
                    renderer,
                    if (row.enabled) AutomaticControlStatusHudText.ENABLED_DOT else AutomaticControlStatusHudText.DISABLED_DOT,
                    placement.x,
                    placement.y,
                    if (row.enabled) ENABLED_COLOR else DISABLED_COLOR,
                )
                context.drawTextWithShadow(
                    renderer,
                    row.label,
                    placement.x + dotWidth + AutomaticControlStatusHudText.DOT_LABEL_GAP,
                    placement.y,
                    if (row.enabled) ENABLED_COLOR else DISABLED_COLOR,
                )
            }
        }
        context.matrices.pop()
    }

    private companion object {
        /** 面板底色（ARGB）。 */
        val BACKGROUND_COLOR = 0xB0182028.toInt()

        /** 已啟用項目的文字與圓點顏色（ARGB）。 */
        val ENABLED_COLOR = 0xFFA8D8A0.toInt()

        /** 未啟用項目的文字與圓點顏色（ARGB）。 */
        val DISABLED_COLOR = 0xFFB6B6B6.toInt()

        /** 隱藏項目數摘要的顏色（ARGB）。 */
        val SUMMARY_COLOR = 0xFFB6B6B6.toInt()
    }
}
