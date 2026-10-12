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
    /** 以貼圖識別碼為鍵的 render layer 快取。 */
    private val layers = ConcurrentHashMap<Identifier, RenderLayer>()

    /**
     * 取得或建立指定貼圖共用的標題 render layer。
     *
     * @param texture 標題圖片的資源識別碼。
     * @return 對應的快取 render layer。
     */
    fun get(texture: Identifier): RenderLayer = layers.computeIfAbsent(texture, ::create)

    /**
     * 建立供該標題貼圖使用的透明、無方向光 render layer。
     *
     * @param texture 標題圖片的資源識別碼。
     * @return 啟用透明混合並寫入深度的 render layer。
     */
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

    /** 標題 quad 使用的頂點緩衝容量。 */
    private const val BUFFER_SIZE = 256
}
