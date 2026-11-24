@file:Suppress("EXTENSION_SHADOWED_BY_MEMBER")

package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.vanilla

import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.MeshData
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat

// 這個版本的原版已有同名方法，呼叫時一律使用原版方法；這裡的同名宣告只讓共用程式碼的 import 能夠解析。

/** 開始一段立即繪製，之後寫入的頂點在結束時一次畫出。 */
fun Tesselator.begin(mode: VertexFormat.Mode, format: VertexFormat): BufferBuilder = begin(mode, format)

/** 結束立即繪製，取得可以交給繪製方法的頂點資料。 */
fun BufferBuilder.buildOrThrow(): MeshData = buildOrThrow()
