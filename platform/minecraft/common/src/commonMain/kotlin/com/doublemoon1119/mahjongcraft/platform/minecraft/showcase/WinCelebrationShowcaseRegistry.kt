package com.doublemoon1119.mahjongcraft.platform.minecraft.showcase

/** 宣告式胡牌展示定義註冊中心；展示理由選不出已登記的定義時，該位贏家不播放展示。 */
interface WinCelebrationShowcaseRegistry {
    /** 目前已登記 cue key 的快照。 */
    val registrationKeys: Set<String> get() = cueKeys.toSet()

    /** registry 是否已凍結。 */
    val isFrozen: Boolean

    /** 目前已註冊 cue key 的唯讀快照。 */
    val cueKeys: Set<String>

    /** 登記一個 cue；重複 key 會失敗。 */
    fun register(definition: WinCelebrationShowcaseDefinition)

    /** 凍結後禁止註冊。 */
    fun freeze()

    /** 依 cue key 取得定義。 */
    fun find(cueKey: String): WinCelebrationShowcaseDefinition?

    /**
     * 從一位贏家的展示理由中挑出要播放的定義。
     *
     * @param cueIds 規則給的展示理由，依規則順序排列。
     * @return 已登記定義中 [WinCelebrationShowcaseDefinition.priority] 最大者，相同時取 [cueIds] 中較前者；
     * 都沒有登記時為 null。
     */
    fun select(cueIds: List<String>): WinCelebrationShowcaseDefinition? = cueIds.withIndex()
        .mapNotNull { (index, id) -> find(id)?.let { index to it } }
        .maxWithOrNull(compareBy<Pair<Int, WinCelebrationShowcaseDefinition>> { it.second.priority }.thenByDescending { it.first })
        ?.second

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

/** [WinCelebrationShowcaseRegistry] 的記憶體實作。 */
class WinCelebrationShowcaseRegistryImpl : WinCelebrationShowcaseRegistry {
    private val definitions = mutableMapOf<String, WinCelebrationShowcaseDefinition>()
    override var isFrozen: Boolean = false
        private set
    override val cueKeys: Set<String> get() = definitions.keys.toSet()

    override fun register(definition: WinCelebrationShowcaseDefinition) {
        check(!isFrozen) { "Win celebration showcase registry is frozen" }
        require(definitions.putIfAbsent(definition.cueKey, definition) == null) {
            "Duplicate win celebration showcase cue: ${definition.cueKey}"
        }
    }

    override fun freeze() {
        isFrozen = true
    }

    override fun find(cueKey: String): WinCelebrationShowcaseDefinition? = definitions[cueKey]
}
