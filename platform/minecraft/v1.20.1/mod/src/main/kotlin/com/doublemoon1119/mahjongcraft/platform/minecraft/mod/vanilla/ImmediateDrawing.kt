package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.vanilla

import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat

// 立即繪製的開始與結束，名稱與新版 Minecraft 相同。

/**
 * 開始一段立即繪製，之後寫入的頂點在結束時一次畫出。
 *
 * @param mode 圖形的組成方式，例如四邊形。
 * @param format 每個頂點包含的資料。
 * @return 接收頂點的 builder。
 */
fun Tesselator.begin(mode: VertexFormat.Mode, format: VertexFormat): BufferBuilder = builder.apply { begin(mode, format) }

/**
 * 結束立即繪製，取得可以交給繪製方法的頂點資料。
 *
 * @return 這段寫入的頂點資料。
 */
fun BufferBuilder.buildOrThrow(): BufferBuilder.RenderedBuffer = end()
