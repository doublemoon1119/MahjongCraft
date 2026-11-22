package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.render

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.config.MahjongClientConfigStore
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.entity.MahjongLobbyInfoEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.MinecraftRoomScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistry
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

/**
 * 將等待中遊戲渲染成面向觀看者的三行半透明提示面板。
 *
 * @param context Minecraft renderer factory 提供的 client context。
 * @param ruleNames 將規則模組 ID 解析為本地化名稱的 registry。
 * @param configStore 提供 lobby 面板可見性設定。
 */
class MahjongLobbyInfoEntityRenderer(
    context: EntityRendererProvider.Context,
    private val ruleNames: RuleModuleDisplayNameRegistry,
    private val configStore: MahjongClientConfigStore,
) : EntityRenderer<MahjongLobbyInfoEntity>(context) {
    private val textRenderer = context.font

    override fun render(
        entity: MahjongLobbyInfoEntity,
        yaw: Float,
        tickDelta: Float,
        matrices: PoseStack,
        vertexConsumers: MultiBufferSource,
        light: Int,
    ) {
        if (!configStore.current.presentationVisibility.lobbyInfoEnabled) return
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light)
        val ruleName = ruleNames.find(entity.ruleModuleId)?.let(Component::translatable) ?: Component.literal(entity.ruleModuleId)
        val lines = listOf(
            Component.translatable(MinecraftRoomScreenKeys.LOBBY_WAITING) to TITLE_COLOR,
            Component.translatable(
                MinecraftRoomScreenKeys.LOBBY_SUMMARY,
                ruleName,
                entity.playerCount,
                entity.maximumPlayerCount,
            ) to if (entity.playerCount >= entity.maximumPlayerCount) FULL_COLOR else TEXT_COLOR,
            Component.translatable(MinecraftRoomScreenKeys.LOBBY_VIEW_DETAILS) to HINT_COLOR,
        )
        WorldPanelDeferral.draw(matrices, vertexConsumers) { panelMatrices, panelConsumers ->
            panelMatrices.pushPose()
            panelMatrices.mulPose(entityRenderDispatcher.cameraOrientation())
            panelMatrices.scale(-TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE)
            val lineHeight = textRenderer.lineHeight + LINE_SPACING
            val totalHeight = lines.size * lineHeight
            val maximumWidth = lines.maxOf { textRenderer.width(it.first) }
            WorldPanelRenderer.drawBackground(
                left = -maximumWidth / 2f - PANEL_PADDING,
                top = -totalHeight / 2f - PANEL_PADDING,
                right = maximumWidth / 2f + PANEL_PADDING,
                bottom = totalHeight / 2f + PANEL_PADDING,
                color = BACKGROUND_COLOR,
                z = 0f,
                matrices = panelMatrices,
                consumers = panelConsumers,
            )
            lines.forEachIndexed { index, (line, color) ->
                WorldPanelRenderer.drawText(
                    renderer = textRenderer,
                    text = line,
                    x = -textRenderer.width(line) / 2f,
                    y = -totalHeight / 2f + index * lineHeight,
                    color = color,
                    z = TEXT_Z,
                    light = light,
                    matrices = panelMatrices,
                    consumers = panelConsumers,
                )
            }
            panelMatrices.popPose()
        }
    }

    override fun getTextureLocation(entity: MahjongLobbyInfoEntity): ResourceLocation? = null

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
