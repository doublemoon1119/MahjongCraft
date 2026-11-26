package com.doublemoon1119.mahjongcraft.example.minecraft.text

/** 範例 Minecraft 呈現使用的翻譯鍵。 */
object ExampleMinecraftMessageKeys {
    /** 簡易配對麻將規則名稱。 */
    const val RULE_NAME = "mahjongcraft_example.rule.pairing.name"

    /** 簡易配對麻將規則說明。 */
    const val RULE_DESCRIPTION = "mahjongcraft_example.rule.pairing.description"

    /** 摸切 AI 名稱。 */
    const val AI_NAME = "mahjongcraft_example.ai.tsumogiri.name"

    /** 倒置一萬牌種名稱。 */
    const val TILE_INVERTED_ONE_MAN = "mahjongcraft_example.tile.inverted_one_man"

    /** 攤牌動作名稱。 */
    const val REVEAL_HAND = "mahjongcraft_example.action.reveal_hand"

    /** 攤牌動作說明。 */
    const val REVEAL_HAND_DESCRIPTION = "mahjongcraft_example.action.reveal_hand.description"

    /** 攤牌操作卡中央的效果說明。 */
    const val REVEAL_HAND_CARD_BODY = "mahjongcraft_example.action.reveal_hand.card_body"

    /** 攤牌按鈕旁的使用次數提示。 */
    const val REVEAL_HAND_CARD_HINT = "mahjongcraft_example.action.reveal_hand.card_hint"

    /** 已攤牌的公開標記。 */
    const val HAND_REVEALED = "mahjongcraft_example.indicator.hand_revealed"

    /** 牌山耗盡流局名稱。 */
    const val EXHAUSTIVE_DRAW = "mahjongcraft_example.draw.wall_exhausted"

    /** 簡易配對規則設定分類名稱。 */
    const val CONFIG_CATEGORY_RULE = "mahjongcraft_example.room.config.category.rule"

    /** 換牌開關欄位名稱。 */
    const val CONFIG_EXCHANGE = "mahjongcraft_example.room.config.field.exchange_enabled"

    /** 換牌開關欄位說明。 */
    const val CONFIG_EXCHANGE_DESCRIPTION = "mahjongcraft_example.room.config.field.exchange_enabled.description"

    /** 公開牌面開關欄位名稱。 */
    const val CONFIG_PUBLIC_FACES = "mahjongcraft_example.room.config.field.public_faces_enabled"

    /** 公開牌面開關欄位說明。 */
    const val CONFIG_PUBLIC_FACES_DESCRIPTION = "mahjongcraft_example.room.config.field.public_faces_enabled.description"

    /** 每位玩家付款點數欄位名稱。 */
    const val CONFIG_PAYMENT = "mahjongcraft_example.room.config.field.payment_per_player"

    /** 每位玩家付款點數欄位說明。 */
    const val CONFIG_PAYMENT_DESCRIPTION = "mahjongcraft_example.room.config.field.payment_per_player.description"

    /** 對局局數欄位名稱。 */
    const val CONFIG_ROUNDS = "mahjongcraft_example.room.config.field.round_count"

    /** 對局局數欄位說明。 */
    const val CONFIG_ROUNDS_DESCRIPTION = "mahjongcraft_example.room.config.field.round_count.description"

    /** 牌山使用點數的多選欄位名稱。 */
    const val CONFIG_WALL_TILE_VALUES = "mahjongcraft_example.room.config.field.wall_tile_values"

    /** 牌山使用點數的多選欄位說明。 */
    const val CONFIG_WALL_TILE_VALUES_DESCRIPTION = "mahjongcraft_example.room.config.field.wall_tile_values.description"

    /** 一般牌種選項的加入張數說明。 */
    const val CONFIG_WALL_TILE_OPTION_DESCRIPTION = "mahjongcraft_example.room.config.option.tile.description"

    /** 一萬選項包含倒置一萬的說明。 */
    const val CONFIG_WALL_ONE_DESCRIPTION = "mahjongcraft_example.room.config.option.tile_one.description"

    /** 一萬至八萬選項各自明確登記的翻譯鍵。 */
    val WALL_TILE_VALUE_OPTIONS: Map<Int, String> = mapOf(
        1 to "mahjongcraft_example.room.config.option.tile_one",
        2 to "mahjongcraft_example.room.config.option.tile_two",
        3 to "mahjongcraft_example.room.config.option.tile_three",
        4 to "mahjongcraft_example.room.config.option.tile_four",
        5 to "mahjongcraft_example.room.config.option.tile_five",
        6 to "mahjongcraft_example.room.config.option.tile_six",
        7 to "mahjongcraft_example.room.config.option.tile_seven",
        8 to "mahjongcraft_example.room.config.option.tile_eight",
    )

    /** 局況中顯示剩餘牌山數量的文字。 */
    const val ROUND_INFO_WALL_REMAINING = "mahjongcraft_example.round_info.wall_remaining"

    /** 局況中顯示目前局數的文字。 */
    const val ROUND_INFO_ROUND_COUNT = "mahjongcraft_example.round_info.round_count"

