package com.doublemoon1119.mahjongcraft.platform.fabric.server.table.prop

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftResourceIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropPlacement
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropTarget
import net.minecraft.entity.Entity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.Box
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/**
 * 一種規則桌面物件在 Fabric 世界中的實作：如何生成、如何找回、如何讀出目前的身分。
 *
 * 規則描述裡的 [TablePropPlacement.kind] 對應到這裡的 [id]。
 */
interface FabricTablePropKind {
    /** 物件種類的命名空間識別碼。 */
    val id: String

    /**
     * 找出 [searchBox] 範圍內、由指定桌子管理的這個種類的 entity。
     *
     * 只能回傳由 [create] 建立並標記給這張桌子的 entity，不能包含玩家自由擺放的物件。
     */
    fun findManaged(world: ServerWorld, tableId: Uuid, searchBox: Box): List<Entity>

    /** 讀出既有 entity 目前的變體；無法辨識時回傳 null，該 entity 會被視為多餘而移除。 */
    fun variantOf(entity: Entity): String?

    /**
     * 建立一個擺在 [target] 落點、標記給指定桌子的新 entity，尚未加入世界。
     *
     * @return 新 entity；不認得 [TablePropTarget.variant] 時回傳 null。
     */
    fun create(world: ServerWorld, tableId: Uuid, target: TablePropTarget): Entity?

    /** entity 成功加入世界後呼叫，例如開始出現動畫。 */
    fun onSpawned(entity: Entity) = Unit
}

/** 依 [FabricTablePropKind.id] 登記 Fabric 端的規則桌面物件種類。 */
interface FabricTablePropKindRegistry {
    /** 目前已登記的種類識別碼快照。 */
    val registrationKeys: Set<String>

    /** registry 是否已凍結。 */
    val isFrozen: Boolean

    /** 目前已登記的全部種類，依登記順序排列。 */
    val kinds: List<FabricTablePropKind>

    /** 登記一個種類。 */
    fun register(kind: FabricTablePropKind)

    /** 查詢指定種類；未登記時回傳 null。 */
    fun find(kindId: String): FabricTablePropKind?

    /** 凍結 registry，禁止後續登記。 */
    fun freeze()
}

/** [FabricTablePropKindRegistry] 的記憶體實作。 */
@Single(binds = [FabricTablePropKindRegistry::class])
class FabricTablePropKindRegistryImpl : FabricTablePropKindRegistry {
    /** 依識別碼索引、保留登記順序的種類。 */
    private val kindsById = linkedMapOf<String, FabricTablePropKind>()

    override val registrationKeys: Set<String> get() = kindsById.keys.toSet()

    override var isFrozen: Boolean = false
        private set

    override val kinds: List<FabricTablePropKind> get() = kindsById.values.toList()

    override fun register(kind: FabricTablePropKind) {
        check(!isFrozen) { "Table prop kind registry is frozen" }
        MinecraftResourceIds.requireValid(kind.id) { "Table prop kind must be namespaced: ${kind.id}" }
        require(kindsById.putIfAbsent(kind.id, kind) == null) { "Duplicate table prop kind: ${kind.id}" }
    }

    override fun find(kindId: String): FabricTablePropKind? = kindsById[kindId]

    override fun freeze() {
        isFrozen = true
    }
}
