package com.doublemoon1119.mahjongcraft.example.fabric.table

import com.doublemoon1119.mahjongcraft.platform.fabric.entity.ManagedTableNbt
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.TablePropIdentityNbt
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.nbt.NbtCompound
import net.minecraft.world.World
import kotlin.uuid.Uuid

/**
 * 範例宣告使用的桌面晶片 entity。
 *
 * 它只由桌面物件同步器管理，不接受玩家互動，也不保存正式牌張身分。
 *
 * @param type Minecraft 實體類型。
 * @param world entity 所屬世界。
 */
class ExampleDeclarationChipEntity(
    type: EntityType<out ExampleDeclarationChipEntity>,
    world: World,
) : Entity(type, world) {
    /** 所屬桌子 UUID。 */
    var managedTableId: Uuid? = null

    /** 桌面物件同步使用的穩定識別碼。 */
    var placementId: String = ""

    /** 建立此籌碼的規則模組。 */
    var tablePropRuleModuleId: String = ""

    init {
        setNoGravity(true)
        noClip = true
    }

    /** 晶片不阻擋其他實體。 */
    override fun isCollidable(): Boolean = false

    /** 晶片不參與實體推擠。 */
    override fun isPushable(): Boolean = false

    /** 晶片不能被玩家點選。 */
    override fun canHit(): Boolean = false

    /** 這個示範 entity 不需要原生資料追蹤欄位。 */
    override fun initDataTracker() = Unit

    /** 保存桌面同步所需的管理身分。 */
    override fun writeCustomDataToNbt(nbt: NbtCompound) {
        ManagedTableNbt.write(nbt, managedTableId)
        TablePropIdentityNbt.write(nbt, placementId, tablePropRuleModuleId)
    }

    /** 還原桌面同步所需的管理身分。 */
    override fun readCustomDataFromNbt(nbt: NbtCompound) {
        managedTableId = ManagedTableNbt.read(nbt)
        val identity = TablePropIdentityNbt.read(nbt)
        placementId = identity?.placementId.orEmpty()
        tablePropRuleModuleId = identity?.ruleModuleId.orEmpty()
    }
}
