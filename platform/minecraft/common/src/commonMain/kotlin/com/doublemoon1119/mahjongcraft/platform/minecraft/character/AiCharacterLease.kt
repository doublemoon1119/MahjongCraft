package com.doublemoon1119.mahjongcraft.platform.minecraft.character

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterOwnership
import kotlin.uuid.Uuid

/**
 * 平台已確認身分及所有權的單次角色租用；取得它尚未開始暫停原生行為。
 *
 * @param R 平台角色型別。
 */
interface AiCharacterLease<R> {
    /** 此次租用固定使用的角色。 */
    val rider: R

    /** 世界角色的穩定 UUID。 */
    val entityId: Uuid

    /** 是否可以在釋放後移除世界角色。 */
    val ownership: AiCharacterOwnership

    /** 角色是否仍可在目前已載入範圍內使用；不能載入新區塊。 */
    fun isValid(): Boolean

    /** 暫停會干擾入座的原生行為；失敗或拋錯後仍會執行 release。 */
    fun prepare(): Boolean

    /** 恢復接管前的行為及清除暫態控制，須可重複呼叫。 */
    fun release()

    /** 移除已驗證為自有的角色；協調器不對借用角色呼叫。 */
    fun discardOwned()
}
