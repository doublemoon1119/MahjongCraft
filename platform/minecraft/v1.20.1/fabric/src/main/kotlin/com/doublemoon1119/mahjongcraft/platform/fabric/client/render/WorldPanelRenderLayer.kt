package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.RenderPhase
import net.minecraft.client.render.VertexFormat
import net.minecraft.client.render.VertexFormats

/**
 * 世界面板的半透明純色底色。
 *
 * 只做深度測試、不寫入深度：面板由 [WorldPanelDeferral] 等整個世界畫完才畫，後方的實體、半透明方塊與雲都已經畫好，
 * 能透過底色看到；前方的東西照常擋住面板。
 */
internal object WorldPanelRenderLayer {
    /** 面板底色的四邊形使用的繪製圖層。 */
    val layer: RenderLayer = RenderLayer.of(
        "mahjongcraft_world_panel",
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
            .writeMaskState(RenderPhase.COLOR_MASK)
            .build(false),
    )
}
