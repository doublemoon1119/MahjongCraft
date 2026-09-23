package com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlSnapshotDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.automatic.BuiltInMinecraftAutomaticControlIds
import net.minecraft.text.Text

/** 一列只反映已確認設定或權威快照的自動操作狀態。 */
internal data class AutomaticControlStatusRow(
    val controlId: String,
    val label: Text,
    val enabled: Boolean,
)

/** 合併本機永久偏好與本人本局權威狀態；任何生命週期不符都不顯示。 */
internal fun automaticControlStatusRows(
    activeGameId: String?,
    snapshot: AutomaticControlSnapshotDto?,
    autoSortHandEnabled: Boolean,
    displays: AutomaticControlDisplayResolver,
): List<AutomaticControlStatusRow> {
    if (snapshot == null || activeGameId != snapshot.gameId) return emptyList()
    val sortId = BuiltInMinecraftAutomaticControlIds.AUTO_SORT_HAND
    val roundControlIds = snapshot.supportedControlIds - sortId
    if (roundControlIds.isEmpty()) return emptyList()
    return listOf(AutomaticControlStatusRow(sortId, displays.resolve(sortId).label, autoSortHandEnabled)) +
        displays.resolveAll(roundControlIds).map { display ->
            AutomaticControlStatusRow(
                controlId = display.controlId,
                label = display.label,
                enabled = display.controlId in snapshot.enabledControlIds,
            )
        }
}
