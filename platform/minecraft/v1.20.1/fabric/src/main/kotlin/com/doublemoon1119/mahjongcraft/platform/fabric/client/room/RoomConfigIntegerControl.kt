package com.doublemoon1119.mahjongcraft.platform.fabric.client.room

import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigEditorSpec

/**
 * 房間設定畫面整數欄位的加減控制語意，與 `RoomScreen` 的 widget 建立完全分離。
 *
 * 可為 null 的欄位（例如「不限制」）把 null 視為比 [GameConfigEditorSpec.IntegerInput.minimum] 更低的
 * 一個狀態：從最小值再往下減會清成 null，而從 null 往上加會回到最小值，因此玩家永遠有辦法回到有值
 * 的狀態。不可為 null 的欄位則在兩端各自停住。
 */
internal object RoomConfigIntegerControl {
    /** 按住 Shift 時每次調整的倍率。 */
    internal const val SHIFT_STEP_MULTIPLIER = 10

    /** 依是否按住 Shift 換算單次調整量。 */
    fun adjustment(
        editor: GameConfigEditorSpec.IntegerInput,
        shiftHeld: Boolean,
    ): Int = editor.step * if (shiftHeld) SHIFT_STEP_MULTIPLIER else 1

    /** 判斷整數欄位是否仍有更低的合法狀態。 */
    fun canDecrease(
        current: Int?,
        editor: GameConfigEditorSpec.IntegerInput,
    ): Boolean = when {
        current == null -> false
        current > editor.minimum -> true
        else -> editor.nullable
    }

    /** 判斷整數欄位是否仍有更高的合法狀態。 */
    fun canIncrease(
        current: Int?,
        editor: GameConfigEditorSpec.IntegerInput,
    ): Boolean = current == null || current < editor.maximum

    /**
     * 計算按下減號後的新值。
     *
     * @return 新值；已經是最小值且欄位可為 null 時回傳 `null` 代表清成未設定，目前已是 `null` 時
     *   維持 `null`。
     */
    fun decrement(
        current: Int?,
        editor: GameConfigEditorSpec.IntegerInput,
        shiftHeld: Boolean,
    ): Int? = when {
        current == null -> null
        editor.nullable && current <= editor.minimum -> null
        else -> (current - adjustment(editor = editor, shiftHeld = shiftHeld)).coerceAtLeast(editor.minimum)
    }

    /**
     * 計算按下加號後的新值。
     *
     * @return 新值；目前為 `null` 時回到 [GameConfigEditorSpec.IntegerInput.minimum]。
     */
    fun increment(
        current: Int?,
        editor: GameConfigEditorSpec.IntegerInput,
        shiftHeld: Boolean,
    ): Int = if (current == null) {
        editor.minimum
    } else {
        (current + adjustment(editor = editor, shiftHeld = shiftHeld)).coerceAtMost(editor.maximum)
    }
}
