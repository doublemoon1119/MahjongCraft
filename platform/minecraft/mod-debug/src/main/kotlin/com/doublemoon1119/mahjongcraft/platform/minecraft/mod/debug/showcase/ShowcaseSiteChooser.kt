package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.showcase

/**
 * 一格寬的一柱地形，挑牌桌位置時使用。
 *
 * @property ground 地面上方第一格的高度。樹葉不算地面，樹幹算。
 * @property obstruction 從 [ground] 往上第一個有碰撞的方塊高度，例如樹葉。查看的範圍內沒有時為 null。花草沒有碰撞，不算。
 * @property water 地面是水之類的液體。
 * @property blossom 這一柱最上面的方塊是櫻花樹葉。
 */
internal data class ShowcaseColumn(val ground: Int, val obstruction: Int?, val water: Boolean, val blossom: Boolean)

/**
 * 選好的牌桌位置。
 *
 * @property x 牌桌中心的 x 座標。
 * @property z 牌桌中心的 z 座標。
 * @property level 牌桌放置的高度，也就是牌桌範圍內最高的地面。
 * @property blossoms 周圍 [ShowcaseSiteChooser.BLOSSOM_RADIUS] 格內最上面是櫻花樹葉的柱數。
 * @property raised 牌桌範圍內比 [level] 低、要往上補到同高的柱。
 */
internal data class ShowcaseSite(val x: Int, val z: Int, val level: Int, val blossoms: Int, val raised: List<ShowcaseRaise>)

/**
 * 要墊高的一柱：從原本的地面補到牌桌的高度下方。
 *
 * @property x x 座標。
 * @property z z 座標。
 * @property ground 原本的地面高度。
 */
internal data class ShowcaseRaise(val x: Int, val z: Int, val ground: Int)

/** 候選位置不能用的原因，依檢查順序。 */
internal enum class ShowcaseRejection {
    /** 牌桌範圍內有水。 */
    WATER,

    /** 牌桌範圍內的地面高低差太大。 */
    UNEVEN,

    /** 牌桌上方低處有樹葉之類的方塊。 */
    TABLE_COVERED,

    /** 鏡頭附近的地面比牌桌高太多，例如山坡。 */
    HILLSIDE,

    /** 鏡頭附近、牌桌高度往上不遠處有樹葉之類的方塊。 */
    CAMERA_COVERED,

    /** 周圍的櫻花樹葉少於 [ShowcaseSiteChooser.MIN_BLOSSOMS] 柱。 */
    FEW_BLOSSOMS,
}

/**
 * 挑選牌桌位置的結果。
 *
 * @property site 最好的位置。沒有符合條件的位置時為 null。
 * @property rejections 每個原因排除的候選位置數，只列出有排除的原因。
 * @property mostBlossoms 地形符合條件的位置中，周圍櫻花樹葉最多的柱數，包括因為櫻花樹太少而排除的位置。沒有這種位置時為 0。
 */
internal data class ShowcaseSiteSurvey(val site: ShowcaseSite?, val rejections: Map<ShowcaseRejection, Int>, val mostBlossoms: Int)

/**
 * 挑選擺放牌桌的位置，不挖地形，只在需要時把低一格的地面補高。
 *
 * 一個候選位置要符合以下條件，牌桌本身不必在櫻花林裡，例如櫻花林旁的平地：
 * - 牌桌與四邊座位的範圍（中心周圍 [TABLE_RADIUS] 格）沒有水，地面高低差不超過 [MAX_STEP] 格，
 *   最高的地面往上 [TABLE_CLEARANCE] 格內沒有樹葉之類的方塊。
 * - 鏡頭附近（中心周圍 [OPEN_RADIUS] 格）的地面不比牌桌高超過 [MAX_STEP] 格，
 *   牌桌高度往上 [OPEN_CLEARANCE] 格內沒有樹葉之類的方塊。
 * - 周圍 [BLOSSOM_RADIUS] 格內至少有 [MIN_BLOSSOMS] 柱最上面是櫻花樹葉。
 *
 * 符合條件的位置中，依序比較：
 * - 周圍 [BLOSSOM_RADIUS] 格內的櫻花樹葉越多越好。
 * - 要墊高的柱越少越好。
 * - 離搜尋中心越近越好。
 */
internal object ShowcaseSiteChooser {
    /** 牌桌與座位的範圍：中心周圍多少格。 */
    const val TABLE_RADIUS = 2

    /** 地面容許的高低差，單位為方塊。 */
    const val MAX_STEP = 1

    /** 牌桌範圍內，牌桌高度往上要空出的格數。 */
    const val TABLE_CLEARANCE = 3

    /** 鏡頭附近的範圍：中心周圍多少格。 */
    const val OPEN_RADIUS = 4

    /** 鏡頭附近，牌桌高度往上要空出的格數。 */
    const val OPEN_CLEARANCE = 5

    /** 計算櫻花樹葉的範圍：中心周圍多少格，取圓形。 */
    const val BLOSSOM_RADIUS = 12

    /** 周圍最少要有多少柱最上面是櫻花樹葉，大約是兩三棵櫻花樹。 */
    const val MIN_BLOSSOMS = 40

