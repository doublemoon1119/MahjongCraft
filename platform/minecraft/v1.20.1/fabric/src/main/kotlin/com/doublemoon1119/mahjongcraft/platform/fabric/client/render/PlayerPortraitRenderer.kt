package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import com.doublemoon1119.mahjongcraft.platform.fabric.client.character.ClientAiCharacterPreviews
import com.doublemoon1119.mahjongcraft.platform.fabric.client.player.ClientPlayerProfileResolver
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterFace
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.AiCharacterClientStateStore
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.AiCharacterSnapshot
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.BuiltInAiCharacters
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PlayerPortraitSource
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PlayerPortraitSourceContext
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PlayerPortraitSourceProviderException
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PlayerPortraitSourceRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.ResolvedPlayerPortraitSource
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.UNKNOWN_TILE_ASSET_KEY
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.isKnownTileAssetKey
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
import kotlin.math.roundToInt
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

/**
 * 世界結算、桌級面板及 GUI 共用的頭像來源繪製器。
 *
 * @property sources 規則與第三方提供的宣告式頭像來源。
 * @property tileAssetRegistry 提供 AI 牌面頭像使用的完整 namespace 與路徑映射。
 * @property profiles 真人普通名稱與原生皮膚材質的共用解析器。
 * @property characters 此連線已授權的角色外觀，不查詢世界實體。
 * @property previews 直接模型與頭像備援；明確 FACE 可用時不額外截圖。
 */
