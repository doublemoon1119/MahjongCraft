package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.vanilla

import net.minecraft.client.Minecraft

/**
 * 鏡頭看得到的地形是否都已經建好網格、可以畫出來。
 *
 * - 讀原版畫面的同一份狀態。Sodium 取代地形繪製時也接手這個判斷。
 *
 * @param client 目前客戶端。
 * @return 沒有等待建立的地形時為 true。
 */
fun hasRenderedAllTerrain(client: Minecraft): Boolean = client.levelRenderer.hasRenderedAllSections()
