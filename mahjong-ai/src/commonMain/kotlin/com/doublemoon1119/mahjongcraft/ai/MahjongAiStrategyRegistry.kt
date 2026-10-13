package com.doublemoon1119.mahjongcraft.ai

/**
 * AI 策略登記中心介面，管理策略 key 與其建構方式的對照關係。
 *
 * 設計刻意比照 `MahjongModuleRegistry`：開放註冊，
 * 內建策略與第三方策略使用相同的登記流程。策略所需的網址、提示內容或憑證等設定，
 * 由建構函式的閉包持有，不放入對局狀態或快照。
 */
interface MahjongAiStrategyRegistry {
    /**
     * 登記策略描述；每次解析時呼叫其中的建立函式，讓各策略實例獨立保存自己的狀態。
     *
     * 凍結後或識別碼重複時拒絕登記，不覆寫先前擴充提供的策略。
     *
     * @param definition 策略 key 與每次解析時建立策略的延遲工廠。
     * @throws IllegalStateException 登記中心已凍結。
     * @throws IllegalArgumentException [definition] 的 key 已被使用或不是 namespaced ID。
     */
    fun register(definition: MahjongAiStrategyDefinition)

    /** 禁止後續註冊；由啟動流程在所有內建與第三方登記完成後呼叫。 */
    fun freeze()

    /**
     * 解析指定策略；[key] 為 null 或未登記時改用預設策略。
     *
     * @param key 要求的策略識別碼；null 表示使用預設策略。
     * @return 對應建構函式產生的策略。
     * @throws NoSuchElementException 要求的策略不存在，且預設策略也尚未登記。
     */
    fun resolve(key: String?): MahjongAiStrategy

    /** 目前已註冊的策略 key 集合，依註冊順序排列。 */
    fun getAllStrategyKeys(): Set<String>
}
