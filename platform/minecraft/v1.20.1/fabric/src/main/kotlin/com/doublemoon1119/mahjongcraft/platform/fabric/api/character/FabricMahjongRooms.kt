package com.doublemoon1119.mahjongcraft.platform.fabric.api.character

import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableInteractionReach
import net.minecraft.entity.Entity

/**
 * 已登記實體的伺服器端空席查詢，不提供客戶端封包或全服房間目錄。
 * 必須在伺服器主執行緒呼叫；不載入區塊、不移動實體、不保留席位。
 * 路徑搜尋與工作範圍由整合者處理，結果不得轉送給未授權的客戶端。
 */
object FabricMahjongRooms {
    /** 目前 session 的查詢實作；不對第三方公開生命週期操作。 */
    private var query: ((Entity, Double) -> List<FabricRoomVacancy>)? = null

    /**
     * 從實體當下位置查詢附近可加入的等待房間。
     *
     * 框架另限制距離、數量及頻率；被限流、無資格或沒有空席都回傳空清單。
     * 結果只代表當下可用，到達及入座時仍須重新驗證。
     *
     * @param entity 已載入、已登記且由整合者確認有控制／工作授權的實體。
     * @param maximumDistance 呼叫端要求的更小範圍；超過框架上限不會增加搜尋範圍。
     * @return 此實體可加入的有界席位清單，沒有時不洩漏其他房間存在。
     */
    fun findVacancies(entity: Entity, maximumDistance: Double = TableInteractionReach.SURVIVAL): List<FabricRoomVacancy> = query?.invoke(entity, maximumDistance).orEmpty()

    /**
     * 替換目前 session 的查詢入口。
     *
     * @param handler 目前 session 的實作；null 表示停止查詢。
     */
    @JvmSynthetic
    internal fun install(handler: ((Entity, Double) -> List<FabricRoomVacancy>)?) {
        query = handler
    }
}
