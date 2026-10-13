package com.doublemoon1119.mahjongcraft.platform.fabric.extension

import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata
import com.doublemoon1119.mahjongcraft.platform.fabric.api.seating.FabricSeatProviderRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.server.seating.BuiltInFabricSeatProviderIds
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.prop.FabricTablePropKindRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.prop.ScoringStickTablePropKind
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.RegisteredSeatProvider
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatProvider
import net.minecraft.entity.Entity
import net.minecraft.server.world.ServerWorld

/**
 * 提供 Fabric 平台內建桌面物件與座位 provider 的 extension。
 *
 * @property seatProvider 內建麻將凳座位 provider。
 */
internal class BuiltInFabricMahjongExtension(
    private val seatProvider: SeatProvider<ServerWorld, Entity>,
) : FabricMahjongExtension {
    /** 內建 Fabric extension 的穩定識別碼。 */
    override val id: String = MahjongCraftMetadata.id("builtin")

    /** 登記內建點棒桌面物件種類。 */
    override fun registerTablePropKinds(registry: FabricTablePropKindRegistry) = registry.register(ScoringStickTablePropKind)

    /** 登記內建麻將凳座位 provider。 */
    override fun registerSeatProviders(registry: FabricSeatProviderRegistry) = registry.register(RegisteredSeatProvider(BuiltInFabricSeatProviderIds.MAHJONG_STOOL, seatProvider))
}
