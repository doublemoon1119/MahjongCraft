package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.judgment.LegalActionContext
import com.doublemoon1119.mahjongcraft.logic.judgment.LegalActionValidator
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleDefinition
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.PendingReaction
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeDiscardPile
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** [AutomaticGameCommandResolver] 的安全代操作測試。 */
class AutomaticGameCommandResolverTest {
    /** 驗證規則指定的立牌不是摸入牌時，解析器仍會遵守指定牌。 */
    @Test
    fun `forced discard selects required standing tile`() {
        val required = FakeIdentifiedTileFactory.create(Tile.Honor.Red)
        val other = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val playerId = Uuid.random()
        val state = ownTurnState(
            FakeMahjongPlayerFactory.create(
                id = playerId,
                hand = Hand(tiles = listOf(required, other)),
            ).recordAction(GameAction.Pon(Uuid.random(), listOf(Uuid.random(), Uuid.random()))),
        )

        assertEquals(
            GameCommand.Discard(required.id),
            resolver(required.id).resolve(state, playerId),
        )
    }

    /** 驗證規則指定不存在於手牌的牌時，解析器不會改打其他牌。 */
    @Test
    fun `missing forced discard returns no command`() {
        val playerId = Uuid.random()
        val state = ownTurnState(
            FakeMahjongPlayerFactory.create(
                id = playerId,
                hand = Hand(tiles = listOf(FakeIdentifiedTileFactory.create(Tile.Honor.Red))),
            ).recordAction(GameAction.Pon(Uuid.random(), listOf(Uuid.random(), Uuid.random()))),
        )

        assertNull(resolver(Uuid.random()).resolve(state, playerId))
    }

    /** 驗證不在反應視窗資格內的玩家不會被代送 Pass。 */
    @Test
    fun `ineligible player does not receive pass`() {
        val eligible = Uuid.random()
        val bystander = Uuid.random()
        val discardedTile = FakeIdentifiedTileFactory.create(Tile.Honor.Red)
        val discarder = FakeMahjongPlayerFactory.create(
            discardPile = FakeDiscardPile().discardTile(discardedTile),
        )
        val state = FakeTableStateFactory.create(
            config = RiichiRuleConfig(),
            players = listOf(
                discarder,
                FakeMahjongPlayerFactory.create(id = eligible),
                FakeMahjongPlayerFactory.create(id = bystander),
            ),
            pendingReaction = PendingReaction(
                discarderId = discarder.id,
                tileId = discardedTile.id,
                eligiblePlayerIds = setOf(eligible),
                ronPlayerIds = setOf(eligible),
                id = Uuid.random(),
            ),
        )

        assertNull(resolver(null).resolve(state, bystander))
    }

    /** 驗證玩家雖在反應資格內，但規則不允許 Pass 時不會產生代操作命令。 */
    @Test
    fun `eligible player does not receive pass when validator rejects pass`() {
        val eligible = Uuid.random()
        val discardedTile = FakeIdentifiedTileFactory.create(Tile.Honor.Red)
        val discarder = FakeMahjongPlayerFactory.create(
            discardPile = FakeDiscardPile().discardTile(discardedTile),
        )
        val state = FakeTableStateFactory.create(
            config = RiichiRuleConfig(),
            players = listOf(discarder, FakeMahjongPlayerFactory.create(id = eligible)),
            pendingReaction = PendingReaction(
                discarderId = discarder.id,
                tileId = discardedTile.id,
                eligiblePlayerIds = setOf(eligible),
                ronPlayerIds = setOf(eligible),
                id = Uuid.random(),
            ),
        )

        assertNull(resolver(forcedTileId = null, rejectPass = true).resolve(state, eligible))
    }

    /** 建立剛鳴牌後、需要直接捨牌的測試桌況。 */
    private fun ownTurnState(player: MahjongPlayer): TableState = FakeTableStateFactory.create(
        config = RiichiRuleConfig(),
        players = listOf(player, FakeMahjongPlayerFactory.create()),
        currentPlayerIndex = 0,
    )

    /** 建立可覆寫強制捨牌與反應跳過資格的測試解析器。 */
    private fun resolver(
        forcedTileId: Uuid?,
        rejectPass: Boolean = false,
    ): AutomaticGameCommandResolver {
        val registry = MahjongModuleRegistryImpl().apply {
            register(
                MahjongModuleDefinition(RiichiRuleConfig::class, "test:forced_discard") { config, id ->
                    ForcedDiscardRuleModule(RiichiRuleModule(id, config), forcedTileId, rejectPass)
                },
            )
        }
        return AutomaticGameCommandResolver(registry)
    }

    /**
     * 只替測試覆寫強制捨牌牌張與 Pass 合法性的日麻規則包裝器。
     *
     * @property delegate 正式日麻規則模組，提供其餘規則行為。
     * @property forcedTileId 測試要求規則優先捨出的牌張識別碼。
     * @property rejectPass 是否讓測試 validator 移除 Pass 動作。
     */
    private class ForcedDiscardRuleModule(
        private val delegate: RiichiRuleModule,
        private val forcedTileId: Uuid?,
        private val rejectPass: Boolean,
    ) : MahjongRuleModule<RiichiRuleConfig> by delegate {
        /** 回傳測試指定的強制捨牌限制。 */
        override fun forcedDiscardTileId(tableState: TableState, player: MahjongPlayer): Uuid? = forcedTileId

        /** 保留原判定，並依測試情境移除跳過選項。 */
        override fun createLegalActionValidator(): LegalActionValidator {
            val validator = delegate.createLegalActionValidator()
            return object : LegalActionValidator by validator {
                /** 產生當下合法動作，再套用本測試的跳過限制。 */
                override fun getLegalActions(context: LegalActionContext): List<GameAction> = validator.getLegalActions(context).filterNot { rejectPass && it == GameAction.Pass }
            }
        }
    }
}
