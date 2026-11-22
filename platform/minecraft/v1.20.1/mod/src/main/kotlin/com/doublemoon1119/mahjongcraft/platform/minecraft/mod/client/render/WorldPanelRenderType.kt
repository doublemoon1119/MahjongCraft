package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.render

import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.renderer.RenderStateShard
import net.minecraft.client.renderer.RenderType

/**
 * 世界面板的半透明純色底色。
 *
 * 只做深度測試、不寫入深度：面板由 [WorldPanelDeferral] 等整個世界畫完才畫，後方的實體、半透明方塊與雲都已經畫好，
 * 能透過底色看到；前方的東西照常擋住面板。
 */
internal object WorldPanelRenderType {
    /** 面板底色的四邊形使用的繪製圖層。 */
    val layer: RenderType = RenderType.create(
        "mahjongcraft_world_panel",
        DefaultVertexFormat.POSITION_COLOR,
        VertexFormat.Mode.QUADS,
        256,
        false,
        true,
        RenderType.CompositeState.builder()
            .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
            .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
            .setCullState(RenderStateShard.NO_CULL)
            .setWriteMaskState(RenderStateShard.COLOR_WRITE)
            .createCompositeState(false),
    )
}
