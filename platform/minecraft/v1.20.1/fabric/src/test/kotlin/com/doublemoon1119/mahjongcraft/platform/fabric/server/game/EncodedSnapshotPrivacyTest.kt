package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkJson
import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.TableStateSnapshotDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.toDto
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicyImpl
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerComponentViews
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerComponents
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileValues
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledCoreExtensionRegistries
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledRuleModules
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.rules.riichi.riichiDeclarationComponents
import com.doublemoon1119.mahjongcraft.testing.logic.rules.riichi.riichiTableComponents
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 經正式可見性政策與網路格式實際編碼後，其他人收到的資料不含私人組件或隱藏牌的牌面與外觀。 */
class EncodedSnapshotPrivacyTest {
    /** 他家的永久振聽、赤五外觀與手牌牌面都不出現在編碼後的資料；本人仍收得到自己的資料。 */
    @Test
    fun `encoded snapshot for another player omits private components and hidden faces`() {
        val registries = bundledCoreExtensionRegistries()
        val redFive = FakeIdentifiedTileFactory.create(RiichiTileValues.redFive(Tile.Suit.Dot))
        val owner = FakeMahjongPlayerFactory.create(
            hand = Hand(tiles = listOf(redFive)),
            discardPile = RiichiDiscardPile(),
            components = riichiDeclarationComponents(declarationTile = FakeIdentifiedTileFactory.create(Tile.Honor.East))
                .with(RiichiPlayerComponents.PERMANENT_FURITEN, Unit),
        )
        val observer = FakeMahjongPlayerFactory.create(discardPile = RiichiDiscardPile())
        val game = Game(
            tableState = FakeTableStateFactory.create(
                players = listOf(owner, observer),
                config = RiichiRuleConfig(),
                components = riichiTableComponents(potPoints = 1000),
            ),
            flowConfig = GameFlowConfig(),
        )
        val policy = GameVisibilityPolicyImpl(MahjongModuleRegistryImpl().apply { registerBundledRuleModules() }, componentRegistry = registries.componentRegistry)
        val json = NetworkJson(registries.networkRegistries)

        fun encodedFor(readerId: Uuid): String = json.encodeToString(TableStateSnapshotDto.serializer(), policy.snapshotFor(game, readerId).toDto(registries.networkRegistries))

        val forObserver = encodedFor(observer.id)
        val privateMarkers = listOf(
            RiichiPlayerComponentViews.PERMANENT_FURITEN.id,
            RiichiPlayerComponents.PERMANENT_FURITEN.id,
            RiichiTileValues.RED_FIVE_APPEARANCE_ID,
        )
        privateMarkers.forEach { marker -> assertFalse(marker in forObserver, marker) }
        assertTrue(RiichiPlayerComponentViews.DECLARATION.id in forObserver)

        val forOwner = encodedFor(owner.id)
        assertTrue(RiichiPlayerComponentViews.PERMANENT_FURITEN.id in forOwner)
        assertTrue(RiichiTileValues.RED_FIVE_APPEARANCE_ID in forOwner)
    }
}
