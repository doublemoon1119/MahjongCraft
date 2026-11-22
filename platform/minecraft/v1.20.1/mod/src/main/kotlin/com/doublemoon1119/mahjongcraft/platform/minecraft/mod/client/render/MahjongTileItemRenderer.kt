package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.render

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.model.FixedMahjongTileModelPreparedData
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.model.MahjongTileModelLoadingPlugin
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.render.AlphaVertexConsumer
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.item.MahjongTileItem
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ClientModels
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ItemStackRenderer
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileSetAppearanceDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.UNKNOWN_TILE_ASSET_KEY
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.tileModelResourceId
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.tileTextureAssetPath
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.tileTextureResourceId
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.resources.model.BakedModel
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.RandomSource
import net.minecraft.world.inventory.InventoryMenu
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import org.joml.Vector3f
import org.koin.core.context.GlobalContext

/**
 * 麻將牌 item 的 runtime renderer；依 NBT 存的 asset key 動態選擇材質，取代固定的 item model
 * predicate override 清單，讓內建與第三方 registry 註冊的牌面都能正確顯示。
 *
 * 所有 asset key（見 `allTileAssetKeys`）透過 [MahjongTileModelLoadingPlugin]
 * 登記為可獨立查詢的已烘焙模型，直接重繪其原始 quad，幾何與 UV 與遷移前完全一致。找不到
 * 已烘焙模型的 asset key，改為手繪與內建模型相同的立方體幾何，正面材質直接綁定 runtime 解析的
 * [ResourceLocation]，不經過 atlas；缺少材質時退回內建 unknown 牌面，不靜默顯示錯誤畫面。
 */
object MahjongTileItemRenderer : ItemStackRenderer {
    private val logger = mahjongCraftLogger(MahjongTileItemRenderer::class)

    /** 已回報缺少材質警告的 asset key，避免同一個 key 每幀重複記錄。 */
    private val reportedMissingTextureKeys = mutableSetOf<String>()

    /** 取得已烘焙模型使用的固定隨機來源；此模型沒有隨機變體，種子值本身不影響外觀。 */
    private val quadRandom = RandomSource.create(42L)

    /** vanilla 供已烘焙模型使用的共用材質 atlas，item／block model 的 sprite 皆繪製於此。 */
    private val BLOCK_ATLAS_TEXTURE: ResourceLocation = InventoryMenu.BLOCK_ATLAS

    /** 半透明牌繪製時的縮放倍率；略小於 1，避免貼著桌面的面與桌面重疊閃爍，肉眼看不出大小差異。 */
    private const val TRANSLUCENT_TILE_SCALE: Float = 0.995f

    override fun render(
        stack: ItemStack,
        mode: ItemDisplayContext,
        matrices: PoseStack,
        vertexConsumers: MultiBufferSource,
        light: Int,
        overlay: Int,
    ) {
        renderInternal(
            assetKey = MahjongTileItem.readTileAssetKey(stack),
            appearanceId = null,
            mode = mode,
            matrices = matrices,
            vertexConsumers = vertexConsumers,
            light = light,
            overlay = overlay,
        )
    }

    /**
     * 以公開牌組外觀繪製一張可見牌；牌的幾何與 display 仍由固定模型模板提供。
     *
     * @param appearanceId 牌組共用外觀的完整識別碼。
     * @param assetKey 牌面素材 key。
     * @param mode Minecraft item 顯示模式。
     * @param matrices 目前的模型矩陣。
     * @param vertexConsumers 頂點緩衝提供者。
     * @param light 世界光照值。
     * @param overlay 覆蓋貼圖座標。
     * @param alpha 0 到 1 之間的不透明度；小於 1 時以半透明圖層畫出淡化的牌。
     */
    fun renderWithAppearance(
        appearanceId: String,
        assetKey: String,
        mode: ItemDisplayContext,
        matrices: PoseStack,
        vertexConsumers: MultiBufferSource,
        light: Int,
        overlay: Int,
        alpha: Float = 1f,
    ) {
        matrices.pushPose()
        matrices.translate(-0.5, -0.5, -0.5)
        try {
            renderInternal(
                assetKey = assetKey,
                appearanceId = appearanceId,
                mode = mode,
                matrices = matrices,
                vertexConsumers = vertexConsumers,
                light = light,
                overlay = overlay,
                alpha = alpha,
            )
        } finally {
            matrices.popPose()
        }
    }

