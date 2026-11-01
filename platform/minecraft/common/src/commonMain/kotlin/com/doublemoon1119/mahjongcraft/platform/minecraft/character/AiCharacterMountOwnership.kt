package com.doublemoon1119.mahjongcraft.platform.minecraft.character

/** 判斷目前乘坐關係是否仍屬於這次角色接管，不干預外部後來接手的載具。 */
object AiCharacterMountOwnership {
    /**
     * 只解除本次安排、仍未被替換的乘坐關係。
     *
     * @param V 平台載具型別。
     * @param original 接管前的載具。
     * @param assigned 本次安排完成後的載具。
     * @param current 目前載具。
     * @return 目前仍乘坐本次新增載具時為 true；比較物件身分，不比較值。
     */
    fun <V : Any> shouldDetach(
        original: V?,
        assigned: V?,
        current: V?,
    ): Boolean = current != null && current === assigned && current !== original
}
