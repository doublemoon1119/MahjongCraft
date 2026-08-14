package com.doublemoon1119.mahjongcraft.platform.fabric.server.event

import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationPublisher
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import com.doublemoon1119.mahjongcraft.logic.table.opening.DiceRollResult
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/**
 * [GamePresentationPublisher] 的 Fabric 實作。
 *
 * TODO: 兩個方法皆為 no-op，尚未接上真正的擲骰／牌牆呈現邏輯。
 */
@Single(binds = [GamePresentationPublisher::class])
class FabricGamePresentationPublisher : GamePresentationPublisher {
    override fun publishDiceRoll(gameId: Uuid, dice: DiceRollResult) = Unit

    override fun publishWallStructure(gameId: Uuid, structure: Map<Uuid, TileWallPosition>) = Unit
}
