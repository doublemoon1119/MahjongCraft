package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.model

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.vanilla.resourceId
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.BuiltInMinecraftTileAppearances
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileResources
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileSetAppearanceDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.allTileAssetKeys
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.Resource
import net.minecraft.server.packs.resources.ResourceManager
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * 一次資源重載的固定牌模型變體快照。
 *
 * @property variants 已通過模板檢查且可交給 resolver 的變體。
 * @property effectiveAppearances 已通過完整外觀資源檢查的牌組外觀。
 */
data class FixedMahjongTileModelPreparedData(
    val variants: List<FixedMahjongTileModelVariant>,
    private val effectiveAppearances: Map<String, MinecraftTileSetAppearanceDefinition>,
) {
    /** 依 synthetic model ID 查詢的變體。 */
    private val variantsById: Map<ResourceLocation, FixedMahjongTileModelVariant> = variants.associateBy { it.modelId }

    /**
     * 依 synthetic model ID 取得變體。
     *
     * @param modelId 要載入的模型 ID。
     * @return 對應的變體；這個 ID 不是本次準備的變體時為 null。
     */
    fun variant(modelId: ResourceLocation): FixedMahjongTileModelVariant? = variantsById[modelId]

    /**
     * 依本次資源重載結果取得有效外觀，完整外觀無法使用時回退內建預設。
     *
     * @param requested 要求使用的牌組外觀。
     * @return 本次資源重載中可完整使用的外觀。
     */
    fun effectiveAppearance(requested: MinecraftTileSetAppearanceDefinition): MinecraftTileSetAppearanceDefinition = effectiveAppearances[requested.id]
        ?: effectiveAppearances[BuiltInMinecraftTileAppearances.DEFAULT.id]
        ?: BuiltInMinecraftTileAppearances.DEFAULT

    /** 依目前 registry 建立可見與未知牌模型變體。 */
    companion object {
        private val logger = mahjongCraftLogger(FixedMahjongTileModelPreparedData::class)

        /**
         * 依目前 registry 建立可見與未知牌模型變體。
         *
         * @param registry 已凍結的牌面與牌組外觀 registry。
         * @param resourceManager 本次資源重載使用的資源管理器。
         * @return 通過固定模型契約檢查的變體快照。
         */
        fun prepare(
            registry: MinecraftTileAssetRegistry,
            resourceManager: ResourceManager,
        ): FixedMahjongTileModelPreparedData {
            val appearances = (registry.registeredAppearances + BuiltInMinecraftTileAppearances.DEFAULT)
                .distinctBy { it.id }
            val validAppearances = appearances.filter { appearance ->
                val coverTexture = resourceId(appearance.coverTextureId)
                val unknownFaceTexture = resourceId(appearance.unknownFaceTextureId)
                resourceManager.hasTexture(coverTexture) &&
                    resourceManager.hasTexture(unknownFaceTexture)
            }
            val variants = buildList {
                validAppearances.forEach { appearance ->
                    val coverTexture = resourceId(appearance.coverTextureId)
                    val unknownFaceTexture = resourceId(appearance.unknownFaceTextureId)
                    registry.allTileAssetKeys().forEach { assetKey ->
                        val resources = registry.findResources(assetKey) ?: MinecraftTileResources.builtIn(assetKey)
                        val sourceModel = resourceId(resources.modelId)
                        val frontTexture = resourceId(resources.textureId)
                        val usableFrontTexture = if (resourceManager.hasTexture(frontTexture)) {
                            frontTexture
                        } else {
                            logger.warn("Using unknown face texture for tile asset {} in appearance {} because its front texture is missing", assetKey, appearance.id)
                            unknownFaceTexture
                        }
                        val isFixedModel = resourceManager.isFixedTileModel(sourceModel) { failure ->
                            logger.warn(
                                "Tile model {} for asset {} in appearance {} could not be read",
                                sourceModel,
                                assetKey,
                                appearance.id,
                                failure,
                            )
                        }
                        val usableTemplate = if (isFixedModel) {
                            sourceModel
                        } else {
                            logger.warn("Using canonical fixed tile template for asset {} in appearance {} because its model is missing or incompatible", assetKey, appearance.id)
                            CANONICAL_TILE_MODEL
                        }
                        add(
                            element = FixedMahjongTileModelVariant(
                                modelId = syntheticModelId(appearance.id, assetKey),
                                templateModelId = usableTemplate,
                                frontTextureId = usableFrontTexture,
                                coverTextureId = coverTexture,
                            ),
                        )
                    }
                    add(
                        element = FixedMahjongTileModelVariant(
                            modelId = syntheticUnknownModelId(appearance.id),
                            templateModelId = CANONICAL_TILE_MODEL,
                            frontTextureId = unknownFaceTexture,
                            coverTextureId = coverTexture,
                        ),
                    )
                }
            }
            return FixedMahjongTileModelPreparedData(
                variants = variants,
                effectiveAppearances = validAppearances.associateBy { it.id },
            )
        }

        /**
         * 建立包含 appearance 與可見 asset key 的 synthetic model ID。
         *
         * @param appearanceId 牌組外觀完整識別碼。
         * @param assetKey 牌面素材 key。
         * @return 不會與未知牌模型碰撞的 synthetic model ID。
         */
        fun syntheticModelId(appearanceId: String, assetKey: String): ResourceLocation = resourceId(appearanceId.substringBefore(':'), "mahjong_tile/appearance/visible/${encode(appearanceId)}/${encode(assetKey)}")

        /**
         * 建立只包含 appearance 的未知牌 synthetic model ID。
         *
         * @param appearanceId 牌組外觀完整識別碼。
         * @return 該牌組專用的未知牌 synthetic model ID。
         */
        fun syntheticUnknownModelId(appearanceId: String): ResourceLocation = resourceId(appearanceId.substringBefore(':'), "mahjong_tile/appearance/unknown/${encode(appearanceId)}")

        /**
         * 將任意識別字串編成只含小寫十六進位字元的模型路徑片段。
         *
         * @param value 原始識別字串。
         * @return 不含分隔符且可逆的 UTF-8 十六進位字串。
         */
        private fun encode(value: String): String = value.encodeToByteArray()
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}

/**
 * 判斷外觀描述是否指向可讀取的 Minecraft texture resource。
 *
 * @param textureId 要檢查的完整 texture resource ID。
 * @return 資源存在且路徑包含 `textures/` 與 `.png` 時為 `true`。
 */
private fun ResourceManager.hasTexture(textureId: ResourceLocation): Boolean = textureId.isFullTextureResource() && getResource(textureId).isPresent

/**
 * 檢查模型是否繼承固定基本模型，且沒有自行改變幾何或 display。
 *
 * @param modelId 要檢查的模型 ID。
 * @param onReadFailure 模型存在但無法讀取或解析時收到原因。
 * @return 模型符合固定牌模型契約時為 `true`。
 */
private fun ResourceManager.isFixedTileModel(modelId: ResourceLocation, onReadFailure: (Exception) -> Unit): Boolean {
    val json = getResource(modelId.toModelResourceId()).orElse(null)?.readJson(onReadFailure) ?: return false
    return try {
        json.get("parent")?.asString == CANONICAL_TILE_MODEL.toString() &&
            !json.has("elements") &&
            !json.has("display") &&
            !json.has("overrides")
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: RuntimeException) {
        onReadFailure(failure)
        false
    }
}

/**
 * 讀取一個模型 JSON 資源；取消仍向外傳遞，一般讀取或解析失敗交由呼叫端回退。
 *
 * @param onReadFailure 無法讀取或解析時收到原因。
 * @return 模型 JSON 物件；無法讀取或解析時為 `null`。
 */
private fun Resource.readJson(onReadFailure: (Exception) -> Unit): JsonObject? = try {
    open().use { JsonParser.parseReader(it.reader(StandardCharsets.UTF_8)).asJsonObject }
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: IOException) {
    onReadFailure(failure)
    null
} catch (failure: RuntimeException) {
    onReadFailure(failure)
    null
}

/**
 * 將模型 ID 轉換成資源管理器中的模型 JSON 路徑。
 *
 * @return 對應的 `models/...json` 資源 ID。
 */
private fun ResourceLocation.toModelResourceId(): ResourceLocation = resourceId(getNamespace(), "models/${getPath()}.json")

/** 判斷資源 ID 是否明確指向 `assets/<namespace>/textures/...png`。 */
private fun ResourceLocation.isFullTextureResource(): Boolean = getPath().startsWith("textures/") && getPath().endsWith(".png")

/** 固定牌模板的 canonical model ID。 */
private val CANONICAL_TILE_MODEL = resourceId("mahjongcraft", "item/mahjong_tile_base")