    /**
     * 以公開牌組外觀繪製一張未知牌；不會讀取或推導未知牌背後的真實牌種。
     *
     * @param appearanceId 牌組共用外觀的完整識別碼。
     * @param mode Minecraft item 顯示模式。
     * @param matrices 目前的模型矩陣。
     * @param vertexConsumers 頂點緩衝提供者。
     * @param light 世界光照值。
     * @param overlay 覆蓋貼圖座標。
     */
    fun renderUnknownWithAppearance(
        appearanceId: String,
        mode: ItemDisplayContext,
        matrices: PoseStack,
        vertexConsumers: MultiBufferSource,
        light: Int,
        overlay: Int,
    ) {
        matrices.pushPose()
        matrices.translate(-0.5, -0.5, -0.5)
        try {
            val registry = GlobalContext.get().get<MinecraftTileAssetRegistry>()
            val appearance = registry.findAppearance(appearanceId)?.let(MahjongTileModelLoadingPlugin::effectiveAppearance)
                ?: return renderInternal(
                    assetKey = UNKNOWN_TILE_ASSET_KEY,
                    appearanceId = null,
                    mode = mode,
                    matrices = matrices,
                    vertexConsumers = vertexConsumers,
                    light = light,
                    overlay = overlay,
                )
            renderModel(
                model = findBakedModel(FixedMahjongTileModelPreparedData.syntheticUnknownModelId(appearance.id)),
                fallbackTextureId = ResourceLocation(appearance.unknownFaceTextureId),
                coverTextureId = ResourceLocation(appearance.coverTextureId),
                mode = mode,
                matrices = matrices,
                vertexConsumers = vertexConsumers,
                light = light,
                overlay = overlay,
            )
        } finally {
            matrices.popPose()
        }
    }

    /**
     * 以公開牌組外觀繪製這張牌時是否使用半透明圖層；與 [renderWithAppearance] 選擇圖層的依據相同。
     *
     * @param appearanceId 牌組共用外觀的完整識別碼。
     * @param assetKey 牌面素材 key。
     * @return 牌身外觀為半透明時為 true。
     */
    fun isTranslucent(appearanceId: String, assetKey: String): Boolean {
        val registry = GlobalContext.get().get<MinecraftTileAssetRegistry>()
        return effectiveAppearance(registry, assetKey, appearanceId)?.translucent == true
    }

    /**
     * 一張牌實際使用的牌身外觀：牌面指定的牌身外觀優先，其次是規則的牌組外觀。
     *
     * @param registry 牌面素材登記表。
     * @param assetKey 牌面素材 key。
     * @param appearanceId 規則的牌組外觀識別碼；未指定時為 null。
     * @return 生效的外觀；兩者都沒有時為 null。
     */
    private fun effectiveAppearance(
        registry: MinecraftTileAssetRegistry,
        assetKey: String,
        appearanceId: String?,
    ): MinecraftTileSetAppearanceDefinition? = (registry.findBodyAppearance(assetKey) ?: appearanceId?.let(registry::findAppearance))
        ?.let(MahjongTileModelLoadingPlugin::effectiveAppearance)

