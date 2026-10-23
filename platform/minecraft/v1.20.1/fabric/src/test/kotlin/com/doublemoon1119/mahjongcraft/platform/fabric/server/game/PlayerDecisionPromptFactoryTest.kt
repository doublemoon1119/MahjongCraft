package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import com.doublemoon1119.mahjongcraft.flow.common.game.model.PlayerDecisionPhase
import com.doublemoon1119.mahjongcraft.logic.base.ExtensionGameAction
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.uuid.Uuid

/** 決策候選識別碼的穩定性測試。 */
class PlayerDecisionPromptFactoryTest {
    /** 固定測試對局。 */
    private val gameId = Uuid.random()

    /** 固定測試玩家。 */
    private val playerId = Uuid.random()

    /** 宣告前後維持相同的摸牌識別碼。 */
    private val drawnTileId = Uuid.random()

    /** 同一張摸牌但合法動作集合改變時，必須產生新的決策識別碼。 */
    @Test
    fun `changed own turn candidates produce a new key`() {
        val before = listOf(candidate(GameAction.Tsumo)).decisionIdentity()
        val after = listOf(candidate(GameAction.Tsumo), candidate(GameAction.Extension(TestExtensionAction))).decisionIdentity()

        assertNotEquals(before, after)
        assertNotEquals(
            buildKey(before),
            buildKey(after),
        )
    }

    /** 候選順序改變但候選內容相同時，識別片段維持不變。 */
    @Test
    fun `equivalent candidates have the same identity regardless of order`() {
        val tsumo = candidate(GameAction.Tsumo)
        val declaration = candidate(GameAction.Extension(TestExtensionAction))

        assertEquals(
            listOf(tsumo, declaration).decisionIdentity(),
            listOf(declaration, tsumo).decisionIdentity(),
        )
    }

    /** 動作相同但可提交的 token 改變時，舊提示不可繼續沿用。 */
    @Test
    fun `changed submission token produces a new decision key`() {
        val original = candidate(GameAction.Tsumo)
        val replacement = original.copy(token = "replacement-token")
        assertNotEquals(buildKey(listOf(original).decisionIdentity()), buildKey(listOf(replacement).decisionIdentity()))
    }

    /**
     * 建立沒有額外選牌限制的候選。
     *
     * @param action 測試動作。
     * @return 供識別碼計算使用的候選。
     */
    private fun candidate(action: GameAction) = GameActionCandidate(
        action = action,
        token = action.toString(),
        referenceTile = null,
        tileSelectionRequirement = null,
        discardAnalyses = emptyList(),
    )

    /**
     * 使用相同對局、玩家及摸牌建立決策識別碼。
     *
     * @param actionIdentity 候選集合的識別字串。
     * @return 決策識別碼。
     */
    private fun buildKey(actionIdentity: String) = buildPlayerDecisionKey(
        gameId = gameId,
        playerId = playerId,
        phase = PlayerDecisionPhase.OWN_TURN,
        ruleDecision = null,
        reference = drawnTileId,
        actionIdentity = actionIdentity,
    )

    /** 不改變摸牌的測試擴充動作。 */
    private data object TestExtensionAction : ExtensionGameAction {
        override val id: String = "mahjongcraft:test_declaration"
    }
}
