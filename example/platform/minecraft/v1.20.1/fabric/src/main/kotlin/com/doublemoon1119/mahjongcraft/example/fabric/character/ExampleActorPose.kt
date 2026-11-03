package com.doublemoon1119.mahjongcraft.example.fabric.character

import net.minecraft.entity.decoration.ArmorStandEntity
import net.minecraft.nbt.NbtCompound
import net.minecraft.util.math.EulerAngle

/**
 * 一次接管前的原生姿態；保存時寫回原姿態，重開後不留下暫時動畫或重力設定。
 *
 * @property head 頭部角度。
 * @property body 身體角度。
 * @property rightArm 右臂角度。
 * @property leftArm 左臂角度。
 * @property rightLeg 右腿角度。
 * @property leftLeg 左腿角度。
 * @property noGravity 原本是否停用重力。
 */
internal data class ExampleActorPose(
    val head: EulerAngle,
    val body: EulerAngle,
    val rightArm: EulerAngle,
    val leftArm: EulerAngle,
    val rightLeg: EulerAngle,
    val leftLeg: EulerAngle,
    val noGravity: Boolean,
) {
    /** 將同一份原始狀態恢復到仍存在的人偶。 */
    fun restore(actor: ArmorStandEntity) {
        actor.setHeadRotation(head)
        actor.setBodyRotation(body)
        actor.setRightArmRotation(rightArm)
        actor.setLeftArmRotation(leftArm)
        actor.setRightLegRotation(rightLeg)
        actor.setLeftLegRotation(leftLeg)
        actor.setNoGravity(noGravity)
    }

    /** 沿用原版 Pose 格式寫入原始狀態，不修改正在顯示的世界實體。 */
    fun writeOriginal(nbt: NbtCompound) {
        val pose = NbtCompound()
        pose.put(HEAD, head.toNbt())
        pose.put(BODY, body.toNbt())
        pose.put(RIGHT_ARM, rightArm.toNbt())
        pose.put(LEFT_ARM, leftArm.toNbt())
        pose.put(RIGHT_LEG, rightLeg.toNbt())
        pose.put(LEFT_LEG, leftLeg.toNbt())
        nbt.put(POSE, pose)
        nbt.putBoolean(NO_GRAVITY, noGravity)
    }

    /** 擷取原版姿態及其原生保存欄位。 */
    companion object {
        /** 原版姿態物件。 */
        private const val POSE = "Pose"

        /** 原版頭部姿態。 */
        private const val HEAD = "Head"

        /** 原版身體姿態。 */
        private const val BODY = "Body"

        /** 原版右臂姿態。 */
        private const val RIGHT_ARM = "RightArm"

        /** 原版左臂姿態。 */
        private const val LEFT_ARM = "LeftArm"

        /** 原版右腿姿態。 */
        private const val RIGHT_LEG = "RightLeg"

        /** 原版左腿姿態。 */
        private const val LEFT_LEG = "LeftLeg"

        /** 原版重力設定。 */
        private const val NO_GRAVITY = "NoGravity"

        /** 只讀取當下姿態，不改動原生實體。 */
        fun capture(actor: ArmorStandEntity): ExampleActorPose = ExampleActorPose(
            head = actor.headRotation,
            body = actor.bodyRotation,
            rightArm = actor.rightArmRotation,
            leftArm = actor.leftArmRotation,
            rightLeg = actor.rightLegRotation,
            leftLeg = actor.leftLegRotation,
            noGravity = actor.hasNoGravity(),
        )
    }
}
