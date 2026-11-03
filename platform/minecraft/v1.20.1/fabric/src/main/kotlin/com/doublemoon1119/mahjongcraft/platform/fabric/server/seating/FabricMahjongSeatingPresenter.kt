package com.doublemoon1119.mahjongcraft.platform.fabric.server.seating

import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.fabric.server.character.FabricAiCharacterBindings
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.AiCharacterSeatRiders
import com.doublemoon1119.mahjongcraft.platform.minecraft.seating.MahjongSeatingPresenter
import net.minecraft.entity.Entity
import net.minecraft.server.network.ServerPlayerEntity
import kotlin.uuid.Uuid

/**
 * 讓完整名冊中的在線玩家與已啟用角色共用入座流程。
 *
 * @property sessions 共用的標準席位建立入口。
 * @property serverHolder 查詢在線玩家。
 * @property characters 查詢本場有效的世界角色。
 */
internal class FabricMahjongSeatingPresenter(
    private val sessions: FabricSeatingSessionFactory,
    private val serverHolder: FabricServerHolder,
    private val characters: FabricAiCharacterBindings,
) : MahjongSeatingPresenter {
    /** 依完整席位順序解析玩家或角色，不把離線或暫時沒有角色的 AI 擠到其他席位。 */
    override fun present(tableId: Uuid, seatedPlayerIds: List<Uuid>) {
        val session = sessions.prepare(tableId, seatedPlayerIds) ?: return
        val riders = AiCharacterSeatRiders.resolve<Entity>(
            roster = seatedPlayerIds,
            includePlayers = true,
            player = serverHolder::findPlayer,
            character = { playerId -> characters.find(tableId, playerId)?.entity },
        )
        val tableRequests = sessions.prepareTable(tableId)?.requests ?: return
        SeatCollisionScope.with(riders, session.requests) {
            session.coordinator.seatAll(session.world, riders, session.requests, tableRequests)
        }.forEachIndexed { index, result ->
            val player = riders[index] as? ServerPlayerEntity ?: return@forEachIndexed
            if (result != null) session.notify(player, result)
        }
    }
}
