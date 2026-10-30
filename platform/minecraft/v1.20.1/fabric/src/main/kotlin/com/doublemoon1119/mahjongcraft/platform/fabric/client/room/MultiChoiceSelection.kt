package com.doublemoon1119.mahjongcraft.platform.fabric.client.room

import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigEditorSpec

/** 多選子畫面使用的純選取規則，避免 UI 元件自行複製上下限判斷。 */
internal object MultiChoiceSelection {
    /**
     * 切換一個選項；取消選取可以暫時低於最小數量，讓使用者能把 A 換成 B。
     *
     * @param selected 目前已選項目。
     * @param optionId 要切換的選項。
     * @param editor 欄位的選項與數量限制。
     * @return 切換後的選取集合。
     */
    fun toggle(
        selected: Set<String>,
        optionId: String,
        editor: GameConfigEditorSpec.MultiChoice,
    ): Set<String> {
        if (optionId !in editor.optionIds) return selected.toSet()
        val next = selected.toMutableSet()
        if (optionId in next) {
            next.remove(optionId)
        } else if (next.size < editor.maximumCount) {
            next.add(optionId)
        }
        return next
    }

    /** 判斷目前選取是否能套用。 */
    fun canApply(selected: Set<String>, editor: GameConfigEditorSpec.MultiChoice): Boolean = selected.size in editor.minimumCount..editor.maximumCount && selected.all { it in editor.optionIds }
}
