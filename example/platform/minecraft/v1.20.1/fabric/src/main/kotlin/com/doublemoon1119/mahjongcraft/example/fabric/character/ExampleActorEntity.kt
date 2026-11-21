package com.doublemoon1119.mahjongcraft.example.fabric.character

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatRequest
import net.minecraft.core.Rotations
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.GameRules
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

/**
 * 可借用的範例人偶，重用原版盔甲架模型，不為打牌加入自主行走或工作 AI。
 *
 * @param type 原生實體類型。
 * @param world 所屬世界。
 */
class ExampleActorEntity(type: EntityType<out ArmorStand>, world: Level) : ArmorStand(type, world) {
    /** 放置者只用於伺服器授權，不以名稱替代身分，也不需送到客戶端。 */
    var ownerId: Uuid? = null
        internal set

    /** 原始姿態與單一接管者，永不保存 handle 或接管旗標。 */
    internal val control = ExampleActorControl(
        capture = { ExampleActorPose.capture(this) },
        restore = { pose -> pose.restore(this) },
    )

    /** 已套用的乘坐姿態，載入或離座時依目前載具重新決定。 */
    private var wasSeated: Boolean? = null

    /** 由公開回呼取得的指定席位，只有範例自己的返回行為會讀取。 */
    internal var assignedSeat: SeatRequest? = null

    /** 揮手結束前剩餘的伺服器 tick。 */
    private var swingTicks = 0

    init {
        setShowArms(true)
    }

    /** 讓世界人偶接受正常指向互動，供座位物品及命名牌使用。 */
    override fun isPickable(): Boolean = !isRemoved

    /** 不提供裝備編輯；命名牌仍可沿物品的互動流程命名。 */
    override fun interactAt(
        player: Player,
        hitPos: Vec3,
        hand: InteractionHand,
    ): InteractionResult = InteractionResult.PASS

    /** 未借用時放置者可以左鍵收回人偶，借用中不接受破壞。 */
    override fun hurt(source: DamageSource, amount: Float): Boolean {
        val player = source.entity as? Player ?: return false
        if (level().isClientSide || source.directEntity !== player || control.isActive || isRemoved) return false
        if (player.isSpectator || !player.abilities.mayBuild || !level().mayInteract(player, blockPosition())) return false
        if (ownerId != player.uuid.toKotlinUuid()) return false
        stopRiding()
        discard()
        if (!player.abilities.instabuild && level().gameRules.getBoolean(GameRules.RULE_DOENTITYDROPS)) spawnAtLocation(ItemStack(ExampleActors.item))
        return true
    }

    /** 保存放置者，借用期間的暫時姿態仍以接管前狀態保存。 */
    override fun addAdditionalSaveData(nbt: CompoundTag) {
        super.addAdditionalSaveData(nbt)
        ownerId?.let { nbt.putUUID(OWNER_KEY, it.toJavaUuid()) }
        control.savedState?.writeOriginal(nbt)
    }

    /** 缺少或無效的放置者保持未授權，不推測或重新指定主人。 */
    override fun readAdditionalSaveData(nbt: CompoundTag) {
        super.readAdditionalSaveData(nbt)
        ownerId = if (nbt.hasUUID(OWNER_KEY)) nbt.getUUID(OWNER_KEY).toKotlinUuid() else null
        setShowArms(true)
    }

    /** 已成立動作使用短暫揮手，未知動作由 handle 忽略。 */
    internal fun animateDiscard() {
        if (!control.isActive) return
        swingTicks = SWING_TICKS
        setRightArmPose(
            Rotations(
                -55f,
                -20f,
                0f,
            ),
        )
    }

    /** 更新返回位置、坐姿與揮手計時，姿態透過原生資料追蹤同步。 */
    override fun tick() {
        super.tick()
        if (!level().isClientSide) {
            if (control.isActive && !isPassenger && tickCount % RETURN_CHECK_TICKS == 0) assignedSeat?.let { ExampleActorReturnToSeat.tryReturn(this, it) }
            updateSittingPose()
            if (swingTicks > 0 && --swingTicks == 0) control.savedState?.let { setRightArmPose(it.rightArm) }
        }
    }

    /** 只在乘坐狀態改變時同步腿部姿態，不讓拆除家具或重開後仍維持懸空坐姿。 */
    internal fun updateSittingPose() {
        val seated = isPassenger
        if (wasSeated == seated) return
        wasSeated = seated
        setLeftLegPose(
            if (seated) {
                Rotations(
                    -85f,
                    -12f,
                    0f,
                )
            } else {
                Rotations(
                    0f,
                    0f,
                    0f,
                )
            },
        )
        setRightLegPose(
            if (seated) {
                Rotations(
                    -85f,
                    12f,
                    0f,
                )
            } else {
                Rotations(
                    0f,
                    0f,
                    0f,
                )
            },
        )
    }

    /** 結束本次揮手計時，姿態由接管快照恢復。 */
    internal fun stopFeedback() {
        swingTicks = 0
    }

    /** 人偶保存欄位與簡單動作時長。 */
    private companion object {
        /** 放置者是原生 UUID，不保存文字名稱或遊戲成員 UUID。 */
        const val OWNER_KEY = "ExampleActorOwner"

        /** 範例每秒檢查一次返回位置，不在公開席位回呼中等待。 */
        const val RETURN_CHECK_TICKS = 20

        /** 與普通揮手相同的基本時長。 */
        const val SWING_TICKS = 6
    }
}
