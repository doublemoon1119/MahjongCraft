package com.doublemoon1119.mahjongcraft.logic.component

import com.doublemoon1119.mahjongcraft.logic.table.TableState

/**
 * 為新建立的捨牌紀錄或副露套用適用的組件初值，並驗證完整結果。
 *
 * 建立者已寫入的值優先於初值；未登記、對象不符、不適用目前規則或缺少必要值的組件會被拒絕。
 *
 * @param O 紀錄型別。
 * @param context 新紀錄與其所在的候選桌況。
 * @param written 建立者已寫入的組件。
 * @return 經驗證的完整組件。
 */
fun <O : Any> ComponentRegistry.initializeRecordComponents(context: ComponentLifecycleContext<O>, written: ComponentSet): ComponentSet {
    val initial = written.types.fold(initialize(context)) { values, type -> values.without(type) }
    val components = initial.merge(written)
    validateState(context, components)
    return components
}

/**
 * 為動作新建立的捨牌紀錄與副露套用組件初值，並驗證建立者寫入的組件。
 *
 * 新捨牌是玩家牌河比 [previous] 多出的紀錄；新副露是同一玩家在 [previous] 中沒有的副露 ID。
 * 加槓沿用原副露 ID，保留原有組件，不再建立初值。
 *
 * @param previous 動作前的權威桌況。
 * @param ruleModuleId 對局採用的規則識別碼。
 * @param registry 本次執行完成登記的組件定義。
 * @return 新紀錄帶有完整組件的桌況。
 */
fun TableState.initializeCreatedRecords(
    previous: TableState,
    ruleModuleId: String,
    registry: ComponentRegistry,
): TableState {
    val previousPlayers = previous.players.associateBy { it.id }
    fun <O : Any> context(ownerType: ComponentOwnerType<O>, owner: O) = ComponentLifecycleContext(
        ruleModuleId = ruleModuleId,
        tableState = this,
        ownerType = ownerType,
        owner = owner,
        previousTableState = null,
    )
    return copy(
        players = players.map { player ->
            val before = requireNotNull(previousPlayers[player.id]) { "An action cannot introduce an unknown player" }
            val previousCount = before.discardPile.entries.size
            require(player.discardPile.entries.size >= previousCount) { "An action cannot remove discard records" }
            val discardPile = (previousCount until player.discardPile.entries.size).fold(player.discardPile) { pile, index ->
                val entry = pile.entries[index]
                val components = registry.initializeRecordComponents(context(BuiltInComponentOwners.DISCARD_ENTRY, entry), entry.components)
                if (components == entry.components) pile else pile.replaceEntryComponents(index, components)
            }
            val previousMeldIds = before.hand.melds.mapTo(mutableSetOf()) { it.id }
            val melds = player.hand.melds.map { meld ->
                if (meld.id in previousMeldIds) {
                    meld
                } else {
                    val components = registry.initializeRecordComponents(context(BuiltInComponentOwners.MELD, meld), meld.components)
                    if (components == meld.components) meld else meld.copy(components = components)
                }
            }
            if (discardPile === player.discardPile && melds == player.hand.melds) {
                player
            } else {
                player.copy(discardPile = discardPile, hand = player.hand.copy(melds = melds))
            }
        },
    )
}
