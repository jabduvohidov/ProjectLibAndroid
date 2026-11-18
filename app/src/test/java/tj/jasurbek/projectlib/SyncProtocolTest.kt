package tj.jasurbek.projectlib

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tj.jasurbek.projectlib.data.AppJson
import tj.jasurbek.projectlib.data.IDEA_FIELDS
import tj.jasurbek.projectlib.data.Idea
import tj.jasurbek.projectlib.data.IdeaStatus
import tj.jasurbek.projectlib.data.Link
import tj.jasurbek.projectlib.data.LinkKind
import tj.jasurbek.projectlib.data.Op
import tj.jasurbek.projectlib.data.Project
import tj.jasurbek.projectlib.data.ProjectStatus
import tj.jasurbek.projectlib.data.SyncRequest
import tj.jasurbek.projectlib.data.SyncResponse
import tj.jasurbek.projectlib.data.applyOps
import tj.jasurbek.projectlib.data.diffFields
import tj.jasurbek.projectlib.data.mergeChanges

/** The wire format must match the web client / server (src/lib/sync-protocol.ts, server/validate.ts). */
class SyncProtocolTest {
    private val idea = Idea("i1", "Тёмная тема", createdAt = 1000, updatedAt = 1000)
    private val project = Project("p1", "ProjectLib", links = listOf(Link("l1", "Сайт", "https://example.com", LinkKind.Web)), ideas = listOf(idea), createdAt = 1000, updatedAt = 1000)

    @Test fun opsSerializeWithTypeDiscriminatorAndWireNames() {
        val json = AppJson.encodeToJsonElement(SyncRequest.serializer(), SyncRequest(5, listOf(Op.ProjectPut("o1", 2000, project)))).jsonObject
        val op = (json["ops"] as kotlinx.serialization.json.JsonArray)[0].jsonObject
        assertEquals("project.put", op["type"]!!.jsonPrimitive.content)
        val p = op["project"]!!.jsonObject
        assertEquals("idea", p["status"]!!.jsonPrimitive.content)
        val link = (p["links"] as kotlinx.serialization.json.JsonArray)[0].jsonObject
        assertEquals("web", link["kind"]!!.jsonPrimitive.content)
        // Optional fields are omitted, not sent as null (the server rejects null platform).
        assertFalse("platform" in link)
        val i = (p["ideas"] as kotlinx.serialization.json.JsonArray)[0].jsonObject
        assertEquals("new", i["status"]!!.jsonPrimitive.content)
        assertFalse("dueDate" in i)
    }

    @Test fun ideaPatchContainsOnlyChangedFields() {
        val next = idea.copy(status = IdeaStatus.InProgress, dueDate = "2026-12-01")
        val patch = diffFields(idea, next, IDEA_FIELDS)!!
        assertEquals(setOf("status", "dueDate"), patch.keys)
        assertEquals("in_progress", patch["status"]!!.jsonPrimitive.content)
        assertNull(diffFields(idea, idea.copy(), IDEA_FIELDS))
    }

    @Test fun applyOpsShowsPendingChanges() {
        val patch = JsonObject(mapOf("status" to JsonPrimitive("done")))
        val after = applyOps(listOf(project), listOf(Op.IdeaPatch("o1", 3000, "p1", "i1", patch)))
        assertEquals(IdeaStatus.Done, after[0].ideas[0].status)
        assertEquals(3000, after[0].updatedAt)
        val deleted = applyOps(after, listOf(Op.ProjectDelete("o2", 4000, "p1")))
        assertTrue(deleted.isEmpty())
    }

    @Test fun mergesServerResponseWithTombstones() {
        val text = """
            {"rev":7,"full":false,
             "projects":[{"id":"p2","name":"Новый","description":"","status":"live","tags":[],"links":[],"createdAt":5000,"updatedAt":5000}],
             "ideas":[{"projectId":"p1","id":"i1","deleted":true},
                      {"projectId":"p2","id":"i9","title":"Идея","description":"","status":"planned","priority":"high","order":-1.5,"createdAt":6000,"updatedAt":6000}],
             "rejected":[]}
        """.trimIndent()
        val res = AppJson.decodeFromString(SyncResponse.serializer(), text)
        val merged = mergeChanges(listOf(project), res)
        assertEquals(listOf("p2", "p1"), merged.map { it.id })
        assertEquals(ProjectStatus.Live, merged[0].status)
        assertEquals(-1.5, merged[0].ideas.single().order!!, 0.0)
        assertTrue(merged[1].ideas.isEmpty())
    }

    @Test fun accessDecidesWhatYouMayDo() {
        val mine = project.copy(access = null)
        assertTrue(mine.isOwn && mine.canEdit && mine.canManage)
        val asAdmin = project.copy(access = tj.jasurbek.projectlib.data.Access.Admin)
        assertTrue(!asAdmin.isOwn && asAdmin.canEdit && asAdmin.canManage)
        val edit = project.copy(access = tj.jasurbek.projectlib.data.Access.Edit)
        assertTrue(edit.canEdit && !edit.canManage)
        val view = project.copy(access = tj.jasurbek.projectlib.data.Access.View)
        assertFalse(view.canEdit || view.canManage)
    }

    @Test fun replaceAllKeepsProjectsSharedWithYou() {
        val shared = project.copy(id = "p2", access = tj.jasurbek.projectlib.data.Access.View)
        val after = applyOps(listOf(project, shared), listOf(Op.ReplaceAll("o1", 5000, emptyList())))
        assertEquals(listOf("p2"), after.map { it.id })
    }

    @Test fun decodesOwnershipAndSharesFromServer() {
        val text = """
            {"rev":3,"full":true,
             "projects":[{"id":"p1","name":"Общий","status":"active","tags":[],"links":[],"createdAt":1,"updatedAt":2,
                          "ownerId":"u1","access":"owner","shares":[{"userId":"u2","access":"edit"}]}],
             "ideas":[{"projectId":"p1","id":"i1","title":"Идея","status":"new","priority":"low","createdAt":1,"updatedAt":2,"createdBy":"u1","updatedBy":"u2"}],
             "rejected":[]}
        """.trimIndent()
        val p = mergeChanges(emptyList(), AppJson.decodeFromString(SyncResponse.serializer(), text)).single()
        assertEquals("u1", p.ownerId)
        assertEquals(1, p.shareCount)
        assertEquals(tj.jasurbek.projectlib.data.ShareAccess.Edit, p.shares!!.single().access)
        assertEquals("u2", p.ideas.single().updatedBy)
    }
}
