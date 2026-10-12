package com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating

/**
 * 固定席位來源；解析時不移動乘坐者、不生成座位、不強制載入區塊。
 *
 * @param W 原生世界型別。
 * @param R 原生乘坐者型別。
 */
fun interface SeatProvider<W, R> {
    /**
     * 在伺服器主執行緒取得本次候選，不跨嘗試保存世界參照。
     *
     * @param world 已載入的世界。
     * @param request 固定席位請求。
     * @return 候選座位，無目標時為空清單。
     */
    fun resolve(world: W, request: SeatRequest): List<SeatTarget<W, R>>
}
