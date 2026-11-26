package com.doublemoon1119.mahjongcraft.platform.minecraft.showcase

/** 第三方可組合的受控展示視覺層。 */
enum class ShowcaseVisualLayer {
    /** 由上方照向展示牌組的聚光。 */
    Spotlight,

    /** 抵達時短暫出現的放射三角光束。 */
    RadialBurst,

    /** 包圍徽記與牌組的光環。 */
    Halo,

    /** 展示區內持續生成的細小火花。 */
    SparkField,
}
