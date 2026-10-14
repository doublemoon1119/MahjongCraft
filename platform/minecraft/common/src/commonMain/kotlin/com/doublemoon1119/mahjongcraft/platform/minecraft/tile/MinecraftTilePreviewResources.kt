package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

/** 平面牌背預覽使用的素材 token，不屬於可登記或保存的牌種 asset key。 */
const val TILE_BACK_PREVIEW_ASSET_KEY: String = "back"

/** South 面在 16×16 cover atlas 中的左側 UV。 */
private const val COVER_U0: Float = 3f / 16f

/** South 面在 16×16 cover atlas 中的上側 UV。 */
private const val COVER_V0: Float = 0f

/** South 面在 16×16 cover atlas 中的右側 UV。 */
private const val COVER_U1: Float = 6f / 16f

/** South 面在 16×16 cover atlas 中的下側 UV。 */
private const val COVER_V1: Float = 4f / 16f

/**
 * 依可見牌面與公開牌組外觀取得平面預覽資源，不讀取隱藏牌的真實牌種。
 *
 * @param assetKey 可見牌面 key、未知牌 key 或平面牌背 token。
 * @param registry 提供可見牌面的資源映射。
 * @param appearance 公開規則決定的牌組外觀；無上下文時使用內建預設。
 * @return 正面、無圖案正面或牌背預覽的貼圖與固定 UV 範圍。
 */
fun tilePreviewTexture(
    assetKey: String,
    registry: MinecraftTileAssetRegistry,
    appearance: MinecraftTileSetAppearanceDefinition = BuiltInMinecraftTileAppearances.DEFAULT,
): MinecraftTilePreviewTexture = when {
    assetKey == TILE_BACK_PREVIEW_ASSET_KEY -> MinecraftTilePreviewTexture(
        resourceId = appearance.coverTextureId,
        u0 = COVER_U0,
        v0 = COVER_V0,
        u1 = COVER_U1,
        v1 = COVER_V1,
    )

    assetKey.normalizedTileAssetKey(registry) == UNKNOWN_TILE_ASSET_KEY -> MinecraftTilePreviewTexture(
        resourceId = appearance.unknownFaceTextureId,
        u0 = 0f,
        v0 = 0f,
        u1 = 1f,
        v1 = 1f,
    )

    else -> MinecraftTilePreviewTexture(
        resourceId = tileTextureResourceId(assetKey, registry),
        u0 = 0f,
        v0 = 0f,
        u1 = 1f,
        v1 = 1f,
    )
}
