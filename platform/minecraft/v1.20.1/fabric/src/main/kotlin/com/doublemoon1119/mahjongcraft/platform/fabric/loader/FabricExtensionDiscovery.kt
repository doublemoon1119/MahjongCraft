package com.doublemoon1119.mahjongcraft.platform.fabric.loader

import com.doublemoon1119.mahjongcraft.extension.MahjongExtension
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.extension.NativeMahjongClientExtension
import net.fabricmc.loader.api.FabricLoader

/** 以 Fabric Loader 的 entrypoint 找出第三方擴充。 */
internal object FabricExtensionDiscovery {
    /** 第三方擴充在 `fabric.mod.json` 宣告的 entrypoint 名稱。 */
    const val ENTRYPOINT: String = "${MinecraftModMetadata.MOD_ID}:extension"

    /** 第三方客戶端擴充在 `fabric.mod.json` 宣告的 entrypoint 名稱，不由 dedicated server 掃描。 */
    const val CLIENT_ENTRYPOINT: String = "${MinecraftModMetadata.MOD_ID}:client_extension"

    /**
     * 找出第三方擴充；同一個類別可同時實作各層擴充介面，只需宣告這一個 entrypoint。
     *
     * @return 依 Fabric Loader 提供順序排列的擴充。
     */
    fun discover(): List<MahjongExtension> = FabricLoader.getInstance().getEntrypoints(ENTRYPOINT, MahjongExtension::class.java)

    /**
     * 找出第三方客戶端擴充。
     *
     * @return 依 Fabric Loader 提供順序排列的客戶端擴充。
     */
    fun discoverClient(): List<NativeMahjongClientExtension> = FabricLoader.getInstance().getEntrypoints(CLIENT_ENTRYPOINT, NativeMahjongClientExtension::class.java)
}
