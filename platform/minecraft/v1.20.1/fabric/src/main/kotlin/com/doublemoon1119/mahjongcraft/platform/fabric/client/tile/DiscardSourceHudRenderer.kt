package com.doublemoon1119.mahjongcraft.platform.fabric.client.tile

import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.MahjongClientConfigStore
import com.doublemoon1119.mahjongcraft.platform.fabric.client.player.ClientPlayerDisplayNameResolver
import com.doublemoon1119.mahjongcraft.platform.fabric.client.state.ClientMahjongStateStore
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftHudKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.DiscardSource
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.text.Text
import net.minecraft.util.hit.EntityHitResult
import kotlin.uuid.toKotlinUuid

/**
 * 準星指著牌河裡的牌，或副露中鳴來的那張牌時，在準星下方顯示是誰打出的。
 *
 * @property stateStore 玩家看得到的桌況。
 * @property configStore 客戶端顯示設定。
 * @property playerNames 真人與 AI 的顯示名稱。
 * @property settlement 捨牌是否已經落到牌河。
 */
class DiscardSourceHudRenderer(
    private val stateStore: ClientMahjongStateStore,
    private val configStore: MahjongClientConfigStore,
    private val playerNames: ClientPlayerDisplayNameResolver,
    private val settlement: ClientDiscardSettlementTracker,
) {
    /**
     * 呼叫端只在沒有開啟畫面、也沒有隱藏 HUD 時呼叫；牌還沒落到牌河或還在移動時不顯示。
     *
     * @param context 本幀繪製環境。
     */
    fun render(context: DrawContext) {
        if (!configStore.current.presentationVisibility.discardSourceEnabled) return
        val client = MinecraftClient.getInstance()
        val tile = (client.crosshairTarget as? EntityHitResult)?.entity as? MahjongTileEntity ?: return
        if (!tile.managedByGame || tile.animating) return
        val tableId = tile.managedTableId ?: return
        val tileId = tile.uuid.toKotlinUuid()
        if (!settlement.isSettled(tableId, tileId)) return
        val snapshot = stateStore.gameSnapshot(tableId) ?: return
        val source = DiscardSource.of(snapshot, tileId, client.player?.uuid?.toKotlinUuid()) ?: return
        val text = discardSourceText(source.relation) { playerNames.resolve(tableId, source.discarderId.toString()) }
        val renderer = client.textRenderer
        val x = (context.scaledWindowWidth - renderer.getWidth(text)) / 2
        val y = context.scaledWindowHeight / 2 + CROSSHAIR_GAP
        context.drawTextWithShadow(renderer, text, x, y, TEXT_COLOR)
    }

    private companion object {
        /** 文字頂端與畫面中心（準星）的距離。 */
        const val CROSSHAIR_GAP: Int = 10

        /** 文字色彩。 */
        const val TEXT_COLOR: Int = 0xFFFFFF
    }
}

/**
 * 打出者的說明文字：自己打的牌只說是自己；入座時加上打出者相對自己的位置；旁觀時只有名字。
 *
 * @param relation 打出者相對觀看者的位置；觀看者沒有入座時為 null。
 * @param discarderName 打出者的顯示名稱；自己打的牌不會取用。
 * @return 可直接顯示的文字。
 */
internal fun discardSourceText(relation: RelativeDirection?, discarderName: () -> String): Text = when (relation) {
    RelativeDirection.Self -> Text.translatable(MinecraftHudKeys.DISCARD_SOURCE_SELF)
    null -> Text.translatable(MinecraftHudKeys.DISCARD_SOURCE, discarderName())
    else -> Text.translatable(MinecraftHudKeys.DISCARD_SOURCE_SEATED, Text.translatable(relationKey(relation)), discarderName())
}

/**
 * 相對位置的翻譯鍵。
 *
 * @param relation 上家、對家或下家。
 * @return 對應的翻譯鍵。
 */
private fun relationKey(relation: RelativeDirection): String = when (relation) {
    RelativeDirection.Left -> MinecraftHudKeys.RELATION_LEFT
    RelativeDirection.Across -> MinecraftHudKeys.RELATION_ACROSS
    RelativeDirection.Right -> MinecraftHudKeys.RELATION_RIGHT
    RelativeDirection.Self -> error("A player's own discard has no relation text")
}
