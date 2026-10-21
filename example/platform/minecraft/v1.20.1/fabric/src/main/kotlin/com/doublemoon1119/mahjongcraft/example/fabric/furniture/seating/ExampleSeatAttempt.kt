package com.doublemoon1119.mahjongcraft.example.fabric.furniture.seating

/** 執行一次座位建立、入座與定位流程，並集中處理失敗清理。 */
internal object ExampleSeatAttempt {
    /**
     * 嘗試完成座位流程；任何一步失敗都會清理座位，只有已入座才恢復乘坐者。
     *
     * @param S 暫時座位或既有家具的型別。
     * @param create 建立或取得座位目標。
     * @param spawn 將新建座位加入世界；既有家具可直接回傳 `true`。
     * @param mount 建立乘坐關係。
     * @param finish 更新位置、面向並確認流程完成。
     * @param restore 入座失敗且已建立乘坐關係時恢復乘坐者。
     * @param discard 清理本次建立的座位；既有家具可不做任何事。
     * @return 整個流程成功完成時為 `true`。
     */
    fun <S> run(
        create: () -> S,
        spawn: (S) -> Boolean,
        mount: (S) -> Boolean,
        finish: (S) -> Boolean,
        restore: () -> Unit,
        discard: (S) -> Unit,
    ): Boolean {
        val seat = create()
        var mounted = false
        var completed = false
        return try {
            if (!spawn(seat)) return false
            if (!mount(seat)) return false
            mounted = true
            completed = finish(seat)
            completed
        } finally {
            if (!completed) {
                try {
                    if (mounted) restore()
                } finally {
                    discard(seat)
                }
            }
        }
    }
}
