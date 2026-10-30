package com.doublemoon1119.mahjongcraft.platform.minecraft.table

/**
 * 將規則物件完整同步；任何建立、生成或出現回呼失敗時清除整批失效顯示。
 *
 * 建立回呼只準備尚未加入世界的物件。全部準備完成後才移除替換目標，全部生成成功後才播放出現效果。
 * 已相符物件保留身分；失敗時連相符舊物件也移除，避免留下不代表最新規則狀態的部分場景。
 * 回呼拋出的例外仍交給平台記錄，清理會在離開函式前執行。
 *
 * @param targets 完整且已通過幾何驗證的目標。
 * @param existing 目前由同一桌管理的物件。
 * @param targetOf 讀取物件身分、內容及位置。
 * @param create 準備新的物件，無法建立時為 null。
 * @param spawn 將準備好的物件加入平台世界。
 * @param remove 移除物件，需能重複執行。
 * @param onSpawned 全部生成成功後啟動單一物件的出現效果。
 * @return 完整同步成功時為 true。
 */
fun <T> updateTablePropScene(
    targets: List<TablePropTarget>,
    existing: List<T>,
    targetOf: (T) -> TablePropTarget?,
    create: (TablePropTarget) -> T?,
    spawn: (T) -> Boolean,
    remove: (T) -> Unit,
    onSpawned: (T) -> Unit,
): Boolean {
    val plan = planTablePropSync(targets, existing, targetOf)
    val prepared = mutableListOf<T>()
    var completed = false
    try {
        for (target in plan.missing) prepared += create(target) ?: return false
        plan.surplus.forEach(remove)
        for (prop in prepared) if (!spawn(prop)) return false
        prepared.forEach(onSpawned)
        completed = true
        return true
    } finally {
        if (!completed) {
            prepared.forEach(remove)
            existing.forEach(remove)
        }
    }
}
