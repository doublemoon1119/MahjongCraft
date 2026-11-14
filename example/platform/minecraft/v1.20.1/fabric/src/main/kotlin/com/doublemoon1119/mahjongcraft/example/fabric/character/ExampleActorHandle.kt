package com.doublemoon1119.mahjongcraft.example.fabric.character

import com.doublemoon1119.mahjongcraft.example.fabric.furniture.entity.ExampleChairSeatEntity
import com.doublemoon1119.mahjongcraft.example.fabric.furniture.entity.ExampleCushionEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.api.character.FabricAiCharacterHandle
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterActionIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterOwnership
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatRequest
import net.minecraft.entity.Entity
import net.minecraft.util.math.EulerAngle
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * 借用與自有能力共用的人偶 handle，清理只恢復姿態，不移除實體。
 *
 * @property actor 本次固定的人偶。
 * @property ownership 工廠決定的所有權，之後不改變。
 */
internal class ExampleActorHandle(
    private val actor: ExampleActorEntity,
    override val ownership: AiCharacterOwnership,
) : FabricAiCharacterHandle {
    /** 本次 handle 的獨立認領身分。 */
    private val token = Any()

    /** 接管期間始終回傳同一隻人偶。 */
    override val entity: Entity get() = actor

    /** 先認領再停用重力，部分失敗時仍能由相同 token 恢復。 */
    override fun prepare(): Boolean {
        if (!actor.isAlive || !actor.control.acquire(token)) return false
        actor.setNoGravity(true)
        return true
    }

    /** 只釋放自己的接管，不刪除借用者，也不影響新的 handle。 */
    override fun release() {
        actor.control.release(token)
        if (!actor.control.isActive) {
            actor.assignedSeat = null
            actor.stopFeedback()
        }
    }

    /**
     * 保存公開指定席位；真正換位或仍坐在舊位置時，先離開範例自己的家具，再由回座更新處理。
     *
     * @param request 目前指定席位，不以請求物件身分判定是否換位。
     */
    override fun seatAssigned(request: SeatRequest) {
        val previous = actor.assignedSeat
        actor.assignedSeat = request
        val changed = previous != null && (previous.tableId != request.tableId || previous.dimensionId != request.dimensionId || !ExampleActorReturnToSeat.sameSeat(previous.seat, request.seat))
        val misplaced = actor.hasVehicle() &&
            !ExampleActorReturnToSeat.isAtSeat(
                x = actor.x,
                y = actor.y,
                z = actor.z,
                seat = request.seat,
            )
        if (changed || misplaced) {
            val vehicle = actor.vehicle
            if (vehicle is ExampleChairSeatEntity || vehicle is ExampleCushionEntity) {
                actor.stopRiding()
                if (vehicle is ExampleChairSeatEntity && !vehicle.hasPassengers()) vehicle.discard()
            }
        }
        actor.updateSittingPose()
    }

    /** 只有已成立出牌觸發右臂回饋。 */
    override fun animate(actionId: String) {
        if (actionId == AiCharacterActionIds.DISCARD) actor.animateDiscard()
    }

    /** 只小幅轉動頭部，不扭轉整個身體，也不讀取任何隱藏牌。 */
    override fun lookAt(target: Vec3d?) {
        val original = actor.control.savedState ?: return
        if (target == null) {
            actor.setHeadRotation(original.head)
            return
        }
        val dx = target.x - actor.x
        val dz = target.z - actor.z
        val dy = target.y - actor.eyeY
        val yaw = MathHelper.wrapDegrees(Math.toDegrees(atan2(-dx, dz)).toFloat() - actor.yaw).coerceIn(-35f, 35f)
        val pitch = -Math.toDegrees(atan2(dy, sqrt(dx * dx + dz * dz))).toFloat().coerceIn(-25f, 25f)
        actor.setHeadRotation(
            EulerAngle(
                pitch,
                yaw,
                0f,
            ),
        )
    }
}
