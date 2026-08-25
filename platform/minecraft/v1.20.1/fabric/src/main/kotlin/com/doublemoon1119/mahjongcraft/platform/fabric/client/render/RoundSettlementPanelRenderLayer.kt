package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.RenderPhase
import net.minecraft.client.render.VertexFormat
import net.minecraft.client.render.VertexFormats

/** 結算面板使用的半透明純色層；寫入自身深度，避免稍後繪製的雲層穿透面板。 */
internal object RoundSettlementPanelRenderLayer {
    val layer: RenderLayer = RenderLayer.of(
        "mahjongcraft_round_settlement_panel",
        VertexFormats.POSITION_COLOR,
        VertexFormat.DrawMode.QUADS,
        256,
        false,
        true,
        RenderLayer.MultiPhaseParameters.builder()
            .program(RenderPhase.COLOR_PROGRAM)
            .transparency(RenderPhase.TRANSLUCENT_TRANSPARENCY)
            .depthTest(RenderPhase.LEQUAL_DEPTH_TEST)
            .cull(RenderPhase.DISABLE_CULLING)
            .writeMaskState(RenderPhase.ALL_MASK)
            .build(false),
    )
}
