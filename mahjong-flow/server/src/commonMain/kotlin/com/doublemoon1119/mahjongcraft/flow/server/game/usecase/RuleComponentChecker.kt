package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.logic.component.BuiltInComponentOwners
import com.doublemoon1119.mahjongcraft.logic.component.ComponentLifecycleContext
import com.doublemoon1119.mahjongcraft.logic.component.ComponentOwnerType
import com.doublemoon1119.mahjongcraft.logic.component.ComponentPatch
import com.doublemoon1119.mahjongcraft.logic.component.ComponentRegistry
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet
import com.doublemoon1119.mahjongcraft.logic.component.applyPatch
import com.doublemoon1119.mahjongcraft.logic.component.initializeCreatedRecords
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 在動作當下驗證及套用規則回傳的組件，使用與提交前相同的登記與適用規則。
 *
 * 不符合契約時以規則契約失敗中止本局，不等到寫入前才發現。
 *
 * @property componentRegistry 正式組件定義與生命週期。
 * @property ruleModuleId 對局採用的規則識別碼。
 */
internal class RuleComponentChecker(
    private val componentRegistry: ComponentRegistry,
    private val ruleModuleId: String,
) {
    /**
     * 驗證玩家目前的完整組件。
     *
     * @param tableState 玩家所在的桌況。
     * @param player 要驗證的玩家。
     * @param source 診斷訊息使用的來源名稱。
     */
    fun requirePlayer(
        tableState: TableState,
        player: MahjongPlayer,
        source: String,
    ) {
        requireValidRuleComponents(source) {
            componentRegistry.validateState(context(tableState, BuiltInComponentOwners.PLAYER, player), player.components)
        }
    }

    /**
     * 確認舊值、對象與適用規則後，套用規則回傳的玩家組件變更。
     *
     * @param tableState 玩家所在的桌況。
     * @param player 變更前的玩家。
     * @param patch 規則回傳的變更。
     * @param source 診斷訊息使用的來源名稱。
     * @return 只有組件改變的玩家。
     */
    fun applyPlayerPatch(
        tableState: TableState,
        player: MahjongPlayer,
        patch: ComponentPatch,
        source: String,
    ): MahjongPlayer = requireValidRuleComponents(source) {
        val updated = player.copy(components = componentRegistry.applyPatch(player.components, patch, BuiltInComponentOwners.PLAYER))
        componentRegistry.validateState(context(tableState, BuiltInComponentOwners.PLAYER, updated), updated.components)
        updated
    }

    /**
     * 依玩家 ID 套用規則回傳的組件變更；沒有列出的玩家不變。
     *
     * @param tableState 玩家所在的桌況。
     * @param players 變更前的玩家列表。
     * @param patches 依玩家 ID 排列的變更。
     * @param source 診斷訊息使用的來源名稱。
     * @return 順序不變的玩家列表。
     */
    fun applyPlayerPatches(
        tableState: TableState,
        players: List<MahjongPlayer>,
        patches: Map<Uuid, ComponentPatch>,
        source: String,
    ): List<MahjongPlayer> {
        requireValidRuleComponents(source) {
            require(patches.keys.all { id -> players.any { it.id == id } }) { "Component patch targets a player outside the match" }
        }
        return players.map { player -> patches[player.id]?.let { applyPlayerPatch(tableState, player, it, source) } ?: player }
    }

    /**
     * 驗證桌況目前的完整對局組件。
     *
     * @param tableState 要驗證的桌況。
     * @param source 診斷訊息使用的來源名稱。
     */
    fun requireTable(tableState: TableState, source: String) {
        requireValidRuleComponents(source) {
            componentRegistry.validateState(context(tableState, BuiltInComponentOwners.TABLE, tableState), tableState.components)
        }
    }

    /**
     * 確認舊值、對象與適用規則後，套用規則回傳的對局組件變更。
     *
     * @param tableState 變更前的桌況。
     * @param patch 規則回傳的變更。
     * @param source 診斷訊息使用的來源名稱。
     * @return 變更後的對局組件。
     */
    fun applyTablePatch(
        tableState: TableState,
        patch: ComponentPatch,
        source: String,
    ): ComponentSet = requireValidRuleComponents(source) {
        val updated = tableState.copy(components = componentRegistry.applyPatch(tableState.components, patch, BuiltInComponentOwners.TABLE))
        componentRegistry.validateState(context(updated, BuiltInComponentOwners.TABLE, updated), updated.components)
        updated.components
    }

    /**
     * 為動作新建立的捨牌紀錄與副露套用初值並驗證。
     *
     * @param before 動作前的權威桌況。
     * @param after 動作後的候選桌況。
     * @param source 診斷訊息使用的來源名稱。
     * @return 新紀錄帶有完整組件的候選桌況。
     */
    fun initializeCreatedRecords(
        before: TableState,
        after: TableState,
        source: String,
    ): TableState = requireValidRuleComponents(source) {
        after.initializeCreatedRecords(before, ruleModuleId, componentRegistry)
    }

    /**
     * 建立驗證用的情境。
     *
     * @param O 對象型別。
     * @param tableState 對象所在的桌況。
     * @param ownerType 對象定義。
     * @param owner 對象。
     * @return 不屬於換局的驗證情境。
     */
    private fun <O : Any> context(
        tableState: TableState,
        ownerType: ComponentOwnerType<O>,
        owner: O,
    ) = ComponentLifecycleContext(
        ruleModuleId = ruleModuleId,
        tableState = tableState,
        ownerType = ownerType,
        owner = owner,
        previousTableState = null,
        random = null,
    )
}
