package com.doublemoon1119.mahjongcraft.platform.forge.mixin;

import com.doublemoon1119.mahjongcraft.platform.forge.loader.ForgeModelLoadings;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 讀取模型時先問程式能否提供模型，能的話就不讀模型檔。
 *
 * <p>Forge 沒有為沒有模型檔的 ID 提供模型的事件；這裡在 Fabric API 提供同名功能的位置，交給 {@link ForgeModelLoadings}。</p>
 */
@Mixin(ModelBakery.class)
public abstract class ModelBakeryMixin {
    /**
     * 記錄模型並排入它依賴的模型。
     *
     * @param id 模型 ID。
     * @param model 未烘焙模型。
     */
    @Shadow
    protected abstract void cacheAndQueueDependencies(ResourceLocation id, UnbakedModel model);

    /** 程式提供模型時改用它，並略過讀取模型檔。 */
    @Inject(method = "loadModel", at = @At("HEAD"), cancellable = true)
    private void mahjongcraft$resolveProvidedModel(ResourceLocation id, CallbackInfo callbackInfo) {
        UnbakedModel model = ForgeModelLoadings.resolve(id);
        if (model != null) {
            cacheAndQueueDependencies(id, model);
            callbackInfo.cancel();
        }
    }
}
