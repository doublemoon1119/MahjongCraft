package com.doublemoon1119.mahjongcraft.platform.fabric.mixin.client;

import net.minecraft.client.render.entity.EntityRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 預覽截圖保存原本的陰影與碰撞框顯示狀態，不改變正常世界渲染設定。 */
@Mixin(EntityRenderDispatcher.class)
public interface EntityRenderDispatcherAccessor {
    /** 取得目前是否繪製陰影。 */
    @Accessor("renderShadows")
    boolean mahjongcraft$getRenderShadows();

    /** 取得目前是否繪製除錯碰撞框。 */
    @Accessor("renderHitboxes")
    boolean mahjongcraft$getRenderHitboxes();
}
