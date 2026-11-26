package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.guide.scene

import kotlin.uuid.Uuid

/**
 * 看著「如何開始」的玩家。示意場景中鏡頭這一側的座位以他的名稱與頭像顯示。
 *
 * @property id 玩家的 UUID。
 * @property name 玩家名稱。
 */
internal data class GuideViewer(val id: Uuid, val name: String)
