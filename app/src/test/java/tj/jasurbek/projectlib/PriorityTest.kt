package tj.jasurbek.projectlib

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import tj.jasurbek.projectlib.data.Idea
import tj.jasurbek.projectlib.data.Priority
import tj.jasurbek.projectlib.data.byPriorityThenOrder

/** Same order as byPriorityThenOrder in the web app's src/lib/types.ts. */
class PriorityTest {
    private fun idea(id: String, p: Priority, order: Double) = Idea(id, id, priority = p, order = order, createdAt = 1, updatedAt = 1)

    @Test fun boardColumnsSortUrgentFirstThenManualOrder() {
        val list = listOf(idea("a", Priority.Low, 1.0), idea("b", Priority.Urgent, 5.0), idea("c", Priority.None, 0.0),
            idea("d", Priority.Low, 0.0), idea("f", Priority.High, 9.0))
        assertEquals(listOf("b", "f", "d", "a", "c"), list.sortedWith(byPriorityThenOrder).map { it.id })
    }

    @Test fun newPriorityValuesUseWebNames() {
        assertEquals("\"urgent\"", Json.encodeToString(Priority.serializer(), Priority.Urgent))
        assertEquals(Priority.None, Json.decodeFromString(Priority.serializer(), "\"none\""))
    }
}
