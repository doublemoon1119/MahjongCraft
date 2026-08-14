package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import com.doublemoon1119.mahjongcraft.platform.fabric.item.MahjongTileItem
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.UNKNOWN_TILE_ASSET_KEY
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.tileModelAssetPath
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.tileTextureAssetPath
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
import org.slf4j.LoggerFactory

/**
 * 麻將牌 item 的 runtime renderer；依 NBT 存的 asset key 動態選擇材質，取代固定的 item model
 * predicate override 清單，讓內建與第三方 registry 註冊的牌面都能正確顯示。
 *
 * 內建 46 種 asset key 透過 [MahjongTileModelLoadingPlugin][com.doublemoon1119.mahjongcraft.platform.fabric.client.model.MahjongTileModelLoadingPlugin]
 * 登記為可獨立查詢的已烘焙模型，直接重繪其原始 quad，幾何與 UV 與遷移前完全一致。第三方或找不到
 * 已烘焙模型的 asset key，改為手繪與內建模型相同的立方體幾何，正面材質直接綁定 runtime 解析的
 * [Identifier]，不經過 atlas；缺少材質時退回內建 unknown 牌面，不靜默顯示錯誤畫面。
 */
object MahjongTileItemRenderer : BuiltinItemRendererRegistry.DynamicItemRenderer {
    private val logger = LoggerFactory.getLogger(MinecraftModMetadata.MOD_ID)

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
        val assetKey = MahjongTileItem.readTileAssetKey(stack)
        val client = MinecraftClient.getInstance()
        val bakedModel = findBakedModel(assetKey)

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
        val referenceTransform = (bakedModel ?: findBakedModel(UNKNOWN_TILE_ASSET_KEY))?.transformation
        referenceTransform?.getTransformation(mode)?.apply(false, matrices)
        matrices.translate(-0.5, -0.5, -0.5)

        if (bakedModel != null) {
            drawBakedModel(bakedModel, matrices, vertexConsumers, light, overlay)
        } else {
            val textureId = Identifier(MinecraftModMetadata.MOD_ID, tileTextureAssetPath(assetKey))
            if (client.resourceManager.getResource(textureId).isPresent) {
                drawDynamicCuboid(textureId, matrices, vertexConsumers, light, overlay)
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

    /** 依 asset key 查詢 [MahjongTileModelLoadingPlugin][com.doublemoon1119.mahjongcraft.platform.fabric.client.model.MahjongTileModelLoadingPlugin] 登記的已烘焙模型。 */
    private fun findBakedModel(assetKey: String): BakedModel? {
        val manager = MinecraftClient.getInstance().bakedModelManager
        val id = Identifier(MinecraftModMetadata.MOD_ID, tileModelAssetPath(assetKey))
        val model = (manager as FabricBakedModelManager).getModel(id) ?: return null
        return if (model === manager.missingModel) null else model
    }

    /** 直接重繪已烘焙模型的原始 quad，沿用 atlas sprite，不重算幾何。 */
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

    /** 找不到已烘焙模型時，手繪與內建模型相同的立方體幾何，正面材質直接綁定。 */
    private fun drawDynamicCuboid(
        faceTextureId: Identifier,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
        light: Int,
        overlay: Int,
    ) {
        val coverTextureId = Identifier(MinecraftModMetadata.MOD_ID, tileTextureAssetPath("cover"))
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

    /** 立方體單一面的幾何與材質對應。 */
    private class CuboidFace(
        val corners: List<Vector3f>,
        val normal: Vector3f,
        val uvRect: FloatArray,
        val usesFaceTexture: Boolean,
    ) {
        /** 依頂點索引取得對應的 UV 座標，四個角依序對應 uvRect 的 (u0,v0)/(u1,v0)/(u1,v1)/(u0,v1)。 */
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

    private operator fun FloatArray.component1() = this[0]
    private operator fun FloatArray.component2() = this[1]
    private operator fun FloatArray.component3() = this[2]
    private operator fun FloatArray.component4() = this[3]

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
