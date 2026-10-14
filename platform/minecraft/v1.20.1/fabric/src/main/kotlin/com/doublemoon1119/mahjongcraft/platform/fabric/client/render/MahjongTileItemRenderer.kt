package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import com.doublemoon1119.mahjongcraft.platform.fabric.client.model.FixedMahjongTileModelPreparedData
import com.doublemoon1119.mahjongcraft.platform.fabric.client.model.MahjongTileModelLoadingPlugin
import com.doublemoon1119.mahjongcraft.platform.fabric.item.MahjongTileItem
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.UNKNOWN_TILE_ASSET_KEY
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.tileModelResourceId
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.tileTextureAssetPath
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.tileTextureResourceId
import net.fabricmc.fabric.api.client.model.loading.v1.FabricBakedModelManager
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.model.BakedModel
import net.minecraft.client.render.model.json.ModelTransformationMode
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.item.ItemStack
import net.minecraft.screen.PlayerScreenHandler
import net.minecraft.util.Identifier
import net.minecraft.util.math.random.Random
import org.joml.Vector3f
import org.koin.core.context.GlobalContext

/**
 * 麻將牌 item 的 runtime renderer；依 NBT 存的 asset key 動態選擇材質，取代固定的 item model
 * predicate override 清單，讓內建與第三方 registry 註冊的牌面都能正確顯示。
 *
 * 所有 asset key（見 `allTileAssetKeys`）透過 [MahjongTileModelLoadingPlugin]
 * 登記為可獨立查詢的已烘焙模型，直接重繪其原始 quad，幾何與 UV 與遷移前完全一致。找不到
 * 已烘焙模型的 asset key，改為手繪與內建模型相同的立方體幾何，正面材質直接綁定 runtime 解析的
 * [Identifier]，不經過 atlas；缺少材質時退回內建 unknown 牌面，不靜默顯示錯誤畫面。
 */
object MahjongTileItemRenderer : BuiltinItemRendererRegistry.DynamicItemRenderer {
    private val logger = mahjongCraftLogger(MahjongTileItemRenderer::class)

    /** 已回報缺少材質警告的 asset key，避免同一個 key 每幀重複記錄。 */
    private val reportedMissingTextureKeys = mutableSetOf<String>()

    /** 取得已烘焙模型使用的固定隨機來源；此模型沒有隨機變體，種子值本身不影響外觀。 */
    private val quadRandom = Random.create(42L)

    /** vanilla 供已烘焙模型使用的共用材質 atlas，item／block model 的 sprite 皆繪製於此。 */
    private val BLOCK_ATLAS_TEXTURE: Identifier = PlayerScreenHandler.BLOCK_ATLAS_TEXTURE

