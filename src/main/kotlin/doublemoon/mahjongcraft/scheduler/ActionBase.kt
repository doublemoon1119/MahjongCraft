package doublemoon.mahjongcraft.scheduler

interface ActionBase {

    /**
     * 停止 [action] 用
     * */
    var stop: Boolean

    /**
     * 下次執行 [action] 的時間 (ms)
     * */
    var timeToAction: Long

    /**
     * 每次執行的動作
     * */
    val action: () -> Unit

    /**
     * 每 tick 執行的動作
     *
     * @return true 如果這個 action 完成
     * */
    fun tick(): Boolean
}