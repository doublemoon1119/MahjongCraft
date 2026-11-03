package com.doublemoon1119.mahjongcraft.example.fabric.character

import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtElement
import net.minecraft.util.math.EulerAngle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** 借用中的原生存檔保存原始姿態，而不是暫時揮手或停用重力的狀態。 */
class ExampleActorPoseTest {
    /** 還原用資料覆蓋目前姿態，但不覆蓋擁有者或其他原生欄位。 */
    @Test
    fun `saved pose uses original angles and gravity without replacing other entity data`() {
        val original = ExampleActorPose(
            head = EulerAngle(
                4f,
                8f,
                0f,
            ),
            body = EulerAngle(
                0f,
                0f,
                0f,
            ),
            rightArm = EulerAngle(
                -10f,
                0f,
                0f,
            ),
            leftArm = EulerAngle(
                -10f,
                0f,
                0f,
            ),
            rightLeg = EulerAngle(
                0f,
                0f,
                0f,
            ),
            leftLeg = EulerAngle(
                0f,
                0f,
                0f,
            ),
            noGravity = false,
        )
        val nbt = NbtCompound().apply {
            putString("CustomName", "Named actor")
            putBoolean("NoGravity", true)
        }
        original.writeOriginal(nbt)
        val pose = nbt.getCompound("Pose")
        assertEquals(original.head, EulerAngle(pose.getList("Head", NbtElement.FLOAT_TYPE.toInt())))
        assertEquals(original.rightArm, EulerAngle(pose.getList("RightArm", NbtElement.FLOAT_TYPE.toInt())))
        assertEquals(original.rightLeg, EulerAngle(pose.getList("RightLeg", NbtElement.FLOAT_TYPE.toInt())))
        assertFalse(nbt.getBoolean("NoGravity"))
        assertEquals("Named actor", nbt.getString("CustomName"))
    }
}
