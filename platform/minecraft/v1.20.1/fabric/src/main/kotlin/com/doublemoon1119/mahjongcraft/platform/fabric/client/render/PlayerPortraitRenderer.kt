package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import com.doublemoon1119.mahjongcraft.platform.fabric.client.player.ClientPlayerProfileResolver
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PlayerPortraitSource
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PlayerPortraitSourceContext
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PlayerPortraitSourceProviderException
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PlayerPortraitSourceRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.ResolvedPlayerPortraitSource
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.UNKNOWN_TILE_ASSET_KEY
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.tileTextureResourceId
import kotlinx.coroutines.CancellationException
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.render.LightmapTextureManager
import net.minecraft.client.render.OverlayTexture
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.util.DefaultSkinHelper
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.Identifier
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import kotlin.math.roundToInt
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

/**
 * 世界結算、桌級面板及 GUI 共用的頭像來源繪製器。
 *
 * @property sources 規則與第三方提供的宣告式頭像來源。
 * @property tileAssetRegistry 提供 AI 牌面頭像使用的完整 namespace 與路徑映射。
 * @property profiles 真人普通名稱與原生皮膚材質的共用解析器。
 */
@Single
class PlayerPortraitRenderer(
    @Provided private val sources: PlayerPortraitSourceRegistry,
    @Provided private val tileAssetRegistry: MinecraftTileAssetRegistry,
    private val profiles: ClientPlayerProfileResolver,
) {
    /** 記錄外觀來源或繪製失敗，並遵守相同來源只警告一次的策略。 */
    private val logger = mahjongCraftLogger(PlayerPortraitRenderer::class)

    /** 已警告過的外觀來源提供者識別碼，避免每次繪製都重複記錄。 */
    private val warnedProviderIds = mutableSetOf<String>()

    /** 已解析的牌面貼圖快取，避免每次繪製都查詢資源管理器。 */
    private val tileTextures = mutableMapOf<String, Identifier>()

    /**
     * 將宣告式頭像來源繪製於世界空間的正方形 slot；牌面保持 3:4 比例並水平置中。
     * 一般來源失敗時使用內建頭像；取消例外與致命錯誤直接外傳。
     *
     * @param playerId 頭像所屬玩家 ID。
     * @param isAi 玩家是否為 AI，影響內建 fallback。
     * @param x slot 左上角 X 座標。
     * @param y slot 左上角 Y 座標。
     * @param size slot 邊長。
     * @param alpha 顯示透明度。
     * @param z 局部深度。
     * @param matrices 目前的模型矩陣。
     * @param consumers 提供頭像頂點緩衝。
     */
    fun render(
        playerId: Uuid,
        isAi: Boolean,
        x: Float,
        y: Float,
        size: Float,
        alpha: Float,
        z: Float,
        matrices: MatrixStack,
        consumers: VertexConsumerProvider,
    ) {
        renderPortraitWithFallback(
            resolved = runPresentationAttempt { sources.resolve(PlayerPortraitSourceContext(playerId, isAi)) },
            fallbackSource = fallbackSource(isAi),
            onResolutionFailure = { cause, providerId ->
                if (warnedProviderIds.add(providerId)) logger.warn("Failed to resolve player portrait provider {}", providerId, cause)
            },
            onDrawFailure = { cause, providerId ->
                if (warnedProviderIds.add(providerId)) logger.warn("Failed to render player portrait provider {}", providerId, cause)
            },
            draw = { source -> renderSource(source, playerId, x, y, size, alpha, z, matrices, consumers) },
        )
    }

    /**
     * 將同一 portrait source 繪製於 GUI；提供者失敗時使用內建來源。
     * 取消例外與致命錯誤直接外傳，不繼續繪製內建頭像。
     *
     * @param playerId 玩家識別碼。
     * @param isAi 是否為 AI。
     * @param context GUI 繪製上下文。
     * @param x 左上角水平座標。
     * @param y 左上角垂直座標。
     * @param size 頭像邊長。
     * @param skinTexture 已解析 profile 的原生材質，null 時交由共用 resolver 或預設材質處理。
     */
    fun renderGui(
        playerId: Uuid,
        isAi: Boolean,
        context: DrawContext,
        x: Int,
        y: Int,
        size: Int,
        skinTexture: Identifier? = null,
    ) {
        renderPortraitWithFallback(
            resolved = runPresentationAttempt { sources.resolve(PlayerPortraitSourceContext(playerId, isAi)) },
            fallbackSource = fallbackSource(isAi),
            onResolutionFailure = { cause, providerId ->
                if (warnedProviderIds.add(providerId)) logger.warn("Failed to resolve player portrait provider {}", providerId, cause)
            },
            onDrawFailure = { cause, providerId ->
                if (warnedProviderIds.add(providerId)) logger.warn("Failed to render player portrait provider {}", providerId, cause)
            },
            draw = { source -> renderGuiSource(source, playerId, context, x, y, size, skinTexture) },
        )
    }

    /**
     * 繪製已解析的頭像來源，不另外查詢第三方提供者。
     *
     * @param source 宣告式頭像來源。
     * @param playerId 玩家識別碼。
     * @param context GUI 繪製上下文。
     * @param x 左上角水平座標。
     * @param y 左上角垂直座標。
     * @param size 頭像邊長。
     * @param skinTexture 已解析的原生皮膚材質。
     */
    private fun renderGuiSource(
        source: PlayerPortraitSource,
        playerId: Uuid,
        context: DrawContext,
        x: Int,
        y: Int,
        size: Int,
        skinTexture: Identifier?,
    ) {
        when (source) {
            PlayerPortraitSource.PlayerSkinFace -> {
                val texture = skinTexture
                    ?: profiles.resolvedSkinTexture(playerId)
                    ?: MinecraftClient.getInstance().networkHandler?.getPlayerListEntry(playerId.toJavaUuid())?.skinTexture
                    ?: DefaultSkinHelper.getTexture(playerId.toJavaUuid())
                context.drawTexture(texture, x, y, size, size, 8f, 8f, 8, 8, 64, 64)
            }
            is PlayerPortraitSource.TileFace -> {
                val width = (size * TILE_ASPECT_RATIO).toInt()
                context.drawTexture(resolveTileTexture(source.assetKey), x + (size - width) / 2, y, width, size, 0f, 0f, 16, 16, 16, 16)
            }
            is PlayerPortraitSource.TextureRegion -> {
                context.drawTexture(Identifier(source.resourceId), x, y, size, size, source.u0 * 256f, source.v0 * 256f, ((source.u1 - source.u0) * 256f).toInt(), ((source.v1 - source.v0) * 256f).toInt(), 256, 256)
            }
        }
    }

    /**
     * 依已解析來源選擇世界空間的皮膚、牌面或自訂貼圖區域。
     *
     * @param source 已解析的頭像來源。
     * @param playerId 頭像所屬玩家 ID。
     * @param x slot 左上角 X 座標。
     * @param y slot 左上角 Y 座標。
     * @param size slot 邊長。
     * @param alpha 顯示透明度。
     * @param z 局部深度。
     * @param matrices 目前的模型矩陣。
     * @param consumers 提供頭像頂點緩衝。
     */
    private fun renderSource(
        source: PlayerPortraitSource,
        playerId: Uuid,
        x: Float,
        y: Float,
        size: Float,
        alpha: Float,
        z: Float,
        matrices: MatrixStack,
        consumers: VertexConsumerProvider,
    ) = when (source) {
        PlayerPortraitSource.PlayerSkinFace -> renderSkin(playerId, x, y, size, alpha, z, matrices, consumers)
        is PlayerPortraitSource.TileFace -> {
            val width = size * TILE_ASPECT_RATIO
            renderRegion(resolveTileTexture(source.assetKey), x + (size - width) / 2f, y, width, size, alpha, z, 0f, 0f, 1f, 1f, false, matrices, consumers)
        }
        is PlayerPortraitSource.TextureRegion -> renderRegion(
            Identifier(source.resourceId), x, y, size, size, alpha, z,
            source.u0, source.v0, source.u1, source.v1, false, matrices, consumers,
        )
    }

    /**
     * 解析並繪製真人玩家的原生皮膚臉部區域。
     *
     * @param playerId 玩家 ID。
     * @param x slot 左上角 X 座標。
     * @param y slot 左上角 Y 座標。
     * @param size slot 邊長。
     * @param alpha 顯示透明度。
     * @param z 局部深度。
     * @param matrices 目前的模型矩陣。
     * @param consumers 提供頭像頂點緩衝。
     */
    private fun renderSkin(
        playerId: Uuid,
        x: Float,
        y: Float,
        size: Float,
        alpha: Float,
        z: Float,
        matrices: MatrixStack,
        consumers: VertexConsumerProvider,
    ) {
        val uuid = playerId.toJavaUuid()
        val texture = profiles.resolvedSkinTexture(playerId)
            ?: MinecraftClient.getInstance().networkHandler?.getPlayerListEntry(uuid)?.skinTexture
            ?: DefaultSkinHelper.getTexture(uuid)
        renderRegion(texture, x, y, size, size, alpha, z, 8f / 64f, 8f / 64f, 16f / 64f, 16f / 64f, true, matrices, consumers)
    }

    /**
     * 以指定 UV 範圍繪製貼圖矩形。
     *
     * @param texture 要繪製的貼圖識別碼。
     * @param x 矩形左側 X 座標。
     * @param y 矩形上側 Y 座標。
     * @param width 矩形寬度。
     * @param height 矩形高度。
     * @param alpha 顯示透明度。
     * @param z 局部深度。
     * @param u0 UV 左側起點。
     * @param v0 UV 上側起點。
     * @param u1 UV 右側終點。
     * @param v1 UV 下側終點。
     * @param usesEntityLayer 是否使用含 overlay/normal 的 entity render layer。
     * @param matrices 目前的模型矩陣。
     * @param consumers 提供貼圖頂點緩衝。
     */
    private fun renderRegion(
        texture: Identifier,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        alpha: Float,
        z: Float,
        u0: Float,
        v0: Float,
        u1: Float,
        v1: Float,
        usesEntityLayer: Boolean,
        matrices: MatrixStack,
        consumers: VertexConsumerProvider,
    ) {
        val layer = if (usesEntityLayer) RenderLayer.getEntityTranslucent(texture) else ExhaustiveDrawSettlementTileFaceRenderLayer.get(texture)
        val buffer = consumers.getBuffer(layer)
        val matrix = matrices.peek().positionMatrix
        val a = (alpha.coerceIn(0f, 1f) * 255).roundToInt()
        fun vertex(px: Float, py: Float, u: Float, v: Float) {
            val vertex = buffer.vertex(matrix, px, py, z).color(255, 255, 255, a).texture(u, v)
            if (usesEntityLayer) {
                vertex.overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0f, 0f, 1f).next()
            } else {
                vertex.light(LightmapTextureManager.MAX_LIGHT_COORDINATE).next()
            }
        }
        vertex(x, y, u0, v0)
        vertex(x + width, y, u1, v0)
        vertex(x + width, y + height, u1, v1)
        vertex(x, y + height, u0, v1)
    }

    /**
     * 解析牌面貼圖，並快取結果；資源不存在時使用 unknown 牌面。
     *
     * @param assetKey 牌面資產 key。
     * @return 可用的貼圖識別碼。
     */
    private fun resolveTileTexture(assetKey: String): Identifier = tileTextures.getOrPut(assetKey) {
        val requested = Identifier(tileTextureResourceId(assetKey, tileAssetRegistry))
        if (MinecraftClient.getInstance().resourceManager.getResource(requested).isPresent) {
            requested
        } else {
            Identifier(tileTextureResourceId(UNKNOWN_TILE_ASSET_KEY, tileAssetRegistry))
        }
    }

    /**
     * 取得內建頭像 fallback：AI 使用牌背，真人使用玩家皮膚。
     *
     * @param isAi 玩家是否為 AI。
     * @return 對應類型的內建頭像來源。
     */
    private fun fallbackSource(isAi: Boolean): PlayerPortraitSource = if (isAi) {
        PlayerPortraitSource.TileFace(UNKNOWN_TILE_ASSET_KEY)
    } else {
        PlayerPortraitSource.PlayerSkinFace
    }

    /** 頭像繪製器使用的固定比例與其他私有常數。 */
    private companion object {
        /** 牌面貼圖相對於頭像 slot 的寬高比例。 */
        const val TILE_ASPECT_RATIO = 0.75f
    }
}

