package com.doublemoon1119.mahjongcraft.flow.server.game.taiwan

import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.CompletedGameActionContext
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TAIWAN_REVEAL_FLOWER_ACTION
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** 驗證台灣麻將四槓與四風連打只在各自正確的完成動作後判定。 */
class TaiwanPostActionExhaustiveDrawResolversTest {
    /** 建立四張一萬組成的暗槓。 */
    private fun closedKan(): Meld {
        val tiles = List(4) { FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1)) }
        return Meld(
            type = MeldType.ClosedKan,
            tiles = tiles,
            sourceDirection = RelativeDirection.Self,
            publiclyRevealedTileIds = emptySet(),
            faceDownTileIds = tiles.map { it.id }.toSet(),
        )
    }

    /** 建立四家各有一個槓的桌況。 */
    private fun fourKanTable(config: TaiwanRuleConfig): TableState = FakeTableStateFactory.create(
        config = config,
        players = List(4) { FakeMahjongPlayerFactory.create(hand = Hand(melds = listOf(closedKan()))) },
    )

    /** 建立四家只打出一張相同東風的桌況。 */
    private fun fourWindTable(config: TaiwanRuleConfig): TableState = FakeTableStateFactory.create(
        config = config,
        players = List(4) {
            FakeMahjongPlayerFactory.create(
                discardPile = TaiwanDiscardPile().discardTile(FakeIdentifiedTileFactory.create(Tile.Honor.East)),
            )
        },
    )

    /** 將第一位玩家的動作紀錄替換後建立完成情境。 */
    private fun context(table: TableState, actions: List<GameAction>): CompletedGameActionContext {
        val actor = table.players.first().copy(actionHistory = actions)
        val updated = table.copy(players = listOf(actor) + table.players.drop(1))
        return CompletedGameActionContext(actor.id, actions.last(), updated)
    }

    /** 建立測試用暗槓動作。 */
    private fun kanAction(): GameAction.Kan = GameAction.Kan(
        type = GameAction.KanType.CLOSED_KAN,
        tileId = Uuid.random(),
        withTiles = List(3) { Uuid.random() },
    )

    /** 四槓必須等槓後補牌及第一張捨牌完成，不能在槓或補牌時提前成立。 */
    @Test
    fun `four kans resolves only after the post-kan discard`() {
        val module = TaiwanRuleModule("test:taiwan", TaiwanRuleConfig())
        val table = fourKanTable(module.config)
        val resolver = TaiwanFourKansResolver()
        val kan = kanAction()
        val discard = GameAction.Discard(Uuid.random())

        assertEquals(
            TaiwanExhaustiveDrawReason.FourKans,
            resolver.resolve(context(table, listOf(kan, GameAction.Draw, discard)), module),
        )
        assertNull(resolver.resolve(context(table, listOf(kan)), module))
        assertNull(resolver.resolve(context(table, listOf(kan, GameAction.Draw)), module))
        assertNull(resolver.resolve(context(table, listOf(discard)), module))
    }

    /** 槓後連續補花仍在最後一張補牌後的捨牌判定四槓。 */
    @Test
    fun `four kans allows flower replacement draws before discard`() {
        val module = TaiwanRuleModule("test:taiwan", TaiwanRuleConfig())
        val table = fourKanTable(module.config)
        val discard = GameAction.Discard(Uuid.random())

        assertEquals(
            TaiwanExhaustiveDrawReason.FourKans,
            TaiwanFourKansResolver().resolve(
                context(
                    table,
                    listOf(
                        kanAction(),
                        GameAction.Draw,
                        TAIWAN_REVEAL_FLOWER_ACTION,
                        GameAction.Draw,
                        discard,
                    ),
                ),
                module,
            ),
        )
    }

    /** 四風連打只在選項開啟且第四張捨牌反應完成後成立。 */
    @Test
    fun `four winds honors its option and discard trigger`() {
        val enabled = TaiwanRuleModule("test:taiwan", TaiwanRuleConfig(fourWindsAbortiveDraw = true))
        val disabled = TaiwanRuleModule("test:taiwan", TaiwanRuleConfig())
        val discard = GameAction.Discard(Uuid.random())
        val enabledTable = fourWindTable(enabled.config)
        val disabledTable = fourWindTable(disabled.config)
        val resolver = TaiwanFourWindsResolver()

        assertEquals(
            TaiwanExhaustiveDrawReason.FourWinds,
            resolver.resolve(context(enabledTable, listOf(discard)), enabled),
        )
        assertNull(resolver.resolve(context(disabledTable, listOf(discard)), disabled))
        assertNull(resolver.resolve(context(enabledTable, listOf(kanAction())), enabled))
    }

    /** 台灣途中流局 resolver 不處理其他規則模組。 */
    @Test
    fun `resolvers ignore non-taiwan modules`() {
        val taiwanConfig = TaiwanRuleConfig(fourWindsAbortiveDraw = true)
        val riichiModule = RiichiRuleModule("test:riichi", RiichiRuleConfig())
        val discard = GameAction.Discard(Uuid.random())

        assertNull(
            TaiwanFourKansResolver().resolve(
                context(fourKanTable(taiwanConfig), listOf(kanAction(), GameAction.Draw, discard)),
                riichiModule,
            ),
        )
        assertNull(
            TaiwanFourWindsResolver().resolve(
                context(fourWindTable(taiwanConfig), listOf(discard)),
                riichiModule,
            ),
        )
    }
}