    /**
     * 依牌面 key 與牌組外觀選擇已烘焙或固定幾何 fallback 後繪製牌面；外觀樣式指定牌身時改用該牌身外觀。
     *
     * @param assetKey 牌面素材 key。
     * @param appearanceId 規則的牌組外觀識別碼；未指定且樣式沒有指定牌身時使用原生模型。
     * @param mode Minecraft item 顯示模式。
     * @param matrices 目前的模型矩陣。
     * @param vertexConsumers 頂點緩衝提供者。
     * @param light 世界光照值。
     * @param overlay 覆蓋貼圖座標。
     * @param alpha 0 到 1 之間的不透明度。
     */
    private fun renderInternal(
        assetKey: String,
        appearanceId: String?,
        mode: ItemDisplayContext,
        matrices: PoseStack,
        vertexConsumers: MultiBufferSource,
        light: Int,
        overlay: Int,
        alpha: Float = 1f,
    ) {
        val registry = GlobalContext.get().get<MinecraftTileAssetRegistry>()
        val appearance = effectiveAppearance(registry, assetKey, appearanceId)
        val resource = registry.findResources(assetKey)
        val textureId = resource?.textureId?.let(::ResourceLocation)
            ?: ResourceLocation(tileTextureResourceId(assetKey, registry))
        val coverTextureId = appearance?.coverTextureId?.let(::ResourceLocation)
            ?: ResourceLocation(MinecraftModMetadata.MOD_ID, tileTextureAssetPath("cover"))
        val bakedModel = if (appearance != null) {
            findBakedModel(FixedMahjongTileModelPreparedData.syntheticModelId(appearance.id, assetKey))
        } else {
            findBakedModel(assetKey)
        }
        val fallbackModel = appearance?.let {
            findBakedModel(FixedMahjongTileModelPreparedData.syntheticUnknownModelId(it.id))
        }
        renderModel(
            model = bakedModel,
            fallbackModel = fallbackModel,
            fallbackTextureId = textureId,
            coverTextureId = coverTextureId,
            mode = mode,
            matrices = matrices,
            vertexConsumers = vertexConsumers,
            light = light,
            overlay = overlay,
            assetKey = assetKey,
            translucent = appearance?.translucent == true,
            alpha = alpha,
        )
    }