class PlayerPortraitRenderer internal constructor(
    private val sources: PlayerPortraitSourceRegistry,
    private val tileAssetRegistry: MinecraftTileAssetRegistry,
    private val profiles: ClientPlayerProfileResolver,
    private val characters: AiCharacterClientStateStore,
    private val previews: ClientAiCharacterPreviews,
) {
    /** 記錄外觀來源或繪製失敗，並遵守相同來源只警告一次的策略。 */
    private val logger = mahjongCraftLogger(PlayerPortraitRenderer::class)

    /** 已警告過的外觀來源提供者識別碼，避免每次繪製都重複記錄。 */
    private val warnedProviderIds = mutableSetOf<String>()

    /** 已解析的牌面貼圖快取，避免每次繪製都查詢資源管理器。 */
    private val tileTextures = mutableMapOf<String, Identifier>()

    /** 資源重載或斷線時清除材質解析與診斷，讓已恢復資源能重新使用。 */
    fun clearResources() {
        tileTextures.clear()
        warnedProviderIds.clear()
    }

    /**
     * 將角色公開頭像轉成既有繪製來源，不另建一套 FACE renderer。
     *
     * @param snapshot 已授權的當場描述。
     * @return 已擷取頭像；缺少時由既有來源與未知牌備援。
     */
    private fun characterPortrait(snapshot: AiCharacterSnapshot?): ResolvedPlayerPortraitSource? {
        snapshot ?: return null
        val face = snapshot.definition?.face ?: return previews.portrait(snapshot)?.let { ResolvedPlayerPortraitSource(snapshot.characterId, it) }
        if (face is AiCharacterFace.Tile && !tileAssetRegistry.isKnownTileAssetKey(face.assetKey)) {
            return ResolvedPlayerPortraitSource(snapshot.characterId, previews.portrait(snapshot) ?: fallbackSource(true))
        }
        val source = when (face) {
            is AiCharacterFace.Tile -> PlayerPortraitSource.TileFace(face.assetKey)
            is AiCharacterFace.TextureRegion -> PlayerPortraitSource.TextureRegion(face.resourceId, face.u0, face.v0, face.u1, face.v1)
        }
        val resource = when (face) {
            is AiCharacterFace.Tile -> Identifier(tileTextureResourceId(face.assetKey, tileAssetRegistry))
            is AiCharacterFace.TextureRegion -> Identifier(face.resourceId)
        }
        if (!MinecraftClient.getInstance().resourceManager.getResource(resource).isPresent) {
            if (warnedProviderIds.add("${snapshot.characterId}/$resource")) logger.warn("Character portrait resource is unavailable characterId={} resourceId={}", snapshot.characterId, resource)
            val fallback = previews.portrait(snapshot) ?: fallbackSource(true)
            return ResolvedPlayerPortraitSource(snapshot.characterId, fallback)
        }
        return ResolvedPlayerPortraitSource(snapshot.characterId, source)
    }

    /**
     * 先遵守既有 registry 的優先序，再對內建角色來源檢查材質與模型備援。
     *
     * @param playerId 頭像所屬成員。
     * @param isAi 是否由 AI 操控。
     * @param snapshot 已授權的角色描述，歷史使用指定場次。
     * @return 登記來源或角色備援，皆無資料時為 null。
     */
    private fun resolvePortrait(playerId: Uuid, isAi: Boolean, snapshot: AiCharacterSnapshot?): ResolvedPlayerPortraitSource? {
        val supplied = sources.resolve(PlayerPortraitSourceContext(playerId, isAi, snapshot?.definition))
        val resolved = if (isAi && supplied?.source == PlayerPortraitSource.PlayerSkinFace) {
            if (warnedProviderIds.add(supplied.providerId)) logger.warn("Player skin portrait cannot use a logical AI identity providerId={}; using character fallback", supplied.providerId)
            null
        } else {
            supplied
        }
        return if (resolved == null || resolved.providerId == BuiltInAiCharacters.PORTRAIT_SOURCE_ID) characterPortrait(snapshot) ?: resolved else resolved
    }

    /**
     * 供既有房間外觀解析器確認預覽能力，不執行工廠。
     *
     * @param id 完整能力識別碼。
     * @return 已登記時為 true。
     */
    fun hasCharacterPreview(id: String): Boolean = previews.hasFactory(id)

    /**
     * 以實際角色描述繪製選定的房間預覽；能力與描述不符時由頭像備援。
     *
     * @param snapshot 已授權的完整角色描述。
     * @param id 選定的備援能力；優先使用本人時可為 null。
     * @param context GUI 繪製來源。
     * @param x 卡片左界。
     * @param y 卡片頂端。
     * @param size 卡片寬度。
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @param worldEntityId 同一授權快照中的即時實體 UUID。
     * @return 已直接畫出模型時為 true。
     */
    fun renderActorModel(snapshot: AiCharacterSnapshot?, id: String?, context: DrawContext, x: Int, y: Int, size: Int, mouseX: Int, mouseY: Int, worldEntityId: Uuid?): Boolean {
        if (id != null && snapshot?.definition?.preview?.capabilityId != id) return false
        return previews.renderModel(snapshot, context, x, y, size, mouseX, mouseY, worldEntityId)
    }

    /**
     * 依保存描述直接繪製獨立模型，沒有模型時由原頭像流程備援。
     *
     * @param snapshot 當場已授權的外觀。
     * @param context GUI 繪製來源。
     * @param x 卡片左界。
     * @param y 卡片頂端。
     * @param size 卡片寬度。
     * @return 已畫出模型時為 true。
     */
    fun renderCharacterModel(snapshot: AiCharacterSnapshot?, context: DrawContext, x: Int, y: Int, size: Int): Boolean = previews.renderModel(snapshot, context, x, y, size, x + size / 2, y + size / 2)

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
            resolved = runPresentationAttempt { resolvePortrait(playerId, isAi, characters.findPlayer(playerId)) },
            fallbackSource = fallbackSource(isAi),
            modelFallback = { previews.portrait(characters.findPlayer(playerId)) },
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
            resolved = runPresentationAttempt { resolvePortrait(playerId, isAi, characters.findPlayer(playerId)) },
            fallbackSource = fallbackSource(isAi),
            modelFallback = { previews.portrait(characters.findPlayer(playerId)) },
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
     * 繪製房主可讀的排除者頭像；缺少角色資料時使用原有備援，不建立模型。
     *
     * @param tableId 已授權的等待房間。
     * @param playerId 被排除者上次的成員編號。
     * @param isAi 是否由 AI 操控，不以 AI UUID 查詢真人皮膚。
     * @param context GUI 繪製來源。
     * @param x 左上角水平位置。
     * @param y 左上角垂直位置。
     * @param size 頭像邊長。
     */
    fun renderExcludedGui(tableId: Uuid, playerId: Uuid, isAi: Boolean, context: DrawContext, x: Int, y: Int, size: Int) {
        val snapshot = characters.find(tableId)?.excludedPlayers?.get(playerId)
        renderPortraitWithFallback(
            resolved = runPresentationAttempt { resolvePortrait(playerId, isAi, snapshot) },
            fallbackSource = fallbackSource(isAi),
            modelFallback = { null },
            onResolutionFailure = { cause, provider -> if (warnedProviderIds.add(provider)) logger.warn("Failed to resolve excluded portrait provider {}", provider, cause) },
            onDrawFailure = { cause, provider -> if (warnedProviderIds.add(provider)) logger.warn("Failed to draw excluded portrait provider {}", provider, cause) },
            draw = { source -> renderGuiSource(source, playerId, context, x, y, size, null) },
        )
    }

    /**
     * 使用指定歷史場次的頭像，不以目前世界中的角色外觀代替缺失資料。
     *
     * @param playerId 該場成員。
     * @param matchId 已授權歷史場次。
     * @param isAi 是否為 AI。
     * @param context 畫面繪製來源。
     * @param x 左上角 X。
     * @param y 左上角 Y。
     * @param size 正方形區域邊長。
     * @param skinTexture 已知真人皮膚，沒有時使用既有備援。
     */
    fun renderHistoryGui(
        playerId: Uuid,
        matchId: Uuid,
        isAi: Boolean,
        context: DrawContext,
        x: Int,
        y: Int,
        size: Int,
        skinTexture: Identifier? = null,
    ) {
        renderPortraitWithFallback(
            resolved = runPresentationAttempt { resolvePortrait(playerId, isAi, characters.history(matchId, playerId)) },
            fallbackSource = fallbackSource(isAi),
            modelFallback = { previews.portrait(characters.history(matchId, playerId)) },
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
            texture = Identifier(source.resourceId), x = x, y = y, width = size, height = size, alpha = alpha, z = z,
            u0 = source.u0, v0 = source.v0, u1 = source.u1, v1 = source.v1, usesEntityLayer = false, matrices = matrices, consumers = consumers,
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
 * @param modelFallback 只有來源不可用時才查詢的模型圖片；沒有模型時仍使用內建備援。
 * @param draw 使用指定來源進行繪製的函式。
 * @return 首次繪製或 fallback 繪製的結果；首次繪製失敗且沒有第三方來源時為 `null`。
 */
internal inline fun <T> renderPortraitWithFallback(
    resolved: Result<ResolvedPlayerPortraitSource?>,
    fallbackSource: PlayerPortraitSource,
    onResolutionFailure: (cause: Throwable, providerId: String) -> Unit,
    onDrawFailure: (cause: Throwable, providerId: String) -> Unit,
    noinline modelFallback: (() -> PlayerPortraitSource?)? = null,
    draw: (source: PlayerPortraitSource) -> T,
): T? {
    val resolutionFailure = resolved.exceptionOrNull()
    if (resolutionFailure != null) {
        val providerId = (resolutionFailure as? PlayerPortraitSourceProviderException)?.providerId ?: "registry"
        onResolutionFailure(resolutionFailure, providerId)
    }
    val resolvedSource = resolved.getOrNull()
    val initialModel = if (resolvedSource == null) modelFallback?.invoke() else null
    val firstSource = resolvedSource?.source ?: initialModel ?: fallbackSource
    val firstAttempt = runPresentationAttempt { draw(firstSource) }
    val drawFailure = firstAttempt.exceptionOrNull()
    if (drawFailure != null) {
        onDrawFailure(drawFailure, resolvedSource?.providerId ?: "built-in-fallback")
    }
    if (firstAttempt.isSuccess || (resolvedSource == null && initialModel == null)) {
        return firstAttempt.getOrNull()
    }
    val alternate = if (resolvedSource != null) modelFallback?.invoke() else null
    if (alternate != null && alternate != firstSource) {
        val attempt = runPresentationAttempt { draw(alternate) }
        if (attempt.isSuccess) return attempt.getOrThrow()
        onDrawFailure(requireNotNull(attempt.exceptionOrNull()), "model-preview")
    }
    return draw(fallbackSource)
}
