package com.doublemoon1119.mahjongcraft.example.minecraft.mod.table

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.entity.ManagedTableNbt
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.entity.TablePropIdentityNbt
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level
import kotlin.uuid.Uuid

/**
 * 範例攤牌使用的桌面籌碼 entity。
 *
 * 它只由桌面物件同步器管理，不接受玩家互動，也不保存正式牌張身分。
 *
 * @param type Minecraft 實體類型。
 * @param world entity 所屬世界。
 */
class ExampleHandRevealChipEntity(
    type: EntityType<out ExampleHandRevealChipEntity>,
    world: Level,
) : Entity(type, world) {
    /** 所屬桌子 UUID。 */
    var managedTableId: Uuid? = null

    /** 桌面物件同步使用的穩定識別碼。 */
    var placementId: String = ""

    /** 建立此籌碼的規則模組。 */
    var tablePropRuleModuleId: String = ""

    init {
        setNoGravity(true)
        noPhysics = true
    }

    /** 晶片不阻擋其他實體。 */
    override fun canBeCollidedWith(): Boolean = false

    /** 晶片不參與實體推擠。 */
    override fun isPushable(): Boolean = false

    /** 晶片不能被玩家點選。 */
    override fun isPickable(): Boolean = false

    /** 這個示範 entity 不需要原生資料追蹤欄位。 */
    override fun defineSynchedData() = Unit

    /** 保存桌面同步所需的管理身分。 */
    override fun addAdditionalSaveData(nbt: CompoundTag) {
        ManagedTableNbt.write(nbt, managedTableId)
        TablePropIdentityNbt.write(nbt, placementId, tablePropRuleModuleId)
    }

    /** 還原桌面同步所需的管理身分。 */
    override fun readAdditionalSaveData(nbt: CompoundTag) {
        managedTableId = ManagedTableNbt.read(nbt)
        val identity = TablePropIdentityNbt.read(nbt)
        placementId = identity?.placementId.orEmpty()
        tablePropRuleModuleId = identity?.ruleModuleId.orEmpty()
    }
}
