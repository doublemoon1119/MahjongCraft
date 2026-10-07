package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.base.MeldType

/**
 * 和牌後展示手牌（結算面板、役滿 showcase）時，一組副露中蓋住的牌位。
 *
 * 暗槓一律蓋住兩端、翻開中間兩張，與明槓、加槓區分；和牌後手牌已經攤開，因此不論規則平時是否公開暗槓都相同。
 *
 * @param type 副露種類。
 * @param tileCount 這組副露的張數。
 * @return 蓋住的牌在組內的位置；不蓋牌時為空集合。
 */
fun winningHandFaceDownIndices(type: MeldType, tileCount: Int): Set<Int> = if (type == MeldType.CLOSED_KAN && tileCount >= 2) setOf(0, tileCount - 1) else emptySet()
