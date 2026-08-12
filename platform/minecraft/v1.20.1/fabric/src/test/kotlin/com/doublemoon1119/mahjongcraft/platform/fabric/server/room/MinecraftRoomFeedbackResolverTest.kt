package com.doublemoon1119.mahjongcraft.platform.fabric.server.room

import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomError
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftPlayerFeedback
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** [MinecraftRoomFeedbackResolver] 的回饋路由測試。 */
class MinecraftRoomFeedbackResolverTest {
    /** 玩家已在其他遊戲時應使用明確回饋。 */
    @Test
    fun `player membership conflict uses already in game feedback`() {
        val feedback = MinecraftRoomFeedbackResolver.joinError(
            RoomError.PlayerAlreadyInAnotherGame(Uuid.random(), Uuid.random()),
        )

        assertEquals(MinecraftPlayerFeedback.PlayerAlreadyInGame, feedback)
    }

    /** 其他加入錯誤應使用通用失敗回饋，避免暴露內部錯誤種類。 */
    @Test
    fun `other join errors use generic failure feedback`() {
        val feedback = MinecraftRoomFeedbackResolver.joinError(
            RoomError.RoomNotFound(Uuid.random()),
        )

        assertEquals(MinecraftPlayerFeedback.GameJoinFailed, feedback)
    }

    /** 房主成功離開時應回饋遊戲解散。 */
    @Test
    fun `host leave uses game dissolved feedback`() {
        assertEquals(
            MinecraftPlayerFeedback.GameDissolved,
            MinecraftRoomFeedbackResolver.successfulLeave(wasHost = true),
        )
    }

    /** 非房主成功離開時應使用一般離開回饋。 */
    @Test
    fun `member leave uses game left feedback`() {
        assertEquals(
            MinecraftPlayerFeedback.GameLeft,
            MinecraftRoomFeedbackResolver.successfulLeave(wasHost = false),
        )
    }
}
