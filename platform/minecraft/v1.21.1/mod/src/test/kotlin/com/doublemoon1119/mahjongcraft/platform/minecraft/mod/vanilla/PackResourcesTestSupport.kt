package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.vanilla

import net.minecraft.network.chat.Component
import net.minecraft.server.packs.PackLocationInfo
import net.minecraft.server.packs.PackResources
import net.minecraft.server.packs.PathPackResources
import net.minecraft.server.packs.repository.PackSource
import java.nio.file.Path
import java.util.Optional

/**
 * 建立讀取指定資料夾的內建資源包，供需要資源來源的測試使用。
 *
 * @param id 資源包 ID。
 * @param root 資源包的根資料夾。
 * @return 資源包。
 */
fun testPackResources(id: String, root: Path): PackResources = PathPackResources(
    PackLocationInfo(id, Component.literal(id), PackSource.BUILT_IN, Optional.empty()),
    root,
)
