package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.RenderPhase
import net.minecraft.client.render.VertexFormat
import net.minecraft.client.render.VertexFormats
import net.minecraft.util.Identifier
import java.util.concurrent.ConcurrentHashMap

/** 中央結算面板的牌面材質層；正常測試並寫入深度，與排行榜背景採相同的世界空間規則。 */
internal object ExhaustiveDrawSettlementTileFaceRenderLayer {
    private val layers = ConcurrentHashMap<Identifier, RenderLayer>()

    /** 取得指定牌面材質的 full-bright 半透明層；同一材質只建立一次。 */
    fun get(texture: Identifier): RenderLayer = layers.computeIfAbsent(texture, ::create)

    private fun create(texture: Identifier): RenderLayer = RenderLayer.of(
        "mahjongcraft_exhaustive_draw_settlement_tile_face",
        VertexFormats.POSITION_COLOR_TEXTURE_LIGHT,
        VertexFormat.DrawMode.QUADS,
        BUFFER_SIZE,
        false,
        true,
        RenderLayer.MultiPhaseParameters.builder()
            .texture(RenderPhase.Texture(texture, false, false))
            .program(RenderPhase.POSITION_COLOR_TEXTURE_LIGHTMAP_PROGRAM)
            .transparency(RenderPhase.TRANSLUCENT_TRANSPARENCY)
            .depthTest(RenderPhase.LEQUAL_DEPTH_TEST)
            .cull(RenderPhase.DISABLE_CULLING)
            .lightmap(RenderPhase.DISABLE_LIGHTMAP)
            .writeMaskState(RenderPhase.ALL_MASK)
            .build(false),
    )

    private const val BUFFER_SIZE = 256
}
