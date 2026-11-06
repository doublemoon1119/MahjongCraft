package com.doublemoon1119.mahjongcraft.platform.fabric.server.network

import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkJson
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.platform.fabric.network.MahjongChannels
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.ArrangedTile
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.PrivateHandOrder
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.PrivateHandOrderEntry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.PrivateHandOrderPayloadDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.PrivateHandOrderSteps
import kotlin.uuid.Uuid

/**
 * 把本人手牌排列的時間軸項目送給坐在桌上的真人玩家，每位玩家只收到自己的牌張識別碼。
 *
 * @property serverHolder 查找目前有效的收件玩家；AI 座位找不到玩家，不會送出。
 * @property json 線路序列化設定。
 */
class PrivateHandOrderSender(
    private val serverHolder: FabricServerHolder,
    private val json: NetworkJson,
) {
    /**
     * 送出一步的項目，時間點對齊這一步的牌張動作。
     *
     * @param tableId 桌子識別碼。
     * @param before 這一步開始時的桌況；之前沒有呈現過時為 null。
     * @param beforeArrangement [before] 的共享擺放；還沒有擺放時為 null。
     * @param after 這一步之後的桌況。
     * @param afterArrangement [after] 的共享擺放。
     * @param departAt 離開動作開始的遊戲時間。
     * @param makeRoomAt 讓位動作開始的遊戲時間。
     */
    fun sendStep(
        tableId: Uuid,
        before: TableState?,
        beforeArrangement: Map<Uuid, ArrangedTile>?,
        after: TableState,
        afterArrangement: Map<Uuid, ArrangedTile>,
        departAt: Long,
        makeRoomAt: Long,
    ) {
        after.players.forEach { player ->
            val recipient = serverHolder.findPlayer(player.id) ?: return@forEach
            val previous = before?.players?.firstOrNull { it.id == player.id }?.let { PrivateHandOrderSteps.orderOf(it.id, it.hand, beforeArrangement) }
            PrivateHandOrderSteps.of(
                before = previous,
                after = PrivateHandOrderSteps.orderOf(player.id, player.hand, afterArrangement),
                departAt = departAt,
                makeRoomAt = makeRoomAt,
            ).forEach { entry -> MahjongChannels.privateHandOrder.sendTo(recipient, json, entry.toPayload(tableId)) }
        }
    }

    /**
     * 送出開局發牌的項目：發牌開始時沒有本人排列，由共享呈現發牌；翻牌開始時換成發完的排列。
     *
     * @param tableId 桌子識別碼。
     * @param state 發完牌的桌況。
     * @param dealStartAt 發牌開始的遊戲時間。
     * @param flipAt 翻牌開始的遊戲時間。
     */
    fun sendDeal(
        tableId: Uuid,
        state: TableState,
        dealStartAt: Long,
        flipAt: Long,
    ) {
        state.players.forEach { player ->
            val recipient = serverHolder.findPlayer(player.id) ?: return@forEach
            listOf(
                PrivateHandOrderEntry(effectiveAt = dealStartAt, order = PrivateHandOrder(standing = emptyList(), drawnId = null, unsortedIds = emptyList())),
                PrivateHandOrderEntry(effectiveAt = flipAt, order = PrivateHandOrderSteps.orderOf(player.id, player.hand, arrangement = null)),
            ).forEach { entry -> MahjongChannels.privateHandOrder.sendTo(recipient, json, entry.toPayload(tableId)) }
        }
    }

    /**
     * 轉成線路資料。
     *
     * @param tableId 桌子識別碼。
     * @return 線路資料。
     */
    private fun PrivateHandOrderEntry.toPayload(tableId: Uuid): PrivateHandOrderPayloadDto = PrivateHandOrderPayloadDto(
        tableId = tableId.toString(),
        effectiveGameTime = effectiveAt,
        standingTileIds = order.standing.map { it?.toString() },
        drawnTileId = order.drawnId?.toString(),
        unsortedTileIds = order.unsortedIds.map(Uuid::toString),
    )
}
