package com.doublemoon1119.mahjongcraft.example.minecraft

import com.doublemoon1119.mahjongcraft.example.ExampleMahjongExtension
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.MinecraftMahjongExtension

/** 結合共用範例與 Minecraft 呈現的入口，供各版本與 loader 的轉接層繼承。 */
open class ExampleMinecraftMahjongExtension :
    ExampleMahjongExtension(),
    MinecraftMahjongExtension
