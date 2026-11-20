package tj.jasurbek.projectlib.data

private const val DAY = 86_400_000L

/** Seed data (same as the web app's demo). Replaceable from Settings. */
fun demoProjects(): List<Project> {
    val now = System.currentTimeMillis()
    fun idea(title: String, description: String, status: String, priority: Priority, created: Long, updated: Long) =
        Idea(uid(), title, description, status, priority, createdAt = now - created * DAY, updatedAt = now - updated * DAY)
    return listOf(
        Project(
            uid(), "ProjectLib", "Библиотека проектов и идей с запуском web-версий.", ProjectStatus.Active, listOf("react", "личное"),
            listOf(Link(uid(), "Локально", "http://localhost:5173", LinkKind.Web)),
            listOf(
                idea("Канбан-доска идей", "Перетаскивание идей между статусами.", IdeaStatus.Planned, Priority.Medium, 2, 2),
                idea("Синхронизация между устройствами", "", IdeaStatus.New, Priority.Low, 1, 1),
                idea("Экспорт и импорт JSON", "Резервная копия всех данных.", IdeaStatus.Done, Priority.High, 3, 1),
            ),
            now - 3 * DAY, now - DAY,
        ),
        Project(
            uid(), "Сайт-визитка", "Пример запущенного сайта: его можно открыть прямо в ProjectLib.", ProjectStatus.Live, listOf("пример"),
            listOf(
                Link(uid(), "Сайт", "https://example.com", LinkKind.Web),
                Link(uid(), "Исходники", "https://github.com", LinkKind.Repo),
            ),
            listOf(
                idea("Тёмная тема", "", IdeaStatus.InProgress, Priority.Medium, 5, 2),
                idea("Офлайн-режим", "Работа сайта без интернета.", IdeaStatus.Paused, Priority.Low, 6, 4),
            ),
            now - 10 * DAY, now - 2 * DAY,
        ),
        Project(
            uid(), "Трекер привычек", "Пока только идея: простое приложение для ежедневных привычек.", ProjectStatus.Idea, listOf("mobile"),
            emptyList(),
            listOf(idea("Напоминания", "Push-уведомления в выбранное время.", IdeaStatus.New, Priority.High, 7, 7)),
            now - 7 * DAY, now - 7 * DAY,
        ),
    )
}
