package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.RenderPhase
import net.minecraft.client.render.VertexFormat
import net.minecraft.client.render.VertexFormats
import net.minecraft.util.Identifier
import java.util.concurrent.ConcurrentHashMap

/**
 * 供 showcase 標題圖片使用的無方向光透明層。
 *
 * 使用原版 position-color-texture-lightmap shader 保留光影模組相容性；該 shader 不讀取法線，
 * 並由 full-bright 頂點資料保持圖片原色。透明混合與深度寫入同時開啟，避免稍後繪製的雲層穿透標題。
 */
internal object WinCelebrationTitleRenderLayer {
    private val layers = ConcurrentHashMap<Identifier, RenderLayer>()

    fun get(texture: Identifier): RenderLayer = layers.computeIfAbsent(texture, ::create)

    private fun create(texture: Identifier): RenderLayer {
        val phases = RenderLayer.MultiPhaseParameters.builder()
            .texture(RenderPhase.Texture(texture, false, false))
            .program(RenderPhase.POSITION_COLOR_TEXTURE_LIGHTMAP_PROGRAM)
            .transparency(RenderPhase.TRANSLUCENT_TRANSPARENCY)
            .depthTest(RenderPhase.LEQUAL_DEPTH_TEST)
            .cull(RenderPhase.DISABLE_CULLING)
            .lightmap(RenderPhase.DISABLE_LIGHTMAP)
            .writeMaskState(RenderPhase.ALL_MASK)
            .build(false)
        return RenderLayer.of(
            "mahjongcraft_win_celebration_title",
            VertexFormats.POSITION_COLOR_TEXTURE_LIGHT,
            VertexFormat.DrawMode.QUADS,
            BUFFER_SIZE,
            false,
            true,
            phases,
        )
    }

    private const val BUFFER_SIZE = 256
}
