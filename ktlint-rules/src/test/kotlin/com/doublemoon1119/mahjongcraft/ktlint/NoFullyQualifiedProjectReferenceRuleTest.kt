package com.doublemoon1119.mahjongcraft.ktlint

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 驗證全類名引用規則只攔截真正的程式碼引用。 */
class NoFullyQualifiedProjectReferenceRuleTest {
    /** 型別位置的全類名應被攔截。 */
    @Test
    fun `reports a fully qualified type reference`() {
        val errors = lint(
            """
            package com.doublemoon1119.mahjongcraft.example

            fun resolve(state: com.doublemoon1119.mahjongcraft.logic.table.TableState) = state
            """.trimIndent(),
            ::NoFullyQualifiedProjectReferenceRule,
        )

        assertEquals(1, errors.size)
        assertTrue(errors.messages().single().contains("logic.table.TableState"))
    }

    /** 運算式位置的全類名應被攔截。 */
    @Test
    fun `reports a fully qualified expression reference`() {
        val errors = lint(
            """
            package com.doublemoon1119.mahjongcraft.example

            val id = com.doublemoon1119.mahjongcraft.logic.base.MeldTypeId.parse("a:b")
            """.trimIndent(),
            ::NoFullyQualifiedProjectReferenceRule,
        )

        assertEquals(1, errors.size)
    }

    /** 巢狀限定式只回報最外層一次。 */
    @Test
    fun `reports a nested qualified reference only once`() {
        val errors = lint(
            """
            package com.doublemoon1119.mahjongcraft.example

            val mode = com.doublemoon1119.mahjongcraft.flow.common.game.model.Mode.BRIEF
            """.trimIndent(),
            ::NoFullyQualifiedProjectReferenceRule,
        )

        assertEquals(1, errors.size)
    }

    /** `package` 與 `import` directive 不是違規。 */
    @Test
    fun `ignores package and import directives`() {
        val errors = lint(
            """
            package com.doublemoon1119.mahjongcraft.example

            import com.doublemoon1119.mahjongcraft.logic.table.TableState

            fun resolve(state: TableState) = state
            """.trimIndent(),
            ::NoFullyQualifiedProjectReferenceRule,
        )

        assertEquals(emptyList(), errors.messages())
    }

    /** 字串常值中的套件名稱是 component scanning 契約，不是違規。 */
    @Test
    fun `ignores package names kept as string literals`() {
        val errors = lint(
            """
            package com.doublemoon1119.mahjongcraft.example

            annotation class ComponentScan(val value: String)

            @ComponentScan("com.doublemoon1119.mahjongcraft.flow.common")
            class Module
            """.trimIndent(),
            ::NoFullyQualifiedProjectReferenceRule,
        )

        assertEquals(emptyList(), errors.messages())
    }

    /** 註解與 KDoc 中的跨依賴邊界引用不是違規。 */
    @Test
    fun `ignores references inside comments and kdoc`() {
        val errors = lint(
            """
            package com.doublemoon1119.mahjongcraft.example

            /**
             * 由外層呼叫，見
             * [com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.GameFlowCoordinator]
             */
            // com.doublemoon1119.mahjongcraft.platform.minecraft.Anything
            class Contract
            """.trimIndent(),
            ::NoFullyQualifiedProjectReferenceRule,
        )

        assertEquals(emptyList(), errors.messages())
    }

    /** 其他套件的全類名不在本規則範圍。 */
    @Test
    fun `ignores fully qualified names outside the project package`() {
        val errors = lint(
            """
            package com.doublemoon1119.mahjongcraft.example

            val ids = mutableMapOf<java.util.UUID, String>()
            """.trimIndent(),
            ::NoFullyQualifiedProjectReferenceRule,
        )

        assertEquals(emptyList(), errors.messages())
    }
}
