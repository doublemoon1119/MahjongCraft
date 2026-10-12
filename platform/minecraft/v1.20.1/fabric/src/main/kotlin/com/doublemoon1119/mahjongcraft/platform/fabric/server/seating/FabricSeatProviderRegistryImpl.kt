package com.doublemoon1119.mahjongcraft.platform.fabric.server.seating

import com.doublemoon1119.mahjongcraft.platform.fabric.api.seating.FabricSeatProviderRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.RegisteredSeatProvider
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatProvider
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftResourceIds
import net.minecraft.entity.Entity
import net.minecraft.server.world.ServerWorld
import org.koin.core.annotation.Single

/** [FabricSeatProviderRegistry] 的記憶體實作。 */
@Single(binds = [FabricSeatProviderRegistry::class])
internal class FabricSeatProviderRegistryImpl : FabricSeatProviderRegistry {
    /** 依 provider 識別碼保存登記資料。 */
    private val providersById = linkedMapOf<String, RegisteredSeatProvider<ServerWorld, Entity>>()

    override val registrationKeys: Set<String>
        get() = providersById.keys.toSet()

    override var isFrozen: Boolean = false
        private set

    override fun register(
        id: String,
        provider: SeatProvider<ServerWorld, Entity>,
        priority: Int,
    ) {
        check(!isFrozen) { "Seat provider registry is frozen" }
        MinecraftResourceIds.requireValid(id) { "Seat provider must be namespaced: $id" }
        require(providersById.putIfAbsent(id, RegisteredSeatProvider(id, provider, priority)) == null) {
            "Duplicate seat provider: $id"
        }
    }

    override fun freeze() {
        isFrozen = true
    }

    override fun entries(): List<RegisteredSeatProvider<ServerWorld, Entity>> = providersById.values
        .sortedWith(compareByDescending<RegisteredSeatProvider<ServerWorld, Entity>> { it.priority }.thenBy { it.id })
}
