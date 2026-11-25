package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.Camera
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.ShaderInstance
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.ItemStack

/**
 * 載入器提供的用戶端事件。
 *
 * 只在用戶端使用；除非另有說明，監聽器都在用戶端主執行緒上呼叫。各載入器各自實作，把自己的事件轉成這裡的呼叫。
 */
interface ClientEvents {
    /**
     * 登記一個用戶端指令；載入器建立用戶端指令表時加入。
     *
     * @param command 要登記的指令。
     */
    fun onRegisterClientCommand(command: ClientCommand)

    /**
     * 每個用戶端 tick 結束時。
     *
     * @param listener 收到用戶端。
     */
    fun onClientTickEnd(listener: (Minecraft) -> Unit)

    /**
     * 用戶端連上伺服器、可以收送封包之後。
     *
     * @param listener 連線時的處理。
     */
    fun onClientJoin(listener: () -> Unit)

    /**
     * 用戶端與伺服器的連線結束時。
     *
     * @param listener 斷線時的處理。
     */
    fun onClientDisconnect(listener: () -> Unit)

    /**
     * 實體從用戶端的世界卸載時。
     *
     * @param listener 收到實體與所在世界。
     */
    fun onClientEntityUnload(listener: (Entity, ClientLevel) -> Unit)

    /**
     * 用戶端玩家對實體按下使用鍵時；回傳 [InteractionResult.PASS] 交給其他處理，其他結果代表已處理並結束這次互動。
     *
     * @param listener 收到玩家、世界、使用的手與被點擊的實體，回傳處理結果。
     */
    fun onClientUseEntity(listener: (LocalPlayer, ClientLevel, InteractionHand, Entity) -> InteractionResult)

    /**
     * 用戶端玩家對空氣使用手上的物品時；回傳 pass 交給其他處理，其他結果代表已處理並結束這次互動。
     *
     * @param listener 收到玩家、世界與使用的手，回傳處理結果與使用後的物品。
     */
    fun onClientUseItem(listener: (LocalPlayer, ClientLevel, InteractionHand) -> InteractionResultHolder<ItemStack>)

    /**
     * 每一幀開始繪製世界時。
     *
     * @param listener 繪製開始時的處理。
     */
    fun onWorldRenderStart(listener: () -> Unit)

    /**
     * 每一幀整個世界畫完、手上物品與介面還沒畫時；光影模組的陰影與後製也已完成，之後畫的東西不會再被加上光影。
     *
     * 原版沒有這個時間點的事件，由共用的 mixin 經 [WorldRenderFinishedHook] 提供。
     *
     * @param listener 畫完時的處理。
     */
    fun onWorldRenderFinished(listener: () -> Unit)

    /**
     * 每一幀遊戲畫面畫聊天欄之前，可以把 HUD 畫在聊天欄下面；介面隱藏時是否呼叫由載入器決定，處理本身仍須自行檢查。
     *
     * 原版沒有這個時間點的事件；以原版 `Gui.render` 繪製遊戲畫面的載入器經 [HudBeforeChatHook] 提供，其他載入器以自己的
     * 畫面分層提供同一個時間點。
     *
     * @param listener 收到本幀繪製環境的處理。
     */
    fun onHudRenderBeforeChat(listener: (GuiGraphics) -> Unit)

    /**
     * 每一幀繪製除錯用線條之前，可以把線框畫進世界。
     *
     * @param listener 收到目前的座標轉換、繪製緩衝與攝影機。
     */
    fun onWorldRenderBeforeDebug(listener: (PoseStack, MultiBufferSource, Camera) -> Unit)

    /**
     * 用戶端資源重新載入時；在資源載入流程中呼叫，不一定在用戶端主執行緒上。
     *
     * @param id 這個處理的識別碼。
     * @param listener 收到重新載入後的資源管理器。
     */
    fun onClientResourceReload(id: ResourceLocation, listener: (ResourceManager) -> Unit)

    /**
     * 登記一個自訂核心著色器；每次用戶端資源重新載入、原版重新建立著色器時都會重新建立並交給 [onLoaded]。
     *
     * 著色器設定檔位於 `assets/<命名空間>/shaders/core/<路徑>.json`，其中的頂點與片段程式可以用帶命名空間的名稱指定。
     *
     * @param id 著色器的識別碼。
     * @param format 著色器接收的頂點格式。
     * @param onLoaded 收到建立好的著色器。
     */
    fun onRegisterCoreShader(id: ResourceLocation, format: VertexFormat, onLoaded: (ShaderInstance) -> Unit)

    /**
     * 登記一個按鍵綁定，讓它出現在按鍵設定中並由用戶端更新狀態。
     *
     * @param mapping 要登記的按鍵綁定。
     * @return 登記後的按鍵綁定。
     */
    fun registerKeyMapping(mapping: KeyMapping): KeyMapping
}
