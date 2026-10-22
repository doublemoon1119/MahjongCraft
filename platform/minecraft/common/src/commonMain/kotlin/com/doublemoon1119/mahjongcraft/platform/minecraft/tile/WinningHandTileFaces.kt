package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import kotlin.uuid.Uuid

/**
 * 和牌後展示手牌（結算面板、役滿 showcase）時，依副露資料計算需要蓋住的牌位。
 *
 * 蓋牌位置由規則提供的牌張識別碼決定，不從副露種類猜測；因此自訂副露也能保留自己的展示方式。
 *
 * @param tileIds 副露中依展示順序排列的牌張識別碼。
 * @param faceDownTileIds 規則指定要蓋住的牌張識別碼。
 * @return 蓋住的牌在組內的位置；不蓋牌時為空集合。
 */
fun winningHandFaceDownIndices(tileIds: List<Uuid>, faceDownTileIds: Set<Uuid>): Set<Int> = tileIds.mapIndexedNotNull { index, tileId -> index.takeIf { tileId in faceDownTileIds } }.toSet()
