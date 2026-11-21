package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.renderer.RenderStateShard
import net.minecraft.client.renderer.RenderType
import net.minecraft.resources.ResourceLocation
import java.util.concurrent.ConcurrentHashMap

/** 中央結算面板的牌面材質層；正常測試並寫入深度，與排行榜背景採相同的世界空間規則。 */
internal object ExhaustiveDrawSettlementTileFaceRenderLayer {
    private val layers = ConcurrentHashMap<ResourceLocation, RenderType>()

    /**
     * 取得指定牌面材質的 full-bright 半透明層；同一材質只建立一次。
     *
     * @param texture 牌面貼圖識別碼。
     * @return 對應貼圖的 render layer。
     */
    fun get(texture: ResourceLocation): RenderType = layers.computeIfAbsent(texture, ::create)

    /**
     * 建立單一貼圖的 render layer。
     *
     * @param texture 此層使用的牌面貼圖。
     * @return 使用此貼圖設定的 render layer。
     */
    private fun create(texture: ResourceLocation): RenderType = RenderType.create(
        "mahjongcraft_exhaustive_draw_settlement_tile_face",
        DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
        VertexFormat.Mode.QUADS,
        BUFFER_SIZE,
        false,
        true,
        RenderType.CompositeState.builder()
            .setTextureState(RenderStateShard.TextureStateShard(texture, false, false))
            .setShaderState(RenderStateShard.POSITION_COLOR_TEX_LIGHTMAP_SHADER)
            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
            .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
            .setCullState(RenderStateShard.NO_CULL)
            .setLightmapState(RenderStateShard.NO_LIGHTMAP)
            .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
            .createCompositeState(false),
    )

    private const val BUFFER_SIZE = 256
}
