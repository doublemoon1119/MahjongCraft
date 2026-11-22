package com.doublemoon1119.mahjongcraft.platform.fabric.extension

import com.doublemoon1119.mahjongcraft.flow.persistence.format.component.ComponentViewPersistenceRegistry
import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata
import com.doublemoon1119.mahjongcraft.platform.fabric.api.character.AiCharacterCapabilityDefinition
import com.doublemoon1119.mahjongcraft.platform.fabric.api.registration.AiCharacterCapabilityRegistrar
import com.doublemoon1119.mahjongcraft.platform.fabric.api.registration.SeatPlacementRegistrar
import com.doublemoon1119.mahjongcraft.platform.fabric.api.registration.SeatProviderRegistrar
import com.doublemoon1119.mahjongcraft.platform.fabric.api.registration.TablePropKindRegistrar
import com.doublemoon1119.mahjongcraft.platform.fabric.api.seating.SeatPlacementDefinition
import com.doublemoon1119.mahjongcraft.platform.fabric.server.character.TileMascotCharacterProvider
import com.doublemoon1119.mahjongcraft.platform.fabric.server.seating.MahjongStoolPlacementProvider
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.prop.FaceUpTileTablePropKind
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.prop.ScoringStickTablePropKind
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.RegisteredSeatProvider
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatProvider
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.BuiltInAiCharacters
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.seating.BuiltInSeatProviderIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.table.prop.TablePropTileFaceCodec
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity

/**
 * 提供內建原生桌面物件與座位 provider 的 extension。
 *
 * @property seatProvider 內建麻將凳座位 provider。
 * @property tileAssets 公開展示牌的資源與規則外觀。
 * @property componentViews 公開展示牌保存組件公開效果所用的轉換。
 */
internal class BuiltInNativeMahjongExtension(
    private val seatProvider: SeatProvider<ServerLevel, Entity>,
    private val tileAssets: MinecraftTileAssetRegistry,
    private val componentViews: ComponentViewPersistenceRegistry,
) : NativeMahjongExtension {
    /** 內建原生 extension 的穩定識別碼。 */
    override val id: String = MahjongCraftMetadata.id("builtin")

    /** 登記點棒與公開展示牌，兩者共用桌面物件規劃流程。 */
    override fun registerTablePropKinds(registry: TablePropKindRegistrar) {
        registry.register(ScoringStickTablePropKind)
        registry.register(FaceUpTileTablePropKind(tileAssets, TablePropTileFaceCodec(componentViews)))
    }

    /** 登記內建麻將凳座位 provider。 */
    override fun registerSeatProviders(registry: SeatProviderRegistrar) = registry.register(RegisteredSeatProvider(BuiltInSeatProviderIds.MAHJONG_STOOL, seatProvider))

    /** 內建牌體角色與第三方共用世界能力登記流程。 */
    override fun registerAiCharacterCapabilities(registry: AiCharacterCapabilityRegistrar) {
        registry.register(AiCharacterCapabilityDefinition(BuiltInAiCharacters.TILE_MASCOT_CAPABILITY_ID, TileMascotCharacterProvider))
    }

    /** 內建凳子沿用物品放置能力，不直接寫入方塊。 */
    override fun registerSeatPlacements(registry: SeatPlacementRegistrar) {
        registry.register(SeatPlacementDefinition(BuiltInSeatProviderIds.MAHJONG_STOOL, MahjongStoolPlacementProvider))
    }
}
