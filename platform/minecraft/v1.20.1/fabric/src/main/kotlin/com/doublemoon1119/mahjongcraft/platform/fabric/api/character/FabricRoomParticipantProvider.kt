package com.doublemoon1119.mahjongcraft.platform.fabric.api.character

import net.minecraft.entity.Entity
import kotlin.uuid.Uuid

/**
 * 唯讀辨識可打麻將的既有實體；所有回呼在伺服器主執行緒，不得載入區塊或改變實體與對局。
 * 原生行走、工作與主人授權由整合者管理，實際接管另由角色 handle 負責。
 */
interface FabricRoomParticipantProvider {
    /**
     * 辨識實體並擷取當下描述，不支援或暫時不允許時回傳 null。
     *
     * @param entity 已載入且非玩家的候選實體。
     * @return 可參加的描述；不得生成另一隻實體。
     */
    fun describe(entity: Entity): FabricRoomParticipant?

    /**
     * 確認實體的工作／控制權允許它加入這位房主的房間，並在開局按正式排位重新入座。
     *
     * @param entity 同一隻候選實體。
     * @param hostId 目前房主，不代表已獲實體主人授權。
     * @return 整合者確認可以參加時為 true；框架仍另驗房間與席位。
     */
    fun canJoin(entity: Entity, hostId: Uuid): Boolean
}
