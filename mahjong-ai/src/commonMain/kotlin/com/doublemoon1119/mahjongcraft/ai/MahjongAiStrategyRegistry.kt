package com.doublemoon1119.mahjongcraft.ai

/**
 * AI 策略登記中心介面，管理策略 key 與其建構方式的對照關係。
 *
 * 設計刻意比照 `com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry`：開放註冊，
 * 內建策略與第三方策略走同一條路，登記表本身不具備任何特權判斷。策略若需要額外設定（例如未來
 * 某個自帶 url/prompt/api key 的策略），由該策略自己在註冊時透過閉包捕捉，不會經過這個介面的
 * 任何參數——避免這類設定被夾帶進 `Room`/`MahjongPlayer` 進而外洩到快照裡。
 */
interface MahjongAiStrategyRegistry {
    /** 註冊一個策略；[factory] 每次解析時呼叫一次，讓策略可以是 stateful 的獨立實例。 */
    fun register(key: String, factory: () -> MahjongAiStrategy)

    /** 解析 [key] 對應的策略；找不到對應 key（含 null、未知字串）時優雅退回預設策略。 */
    fun resolve(key: String?): MahjongAiStrategy

    /** 目前已註冊的策略 key 集合。 */
    fun getAllStrategyKeys(): Set<String>
}
