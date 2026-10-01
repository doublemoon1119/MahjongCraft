package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryAccess
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryPolicy
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryScope
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryQueryErrorCodeDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** 驗證歷史查詢回覆前的政策與權限重新檢查。 */
class HistoryQueryReplyPolicyTest {
    /** 查詢重新載入後停用時，所有範圍都應拒絕。 */
    @Test
    fun `query disabled rejects own and all scopes`() {
        val access = HistoryQueryAccess(Uuid.random(), isAdministrator = true)
        val policy = HistoryQueryPolicy(queryEnabled = false, allowAdministratorQuery = true)

        assertEquals(HistoryQueryErrorCodeDto.QUERY_DISABLED, replyError(access, policy, HistoryQueryScope.OWN))
        assertEquals(HistoryQueryErrorCodeDto.QUERY_DISABLED, replyError(access, policy, HistoryQueryScope.ALL))
    }

    /** 管理員查詢關閉時，管理員仍可查詢自己的對局但不可查詢全部。 */
    @Test
    fun `admin query disabled still allows own scope`() {
        val access = HistoryQueryAccess(Uuid.random(), isAdministrator = true)
        val policy = HistoryQueryPolicy(queryEnabled = true, allowAdministratorQuery = false)

        assertNull(replyError(access, policy, HistoryQueryScope.OWN))
        assertEquals(HistoryQueryErrorCodeDto.ACCESS_DENIED, replyError(access, policy, HistoryQueryScope.ALL))
    }

    /** 普通玩家永遠不能升級為全部歷史查詢。 */
    @Test
    fun `non administrator cannot query all scope`() {
        val access = HistoryQueryAccess(Uuid.random(), isAdministrator = false)
        val policy = HistoryQueryPolicy()

        assertNull(replyError(access, policy, HistoryQueryScope.OWN))
        assertEquals(HistoryQueryErrorCodeDto.ACCESS_DENIED, replyError(access, policy, HistoryQueryScope.ALL))
    }
}
