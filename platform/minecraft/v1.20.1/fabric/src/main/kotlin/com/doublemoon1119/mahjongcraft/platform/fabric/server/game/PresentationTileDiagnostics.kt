package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import kotlin.uuid.Uuid

/**
 * 結算演出時各座位找不到 entity 的牌，以一行英文彙整供 log 使用，例如 `seat 1: 2 missing [a, b]; seat 3: 1 missing [c]`。
 *
 * @param missingBySeat 以座位為鍵、找不到 entity 的牌。
 * @return 彙整文字；沒有缺少任何牌時為 null。
 */
internal fun missingPresentationTilesText(missingBySeat: Map<Int, List<Uuid>>): String? = missingBySeat
    .filterValues { it.isNotEmpty() }
    .toSortedMap()
    .entries
    .takeIf { it.isNotEmpty() }
    ?.joinToString("; ") { (seat, tileIds) -> "seat $seat: ${tileIds.size} missing ${tileIds.joinToString(prefix = "[", postfix = "]")}" }
