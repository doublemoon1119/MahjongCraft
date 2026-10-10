package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

/**
 * 提供給欄位 provider 的規則中立、已序列化快照。
 *
 * @property outcomeId 結算結果 ID。
 * @property isTsumo 是否為自摸。
 * @property winnerId 贏家 ID。
 * @property winnerDisplayName 贏家顯示名稱。
 * @property winnerIsAi 贏家是否為 AI。
 * @property responsiblePlayerId 負責支付者 ID。
 * @property responsiblePlayerDisplayName 負責支付者顯示名稱。
 * @property responsiblePlayerIsAi 負責支付者是否為 AI。
 * @property totalScore 總分。
 * @property tileAssetKeys 手牌 asset key。
 * @property tileAssetGroups 手牌群組 asset key。
 * @property winningTileAssetKey 和牌張 asset key。
 * @property extensionFields 第三方欄位。
 * @property initialFadeTicks 初始淡入時間。
 * @property entryStaggerTicks 條目間隔。
 * @property scoreRevealTicks 分數揭曉時間。
 * @property scoreRevealDelayTicks 分數揭曉延遲。
 */
data class WinSettlementPresentationFieldSnapshot(
    val outcomeId: String,
    val isTsumo: Boolean,
    val winnerId: String,
    val winnerDisplayName: String,
    val winnerIsAi: Boolean,
    val responsiblePlayerId: String?,
    val responsiblePlayerDisplayName: String?,
    val responsiblePlayerIsAi: Boolean?,
    val totalScore: Int,
    val tileAssetKeys: List<String>,
    val tileAssetGroups: List<List<String>>,
    val winningTileAssetKey: String?,
    val extensionFields: List<ExtensionPresentationField> = emptyList(),
    val initialFadeTicks: Int = 16,
    val entryStaggerTicks: Int = 8,
    val scoreRevealTicks: Int = 18,
    val scoreRevealDelayTicks: Int = 0,
) {
    /** 依完整 ID 取得強型別 extension 欄位；重複 ID 由建構端拒絕。 */
    fun extensionField(id: PresentationFieldId): PresentationValue? = extensionFields.firstOrNull { it.id == id }?.value

    init {
        require(extensionFields.map(ExtensionPresentationField::id).distinct().size == extensionFields.size)
    }
}
