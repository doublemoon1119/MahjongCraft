package com.doublemoon1119.mahjongcraft.example.fabric

import com.doublemoon1119.mahjongcraft.example.minecraft.ExampleMinecraftMahjongExtension
import com.doublemoon1119.mahjongcraft.platform.fabric.extension.FabricMahjongExtension

/** 由 Fabric entrypoint 發現，將同一個範例交給核心、Minecraft 與原生擴充登記流程。 */
class ExampleFabricMahjongExtension :
    ExampleMinecraftMahjongExtension(),
    FabricMahjongExtension
