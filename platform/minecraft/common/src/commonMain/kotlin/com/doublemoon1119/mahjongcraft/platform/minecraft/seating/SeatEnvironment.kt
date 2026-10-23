package com.doublemoon1119.mahjongcraft.platform.minecraft.seating

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatGeometry
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatRequest

/**
 * 協調元件使用的原生安全操作；不提供權威牌局寫入。
 *
 * @param W 世界型別。
 * @param R 乘坐者型別。
 * @param S 原位置與載具快照型別。
 */
interface SeatEnvironment<W, R, S> {
    /**
     * 保存乘坐者目前位置及載具。
     *
     * @param rider 乘坐者。
     * @return 本次回復使用的快照。
     */
    fun snapshot(rider: R): S

    /**
     * 判斷提供者是否未改動位置及乘坐關係。
     *
     * @param rider 乘坐者。
     * @param snapshot 原快照。
     * @return 未改動時為 true。
     */
    fun unchanged(rider: R, snapshot: S): Boolean

    /**
     * 判斷乘坐者是否已有載具。
     *
     * @param rider 乘坐者。
     * @return 已乘坐任意載具時為 true。
     */
    fun hasVehicle(rider: R): Boolean

    /**
     * 嘗試安全站到固定席位，檢查支撐、身體、實體及區塊；失敗不移動。
     *
     * @param world 目標世界。
     * @param rider 乘坐者。
     * @param request 固定席位。
     * @return 安全傳送成功時為 true。
     */
    fun stand(
        world: W,
        rider: R,
        request: SeatRequest,
    ): Boolean

    /**
     * 確認成功後位置符合實際乘坐幾何。
     *
     * @param world 目標世界。
     * @param rider 乘坐者。
     * @param geometry 提供者宣告的實際位置。
     * @return 確實位於目標時為 true。
     */
    fun positioned(
        world: W,
        rider: R,
        geometry: SeatGeometry,
    ): Boolean

    /**
     * 讓乘坐者離開本桌的席位，供局間換位讓出位置；只在已確認乘坐者坐在本桌某個席位上時呼叫。
     *
     * @param rider 乘坐者。
     */
    fun leaveSeat(rider: R)

    /**
     * 提供者違約時嘗試安全回復，不強制解除未知載具或載入區塊。
     *
     * @param rider 乘坐者。
     * @param snapshot 原快照。
     */
    fun recover(rider: R, snapshot: S)
}
