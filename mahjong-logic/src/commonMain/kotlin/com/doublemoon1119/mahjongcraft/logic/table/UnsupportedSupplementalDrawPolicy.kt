package com.doublemoon1119.mahjongcraft.logic.table

/** 未提供補牌能力的規則所使用的安全預設 policy。 */
object UnsupportedSupplementalDrawPolicy : SupplementalDrawPolicy {
    /** 所有送入此 policy 的動作都明確拒絕，不套用其他玩法的補牌規則。 */
    override fun resolve(context: SupplementalDrawContext): SupplementalDrawDecision = SupplementalDrawDecision.Rejected(UNSUPPORTED_REASON_ID)

    /** 規則未提供補牌能力的完整原因 ID。 */
    const val UNSUPPORTED_REASON_ID = "mahjongcraft:unsupported_supplemental_draw"
}
