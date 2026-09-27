package com.doublemoon1119.mahjongcraft.platform.fabric.item

import com.doublemoon1119.mahjongcraft.platform.minecraft.stick.MahjongScoringStickDenomination

/** 將點棒面額映射到 item model predicate 的 `[0, 1]` 範圍。 */
internal fun MahjongScoringStickDenomination.toModelPredicateValue(): Float = ordinal.toFloat() / (MahjongScoringStickDenomination.entries.size - 1)
