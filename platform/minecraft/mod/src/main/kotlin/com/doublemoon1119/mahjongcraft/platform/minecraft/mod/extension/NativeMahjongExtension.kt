package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.extension

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.api.registration.AiCharacterCapabilityRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.api.registration.RoomParticipantRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.api.registration.SeatPlacementRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.api.registration.SeatProviderRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.api.registration.TablePropKindRegistrar

/**
 * 第三方 mod 登記直接使用 Minecraft 實體、方塊等原生內容的整合契約，例如需要自己的 entity 才能呈現的規則桌面物件種類。
 *
 * 發現方式與 `MinecraftMahjongExtension` 相同：
 *
 * - 同一個類別同時實作 `MahjongExtension` 與這個介面即可。
 * - 從載入器找出的 `MahjongExtension` 中，篩選出有實作這個介面的部分。
 */
interface NativeMahjongExtension {
    /**
     * 登記哪些既有實體可以坐在固定席位後自動參加房間。
     *
     * @param registry 初始化期間的唯寫登記入口。
     */
    fun registerRoomParticipants(registry: RoomParticipantRegistrar) = Unit

    /** 第三方 extension 的穩定識別字串，用於診斷註冊錯誤。 */
    val id: String

    /**
     * 登記規則桌面物件種類，說明描述裡的種類識別碼要生成哪種 entity、如何找回既有的 entity。
     *
     * 預設不登記。只使用內建種類的 extension 不必實作。
     *
     * @param registry 桌面物件種類 registry。
     */
    fun registerTablePropKinds(registry: TablePropKindRegistrar) = Unit

    /**
     * 登記此 Minecraft 版本可解析的座位 provider。
     *
     * 預設不登記。只使用內建座位 provider 的 extension 不必實作。
     *
     * @param registry 座位 provider registry。
     */
    fun registerSeatProviders(registry: SeatProviderRegistrar) = Unit

    /**
     * 登記世界角色能力。第三方不需要接觸 MahjongCraft 的依賴注入。
     *
     * @param registry 初始化期間可寫入的角色能力登記入口。
     */
    fun registerAiCharacterCapabilities(registry: AiCharacterCapabilityRegistrar) = Unit

    /**
     * 登記物品如何在 AI 的固定席位放置座位。不代替已有座位的解析。
     *
     * @param registry 初始化期間的座位放置能力登記入口。
     */
    fun registerSeatPlacements(registry: SeatPlacementRegistrar) = Unit
}
