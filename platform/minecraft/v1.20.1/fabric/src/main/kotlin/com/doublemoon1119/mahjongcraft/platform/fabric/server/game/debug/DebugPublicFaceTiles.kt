package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.tile.component.BuiltInTileComponents
import com.doublemoon1119.mahjongcraft.logic.tile.component.TileFaceAccess
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileResources
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileSetAppearanceDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileStyleAssetDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.tileTextureAssetPath
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.toAssetKey

/** debug 情境使用的公開牌面樣式；牌面沿用基本牌面，牌身換成不同顏色。 */
internal object DebugPublicFaceTiles {
    /** 公開牌面樣式的完整 ID。 */
    const val APPEARANCE_ID = "${MinecraftModMetadata.MOD_ID}:debug/public_face"

    /** 公開牌面樣式使用的牌身外觀 ID。 */
    private const val BODY_APPEARANCE_ID = "${MinecraftModMetadata.MOD_ID}:debug/public_face_body"

    /** 日麻使用的全部基本牌面。 */
    private val BASE_TILES: List<Tile> = Tile.Suit.entries.flatMap { suit -> (1..9).map { Tile.Numeric(suit, it) } } +
        listOf(Tile.Honor.East, Tile.Honor.South, Tile.Honor.West, Tile.Honor.North, Tile.Honor.White, Tile.Honor.Green, Tile.Honor.Red)

    /**
     * 讓牌張所有人都看得到牌面，並套用公開牌面樣式。
     *
     * @param tile 原本的牌張。
     * @return 保留 UUID 與牌面、加上公開牌面組件的牌張。
     */
    fun mark(tile: IdentifiedTile): IdentifiedTile = tile.copy(
        components = tile.components
            .with(BuiltInTileComponents.FACE_ACCESS, TileFaceAccess.Public)
            .with(BuiltInTileComponents.APPEARANCE, APPEARANCE_ID),
    )

    /**
     * 登記不同顏色的牌身與每種基本牌面的公開牌面樣式素材。
     *
     * @param registry 尚未凍結的牌面素材 registry。
     */
    fun register(registry: MinecraftTileAssetRegistry) {
        registry.register(
            MinecraftTileSetAppearanceDefinition(
                id = BODY_APPEARANCE_ID,
                coverTextureId = "${MinecraftModMetadata.MOD_ID}:${tileTextureAssetPath("cover_debug_public_face")}",
            ),
        )
        BASE_TILES.forEach { tile ->
            val baseAssetKey = tile.toAssetKey(registry)
            registry.register(
                MinecraftTileStyleAssetDefinition(
                    styleId = APPEARANCE_ID,
                    tile = tile,
                    assetKey = "${MinecraftModMetadata.MOD_ID}:debug_public_face_$baseAssetKey",
                    resources = MinecraftTileResources.builtIn(baseAssetKey),
                    bodyAppearanceId = BODY_APPEARANCE_ID,
                ),
            )
        }
    }
}
