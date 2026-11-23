package tj.jasurbek.projectlib

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tj.jasurbek.projectlib.data.DEFAULT_STATUSES
import tj.jasurbek.projectlib.data.StatusDef
import tj.jasurbek.projectlib.data.StatusTone
import tj.jasurbek.projectlib.data.adoptTemplate
import tj.jasurbek.projectlib.data.isDefaultColumns
import tj.jasurbek.projectlib.data.nearestStatus
import tj.jasurbek.projectlib.data.uniqueColumnName

/** Same rules as the web app's src/lib/templates.ts. */
class TemplatesTest {
    private val next = listOf(
        StatusDef("todo", "Сделать", percent = 0),
        StatusDef("wip", "В работе", StatusTone.Warning, 60),
        StatusDef("ok", "Готово", StatusTone.Success, 100),
        StatusDef("no", "Не будем", StatusTone.Danger, 0, excluded = true),
    )

    @Test fun nearestPrefersSameNameThenExcludedThenClosestPercent() {
        assertEquals("ok", nearestStatus(StatusDef("x", "  готово ", percent = 10), next)!!.id)
        assertEquals("no", nearestStatus(StatusDef("x", "Отменено", excluded = true), next)!!.id)
        assertEquals("wip", nearestStatus(StatusDef("x", "Пауза", percent = 50), next)!!.id)
        assertEquals("todo", nearestStatus(StatusDef("x", "Новая", percent = 0), next)!!.id)
    }

    @Test fun adoptKeepsIdsOfSameNamedColumns() {
        var n = 0
        val tpl = listOf(StatusDef("a", "Сделать"), StatusDef("b", "в процессе", percent = 60), StatusDef("new", "Готово", percent = 100), StatusDef("d", "Выполнено", percent = 100))
        val out = adoptTemplate(DEFAULT_STATUSES, tpl) { "fresh${n++}" }
        assertEquals(listOf("a", "in_progress", "fresh0", "done"), out.map { it.id })
        // A template id that clashes with an existing column id gets a fresh one.
        val clash = adoptTemplate(DEFAULT_STATUSES, listOf(StatusDef("new", "Другое"))) { "fresh" }
        assertEquals("fresh", clash.single().id)
    }

    @Test fun defaultColumnsAreDetected() {
        assertTrue(isDefaultColumns(DEFAULT_STATUSES.map { it.copy() }))
        assertFalse(isDefaultColumns(DEFAULT_STATUSES.map { if (it.id == "planned") it.copy(percent = 20) else it }))
        assertFalse(isDefaultColumns(DEFAULT_STATUSES.reversed()))
    }

    @Test fun uniqueNames() {
        assertEquals("Новая колонка", uniqueColumnName(next, "Новая колонка"))
        assertEquals("Новая колонка 2", uniqueColumnName(next + StatusDef("a", "новая колонка"), "Новая колонка"))
    }
}
