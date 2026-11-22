package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.table

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ClientEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.RenderType
import net.minecraft.world.phys.AABB

/**
 * 在世界中以紅色線框標出等待開局桌子被擋住的擺牌格。
 *
 * @property store 被擋住的格子。
 */
class TableSpaceObstructionRenderer(
    private val store: ClientTableSpaceObstructionStore,
) {
    /**
     * 註冊世界繪製事件。
     *
     * @param events 登記用戶端事件用的事件來源。
     */
    fun registerEvents(events: ClientEvents) {
        events.onWorldRenderBeforeDebug { matrices, consumers, cameraState ->
            val world = Minecraft.getInstance().level ?: return@onWorldRenderBeforeDebug
            val cells = store.cellsIn(world.dimension().location().toString())
            if (cells.isEmpty()) return@onWorldRenderBeforeDebug
            val camera = cameraState.position
            val lines = consumers.getBuffer(RenderType.lines())
            cells.forEach { cell ->
                val box = AABB(
                    cell.x.toDouble(),
                    cell.y.toDouble(),
                    cell.z.toDouble(),
                    cell.x + 1.0,
                    cell.y + 1.0,
                    cell.z + 1.0,
                ).inflate(OUTLINE_INFLATION).move(-camera.x, -camera.y, -camera.z)
                LevelRenderer.renderLineBox(matrices, lines, box, RED, GREEN, BLUE, ALPHA)
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
