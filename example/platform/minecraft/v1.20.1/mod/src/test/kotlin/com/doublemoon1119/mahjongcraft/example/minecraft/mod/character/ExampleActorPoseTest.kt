package com.doublemoon1119.mahjongcraft.example.minecraft.mod.character

import net.minecraft.core.Rotations
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** 借用中的原生存檔保存原始姿態，而不是暫時揮手或停用重力的狀態。 */
class ExampleActorPoseTest {
    /** 還原用資料覆蓋目前姿態，但不覆蓋擁有者或其他原生欄位。 */
    @Test
    fun `saved pose uses original angles and gravity without replacing other entity data`() {
        val original = ExampleActorPose(
            head = Rotations(
                4f,
                8f,
                0f,
            ),
            body = Rotations(
                0f,
                0f,
                0f,
            ),
            rightArm = Rotations(
                -10f,
                0f,
                0f,
            ),
            leftArm = Rotations(
                -10f,
                0f,
                0f,
            ),
            rightLeg = Rotations(
                0f,
                0f,
                0f,
            ),
            leftLeg = Rotations(
                0f,
                0f,
                0f,
            ),
            noGravity = false,
        )
        val nbt = CompoundTag().apply {
            putString("CustomName", "Named actor")
            putBoolean("NoGravity", true)
        }
        original.writeOriginal(nbt)
        val pose = nbt.getCompound("Pose")
        assertEquals(original.head, Rotations(pose.getList("Head", Tag.TAG_FLOAT.toInt())))
        assertEquals(original.rightArm, Rotations(pose.getList("RightArm", Tag.TAG_FLOAT.toInt())))
        assertEquals(original.rightLeg, Rotations(pose.getList("RightLeg", Tag.TAG_FLOAT.toInt())))
        assertFalse(nbt.getBoolean("NoGravity"))
        assertEquals("Named actor", nbt.getString("CustomName"))
    }
}