/**
 * 執行可使用內建外觀降級的呈現操作。
 *
 * 外觀提供者或繪製操作的一般例外會以失敗結果回傳；協程取消與致命錯誤則維持原樣向外傳遞。
 *
 * @param T 操作的回傳型別。
 * @param operation 要執行的外觀解析或繪製操作。
 * @return 操作成功或一般例外失敗的結果。
 */
internal inline fun <T> runPresentationAttempt(operation: () -> T): Result<T> = try {
    Result.success(operation())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (cause: Exception) {
    Result.failure(cause)
}

/**
 * 執行頭像來源解析與繪製，並在可恢復的來源失敗時使用內建來源重試。
 *
 * 解析失敗時會繪製內建來源，但不會再嘗試第二次；已解析的第三方來源繪製失敗時會再繪製一次內建來源。
 * 兩個失敗回呼只接收一般例外；取消與致命錯誤會由呈現邊界直接向外傳遞。
 *
 * @param T 繪製操作的回傳型別。
 * @param resolved 外觀來源解析結果。
 * @param fallbackSource 解析或繪製失敗時使用的內建來源。
 * @param onResolutionFailure 回報一般解析失敗的函式。
 * @param onDrawFailure 回報一般繪製失敗的函式。
 * @param draw 使用指定來源進行繪製的函式。
 * @return 首次繪製或 fallback 繪製的結果；首次繪製失敗且沒有第三方來源時為 `null`。
 */
internal inline fun <T> renderPortraitWithFallback(
    resolved: Result<ResolvedPlayerPortraitSource?>,
    fallbackSource: PlayerPortraitSource,
    onResolutionFailure: (cause: Throwable, providerId: String) -> Unit,
    onDrawFailure: (cause: Throwable, providerId: String) -> Unit,
    draw: (source: PlayerPortraitSource) -> T,
): T? {
    val resolutionFailure = resolved.exceptionOrNull()
    if (resolutionFailure != null) {
        val providerId = (resolutionFailure as? PlayerPortraitSourceProviderException)?.providerId ?: "registry"
        onResolutionFailure(resolutionFailure, providerId)
    }
    val resolvedSource = resolved.getOrNull()
    val firstAttempt = runPresentationAttempt { draw(resolvedSource?.source ?: fallbackSource) }
    val drawFailure = firstAttempt.exceptionOrNull()
    if (drawFailure != null) {
        onDrawFailure(drawFailure, resolvedSource?.providerId ?: "built-in-fallback")
    }
    if (firstAttempt.isSuccess || resolvedSource == null) {
        return firstAttempt.getOrNull()
    }
    return draw(fallbackSource)
}
