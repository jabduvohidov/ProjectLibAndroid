package tj.jasurbek.projectlib

import org.junit.Assert.assertEquals
import org.junit.Test
import tj.jasurbek.projectlib.data.Project
import tj.jasurbek.projectlib.data.SecretLock

class SecretLockTest {
    private fun project(id: String, secret: Boolean?) = Project(id = id, name = id, createdAt = 1, updatedAt = 1, secret = secret)

    @Test fun secretProjectsAreHiddenUntilUnlocked() {
        val all = listOf(project("a", null), project("s", true), project("b", false))
        assertEquals(listOf("a", "b"), SecretLock.visible(all, unlocked = false).map { it.id })
        assertEquals(listOf("a", "s", "b"), SecretLock.visible(all, unlocked = true).map { it.id })
    }
}
