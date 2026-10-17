package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.logic.base.ExtensionGameAction
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** [DiscardReactionActionSupport] 的反應動作邊界測試。 */
class DiscardReactionActionSupportTest {
    /** 驗證內建反應動作都能通過，且暗槓與加槓不會被誤當成明槓。 */
    @Test
    fun `built in discard reactions are supported selectively`() {
        assertTrue(DiscardReactionActionSupport.supports(GameAction.Pass))
        assertTrue(DiscardReactionActionSupport.supports(GameAction.Ron(Uuid.random())))
        assertTrue(DiscardReactionActionSupport.supports(GameAction.Chi(Uuid.random(), emptyList())))
        assertTrue(DiscardReactionActionSupport.supports(GameAction.Pon(Uuid.random(), emptyList())))
        assertTrue(
            DiscardReactionActionSupport.supports(
                GameAction.Kan(GameAction.KanType.OPEN_KAN, Uuid.random(), emptyList()),
            ),
        )
        assertFalse(
            DiscardReactionActionSupport.supports(
                GameAction.Kan(GameAction.KanType.CLOSED_KAN, Uuid.random(), emptyList()),
            ),
        )
        assertFalse(
            DiscardReactionActionSupport.supports(
                GameAction.Kan(GameAction.KanType.ADDED_KAN, Uuid.random(), emptyList()),
            ),
        )
    }

    /** 驗證擴充動作即使混在規則回傳的選項中，也會被集中判定排除。 */
    @Test
    fun `extension actions are excluded from discard reactions`() {
        val extension = GameAction.Extension(TestReactionAction)
        val actions = listOf(
            extension,
            GameAction.Pon(Uuid.random(), emptyList()),
            GameAction.Pass,
        )

        assertEquals(
            listOf(actions[1], actions[2]),
            DiscardReactionActionSupport.filter(actions),
        )
        assertFalse(DiscardReactionActionSupport.supports(extension))
    }

    /** 反應測試用的擴充動作。 */
    private data object TestReactionAction : ExtensionGameAction {
        override val id: String = "test:reaction"
    }
}