    /** 局況中顯示最近打出的牌。 */
    const val ROUND_INFO_LATEST_DISCARD = "mahjongcraft_example.round_info.latest_discard"

    /** 局況中顯示換牌功能狀態。 */
    const val ROUND_INFO_EXCHANGE = "mahjongcraft_example.round_info.exchange"

    /** 局況中顯示換牌功能已開啟。 */
    const val ROUND_INFO_EXCHANGE_ENABLED = "mahjongcraft_example.round_info.exchange_enabled"

    /** 局況中顯示換牌功能已關閉。 */
    const val ROUND_INFO_EXCHANGE_DISABLED = "mahjongcraft_example.round_info.exchange_disabled"

    /** 規則目錄分類名稱。 */
    const val CATALOGUE_CATEGORY = "mahjongcraft_example.catalogue.category.pairing"

    /** 規則目錄分類說明。 */
    const val CATALOGUE_ENTRY = "mahjongcraft_example.catalogue.entry.pairing"

    /** 規則目錄條目詳細說明。 */
    const val CATALOGUE_ENTRY_DESCRIPTION = "mahjongcraft_example.catalogue.entry.pairing.description"

    /** 「基本玩法」：牌與手牌。 */
    const val BASICS_TILES = "mahjongcraft_example.catalogue.basics.tiles"

    /** 「基本玩法」：牌與手牌的說明。 */
    const val BASICS_TILES_DESCRIPTION = "mahjongcraft_example.catalogue.basics.tiles.description"

    /** 「基本玩法」：和牌條件。 */
    const val BASICS_WINNING = "mahjongcraft_example.catalogue.basics.winning"

    /** 「基本玩法」：和牌條件的說明。 */
    const val BASICS_WINNING_DESCRIPTION = "mahjongcraft_example.catalogue.basics.winning.description"

    /** 「基本玩法」：開局前交換牌。 */
    const val BASICS_EXCHANGE = "mahjongcraft_example.catalogue.basics.exchange"

    /** 「基本玩法」：開局前交換牌的說明。 */
    const val BASICS_EXCHANGE_DESCRIPTION = "mahjongcraft_example.catalogue.basics.exchange.description"

    /** 開局前交換牌沒有開啟時的不適用原因。 */
    const val BASICS_EXCHANGE_UNAVAILABLE = "mahjongcraft_example.catalogue.basics.exchange.unavailable"

    /** 「基本玩法」：加分牌與付款。 */
    const val BASICS_BONUS = "mahjongcraft_example.catalogue.basics.bonus"

    /** 「基本玩法」：加分牌與付款的說明。 */
    const val BASICS_BONUS_DESCRIPTION = "mahjongcraft_example.catalogue.basics.bonus.description"

    /** 「基本玩法」：攤牌。 */
    const val BASICS_REVEAL = "mahjongcraft_example.catalogue.basics.reveal"

    /** 「基本玩法」：攤牌的說明。 */
    const val BASICS_REVEAL_DESCRIPTION = "mahjongcraft_example.catalogue.basics.reveal.description"

    /** 「基本玩法」：流局與對局結束。 */
    const val BASICS_DRAW = "mahjongcraft_example.catalogue.basics.draw"

    /** 「基本玩法」：流局與對局結束的說明。 */
    const val BASICS_DRAW_DESCRIPTION = "mahjongcraft_example.catalogue.basics.draw.description"

    /** 「基本玩法」：公開牌面。 */
    const val BASICS_PUBLIC_FACES = "mahjongcraft_example.catalogue.basics.public_faces"

    /** 「基本玩法」：公開牌面的說明。 */
    const val BASICS_PUBLIC_FACES_DESCRIPTION = "mahjongcraft_example.catalogue.basics.public_faces.description"

    /** 公開牌面沒有開啟時的不適用原因。 */
    const val BASICS_PUBLIC_FACES_UNAVAILABLE = "mahjongcraft_example.catalogue.basics.public_faces.unavailable"

    /** 範例小標題：兩個對子。 */
    const val BASICS_EXAMPLE_TWO_PAIRS = "mahjongcraft_example.catalogue.basics.example.two_pairs"

    /** 兩個對子範例的說明。 */
    const val BASICS_EXAMPLE_TWO_PAIRS_NOTE = "mahjongcraft_example.catalogue.basics.example.two_pairs.note"

    /** 付款點數詳情欄位名稱。 */
    const val PAYMENT_PER_PLAYER = "mahjongcraft_example.settlement.payment_per_player"

    /** 自摸命中的不同加分牌種類數量欄位名稱。 */
    const val BONUS_TILE_COUNT = "mahjongcraft_example.settlement.bonus_tile_count"

    /** 顯示自摸手牌由加分牌取得的點數。 */
    const val BONUS_TILE_POINTS = "mahjongcraft_example.settlement.bonus_tile_points"

    /** 結算面板的本局加分牌標題。 */
    const val BONUS_TILES = "mahjongcraft_example.settlement.bonus_tiles"

    /** 每位玩家實際付款的點數文字。 */
    const val PAYMENT_AMOUNT = "mahjongcraft_example.settlement.payment_amount"
}
