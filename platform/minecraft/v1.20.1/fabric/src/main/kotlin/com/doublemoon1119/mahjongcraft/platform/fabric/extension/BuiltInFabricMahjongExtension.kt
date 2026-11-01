package com.doublemoon1119.mahjongcraft.platform.fabric.extension

import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata
import com.doublemoon1119.mahjongcraft.platform.fabric.api.character.FabricAiCharacterCapabilityDefinition
import com.doublemoon1119.mahjongcraft.platform.fabric.api.registration.FabricAiCharacterCapabilityRegistrar
import com.doublemoon1119.mahjongcraft.platform.fabric.api.registration.FabricSeatPlacementRegistrar
import com.doublemoon1119.mahjongcraft.platform.fabric.api.registration.FabricSeatProviderRegistrar
import com.doublemoon1119.mahjongcraft.platform.fabric.api.registration.FabricTablePropKindRegistrar
import com.doublemoon1119.mahjongcraft.platform.fabric.api.seating.FabricSeatPlacementDefinition
import com.doublemoon1119.mahjongcraft.platform.fabric.server.character.TileMascotCharacterProvider
import com.doublemoon1119.mahjongcraft.platform.fabric.server.seating.BuiltInFabricSeatProviderIds
import com.doublemoon1119.mahjongcraft.platform.fabric.server.seating.MahjongStoolPlacementProvider
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.prop.FaceUpTileTablePropKind
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.prop.ScoringStickTablePropKind
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.RegisteredSeatProvider
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatProvider
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.BuiltInAiCharacters
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import net.minecraft.entity.Entity
import net.minecraft.server.world.ServerWorld

/**
 * 提供 Fabric 平台內建桌面物件與座位 provider 的 extension。
 *
 * @property tileAssets 公開展示牌的資源與規則外觀。
 * @property seatProvider 內建麻將凳座位 provider。
 */
internal class BuiltInFabricMahjongExtension(
    private val seatProvider: SeatProvider<ServerWorld, Entity>,
    private val tileAssets: MinecraftTileAssetRegistry,
) : FabricMahjongExtension {
    /** 內建 Fabric extension 的穩定識別碼。 */
    override val id: String = MahjongCraftMetadata.id("builtin")

    /** 登記點棒與公開展示牌，兩者共用桌面物件規劃流程。 */
    override fun registerTablePropKinds(registry: FabricTablePropKindRegistrar) {
        registry.register(ScoringStickTablePropKind)
        registry.register(FaceUpTileTablePropKind(tileAssets))
    }

    /** 登記內建麻將凳座位 provider。 */
    override fun registerSeatProviders(registry: FabricSeatProviderRegistrar) = registry.register(RegisteredSeatProvider(BuiltInFabricSeatProviderIds.MAHJONG_STOOL, seatProvider))

    /** 內建牌體角色與第三方共用世界能力登記流程。 */
    override fun registerAiCharacterCapabilities(registry: FabricAiCharacterCapabilityRegistrar) {
        registry.register(FabricAiCharacterCapabilityDefinition(BuiltInAiCharacters.TILE_MASCOT_CAPABILITY_ID, TileMascotCharacterProvider))
    }

    /** 內建凳子沿用物品放置能力，不直接寫入方塊。 */
    override fun registerSeatPlacements(registry: FabricSeatPlacementRegistrar) {
        registry.register(FabricSeatPlacementDefinition(BuiltInFabricSeatProviderIds.MAHJONG_STOOL, MahjongStoolPlacementProvider))
    }
}
