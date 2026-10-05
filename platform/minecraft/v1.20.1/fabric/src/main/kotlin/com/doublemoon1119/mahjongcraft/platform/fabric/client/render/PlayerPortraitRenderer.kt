package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import com.doublemoon1119.mahjongcraft.platform.fabric.client.player.ClientPlayerProfileResolver
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PlayerPortraitSource
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PlayerPortraitSourceContext
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PlayerPortraitSourceProviderException
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PlayerPortraitSourceRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.UNKNOWN_TILE_ASSET_KEY
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.tileTextureAssetPath
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
 * @property profiles 真人普通名稱與原生皮膚材質的共用解析器。
 */
@Single
class PlayerPortraitRenderer(
    @Provided private val sources: PlayerPortraitSourceRegistry,
    private val profiles: ClientPlayerProfileResolver,
) {
    private val logger = mahjongCraftLogger(PlayerPortraitRenderer::class)
    private val warnedProviderIds = mutableSetOf<String>()
    private val tileTextures = mutableMapOf<String, Identifier>()

    /** 將宣告式來源繪製於正方形 slot；牌面會保持 3:4 比例並水平置中。 */
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
        val resolved = runCatching { sources.resolve(PlayerPortraitSourceContext(playerId, isAi)) }
            .onFailure { cause ->
                val providerId = (cause as? PlayerPortraitSourceProviderException)?.providerId ?: "registry"
                if (warnedProviderIds.add(providerId)) logger.warn("Failed to resolve player portrait provider {}", providerId, cause)
            }
            .getOrNull()
        val source = resolved?.source ?: fallbackSource(isAi)
        val rendered = runCatching {
            renderSource(source, playerId, x, y, size, alpha, z, matrices, consumers)
        }.onFailure { cause ->
            val providerId = resolved?.providerId ?: "built-in-fallback"
            if (warnedProviderIds.add(providerId)) logger.warn("Failed to render player portrait provider {}", providerId, cause)
        }.isSuccess
        if (!rendered && resolved != null) {
            renderSource(fallbackSource(isAi), playerId, x, y, size, alpha, z, matrices, consumers)
        }
    }

    /**
     * 將同一 portrait source 繪製於 GUI；提供者失敗時使用內建來源。
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
        val resolved = runCatching { sources.resolve(PlayerPortraitSourceContext(playerId, isAi)) }
            .onFailure { cause ->
                val providerId = (cause as? PlayerPortraitSourceProviderException)?.providerId ?: "registry"
                if (warnedProviderIds.add(providerId)) logger.warn("Failed to resolve player portrait provider {}", providerId, cause)
            }.getOrNull()
        val source = resolved?.source ?: fallbackSource(isAi)
        val rendered = runCatching { renderGuiSource(source, playerId, context, x, y, size, skinTexture) }
            .onFailure { cause ->
                val providerId = resolved?.providerId ?: "built-in-fallback"
                if (warnedProviderIds.add(providerId)) logger.warn("Failed to render player portrait provider {}", providerId, cause)
            }.isSuccess
        if (!rendered && resolved != null) renderGuiSource(fallbackSource(isAi), playerId, context, x, y, size, skinTexture)
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

    private fun resolveTileTexture(assetKey: String): Identifier = tileTextures.getOrPut(assetKey) {
        val requested = Identifier(MinecraftModMetadata.MOD_ID, tileTextureAssetPath(assetKey))
        if (MinecraftClient.getInstance().resourceManager.getResource(requested).isPresent) {
            requested
        } else {
            Identifier(MinecraftModMetadata.MOD_ID, tileTextureAssetPath(UNKNOWN_TILE_ASSET_KEY))
        }
    }

    private fun fallbackSource(isAi: Boolean): PlayerPortraitSource = if (isAi) {
        PlayerPortraitSource.TileFace(UNKNOWN_TILE_ASSET_KEY)
    } else {
        PlayerPortraitSource.PlayerSkinFace
    }

    private companion object {
        const val TILE_ASPECT_RATIO = 0.75f
    }
}