    override fun render(
        stack: ItemStack,
        mode: ModelTransformationMode,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
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
     */
    fun renderWithAppearance(
        appearanceId: String,
        assetKey: String,
        mode: ModelTransformationMode,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
        light: Int,
        overlay: Int,
    ) {
        matrices.push()
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
            )
        } finally {
            matrices.pop()
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
        mode: ModelTransformationMode,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
        light: Int,
        overlay: Int,
    ) {
        matrices.push()
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
                fallbackTextureId = Identifier(appearance.unknownFaceTextureId),
                coverTextureId = Identifier(appearance.coverTextureId),
                mode = mode,
                matrices = matrices,
                vertexConsumers = vertexConsumers,
                light = light,
                overlay = overlay,
            )
        } finally {
            matrices.pop()
        }
    }

    /**
     * 依牌面 key 與牌組外觀選擇已烘焙或固定幾何 fallback 後繪製牌面。
     *
     * @param assetKey 牌面素材 key。
     * @param appearanceId 牌組外觀識別碼；未指定時使用原生模型。
     * @param mode Minecraft item 顯示模式。
     * @param matrices 目前的模型矩陣。
     * @param vertexConsumers 頂點緩衝提供者。
     * @param light 世界光照值。
     * @param overlay 覆蓋貼圖座標。
     */
    private fun renderInternal(
        assetKey: String,
        appearanceId: String?,
        mode: ModelTransformationMode,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
        light: Int,
        overlay: Int,
    ) {
        val registry = GlobalContext.get().get<MinecraftTileAssetRegistry>()
        val appearance = appearanceId?.let(registry::findAppearance)
            ?.let(MahjongTileModelLoadingPlugin::effectiveAppearance)
        val resource = registry.findResources(assetKey)
        val textureId = resource?.textureId?.let(::Identifier)
            ?: Identifier(tileTextureResourceId(assetKey, registry))
        val coverTextureId = appearance?.coverTextureId?.let(::Identifier)
            ?: Identifier(MinecraftModMetadata.MOD_ID, tileTextureAssetPath("cover"))
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
     */
    private fun renderModel(
        model: BakedModel?,
        fallbackModel: BakedModel? = null,
        fallbackTextureId: Identifier,
        coverTextureId: Identifier,
        mode: ModelTransformationMode,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
        light: Int,
        overlay: Int,
        assetKey: String = UNKNOWN_TILE_ASSET_KEY,
    ) {
        val client = MinecraftClient.getInstance()

        matrices.push()
        // vanilla ItemRenderer.renderItem(...BakedModel) 在呼叫 builtin renderer 前，已經對「這個
        // ItemStack 解析到的模型」（本身沒有 display 區塊的 builtin/entity marker，等同 identity
        // transform）無條件做過一次 translate(-0.5,-0.5,-0.5) 置中，此時已疊加在 matrices 最外層。
        // 若直接在這層之上套用子模型 transform，等同「先縮放旋轉、再置中」，順序與原本「先置中、
        // 再套用子模型 transform」相反，縮放後的極小幾何會被整整位移 0.5，造成明顯偏移。
        // 這裡先用 +0.5 抵銷 vanilla 疊加的置中，把 matrices 還原成呼叫此 render() 之前的狀態，
        // 再依原本順序（子模型 transform → 置中）重建，維持與遷移前完全相同的組合結果。
        matrices.translate(0.5, 0.5, 0.5)
        // 所有內建子模型共用同一個 display transform；找不到已烘焙模型時借用 unknown 子模型的
        // transform，避免手動抄寫 mahjong_tile_base.json 的 8 組數值造成打字誤差。
        val referenceTransform = (model ?: fallbackModel ?: findBakedModel(UNKNOWN_TILE_ASSET_KEY))?.transformation
        referenceTransform?.getTransformation(mode)?.apply(false, matrices)
        matrices.translate(-0.5, -0.5, -0.5)

        if (model != null) {
            drawBakedModel(model, matrices, vertexConsumers, light, overlay)
        } else if (fallbackModel != null && !client.resourceManager.getResource(fallbackTextureId).isPresent) {
            drawBakedModel(fallbackModel, matrices, vertexConsumers, light, overlay)
        } else {
            if (client.resourceManager.getResource(fallbackTextureId).isPresent) {
                drawDynamicCuboid(fallbackTextureId, coverTextureId, matrices, vertexConsumers, light, overlay)
            } else {
                if (reportedMissingTextureKeys.add(assetKey)) {
                    logger.warn("No mahjong tile texture found for asset key '{}'; falling back to unknown", assetKey)
                }
                findBakedModel(UNKNOWN_TILE_ASSET_KEY)?.let { unknownModel ->
                    drawBakedModel(unknownModel, matrices, vertexConsumers, light, overlay)
                }
            }
        }

        matrices.pop()
    }

    /**
     * 依 asset key 查詢 [MahjongTileModelLoadingPlugin] 登記的已烘焙模型。
     *
     * @param assetKey 要查詢的牌面資產 key。
     * @return 找到的有效烘焙模型；缺少或為 missing model 時回傳 `null`。
     */
    private fun findBakedModel(assetKey: String): BakedModel? {
        val registry = GlobalContext.get().get<MinecraftTileAssetRegistry>()
        return findBakedModel(Identifier(tileModelResourceId(assetKey, registry)))
    }

    /**
     * 依完整 model ID 查詢已烘焙模型。
     *
     * @param modelId 要查詢的模型 ID。
     * @return 找到的有效烘焙模型；缺少或為 missing model 時回傳 `null`。
     */
    private fun findBakedModel(modelId: Identifier): BakedModel? {
        val manager = MinecraftClient.getInstance().bakedModelManager
        val model = (manager as FabricBakedModelManager).getModel(modelId) ?: return null
        return if (model === manager.missingModel) null else model
    }

    /**
     * 直接重繪已烘焙模型的原始 quad，沿用 atlas sprite，不重算幾何。
     *
     * @param model 要繪製的烘焙模型。
     * @param matrices 目前的模型矩陣。
     * @param vertexConsumers 提供 atlas 頂點緩衝。
     * @param light 世界光照值。
     * @param overlay 覆蓋貼圖座標。
     */
    private fun drawBakedModel(
        model: BakedModel,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
        light: Int,
        overlay: Int,
    ) {
        val buffer = vertexConsumers.getBuffer(RenderLayer.getEntityCutout(BLOCK_ATLAS_TEXTURE))
        val entry = matrices.peek()
        model.getQuads(null, null, quadRandom).forEach { quad ->
            buffer.quad(entry, quad, 1.0f, 1.0f, 1.0f, light, overlay)
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
     */
    private fun drawDynamicCuboid(
        faceTextureId: Identifier,
        coverTextureId: Identifier,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
        light: Int,
        overlay: Int,
    ) {
        // 此處只處理缺少有效模型的回退立方體，非正面五面沿用牌組共用 cover 牌體材質圖集。
        // 有效的自訂模型不經此流程，其各面材質與 UV 由模型 JSON 定義。
        val entry = matrices.peek()
        val faceBuffer = vertexConsumers.getBuffer(RenderLayer.getEntityCutout(faceTextureId))
        val coverBuffer = vertexConsumers.getBuffer(RenderLayer.getEntityCutout(coverTextureId))

        CUBOID_FACES.forEach { face ->
            val buffer = if (face.usesFaceTexture) faceBuffer else coverBuffer
            face.corners.forEachIndexed { index, corner ->
                val (u, v) = face.uvAt(index)
                buffer.vertex(entry.positionMatrix, corner.x, corner.y, corner.z)
                    .color(1.0f, 1.0f, 1.0f, 1.0f)
                    .texture(u, v)
                    .overlay(overlay)
                    .light(light)
                    .normal(entry.normalMatrix, face.normal.x, face.normal.y, face.normal.z)
                    .next()
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
