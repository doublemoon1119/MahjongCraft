package com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating

/**
 * 本次嘗試的一個實際座位；所有呼叫均在伺服器主執行緒，不要求繼承內建家具。
 *
 * 正常失敗不得移動乘坐者、留下乘坐關係或多餘實體。只清理自己新建的資源，不踢既有乘客。
 *
 * @param W 原生世界型別。
 * @param R 原生乘坐者型別。
 */
interface SeatTarget<W, R> {
    /** 與提供者無關的實際目標及子座位識別。 */
    val identity: SeatIdentity

    /**
     * 查詢乘坐幾何，不改動世界。
     *
     * 必須回報乘坐者入座後真正所在的腳底位置與面向，不能為了符合 [SeatRequest] 的容許範圍而回報理想值；
     * 入座後乘坐者與回報位置相差超過 0.01 格時，視為提供者錯誤。
     *
     * @param world 目標世界。
     * @param rider 乘坐者。
     * @return 實際腳底位置與面向。
     */
    fun geometry(world: W, rider: R): SeatGeometry

    /**
     * 檢查占用、有效性、適用性與身體空間，不產生副作用。
     *
     * @param world 目標世界。
     * @param rider 乘坐者。
     * @return 本人為 SEATED，他人占用為 OCCUPIED；安全空位才是 AVAILABLE。
     */
    fun inspect(world: W, rider: R): SeatState

    /**
     * 重新驗證後直接入座，立即完成定位；不先傳送到未檢查的位置。
     *
     * @param world 目標世界。
     * @param rider 乘坐者。
     * @return 成功為 SEATED，其他值表示未留下變更，不可用 AVAILABLE 表示成功。
     */
    fun trySit(world: W, rider: R): SeatState
}
