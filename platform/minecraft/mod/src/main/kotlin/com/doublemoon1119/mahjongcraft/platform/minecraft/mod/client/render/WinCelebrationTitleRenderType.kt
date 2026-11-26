package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.render

import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.renderer.RenderStateShard
import net.minecraft.client.renderer.RenderType
import net.minecraft.resources.ResourceLocation
import java.util.concurrent.ConcurrentHashMap

/**
 * 供 showcase 標題圖片使用的無方向光透明層。
 *
 * 使用原版 position-color-texture-lightmap shader 保留光影模組相容性。該 shader 不讀取法線，
 * 並由 full-bright 頂點資料保持圖片原色。透明混合與深度寫入同時開啟，避免稍後繪製的雲層穿透標題。
 */
internal object WinCelebrationTitleRenderType {
    /** 以貼圖識別碼為鍵的 render layer 快取。 */
    private val layers = ConcurrentHashMap<ResourceLocation, RenderType>()

    /**
     * 取得或建立指定貼圖共用的標題 render layer。
     *
     * @param texture 標題圖片的資源識別碼。
     * @return 對應的快取 render layer。
     */
    fun get(texture: ResourceLocation): RenderType = layers.computeIfAbsent(texture, ::create)

    /**
     * 建立供該標題貼圖使用的透明、無方向光 render layer。
     *
     * @param texture 標題圖片的資源識別碼。
     * @return 啟用透明混合並寫入深度的 render layer。
     */
    private fun create(texture: ResourceLocation): RenderType {
        val phases = RenderType.CompositeState.builder()
            .setTextureState(RenderStateShard.TextureStateShard(texture, false, false))
            .setShaderState(RenderStateShard.POSITION_COLOR_TEX_LIGHTMAP_SHADER)
            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
            .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
            .setCullState(RenderStateShard.NO_CULL)
            .setLightmapState(RenderStateShard.NO_LIGHTMAP)
            .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
            .createCompositeState(false)
        return RenderType.create(
            "mahjongcraft_win_celebration_title",
            DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
            VertexFormat.Mode.QUADS,
            BUFFER_SIZE,
            false,
            true,
            phases,
        )
    }

    /** 標題 quad 使用的頂點緩衝容量。 */
    private const val BUFFER_SIZE = 256
}