    /**
     * 套用 item 模型 transform 並繪製已烘焙模型或動態固定幾何。
     *
     * @param model 已驗證的牌面模型；缺少時使用 fallback。
     * @param fallbackModel 同牌組的隱藏模型 fallback。
     * @param fallbackTextureId 缺少模型時使用的正面貼圖。
     * @param coverTextureId 缺少模型時使用的牌組共用牌體貼圖。
     * @param mode Minecraft item 顯示模式。
     * @param matrices 目前的模型矩陣。
     * @param vertexConsumers 頂點緩衝提供者。
     * @param light 世界光照值。
     * @param overlay 覆蓋貼圖座標。
     * @param assetKey 用於缺少資源警告的素材 key。
     * @param translucent 是否以半透明方式繪製，牌面從背面也看得到；用於帶半透明貼圖的牌身外觀。
     * @param alpha 0 到 1 之間的不透明度。
     */
    private fun renderModel(
        model: BakedModel?,
        fallbackModel: BakedModel? = null,
        fallbackTextureId: ResourceLocation,
        coverTextureId: ResourceLocation,
        mode: ItemDisplayContext,
        matrices: PoseStack,
        vertexConsumers: MultiBufferSource,
        light: Int,
        overlay: Int,
        assetKey: String = UNKNOWN_TILE_ASSET_KEY,
        translucent: Boolean = false,
        alpha: Float = 1f,
    ) {
        val client = Minecraft.getInstance()

        matrices.pushPose()
        // vanilla ItemRenderer.render(...BakedModel) 在呼叫 builtin renderer 前，已經對「這個
        // ItemStack 解析到的模型」（本身沒有 display 區塊的 builtin/entity marker，等同 identity
        // transform）無條件做過一次 translate(-0.5,-0.5,-0.5) 置中，此時已疊加在 matrices 最外層。
        // 若直接在這層之上套用子模型 transform，等同「先縮放旋轉、再置中」，順序與原本「先置中、
        // 再套用子模型 transform」相反，縮放後的極小幾何會被整整位移 0.5，造成明顯偏移。
        // 這裡先用 +0.5 抵銷 vanilla 疊加的置中，把 matrices 還原成呼叫此 render() 之前的狀態，
        // 再依原本順序（子模型 transform → 置中）重建，維持與遷移前完全相同的組合結果。
        matrices.translate(0.5, 0.5, 0.5)
        // 所有內建子模型共用同一個 display transform；找不到已烘焙模型時借用 unknown 子模型的
        // transform，避免手動抄寫 mahjong_tile_base.json 的 8 組數值造成打字誤差。
        val referenceTransform = (model ?: fallbackModel ?: findBakedModel(UNKNOWN_TILE_ASSET_KEY))?.transforms
        referenceTransform?.getTransform(mode)?.apply(false, matrices)
        // 半透明牌貼著桌面的面若與桌面同一平面會閃爍；以模型中心略微縮小，讓每一面都離開所接觸的平面。
        if (translucent) matrices.scale(TRANSLUCENT_TILE_SCALE, TRANSLUCENT_TILE_SCALE, TRANSLUCENT_TILE_SCALE)
        matrices.translate(-0.5, -0.5, -0.5)

        if (model != null) {
            drawBakedModel(model, matrices, vertexConsumers, light, overlay, translucent, alpha)
        } else if (fallbackModel != null && !client.resourceManager.getResource(fallbackTextureId).isPresent) {
            drawBakedModel(fallbackModel, matrices, vertexConsumers, light, overlay, translucent, alpha)
        } else {
            if (client.resourceManager.getResource(fallbackTextureId).isPresent) {
                drawDynamicCuboid(fallbackTextureId, coverTextureId, matrices, vertexConsumers, light, overlay, translucent, alpha)
            } else {
                if (reportedMissingTextureKeys.add(assetKey)) {
                    logger.warn("No mahjong tile texture found for asset key '{}'; falling back to unknown", assetKey)
                }
                findBakedModel(UNKNOWN_TILE_ASSET_KEY)?.let { unknownModel ->
                    drawBakedModel(unknownModel, matrices, vertexConsumers, light, overlay, alpha = alpha)
                }
            }
        }

        matrices.popPose()
    }

    /**
     * 牌使用的頂點緩衝：一般牌用鏤空圖層；半透明牌用不剔除背面的半透明圖層，讓牌面從背面也看得到；淡化的牌用剔除背面的
     * 半透明圖層並把不透明度乘上比例，與原版畫看得見的隱形生物相同。
     *
     * @param vertexConsumers 頂點緩衝提供者。
     * @param texture 圖層使用的貼圖。
     * @param translucent 是否為半透明牌。
     * @param alpha 0 到 1 之間的不透明度。
     * @return 頂點緩衝。
     */
    private fun tileBuffer(vertexConsumers: MultiBufferSource, texture: ResourceLocation, translucent: Boolean, alpha: Float): VertexConsumer = when {
        alpha < 1f -> AlphaVertexConsumer(vertexConsumers.getBuffer(RenderType.itemEntityTranslucentCull(texture)), alpha)
        translucent -> vertexConsumers.getBuffer(RenderType.entityTranslucent(texture))
        else -> vertexConsumers.getBuffer(RenderType.entityCutout(texture))
    }

    /**
     * 依 asset key 查詢 [MahjongTileModelLoadingPlugin] 登記的已烘焙模型。
     *
     * @param assetKey 要查詢的牌面資產 key。
     * @return 找到的有效烘焙模型；缺少或為 missing model 時回傳 `null`。
     */
    private fun findBakedModel(assetKey: String): BakedModel? {
        val registry = GlobalContext.get().get<MinecraftTileAssetRegistry>()
        return findBakedModel(ResourceLocation(tileModelResourceId(assetKey, registry)))
    }

