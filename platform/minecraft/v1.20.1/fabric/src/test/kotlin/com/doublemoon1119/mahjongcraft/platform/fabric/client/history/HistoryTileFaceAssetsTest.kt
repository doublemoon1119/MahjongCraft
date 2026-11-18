package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.component.TransparentTileViewDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.component.registerBuiltInComponentViewNetworkDtos
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.TileFaceViewDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.DefaultNetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.BuiltInMinecraftTileAppearances
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.BuiltInMinecraftTileStyles
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistryImpl
import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證牌譜回覆的牌面特徵能選出對應的平面素材。 */
class HistoryTileFaceAssetsTest {
    @Test
    fun `history selects the transparent variant only for a transparent tile`() {
        val registries = DefaultNetworkDtoRegistries()
        registerBuiltInComponentViewNetworkDtos(registries.componentViews)
        val assets = MinecraftTileAssetRegistryImpl().apply {
            register(BuiltInMinecraftTileAppearances.TRANSPARENT)
            BuiltInMinecraftTileStyles.TRANSPARENT.forEach(::register)
            freeze()
        }
        val face = TileFaceViewDto(tile = Tile.Honor.East.toDto(), views = emptyList(), appearanceId = null)
        val transparent = face.copy(views = listOf(TransparentTileViewDto))

        assertEquals("east", face.toHistoryAssetKey(registry = assets, registries = registries))
        assertEquals("transparent_east", transparent.toHistoryAssetKey(registry = assets, registries = registries))
        val duplicateViews = transparent.copy(views = transparent.views + transparent.views)
        assertEquals("east", duplicateViews.toHistoryAssetKey(registry = assets, registries = registries))
    }
}
