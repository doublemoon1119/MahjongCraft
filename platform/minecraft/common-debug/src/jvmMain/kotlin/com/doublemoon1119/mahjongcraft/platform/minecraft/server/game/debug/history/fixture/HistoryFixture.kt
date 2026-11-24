package com.doublemoon1119.mahjongcraft.platform.minecraft.server.game.debug.history.fixture

import com.doublemoon1119.mahjongcraft.flow.common.game.random.MatchSeed
import com.doublemoon1119.mahjongcraft.flow.server.game.history.generation.HeadlessHistoryScenario
import java.security.MessageDigest

/**
 * 一份固定內容的測試牌譜：前面幾局照腳本進行，之後各局以固定種子發牌，四家都打出剛摸到的牌、遇到回應一律放過，直到整場依規則結束。
 *
 * 流局時莊家連莊的規則（例如台灣麻將）這樣打不完整場，這種測試牌譜的每一局都要照腳本進行。同一份測試牌譜每次產生的內容都相同。
 *
 * @property id 測試牌譜的識別碼。
 * @property scenario 規則與場長。
 * @property rounds 依序照腳本進行的局。
 */
data class HistoryFixture(
    val id: String,
    val scenario: HeadlessHistoryScenario,
    val rounds: List<HistoryFixtureRound>,
) {
    init {
        require(rounds.isNotEmpty()) { "A history fixture scripts at least one round" }
    }

    /** 由識別碼衍生的固定種子，決定座位與照腳本之後各局的牌山。 */
    val matchSeed: MatchSeed
        get() = MatchSeed.fromByteArray(MessageDigest.getInstance("SHA-256").digest("$SEED_LABEL/$id".encodeToByteArray()))

    private companion object {
        /** 衍生種子的固定前綴。 */
        const val SEED_LABEL = "mahjongcraft:history_fixture"
    }
}
