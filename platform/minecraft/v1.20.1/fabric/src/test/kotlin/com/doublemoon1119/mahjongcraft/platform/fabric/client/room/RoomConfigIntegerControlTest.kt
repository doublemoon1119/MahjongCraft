package com.doublemoon1119.mahjongcraft.platform.fabric.client.room

import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigEditorSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 驗證房間設定畫面整數欄位的上下限禁用條件、Shift 加速調整與可為 null 欄位的清空／復原語意。
 */
class RoomConfigIntegerControlTest {
    /** 到達下限時減號禁用，到達上限時加號禁用。 */
    @Test
    fun `adjustment controls are disabled at their respective bounds`() {
        val editor = bounded()

        assertFalse(RoomConfigIntegerControl.canDecrease(current = editor.minimum, editor = editor))
        assertTrue(RoomConfigIntegerControl.canIncrease(current = editor.minimum, editor = editor))
        assertTrue(RoomConfigIntegerControl.canDecrease(current = editor.maximum, editor = editor))
        assertFalse(RoomConfigIntegerControl.canIncrease(current = editor.maximum, editor = editor))
    }

    /** 中間值兩個方向都可調整。 */
    @Test
    fun `both controls stay enabled between the bounds`() {
        val editor = bounded()
        val middle = (editor.minimum + editor.maximum) / 2

        assertTrue(RoomConfigIntegerControl.canDecrease(current = middle, editor = editor))
        assertTrue(RoomConfigIntegerControl.canIncrease(current = middle, editor = editor))
    }

    /** 一般點擊不得越過上下限。 */
    @Test
    fun `single clicks never cross the legal bounds`() {
        val editor = bounded()

        assertEquals(
            editor.minimum,
            RoomConfigIntegerControl.decrement(current = editor.minimum, editor = editor, shiftHeld = false),
        )
        assertEquals(
            editor.maximum,
            RoomConfigIntegerControl.increment(current = editor.maximum, editor = editor, shiftHeld = false),
        )
    }

    /** Shift 點擊放大調整量，但同樣不得越界。 */
    @Test
    fun `shift clicks accelerate the step without crossing the bounds`() {
        val editor = bounded()
        val middle = (editor.minimum + editor.maximum) / 2

        assertEquals(
            middle - editor.step * RoomConfigIntegerControl.SHIFT_STEP_MULTIPLIER,
            RoomConfigIntegerControl.decrement(current = middle, editor = editor, shiftHeld = true),
        )
        assertEquals(
            middle + editor.step * RoomConfigIntegerControl.SHIFT_STEP_MULTIPLIER,
            RoomConfigIntegerControl.increment(current = middle, editor = editor, shiftHeld = true),
        )
        assertEquals(
            editor.minimum,
            RoomConfigIntegerControl.decrement(
                current = editor.minimum + editor.step,
                editor = editor,
                shiftHeld = true,
            ),
        )
        assertEquals(
            editor.maximum,
            RoomConfigIntegerControl.increment(
                current = editor.maximum - editor.step,
                editor = editor,
                shiftHeld = true,
            ),
        )
    }

    /** 從任何合法起點反覆調整都不會離開合法範圍。 */
    @Test
    fun `repeated adjustments always land inside the legal range`() {
        val editor = bounded()
        var value: Int? = editor.minimum

        repeat(40) { index ->
            value = if (index % 3 == 0) {
                RoomConfigIntegerControl.decrement(current = value, editor = editor, shiftHeld = index % 2 == 0)
            } else {
                RoomConfigIntegerControl.increment(current = value, editor = editor, shiftHeld = index % 2 == 0)
            }
            val current = value
            if (current != null) {
                assertTrue(
                    current in editor.minimum..editor.maximum,
                    "step $index produced out-of-range value $current",
                )
            }
        }
    }

    /** 不可為 null 的欄位在下限不得被清空。 */
    @Test
    fun `non nullable fields never clear to an empty value`() {
        val editor = bounded()

        assertEquals(
            editor.minimum,
            RoomConfigIntegerControl.decrement(current = editor.minimum, editor = editor, shiftHeld = false),
        )
        assertEquals(
            editor.minimum,
            RoomConfigIntegerControl.decrement(current = editor.minimum, editor = editor, shiftHeld = true),
        )
    }

    /** 可為 null 的欄位在下限再減一次會清成未設定，且此時減號被視為已無更低狀態。 */
    @Test
    fun `nullable fields clear once they fall below the minimum`() {
        val editor = nullable()

        assertTrue(RoomConfigIntegerControl.canDecrease(current = editor.minimum, editor = editor))
        assertNull(RoomConfigIntegerControl.decrement(current = editor.minimum, editor = editor, shiftHeld = false))
        assertFalse(RoomConfigIntegerControl.canDecrease(current = null, editor = editor))
        assertNull(RoomConfigIntegerControl.decrement(current = null, editor = editor, shiftHeld = false))
    }

    /** 未設定的可為 null 欄位一定能透過加號回到最小值。 */
    @Test
    fun `an empty nullable field can always be restored to the minimum`() {
        val editor = nullable()

        assertTrue(RoomConfigIntegerControl.canIncrease(current = null, editor = editor))
        assertEquals(
            editor.minimum,
            RoomConfigIntegerControl.increment(current = null, editor = editor, shiftHeld = false),
        )
        assertEquals(
            editor.minimum,
            RoomConfigIntegerControl.increment(current = null, editor = editor, shiftHeld = true),
        )
    }

    /** 清空與復原可以往返，不會卡在任何一端。 */
    @Test
    fun `clearing and restoring a nullable field round trips`() {
        val editor = nullable()
        val cleared = RoomConfigIntegerControl.decrement(
            current = editor.minimum,
            editor = editor,
            shiftHeld = false,
        )
        val restored = RoomConfigIntegerControl.increment(current = cleared, editor = editor, shiftHeld = false)

        assertNull(cleared)
        assertEquals(editor.minimum, restored)
    }

    /** 上下限相同的欄位兩個方向都禁用，且調整不改變數值。 */
    @Test
    fun `a single valued field disables both controls`() {
        val editor = GameConfigEditorSpec.IntegerInput(minimum = 4, maximum = 4)

        assertFalse(RoomConfigIntegerControl.canDecrease(current = 4, editor = editor))
        assertFalse(RoomConfigIntegerControl.canIncrease(current = 4, editor = editor))
        assertEquals(4, RoomConfigIntegerControl.decrement(current = 4, editor = editor, shiftHeld = false))
        assertEquals(4, RoomConfigIntegerControl.increment(current = 4, editor = editor, shiftHeld = false))
    }

    /** 建立不可為 null 的整數欄位規格。 */
    private fun bounded(): GameConfigEditorSpec.IntegerInput = GameConfigEditorSpec.IntegerInput(
        minimum = 10,
        maximum = 310,
        step = 5,
    )

    /** 建立可為 null 的整數欄位規格。 */
    private fun nullable(): GameConfigEditorSpec.IntegerInput = GameConfigEditorSpec.IntegerInput(
        minimum = 10,
        maximum = 310,
        step = 5,
        nullable = true,
    )
}
