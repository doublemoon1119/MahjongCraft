package com.doublemoon1119.mahjongcraft.platform.fabric.server.table.prop

import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropPlacement
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropTarget
import net.minecraft.entity.Entity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.Box
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
     *
     * @param world entity 所在的伺服器世界。
     * @param tableId 要查找的桌子 UUID。
     * @param searchBox 限定搜尋範圍的方塊盒。
     * @return 此種類中由該桌管理的 entity。
     */
    fun findManaged(world: ServerWorld, tableId: Uuid, searchBox: Box): List<Entity>

    /**
     * 讀出既有 entity 目前的變體；無法辨識時回傳 null，該 entity 會被視為多餘而移除。
     *
     * @param entity 要判定變體的既有 entity。
     * @return 此種類的變體 key；無法辨識時為 null。
     */
    fun variantOf(entity: Entity): String?

    /**
     * 建立一個擺在 [target] 落點、標記給指定桌子的新 entity，尚未加入世界。
     *
     * @return 新 entity；不認得 [TablePropTarget.variant] 時回傳 null。
     * @param world 用來建立 entity 的伺服器世界。
     * @param tableId 要管理新 entity 的桌子 UUID。
     * @param target 物件種類、變體與落點。
     */
    fun create(world: ServerWorld, tableId: Uuid, target: TablePropTarget): Entity?

    /**
     * entity 成功加入世界後呼叫，例如開始出現動畫。
     *
     * @param entity 已成功加入世界的桌面物件。
     */
    fun onSpawned(entity: Entity) = Unit
}
