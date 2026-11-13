package com.doublemoon1119.mahjongcraft.platform.fabric.client.tile

import kotlin.uuid.Uuid

/**
 * 記錄每張桌子的捨牌是否已經落到牌河。
 *
 * 牌局一記下打出的牌，桌況就把它列在牌河，但牌要等排定的動畫時間到了才從手牌飛出去；在那之前它還停在原位。
 * 剛出現在牌河紀錄的牌先記為還沒落定，等到它的位置改變、而且不在移動中，或看過它移動之後停下來，才算落定。
 * 第一次看到一張桌子時，已經在牌河的牌都當作已落定（例如剛進入或重新連線）。
 *
 * @param P 牌的位置型別，只用來比較是否相同。
 */
internal class DiscardSettlementState<P : Any> {
    /** 每張桌子目前在牌河紀錄中的牌，與還沒落定的牌。 */
    private val tables = mutableMapOf<Uuid, Table<P>>()

    /**
     * 以一張桌子目前的牌河紀錄與牌的實際狀態更新。
     *
     * @param tableId 桌子。
     * @param discardTileIds 目前在各家牌河紀錄中的牌，包含被鳴走的。
     * @param positionOf 牌目前的位置；牌不在目前世界中時為 null。
     * @param isMoving 牌是否正在播放移動動畫。
     */
    fun update(
        tableId: Uuid,
        discardTileIds: Set<Uuid>,
        positionOf: (Uuid) -> P?,
        isMoving: (Uuid) -> Boolean,
    ) {
        val table = tables[tableId]
        if (table == null) {
            tables[tableId] = Table(known = discardTileIds, pending = mutableMapOf())
            return
        }
        (discardTileIds - table.known).forEach { tileId -> table.pending[tileId] = Pending() }
        table.pending.keys.retainAll(discardTileIds)
        table.known = discardTileIds
        table.pending.entries.removeIf { (tileId, pending) ->
            val position = positionOf(tileId) ?: return@removeIf false
            val moving = isMoving(tileId)
            if (moving) pending.sawMotion = true
            if (pending.startPosition == null) pending.startPosition = position
            !moving && (pending.sawMotion || position != pending.startPosition)
        }
    }

    /**
     * 判斷一張牌是否已經落到牌河。
     *
     * @param tableId 桌子。
     * @param tileId 牌。
     * @return 上次更新時這張牌已在牌河紀錄中、而且已經落定時為 true；桌況在兩次更新之間才列出的牌還沒判斷過，不算落定。
     */
    fun isSettled(tableId: Uuid, tileId: Uuid): Boolean = tables[tableId]?.let { tileId in it.known && tileId !in it.pending } == true

    /**
     * 只保留仍有對局的桌子。
     *
     * @param tableIds 仍有對局的桌子。
     */
    fun retainTables(tableIds: Set<Uuid>) {
        tables.keys.retainAll(tableIds)
    }

    /**
     * 一張桌子的追蹤資料。
     *
     * @property known 上次更新時在牌河紀錄中的牌。
     * @property pending 還沒落定的牌。
     */
    private class Table<P : Any>(
        var known: Set<Uuid>,
        val pending: MutableMap<Uuid, Pending<P>>,
    )

    /**
     * 一張還沒落定的牌。
     *
     * @property startPosition 第一次看到它時的位置。
     * @property sawMotion 是否看過它正在移動。
     */
    private class Pending<P : Any>(
        var startPosition: P? = null,
        var sawMotion: Boolean = false,
    )
}
