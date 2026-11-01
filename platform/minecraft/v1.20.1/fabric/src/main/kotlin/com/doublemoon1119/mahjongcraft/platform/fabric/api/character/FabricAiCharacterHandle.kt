package com.doublemoon1119.mahjongcraft.platform.fabric.api.character

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterOwnership
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatRequest
import net.minecraft.entity.Entity
import net.minecraft.util.math.Vec3d

/**
 * 一次接管固定世界角色的能力；所有回呼只在伺服器主執行緒執行。
 *
 * 回呼須立即返回，不阻塞或等待外部作業。
 * entity 必須在本次接管中保持相同，且不能是玩家。自有角色必須尚未加入世界，且原生類型須停用存檔，避免讀檔時重複生成；借用角色必須已在席位世界。
 * 不得在工廠中生成、移動、暫停原生 AI 或建立支撐實體。MahjongCraft 先認領 UUID，再呼叫 prepare。
 * 借用 adapter 須自行暫停及恢復走路、戰鬥等行為；若暫停狀態會保存，還須處理重開後的失效接管恢復。
 * 無法保證恢復的 adapter 應拒絕接管。解除時 MahjongCraft 不會移除借用角色，也不會自行復活它。
 */
interface FabricAiCharacterHandle {
    /** 此次接管固定使用的原生實體。 */
    val entity: Entity

    /** 自有或借用；角色建立後不得改變。 */
    val ownership: AiCharacterOwnership

    /**
     * 暫停會干擾入座的行為。
     *
     * @return 可接管時為 true；回傳 false 或拋錯後仍會呼叫 release，恢復部分完成的操作。
     */
    fun prepare(): Boolean

    /** 恢復接管前的行為並停止動畫及注視，須可重複呼叫，且在角色被移除後仍可安全呼叫。 */
    fun release()

    /**
     * 已完成入座或安全站立，通知目前固定席位；換位後會再次呼叫。
     *
     * @param request 本次成功安排的席位，只可用於角色姿態及朝向。
     */
    fun seatAssigned(request: SeatRequest) = Unit

    /**
     * 回應已成立操作的視覺回饋，不送出遊戲命令；不認識的識別碼可忽略。
     *
     * @param actionId 完整 namespaced 視覺動作識別碼。
     */
    fun animate(actionId: String) = Unit

    /**
     * 更新注視目標；只包含公開位置。adapter 可平順轉頭或忽略，不能據此改變遊戲決策。
     *
     * @param target 已載入範圍中的公開目標位置；null 表示停止注視。相同目標不會逐 tick 重送。
     */
    fun lookAt(target: Vec3d?) = Unit
}