    /**
     * 依完整 model ID 查詢已烘焙模型。
     *
     * @param modelId 要查詢的模型 ID。
     * @return 找到的有效烘焙模型；缺少或為 missing model 時回傳 `null`。
     */
    private fun findBakedModel(modelId: ResourceLocation): BakedModel? {
        val model = ClientModels.find(modelId) ?: return null
        return if (model === Minecraft.getInstance().modelManager.missingModel) null else model
    }

    /**
     * 直接重繪已烘焙模型的原始 quad，沿用 atlas sprite，不重算幾何。
     *
     * @param model 要繪製的烘焙模型。
     * @param matrices 目前的模型矩陣。
     * @param vertexConsumers 提供 atlas 頂點緩衝。
     * @param light 世界光照值。
     * @param overlay 覆蓋貼圖座標。
     * @param translucent 是否以不剔除背面的半透明圖層繪製。
     * @param alpha 0 到 1 之間的不透明度。
     */
    private fun drawBakedModel(
        model: BakedModel,
        matrices: PoseStack,
        vertexConsumers: MultiBufferSource,
        light: Int,
        overlay: Int,
        translucent: Boolean = false,
        alpha: Float = 1f,
    ) {
        val buffer = tileBuffer(vertexConsumers, BLOCK_ATLAS_TEXTURE, translucent, alpha)
        val entry = matrices.last()
        model.getQuads(null, null, quadRandom).forEach { quad ->
            buffer.putBulkData(entry, quad, 1.0f, 1.0f, 1.0f, light, overlay)
        }
    }

    /**
     * 找不到有效的已烘焙模型時，以內建立方體作為回退外觀，正面綁定登記的貼圖。
     * 其餘五面沿用內建牌體材質圖集；有效自訂模型的各面材質與 UV 則由模型自行定義。
     *
     * @param faceTextureId 動態解析的正面貼圖。
     * @param matrices 目前的模型矩陣。
     * @param vertexConsumers 提供正面貼圖與牌體材質圖集的頂點緩衝。
     * @param light 世界光照值。
     * @param overlay 覆蓋貼圖座標。
     * @param translucent 是否以半透明圖層繪製。
     * @param alpha 0 到 1 之間的不透明度。
     */
    private fun drawDynamicCuboid(
        faceTextureId: ResourceLocation,
        coverTextureId: ResourceLocation,
        matrices: PoseStack,
        vertexConsumers: MultiBufferSource,
        light: Int,
        overlay: Int,
        translucent: Boolean = false,
        alpha: Float = 1f,
    ) {
        // 此處只處理缺少有效模型的回退立方體，非正面五面沿用牌組共用 cover 牌體材質圖集。
        // 有效的自訂模型不經此流程，其各面材質與 UV 由模型 JSON 定義。
        val entry = matrices.last()
        val faceBuffer = tileBuffer(vertexConsumers, faceTextureId, translucent, alpha)
        val coverBuffer = tileBuffer(vertexConsumers, coverTextureId, translucent, alpha)

        CUBOID_FACES.forEach { face ->
            val buffer = if (face.usesFaceTexture) faceBuffer else coverBuffer
            face.corners.forEachIndexed { index, corner ->
                val (u, v) = face.uvAt(index)
                buffer.vertex(entry.pose(), corner.x, corner.y, corner.z)
                    .color(1.0f, 1.0f, 1.0f, 1.0f)
                    .uv(u, v)
                    .overlayCoords(overlay)
                    .uv2(light)
                    .normal(entry.normal(), face.normal.x, face.normal.y, face.normal.z)
                    .endVertex()
            }
        }
    }

