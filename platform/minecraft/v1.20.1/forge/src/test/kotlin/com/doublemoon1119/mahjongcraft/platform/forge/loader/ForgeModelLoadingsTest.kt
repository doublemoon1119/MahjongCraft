package com.doublemoon1119.mahjongcraft.platform.forge.loader

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ModelLoading
import net.minecraft.client.renderer.block.model.BlockModel
import net.minecraft.client.resources.model.UnbakedModel
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/** 驗證模型載入在每次重載時準備資料，並只為準備好的 ID 提供模型。 */
class ForgeModelLoadingsTest {
    /** 準備後交出要額外載入的模型，並為這次準備的 ID 提供模型；其他 ID 交回原版。 */
    @Test
    fun `prepares each reload and resolves only prepared models`() {
        val first = BlockModel.fromString("{}")
        val second = BlockModel.fromString("{}")
        val loading = FixedModelLoading()
        ForgeModelLoadings.add(loading)

        loading.next = PROVIDED to first
        assertEquals(listOf(PROVIDED), ForgeModelLoadings.prepare(ResourceManager.Empty.INSTANCE))
        assertSame(first, ForgeModelLoadings.resolve(PROVIDED))
        assertNull(ForgeModelLoadings.resolve(ResourceLocation("test", "other")))

        loading.next = PROVIDED to second
        ForgeModelLoadings.prepare(ResourceManager.Empty.INSTANCE)
        assertSame(second, ForgeModelLoadings.resolve(PROVIDED), "A later reload must replace the prepared models")
    }

    /** 每次重載只提供一個指定模型的測試用模型載入。 */
    private class FixedModelLoading : ModelLoading<Pair<ResourceLocation, UnbakedModel>> {
        /** 下一次準備要提供的模型。 */
        lateinit var next: Pair<ResourceLocation, UnbakedModel>

        override fun prepare(resourceManager: ResourceManager, executor: Executor): CompletableFuture<Pair<ResourceLocation, UnbakedModel>> = CompletableFuture.supplyAsync({ next }, executor)

        override fun additionalModels(data: Pair<ResourceLocation, UnbakedModel>): Collection<ResourceLocation> = listOf(data.first)

        override fun resolveModel(data: Pair<ResourceLocation, UnbakedModel>, id: ResourceLocation): UnbakedModel? = data.second.takeIf { id == data.first }
    }

    private companion object {
        /** 程式提供的模型 ID。 */
        val PROVIDED = ResourceLocation("test", "provided")
    }
}
