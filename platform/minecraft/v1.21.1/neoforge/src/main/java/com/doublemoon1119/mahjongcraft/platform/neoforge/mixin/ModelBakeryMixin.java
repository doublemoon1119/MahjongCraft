package com.doublemoon1119.mahjongcraft.platform.neoforge.mixin;

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ModelLoadings;
import java.util.Map;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 讓沒有模型檔的 ID 取得程式提供的模型。
 *
 * <p>NeoForge 沒有為沒有模型檔的 ID 提供模型的事件；原版讀取模型時，先把 {@link ModelLoadings} 提供的模型放進未烘焙模型的快取，
 * 原版就不會讀取模型檔。模型依賴的其他模型由原版之後解析。</p>
 */
@Mixin(ModelBakery.class)
public abstract class ModelBakeryMixin {
    /** 原版已讀取的未烘焙模型。 */
    @Shadow @Final private Map<ResourceLocation, UnbakedModel> unbakedCache;

    /** 程式提供模型時先放進快取。 */
    @Inject(method = "getModel", at = @At("HEAD"))
    private void mahjongcraft$provideModel(ResourceLocation id, CallbackInfoReturnable<UnbakedModel> callbackInfo) {
        if (!unbakedCache.containsKey(id)) {
            UnbakedModel model = ModelLoadings.resolve(id);
            if (model != null) {
                unbakedCache.put(id, model);
            }
        }
    }
}
