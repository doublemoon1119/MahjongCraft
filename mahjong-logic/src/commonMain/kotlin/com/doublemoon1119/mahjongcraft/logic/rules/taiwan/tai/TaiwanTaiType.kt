package com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tai

/**
 * 台灣麻將的計台項目；莊家與連莊由付款另計。
 *
 * 只表示項目本身，台數由各項目的判定在成立時決定。成立結果依宣告順序排列。
 */
enum class TaiwanTaiType {
    /** 門清。 */
    ConcealedHand,

    /** 自摸。 */
    SelfDraw,

    /** 門清自摸額外的一台，也稱不求人。 */
    FullyConcealedHand,

    /** 門風台。 */
    SeatWind,

    /** 圈風台。 */
    RoundWind,

    /** 三元台。 */
    Dragon,

    /** 正花。 */
    SeatFlower,

    /** 花槓。 */
    FlowerSet,

    /** 獨聽，包含符合條件的單吊、中洞及邊張。 */
    SingleWait,

    /** 搶槓。 */
    RobbingKong,

    /** 槓上開花。 */
    ReplacementWin,

    /** 海底撈月。 */
    LastDraw,

    /** 平胡。 */
    AllSequences,

    /** 三暗刻。 */
    ThreeConcealedTriplets,

    /** 四暗刻。 */
    FourConcealedTriplets,

    /** 五暗刻。 */
    FiveConcealedTriplets,

    /** 全求人。 */
    AllExposed,

    /** 碰碰胡。 */
    AllTriplets,

    /** 小三元。 */
    LittleDragons,

    /** 大三元。 */
    BigDragons,

    /** 混一色，也稱湊一色。 */
    HalfFlush,

    /** 清一色。 */
    FullFlush,

    /** 小四喜。 */
    LittleWinds,

    /** 大四喜。 */
    BigWinds,

    /** 字一色。 */
    AllHonors,

    /** 宣告聽牌後胡牌。 */
    DeclaredReady,

    /** 莊家第一打後已聽牌的資格。 */
    HeavenlyReady,

    /** 地聽資格。 */
    EarthlyReady,

    /** 人胡。 */
    HumanWin,

    /** 地胡。 */
    EarthlyWin,

    /** 天胡。 */
    HeavenlyWin,

    /** 八仙過海的獨立花胡。 */
    EightFlowers,

    /** 七搶一的獨立花胡。 */
    SevenFlowers,
}
