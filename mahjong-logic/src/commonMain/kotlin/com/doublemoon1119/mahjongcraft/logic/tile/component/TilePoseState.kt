package com.doublemoon1119.mahjongcraft.logic.tile.component

import kotlin.uuid.Uuid

/**
 * 一張牌共同使用的擺放姿態，不攜帶牌面或授權名單。
 *
 * @property tileId 目前牌張的識別碼。
 * @property pose 這張牌的共同姿態，不隨讀取者改變。
 */
data class TilePoseState(
    val tileId: Uuid,
    val pose: TilePose,
)
