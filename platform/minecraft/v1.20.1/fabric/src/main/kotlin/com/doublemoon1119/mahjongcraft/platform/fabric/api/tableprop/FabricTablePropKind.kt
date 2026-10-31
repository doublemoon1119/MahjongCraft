package com.doublemoon1119.mahjongcraft.platform.fabric.api.tableprop

import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropContent
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropKindDefinition
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
    /** 共用的種類身分、內容驗證與實際幾何尺寸。 */
    val definition: TablePropKindDefinition

    /** 物件種類的命名空間識別碼，與共用定義使用同一來源。 */
    val id: String get() = definition.id

    /**
     * 讀取實體保存的規則物件 ID。
     *
     * @param entity 要辨識的實體。
     * @return 穩定 ID；無法辨識時為 null，交由同步移除。
     */
    fun placementIdOf(entity: Entity): String?

    /**
     * 讀取物件建立時所屬規則，不用目前場景的規則補造既有物件資料。
     *
     * @param entity 要辨識的實體。
     * @return 已保存的規則 ID；無法辨識時為 null。
     */
    fun ruleModuleIdOf(entity: Entity): String?

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
     * 讀取實體保存的型別化公開內容。
     *
     * @param entity 要辨識的實體。
     * @return 可辨識內容；資料不完整時為 null。
     */
    fun contentOf(entity: Entity): TablePropContent?

    /**
     * 建立一個擺在 [target] 落點、標記給指定桌子的新 entity，尚未加入世界。
     *
     * 只能準備物件，不得自行生成、改動權威狀態或發布其他副作用；失敗前配置的外部資源由種類自行釋放。
     * 將穩定 ID 和內容寫入可重建的實體保存資料，讓重載後的同步能找回同一物件。
     *
     * @return 新 entity；無法建立已通過共用驗證的內容時回傳 null。
     * @param world 用來建立 entity 的伺服器世界。
     * @param tableId 要管理新 entity 的桌子 UUID。
     * @param target 物件種類、變體與落點。
     */
    fun create(world: ServerWorld, tableId: Uuid, target: TablePropTarget): Entity?

    /**
     * 同一批新 entity 全部成功加入世界後呼叫，例如開始不推擠其他物件的出現動畫。
     *
     * @param entity 已成功加入世界的桌面物件。
     */
    fun onSpawned(entity: Entity) = Unit
}
