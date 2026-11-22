package com.doublemoon1119.mahjongcraft.platform.fabric.client.model

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.model.FixedMahjongTileModelPreparedData
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.model.FixedMahjongTileModelVariant
import net.fabricmc.fabric.api.client.model.loading.v1.ModelResolver
import net.minecraft.client.renderer.block.model.BlockModel
import net.minecraft.client.resources.model.ModelBakery
import net.minecraft.client.resources.model.UnbakedModel
import net.minecraft.resources.ResourceLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** 驗證固定牌模型的材質變體能保留模板並接入 Fabric 的 synthetic model resolver。 */
class FixedMahjongTileModelVariantTest {
    /** 變體覆寫正面、牌體與粒子材質，但仍以模板提供 parent 與 display。 */
    @Test
    fun `variant overrides tile textures while preserving template parent`() {
        val variant = variant()
        val model = variant.toUnbakedModel()

        assertEquals(variant.modelId.toString(), model.name)
        assertEquals(setOf(variant.templateModelId), model.dependencies.toSet())
        assertEquals(
            expected = ResourceLocation("example", "item/mahjong_tile/inverted_five_pin"),
            actual = model.getMaterial("0").texture(),
        )
        assertEquals(
            expected = ResourceLocation("example", "item/mahjong_tile/example_cover"),
            actual = model.getMaterial("1").texture(),
        )
        assertEquals(
            expected = ResourceLocation("example", "item/mahjong_tile/example_cover"),
            actual = model.getMaterial("particle").texture(),
        )

        val template = BlockModel.fromString(
            """
            {
              "display": {"gui": {"rotation": [15, 150, 0], "scale": [0.7, 0.7, 0.7]}}
            }
            """.trimIndent(),
        )
        model.resolveParents { id -> if (id == variant.templateModelId) template else null }
        assertEquals(template.transforms.gui, model.transforms.gui)
    }

    /** resolver 只接管已登記的 synthetic ID，其他模型交回 vanilla 載入流程。 */
    @Test
    fun `resolver selects registered synthetic model before baking`() {
        val variant = variant()
        val resolver = FixedMahjongTileModelVariantResolver(listOf(variant))
        val context = TestResolverContext(variant.modelId)

        assertNotNull(resolver.resolveModel(context))
        assertNull(resolver.resolveModel(context.copy(requestedId = ResourceLocation("example", "item/other"))))
    }

    /** 可見與未知牌 synthetic ID 不會因為 asset key 剛好叫 unknown 而互相覆蓋。 */
    @Test
    fun `synthetic ids separate visible and unknown variants`() {
        val visible = FixedMahjongTileModelPreparedData.syntheticModelId("example:tiles", "unknown")
        val unknown = FixedMahjongTileModelPreparedData.syntheticUnknownModelId("example:tiles")

        assertNotEquals(visible, unknown)
        assertEquals("example", visible.namespace)
        assertEquals("example", unknown.namespace)
    }

    /** 建立使用第三方 namespace 的固定牌模型變體測試資料。 */
    private fun variant(): FixedMahjongTileModelVariant = FixedMahjongTileModelVariant(
        modelId = ResourceLocation("example", "mahjong_tile/variant/demo"),
        templateModelId = ResourceLocation("mahjongcraft", "item/mahjong_tile_base"),
        frontTextureId = ResourceLocation("example", "textures/item/mahjong_tile/inverted_five_pin.png"),
        coverTextureId = ResourceLocation("example", "textures/item/mahjong_tile/example_cover.png"),
    )

    /**
     * 提供 resolver 測試所需的最小模型載入上下文。
     *
     * @property requestedId 本次 resolver 查詢的 synthetic model ID。
     */
    private data class TestResolverContext(
        private val requestedId: ResourceLocation,
    ) : ModelResolver.Context {
        override fun id(): ResourceLocation = requestedId

        override fun getOrLoadModel(id: ResourceLocation): UnbakedModel = BlockModel.fromString("{}")

        override fun loader(): ModelBakery = error("Not needed by this test")
    }
}