    /** 取得地形時從地面往上查看障礙物的格數，足以判斷每個候選位置的空間。 */
    const val SCAN_HEIGHT = MAX_STEP + OPEN_CLEARANCE

    /** 候選位置需要的地形範圍：中心周圍多少格。 */
    const val REACH = BLOSSOM_RADIUS

    /**
     * 在搜尋中心周圍挑出牌桌位置，並記錄每個原因排除了多少候選位置。
     *
     * @param originX 搜尋中心的 x 座標。
     * @param originZ 搜尋中心的 z 座標。
     * @param searchRadius 候選位置離搜尋中心最多幾格。
     * @param step 候選位置的間隔，單位為方塊。
     * @param column 取得一柱地形。同一柱可能被要求多次，呼叫端可以自行快取。
     * @return 挑選的結果。
     */
    fun survey(originX: Int, originZ: Int, searchRadius: Int, step: Int, column: (x: Int, z: Int) -> ShowcaseColumn): ShowcaseSiteSurvey {
        val offsets = (-searchRadius..searchRadius step step).toList()
        val sites = mutableListOf<ShowcaseSite>()
        val rejections = sortedMapOf<ShowcaseRejection, Int>()
        var mostBlossoms = 0
        offsets.forEach { dx ->
            offsets.forEach { dz ->
                when (val evaluation = evaluate(originX + dx, originZ + dz, column)) {
                    is Evaluation.Accepted -> {
                        sites += evaluation.site
                        mostBlossoms = maxOf(mostBlossoms, evaluation.site.blossoms)
                    }
                    is Evaluation.Rejected -> {
                        rejections.merge(evaluation.reason, 1, Int::plus)
                        mostBlossoms = maxOf(mostBlossoms, evaluation.blossoms)
                    }
                }
            }
        }
        val best = sites.minWithOrNull(
            comparator = compareByDescending<ShowcaseSite> { it.blossoms }
                .thenBy { it.raised.size }
                .thenBy { (it.x - originX) * (it.x - originX) + (it.z - originZ) * (it.z - originZ) },
        )
        return ShowcaseSiteSurvey(site = best, rejections = rejections.toMap(), mostBlossoms = mostBlossoms)
    }

    /**
     * 判斷一個候選位置是否可用，可用時算出它的高度、櫻花樹葉數量與要墊高的柱。
     *
     * @param x 候選位置的 x 座標。
     * @param z 候選位置的 z 座標。
     * @param column 取得一柱地形。
     * @return 可用時為選好的位置，否則為第一個不符合的原因。
     */
    private fun evaluate(x: Int, z: Int, column: (x: Int, z: Int) -> ShowcaseColumn): Evaluation {
        val table = square(TABLE_RADIUS).map { (dx, dz) -> Triple(x + dx, z + dz, column(x + dx, z + dz)) }
        if (table.any { it.third.water }) return Evaluation.Rejected(ShowcaseRejection.WATER)
        val level = table.maxOf { it.third.ground }
        if (level - table.minOf { it.third.ground } > MAX_STEP) return Evaluation.Rejected(ShowcaseRejection.UNEVEN)
        if (table.any { !it.third.isClear(level, TABLE_CLEARANCE) }) return Evaluation.Rejected(ShowcaseRejection.TABLE_COVERED)
        val open = square(OPEN_RADIUS).map { (dx, dz) -> column(x + dx, z + dz) }
        if (open.any { it.ground > level + MAX_STEP }) return Evaluation.Rejected(ShowcaseRejection.HILLSIDE)
        if (open.any { !it.isClear(level, OPEN_CLEARANCE) }) return Evaluation.Rejected(ShowcaseRejection.CAMERA_COVERED)
        val blossoms = square(BLOSSOM_RADIUS)
            .filter { (dx, dz) -> dx * dx + dz * dz <= BLOSSOM_RADIUS * BLOSSOM_RADIUS }
            .count { (dx, dz) -> column(x + dx, z + dz).blossom }
        if (blossoms < MIN_BLOSSOMS) return Evaluation.Rejected(ShowcaseRejection.FEW_BLOSSOMS, blossoms)
        val raised = table.filter { it.third.ground < level }.map { (columnX, columnZ, terrain) -> ShowcaseRaise(columnX, columnZ, terrain.ground) }
        return Evaluation.Accepted(ShowcaseSite(x = x, z = z, level = level, blossoms = blossoms, raised = raised))
    }

    /** 從 [level] 往上 [clearance] 格內沒有障礙物。 */
    private fun ShowcaseColumn.isClear(level: Int, clearance: Int): Boolean = obstruction == null || obstruction >= level + clearance

    /** 中心周圍 [radius] 格的正方形內每一格的位移。 */
    private fun square(radius: Int): List<Pair<Int, Int>> = (-radius..radius).flatMap { dx -> (-radius..radius).map { dz -> dx to dz } }

    /** 一個候選位置的判斷結果。 */
    private sealed interface Evaluation {
        /** 可以使用。 */
        data class Accepted(val site: ShowcaseSite) : Evaluation

        /**
         * 不能使用。
         *
         * @property reason 第一個不符合的原因。
         * @property blossoms 周圍的櫻花樹葉柱數。地形不符合時沒有計算，為 0。
         */
        data class Rejected(val reason: ShowcaseRejection, val blossoms: Int = 0) : Evaluation
    }
}
