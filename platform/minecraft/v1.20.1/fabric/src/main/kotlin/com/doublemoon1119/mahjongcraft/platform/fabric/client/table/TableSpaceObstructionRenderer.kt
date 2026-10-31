package com.doublemoon1119.mahjongcraft.platform.fabric.client.table

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.WorldRenderer
import net.minecraft.util.math.Box

/**
 * 在世界中以紅色線框標出等待開局桌子被擋住的擺牌格。
 *
 * @property store 被擋住的格子。
 */
class TableSpaceObstructionRenderer(
    private val store: ClientTableSpaceObstructionStore,
) {
    /** 註冊世界繪製事件。 */
    fun registerEvents() {
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register { context ->
            val world = MinecraftClient.getInstance().world ?: return@register
            val cells = store.cellsIn(world.registryKey.value.toString())
            if (cells.isEmpty()) return@register
            val consumers = context.consumers() ?: return@register
            val camera = context.camera().pos
            val lines = consumers.getBuffer(RenderLayer.getLines())
            cells.forEach { cell ->
                val box = Box(
                    cell.x.toDouble(),
                    cell.y.toDouble(),
                    cell.z.toDouble(),
                    cell.x + 1.0,
                    cell.y + 1.0,
                    cell.z + 1.0,
                ).expand(OUTLINE_INFLATION).offset(-camera.x, -camera.y, -camera.z)
                WorldRenderer.drawBox(context.matrixStack(), lines, box, RED, GREEN, BLUE, ALPHA)
            }
        }
    }

    /** 線框的顏色與大小。 */
    private companion object {
        /** 線框向外放大的距離，避免與方塊表面重疊閃爍。 */
        const val OUTLINE_INFLATION: Double = 0.002

        /** 線框的紅色分量。 */
        const val RED: Float = 1.0f

        /** 線框的綠色分量。 */
        const val GREEN: Float = 0.2f

        /** 線框的藍色分量。 */
        const val BLUE: Float = 0.2f

        /** 線框的不透明度。 */
        const val ALPHA: Float = 1.0f
    }
}
