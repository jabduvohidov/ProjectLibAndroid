package tj.jasurbek.projectlib.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FolderSpecial
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tj.jasurbek.projectlib.data.Project
import tj.jasurbek.projectlib.ui.common.GlassDropdown
import tj.jasurbek.projectlib.ui.common.SearchField
import tj.jasurbek.projectlib.ui.designsystem.DS
import tj.jasurbek.projectlib.ui.designsystem.DSSpace

private fun plural(n: Int): String {
    val m10 = n % 10; val m100 = n % 100
    return when {
        m10 == 1 && m100 != 11 -> "проект"
        m10 in 2..4 && (m100 < 12 || m100 > 14) -> "проекта"
        else -> "проектов"
    }
}

/**
 * Multi-select project filter (web ProjectPicker): a capsule that opens a glass menu with checkboxes.
 * Empty selection = all projects; the menu stays open while you tick several.
 */
@Composable
fun ProjectPicker(projects: List<Project>, selected: List<String>, onChange: (List<String>) -> Unit, counts: Map<String, Int>? = null) {
    val c = DS.colors
    var open by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val chosen = selected.toSet()
    val label = when (selected.size) {
        0 -> "Все проекты"
        1 -> projects.firstOrNull { it.id == selected[0] }?.name ?: "1 проект"
        else -> "${selected.size} ${plural(selected.size)}"
    }
    val on = selected.isNotEmpty()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box {
            Row(
                Modifier.heightIn(min = 44.dp).clip(CircleShape)
                    .background(if (on) c.accentSubtle else c.surface).border(1.dp, if (on) c.accent else c.border, CircleShape)
                    .clickable(role = Role.Button) { open = !open; if (!open) query = "" }.padding(horizontal = DSSpace.md),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DSSpace.sm),
            ) {
                val fg = if (on) c.accentText else c.textPrimary
                Icon(Icons.Rounded.FolderSpecial, null, tint = fg, modifier = Modifier.size(18.dp))
                Text(label, style = MaterialTheme.typography.bodyMedium, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 220.dp))
                Icon(Icons.Rounded.KeyboardArrowDown, null, tint = fg, modifier = Modifier.size(18.dp).rotate(if (open) 180f else 0f))
            }
            GlassDropdown(expanded = open, onDismissRequest = { open = false; query = "" }) {
                Column(Modifier.widthIn(min = 260.dp, max = 320.dp).padding(DSSpace.xs)) {
                    if (projects.size > 7) SearchField(query, { query = it }, "Найти проект", Modifier.padding(bottom = DSSpace.xs))
                    val q = query.trim().lowercase()
                    val visible = projects.sortedWith(compareBy(java.text.Collator.getInstance(java.util.Locale.forLanguageTag("ru"))) { it.name })
                        .filter { q.isEmpty() || it.name.lowercase().contains(q) }
                    Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                        visible.forEach { p ->
                            val picked = p.id in chosen
                            Row(
                                Modifier.heightIn(min = 44.dp).clip(MaterialTheme.shapes.medium)
                                    .clickable(role = Role.Checkbox) { onChange(if (picked) selected - p.id else selected + p.id) }
                                    .padding(horizontal = DSSpace.md),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DSSpace.md),
                            ) {
                                val box = RoundedCornerShape(4.dp)
                                Box(
                                    Modifier.size(18.dp).clip(box).background(if (picked) c.accent else c.surface)
                                        .border(1.dp, if (picked) c.accent else c.borderStrong, box),
                                    contentAlignment = Alignment.Center,
                                ) { if (picked) Icon(Icons.Rounded.Check, null, tint = c.textOnAccent, modifier = Modifier.size(13.dp)) }
                                Text(p.name, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                counts?.let { Text("${it[p.id] ?: 0}", style = MaterialTheme.typography.bodySmall, color = c.textTertiary) }
                            }
                        }
                        if (visible.isEmpty()) Text("Ничего не найдено", style = MaterialTheme.typography.bodySmall, color = c.textTertiary,
                            modifier = Modifier.padding(horizontal = DSSpace.md, vertical = DSSpace.sm))
                    }
                    HorizontalDivider(Modifier.padding(top = DSSpace.xs), color = c.border)
                    Row(Modifier.padding(top = DSSpace.xs), verticalAlignment = Alignment.CenterVertically) {
                        Text("Сбросить", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = c.accentText,
                            modifier = Modifier.alpha(if (on) 1f else 0.4f).clip(CircleShape).clickable(enabled = on) { onChange(emptyList()) }
                                .padding(horizontal = DSSpace.md, vertical = DSSpace.sm))
                        Box(Modifier.weight(1f))
                        Text("Готово", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = c.textPrimary,
                            modifier = Modifier.clip(CircleShape).background(c.fill).clickable { open = false; query = "" }
                                .padding(horizontal = DSSpace.md, vertical = DSSpace.sm))
                    }
                }
            }
        }
        if (on) Box(
            Modifier.padding(start = DSSpace.xs).size(44.dp).clip(CircleShape).clickable { onChange(emptyList()) },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Close, "Показать все проекты", tint = c.textTertiary, modifier = Modifier.size(18.dp)) }
    }
}
