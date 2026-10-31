package com.doublemoon1119.mahjongcraft.platform.minecraft.showcase

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.WinCelebrationShowcaseRegistrar

/** 宣告式胡牌展示定義註冊中心；展示理由選不出已登記的定義時，該位贏家不播放展示；擴充只透過 [WinCelebrationShowcaseRegistrar] 登記。 */
interface WinCelebrationShowcaseRegistry : WinCelebrationShowcaseRegistrar {
    /** 由已登記內容推得的登記識別碼。 */
    override val registrationKeys: Set<String> get() = cueKeys.toSet()

    /** 目前已註冊 cue key 的唯讀快照。 */
    val cueKeys: Set<String>

    /** 凍結後禁止註冊。 */
    fun freeze()

    /** 依 cue key 取得定義。 */
    fun find(cueKey: String): WinCelebrationShowcaseDefinition?

    /**
     * 從一位贏家的展示理由中挑出要播放的定義。
     *
     * @param cueIds 規則給的展示理由，依規則順序排列。
     * @return 已登記定義中 [WinCelebrationShowcaseDefinition.priority] 最大者，相同時取 cue key 字典序較小者；
     * 都沒有登記時為 null。
     */
    fun select(cueIds: List<String>): WinCelebrationShowcaseDefinition? = cueIds.mapNotNull(::find)
        .minWithOrNull(compareByDescending<WinCelebrationShowcaseDefinition> { it.priority }.thenBy { it.cueKey })

    /**
     * 本局在胡牌後繼續時，這次胡牌的展示是否要讓仍在本局中的玩家等它播完。
     *
     * @param cueIdsByWinner 每位贏家的展示理由。
     * @return 任何一位贏家選出的定義要求等待時為 `true`；選不出定義的贏家不播放展示，不影響結果。
     */
    fun pausesContinuingRound(cueIdsByWinner: List<List<String>>): Boolean = cueIdsByWinner.any { cueIds ->
        select(cueIds)?.pausesContinuingRound == true
    }
}