    /**
     * 立方體單一面的幾何與材質對應。
     *
     * @property corners 此面的四個頂點。
     * @property normal 此面的法線方向。
     * @property uvRect 此面的 UV 範圍。
     * @property usesFaceTexture 是否使用正面貼圖；否則使用內建牌體材質圖集。
     */
    private class CuboidFace(
        val corners: List<Vector3f>,
        val normal: Vector3f,
        val uvRect: FloatArray,
        val usesFaceTexture: Boolean,
    ) {
        /**
         * 依頂點索引取得對應 UV；前三個索引對應前三角，其他值使用左下角座標；四角依序對應 uvRect 的 (u0,v0)/(u1,v0)/(u1,v1)/(u0,v1)。
         *
         * @param index 頂點在面內的索引。
         * @return 對應的 U、V 座標。
         */
        fun uvAt(index: Int): Pair<Float, Float> {
            val (u0, v0, u1, v1) = uvRect
            return when (index) {
                0 -> u0 to v0
                1 -> u1 to v0
                2 -> u1 to v1
                else -> u0 to v1
            }
        }
    }

    /**
     * 與 `mahjong_tile_base.json` 相同的立方體幾何（`from=[2,0,4]`、`to=[14,16,12]`，除以 16 換算為
     * 0..1 model 空間）。正面使用 runtime 材質，其餘五面沿用內建 `mahjong_tile_cover.png` 的 UV
     * 裁切比例（原始 uv 值除以宣告的 `texture_size` 64，與貼圖實際解析度無關）。
     */
    private val CUBOID_FACES: List<CuboidFace> = run {
        val x0 = 2f / 16f
        val x1 = 14f / 16f
        val y0 = 0f / 16f
        val y1 = 16f / 16f
        val z0 = 4f / 16f
        val z1 = 12f / 16f

        fun v(x: Float, y: Float, z: Float) = Vector3f(x, y, z)

        listOf(
            CuboidFace(
                corners = listOf(v(x0, y1, z0), v(x1, y1, z0), v(x1, y0, z0), v(x0, y0, z0)),
                normal = v(0f, 0f, -1f),
                uvRect = floatArrayOf(0f, 0f, 1f, 1f),
                usesFaceTexture = true,
            ),
            CuboidFace(
                corners = listOf(v(x1, y1, z1), v(x0, y1, z1), v(x0, y0, z1), v(x1, y0, z1)),
                normal = v(0f, 0f, 1f),
                uvRect = floatArrayOf(3f / 64f, 0f / 64f, 6f / 64f, 4f / 64f),
                usesFaceTexture = false,
            ),
            CuboidFace(
                corners = listOf(v(x1, y1, z0), v(x1, y1, z1), v(x1, y0, z1), v(x1, y0, z0)),
                normal = v(1f, 0f, 0f),
                uvRect = floatArrayOf(0f / 64f, 4f / 64f, 2f / 64f, 8f / 64f),
                usesFaceTexture = false,
            ),
            CuboidFace(
                corners = listOf(v(x0, y1, z1), v(x0, y1, z0), v(x0, y0, z0), v(x0, y0, z1)),
                normal = v(-1f, 0f, 0f),
                uvRect = floatArrayOf(2f / 64f, 4f / 64f, 4f / 64f, 8f / 64f),
                usesFaceTexture = false,
            ),
            CuboidFace(
                corners = listOf(v(x0, y1, z0), v(x0, y1, z1), v(x1, y1, z1), v(x1, y1, z0)),
                normal = v(0f, 1f, 0f),
                uvRect = floatArrayOf(7f / 64f, 6f / 64f, 4f / 64f, 4f / 64f),
                usesFaceTexture = false,
            ),
            CuboidFace(
                corners = listOf(v(x0, y0, z1), v(x0, y0, z0), v(x1, y0, z0), v(x1, y0, z1)),
                normal = v(0f, -1f, 0f),
                uvRect = floatArrayOf(9f / 64f, 0f / 64f, 6f / 64f, 2f / 64f),
                usesFaceTexture = false,
            ),
        )
    }
}
