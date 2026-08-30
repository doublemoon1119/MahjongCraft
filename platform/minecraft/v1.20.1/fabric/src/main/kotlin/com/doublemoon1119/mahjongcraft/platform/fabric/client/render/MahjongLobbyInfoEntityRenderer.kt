package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongLobbyInfoEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.MinecraftRoomScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistry
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.entity.EntityRenderer
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.text.Text
import net.minecraft.util.Identifier

/** 將等待中遊戲渲染成面向觀看者的三行半透明提示面板。 */
class MahjongLobbyInfoEntityRenderer(
    context: EntityRendererFactory.Context,
    private val ruleNames: RuleModuleDisplayNameRegistry,
) : EntityRenderer<MahjongLobbyInfoEntity>(context) {
    private val textRenderer = context.textRenderer

    override fun render(
        entity: MahjongLobbyInfoEntity,
        yaw: Float,
        tickDelta: Float,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
        light: Int,
    ) {
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light)
        val ruleName = ruleNames.find(entity.ruleModuleId)?.let(Text::translatable) ?: Text.literal(entity.ruleModuleId)
        val lines = listOf(
            Text.translatable(MinecraftRoomScreenKeys.LOBBY_WAITING) to TITLE_COLOR,
            Text.translatable(
                MinecraftRoomScreenKeys.LOBBY_SUMMARY,
                ruleName,
                entity.playerCount,
                entity.maximumPlayerCount,
            ) to if (entity.playerCount >= entity.maximumPlayerCount) FULL_COLOR else TEXT_COLOR,
            Text.translatable(MinecraftRoomScreenKeys.LOBBY_VIEW_DETAILS) to HINT_COLOR,
        )
        matrices.push()
        matrices.multiply(dispatcher.rotation)
        matrices.scale(-TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE)
        val lineHeight = textRenderer.fontHeight + LINE_SPACING
        val totalHeight = lines.size * lineHeight
        val maximumWidth = lines.maxOf { textRenderer.getWidth(it.first) }
        WorldPanelRenderer.drawBackground(
            -maximumWidth / 2f - PANEL_PADDING,
            -totalHeight / 2f - PANEL_PADDING,
            maximumWidth / 2f + PANEL_PADDING,
            totalHeight / 2f + PANEL_PADDING,
            BACKGROUND_COLOR,
            0f,
            matrices,
            vertexConsumers,
        )
        lines.forEachIndexed { index, (line, color) ->
            WorldPanelRenderer.drawText(
                textRenderer,
                line,
                -textRenderer.getWidth(line) / 2f,
                -totalHeight / 2f + index * lineHeight,
                color,
                TEXT_Z,
                light,
                matrices,
                vertexConsumers,
            )
        }
        matrices.pop()
    }

    override fun getTexture(entity: MahjongLobbyInfoEntity): Identifier? = null

    private companion object {
        const val TEXT_SCALE = 0.025f
        const val LINE_SPACING = 3
        const val PANEL_PADDING = 6f
        const val TEXT_Z = -0.02f
        const val BACKGROUND_COLOR = 0xB01A2232.toInt()
        const val TITLE_COLOR = 0xFFFFD45A.toInt()
        const val TEXT_COLOR = 0xFFFFFFFF.toInt()
        const val FULL_COLOR = 0xFFFF9D45.toInt()
        const val HINT_COLOR = 0xFFAAAAAA.toInt()
    }
}
