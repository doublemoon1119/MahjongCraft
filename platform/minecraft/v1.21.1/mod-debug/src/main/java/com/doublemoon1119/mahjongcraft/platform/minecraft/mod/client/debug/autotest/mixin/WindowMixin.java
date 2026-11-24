package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.debug.autotest.mixin;

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.debug.autotest.AutotestRuntime;
import com.mojang.blaze3d.platform.Window;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 自動測試期間建立的遊戲視窗出現時不搶走焦點；是否生效取決於作業系統。
 *
 * <p>載入器在原版之前就以自己的載入畫面建立視窗時（例如 Forge），原版不再建立視窗，這裡不生效；輸入仍由其他 mixin 忽略。</p>
 */
@Mixin(Window.class)
public abstract class WindowMixin {
    /** 建立視窗前要求顯示時不取得焦點。 */
    @Inject(
        method = "<init>",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J", remap = false),
        require = 0
    )
    private void mahjongcraft$doNotFocusOnShow(CallbackInfo callbackInfo) {
        if (AutotestRuntime.isActive()) {
            GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED, GLFW.GLFW_FALSE);
        }
    }
}
