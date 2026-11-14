package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModEntities
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModItems
import com.doublemoon1119.mahjongcraft.platform.minecraft.ruler.MahjongRulerColor
import com.doublemoon1119.mahjongcraft.platform.minecraft.ruler.MahjongRulerDimensions
import com.doublemoon1119.mahjongcraft.platform.minecraft.ruler.MahjongRulerPosture
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.entity.data.DataTracker
import net.minecraft.entity.data.TrackedData
import net.minecraft.entity.data.TrackedDataHandlerRegistry
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.sound.SoundEvents
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.math.Box
import net.minecraft.world.World

/**
 * 放在方塊上的牌尺，純裝飾，不參與牌局。
 *
 * 剛放下時平放，蹲下右鍵在平放與豎放之間切換。碰撞範圍依朝向與擺法貼齊整支牌尺，不會擋住旁邊的牌；左鍵收回並掉落
 * 同色牌尺，創造模式只移除。
 *
 * @param type Minecraft entity 類型。
 * @param world 實體所屬世界。
 */
class MahjongRulerEntity(
    type: EntityType<out MahjongRulerEntity> = ModEntities.mahjongRuler,
    world: World,
) : Entity(type, world) {
    /** 牌尺的顏色。 */
    var color: MahjongRulerColor
        get() = MahjongRulerColor.fromNameOrDefault(dataTracker[COLOR])
        set(value) = dataTracker.set(COLOR, value.name)

    /** 牌尺的擺法；改變時一併更新碰撞範圍。 */
    var posture: MahjongRulerPosture
        get() = MahjongRulerPosture.fromNameOrDefault(dataTracker[POSTURE])
        set(value) = dataTracker.set(POSTURE, value.name)

    init {
        setNoGravity(true)
    }

    /**
     * 轉動時一併更新碰撞範圍，因為範圍隨朝向改變。
     *
     * @param yaw 新的朝向角度。
     */
    override fun setYaw(yaw: Float) {
        super.setYaw(yaw)
        boundingBox = calculateBoundingBox()
    }

    /**
     * 擺法同步到 client 時一併更新碰撞範圍。
     *
     * @param data 改變的同步欄位。
     */
    override fun onTrackedDataSet(data: TrackedData<*>) {
        super.onTrackedDataSet(data)
        if (data == POSTURE) boundingBox = calculateBoundingBox()
    }

    /** 依朝向與擺法計算包住整支牌尺的範圍，底面貼齊實體位置。 */
    override fun calculateBoundingBox(): Box {
        // 父類別建構時就會計算範圍，此時同步欄位還沒建立，以剛放下時的平放計算。
        val currentPosture = if (dataTracker == null) MahjongRulerPosture.FLAT else posture
        val footprint = MahjongRulerDimensions.footprint(yaw.toDouble(), currentPosture)
        return Box(
            x - footprint.halfX,
            y,
            z - footprint.halfZ,
            x + footprint.halfX,
            y + MahjongRulerDimensions.height(currentPosture),
            z + footprint.halfZ,
        )
    }

    /** 牌尺不提供物理阻擋，僅供視線選取。 */
    override fun isCollidable(): Boolean = false

    /** 牌尺不參與一般 entity 推擠。 */
    override fun isPushable(): Boolean = false

    /** 允許玩家視線選取牌尺。 */
    override fun canHit(): Boolean = !isRemoved

    /** 允許牌尺成為左鍵收回目標。 */
    override fun isAttackable(): Boolean = !isRemoved

    /**
     * 蹲下右鍵在平放與豎放之間切換；沒有蹲下時不做任何事。
     *
     * @param player 進行互動的玩家。
     * @param hand 玩家使用的手。
     */
    override fun interact(player: PlayerEntity, hand: Hand): ActionResult {
        if (!player.isSneaking) return ActionResult.PASS
        if (world.isClient) return ActionResult.SUCCESS
        posture = posture.next()
        return ActionResult.CONSUME
    }

    /**
     * 左鍵收回牌尺；創造模式只移除實體，其他模式掉落同色牌尺。
     *
     * @param attacker 發起攻擊的 entity；非玩家不會收回牌尺。
     */
    override fun handleAttack(attacker: Entity): Boolean {
        val player = attacker as? PlayerEntity ?: return false
        if (!world.isClient) {
            if (!player.abilities.creativeMode) dropStack(asItemStack())
            playSound(SoundEvents.ENTITY_ITEM_FRAME_BREAK, 1.0f, 1.0f)
            discard()
        }
        return true
    }

    /** 創造模式中鍵取得同色牌尺。 */
    override fun getPickBlockStack(): ItemStack = asItemStack()

    /** 建立同色的牌尺物品。 */
    private fun asItemStack(): ItemStack = ItemStack(ModItems.MAHJONG_RULERS.getValue(color))

    /** 初始化 client/server 同步的顏色與擺法。 */
    override fun initDataTracker() {
        dataTracker.startTracking(COLOR, MahjongRulerColor.CLEAR.name)
        dataTracker.startTracking(POSTURE, MahjongRulerPosture.FLAT.name)
    }

    /**
     * 從世界存檔還原顏色與擺法。
     *
     * @param nbt entity 的持久化資料。
     */
    override fun readCustomDataFromNbt(nbt: NbtCompound) {
        color = MahjongRulerColor.fromNameOrDefault(nbt.getString(NBT_KEY_COLOR))
        posture = MahjongRulerPosture.fromNameOrDefault(nbt.getString(NBT_KEY_POSTURE))
    }

    /**
     * 將顏色與擺法寫入世界存檔。
     *
     * @param nbt 接收 entity 持久化資料的 NBT。
     */
    override fun writeCustomDataToNbt(nbt: NbtCompound) {
        nbt.putString(NBT_KEY_COLOR, color.name)
        nbt.putString(NBT_KEY_POSTURE, posture.name)
    }

    private companion object {
        /** 世界存檔保存顏色的欄位名稱。 */
        const val NBT_KEY_COLOR: String = "Color"

        /** 世界存檔保存擺法的欄位名稱。 */
        const val NBT_KEY_POSTURE: String = "Posture"

        /** 以名稱同步顏色。 */
        val COLOR: TrackedData<String> = DataTracker.registerData(MahjongRulerEntity::class.java, TrackedDataHandlerRegistry.STRING)

        /** 以名稱同步擺法。 */
        val POSTURE: TrackedData<String> = DataTracker.registerData(MahjongRulerEntity::class.java, TrackedDataHandlerRegistry.STRING)
    }
}
