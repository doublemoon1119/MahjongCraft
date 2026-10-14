package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 驗證未知牌與牌背預覽只使用公開外觀，且不改變基本牌素材 key。 */
class MinecraftTilePreviewResourcesTest {
    /** 未知牌正面使用正面素材，牌背預覽裁切同一套 cover。 */
    @Test
    fun `unknown previews use the public appearance rather than tile identity`() {
        val registry = MinecraftTileAssetRegistryImpl()
        val appearance = MinecraftTileSetAppearanceDefinition(
            id = "example:wood",
            coverTextureId = "example:textures/wood/cover.png",
            unknownFaceTextureId = "example:textures/wood/unknown.png",
        )

        assertEquals(
            MinecraftTilePreviewTexture(appearance.coverTextureId, 3f / 16f, 0f, 6f / 16f, 4f / 16f),
            tilePreviewTexture(TILE_BACK_PREVIEW_ASSET_KEY, registry, appearance),
        )
        assertEquals(
            MinecraftTilePreviewTexture(appearance.unknownFaceTextureId, 0f, 0f, 1f, 1f),
            tilePreviewTexture(UNKNOWN_TILE_ASSET_KEY, registry, appearance),
        )
        assertEquals(
            MinecraftTilePreviewTexture(appearance.unknownFaceTextureId, 0f, 0f, 1f, 1f),
            tilePreviewTexture("unregistered", registry, appearance),
        )
        assertEquals(
            MinecraftTilePreviewTexture("mahjongcraft:textures/item/mahjong_tile/mahjong_tile_p5.png", 0f, 0f, 1f, 1f),
            tilePreviewTexture("p5", registry, appearance),
        )
    }

    /** 缺少規則上下文時，牌背預覽仍使用內建 cover 並裁切到牌背所在的 south 面。 */
    @Test
    fun `default flat back uses the built in cover crop`() {
        assertEquals(
            BuiltInMinecraftTileAppearances.DEFAULT.coverTextureId,
            tilePreviewTexture(TILE_BACK_PREVIEW_ASSET_KEY, MinecraftTileAssetRegistryImpl()).resourceId,
        )
    }

    /** 省略未知牌材質時，外觀定義使用內建 unknown 資源。 */
    @Test
    fun `appearance defaults unknown face texture`() {
        assertEquals(
            "mahjongcraft:textures/item/mahjong_tile/mahjong_tile_unknown.png",
            MinecraftTileSetAppearanceDefinition(
                id = "example:defaulted",
                coverTextureId = "example:textures/cover.png",
            ).unknownFaceTextureId,
        )
    }

    /** 外觀資源 ID 必須是完整 namespaced ID。 */
    @Test
    fun `appearance rejects invalid unknown face texture id`() {
        assertFailsWith<IllegalArgumentException> {
            MinecraftTileSetAppearanceDefinition(
                id = "example:invalid",
                coverTextureId = "example:textures/cover.png",
                unknownFaceTextureId = "not-namespaced",
            )
        }
    }
}
