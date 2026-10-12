package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExtensionGameCommand
import com.doublemoon1119.mahjongcraft.logic.base.ExtensionGameAction
import kotlin.uuid.Uuid

/**
 * 將一種規則擴充動作及其選牌結果轉換為可執行命令。
 *
 * @param A 此 factory 接受的規則擴充動作型別。
 */
fun interface ExtensionGameActionCommandFactory<A : ExtensionGameAction> {
    /**
     * 建立命令；選牌資料不符合動作契約時回傳 null。
     *
     * @param action 要轉換的規則擴充動作。
     * @param selectedTileIds 玩家選取的牌識別碼。
     * @return 可執行的擴充命令；選牌資料不符合動作契約時為 null。
     */
    fun create(action: A, selectedTileIds: List<Uuid>): ExtensionGameCommand?
}
