package com.eo.versionos

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

val LEVELS = listOf(
    "ZERO", "STARTER", "BEGINNER", "LEARNER", "INTERMEDIATE", "ADVANCED",
    "SPECIALIST", "EXPERT", "PROFESSIONAL", "PRO", "BEYOND"
)

data class Category(val code: String, val name: String, val archived: Boolean = false)

data class Project(
    val id: String,
    val categoryCode: String,
    val name: String,
    val major: Int,
    val minor: Int,
    val createdAt: Long
) {
    fun versionId(): String = "$id.EO.V$major.$minor"
}

data class VersionRecord(
    val id: Int,
    val projectId: String,
    val title: String,
    val reason: String,
    val type: String,
    val prevVersion: String,
    val newVersion: String,
    val completedAt: Long,
    val awardedAt: Long,
    val elapsedMs: Long
)

data class AuditEntry(val time: Long, val text: String)

data class AppState(
    val level: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val categories: List<Category> = emptyList(),
    val projects: List<Project> = emptyList(),
    val records: List<VersionRecord> = emptyList(),
    val audit: List<AuditEntry> = emptyList()
)

data class Op(val state: AppState, val error: String? = null)

fun fmtTime(ms: Long): String {
    val f = SimpleDateFormat("dd MMM yyyy, hh:mm:ss a", Locale.ENGLISH)
    f.timeZone = TimeZone.getTimeZone("Asia/Karachi")
    return f.format(Date(ms)) + " PKT"
}

fun fmtElapsed(ms: Long): String {
    val t = if (ms < 0) 0L else ms / 1000
    val d = t / 86400
    val h = (t % 86400) / 3600
    val m = (t % 3600) / 60
    val s = t % 60
    return "${d}d ${h}h ${m}m ${s}s"
}

fun nextRecordId(s: AppState): Int = (s.records.maxOfOrNull { it.id } ?: 0) + 1

fun addCategory(s: AppState, rawCode: String, rawName: String): Op {
    val code = rawCode.trim().uppercase()
    val name = rawName.trim()
    if (code.isEmpty() || name.isEmpty()) return Op(s, "Code aur naam dono likhein")
    if (!code.all { it.isLetterOrDigit() }) return Op(s, "Code mein sirf harf ya number")
    if (s.categories.any { it.code == code }) return Op(s, "Ye code pehle se maujood hai")
    val now = System.currentTimeMillis()
    return Op(
        s.copy(
            categories = s.categories + Category(code, name),
            audit = s.audit + AuditEntry(now, "Category created: $code - $name")
        )
    )
}

fun seedCategories(s: AppState): Op {
    var st = s
    val list = listOf(
        "AF" to "Afsana", "RD" to "Reading", "SK" to "Skills", "BS" to "Business",
        "PX" to "Projects", "TG" to "Technology", "PG" to "Personal Growth"
    )
    for (pair in list) {
        val r = addCategory(st, pair.first, pair.second)
        if (r.error == null) st = r.state
    }
    return Op(st)
}

fun addProject(s: AppState, catCode: String, rawName: String): Op {
    val name = rawName.trim()
    if (name.isEmpty()) return Op(s, "Project ka naam likhein")
    if (s.categories.none { it.code == catCode }) return Op(s, "Pehle category chunein")
    val n = s.projects.count { it.categoryCode == catCode } + 1
    val id = catCode + "." + n.toString().padStart(2, '0')
    val now = System.currentTimeMillis()
    val p = Project(id, catCode, name, 0, 0, now)
    val rec = VersionRecord(
        nextRecordId(s), id, "Project registered", "Initial registration",
        "REGISTER", "-", p.versionId(), now, now, 0L
    )
    return Op(
        s.copy(
            projects = s.projects + p,
            records = s.records + rec,
            audit = s.audit + AuditEntry(now, "Project created: $id - $name (${p.versionId()})")
        )
    )
}

fun addAchievement(s: AppState, projectId: String, rawTitle: String, rawReason: String, major: Boolean): Op {
    val title = rawTitle.trim()
    if (title.isEmpty()) return Op(s, "Achievement ka title likhein")
    val p = s.projects.find { it.id == projectId } ?: return Op(s, "Project nahi mila")
    val dup = s.records.any {
        it.projectId == projectId && it.type != "REGISTER" && it.title.equals(title, ignoreCase = true)
    }
    if (dup) return Op(s, "Ye achievement is project mein pehle se hai")
    val now = System.currentTimeMillis()
    val prev = s.records.filter { it.projectId == projectId }.maxOfOrNull { it.awardedAt } ?: p.createdAt
    val np = if (major) p.copy(major = p.major + 1, minor = 0) else p.copy(minor = p.minor + 1)
    val rec = VersionRecord(
        nextRecordId(s), projectId, title, rawReason.trim(),
        if (major) "MAJOR" else "MINOR",
        p.versionId(), np.versionId(), now, now, now - prev
    )
    return Op(
        s.copy(
            projects = s.projects.map { if (it.id == projectId) np else it },
            records = s.records + rec,
            audit = s.audit + AuditEntry(now, "Upgrade $projectId: ${p.versionId()} -> ${np.versionId()} ($title)")
        )
    )
}

fun intervalStats(s: AppState, projectId: String): String {
    val xs = s.records.filter { it.projectId == projectId && it.type != "REGISTER" }.map { it.elapsedMs }
    if (xs.isEmpty()) return "Abhi koi upgrade nahi"
    return "Average: " + fmtElapsed(xs.sum() / xs.size) +
        "\nShortest: " + fmtElapsed(xs.min()) +
        "\nLongest: " + fmtElapsed(xs.max())
}

fun exportText(s: AppState): String {
    val sb = StringBuilder()
    val levelName = LEVELS.getOrElse(s.level) { "?" }
    sb.appendLine(".EO LIFE VERSION HISTORY")
    sb.appendLine("Identity: .EO")
    sb.appendLine("Master version: .EO.V0.0")
    sb.appendLine("Level: " + "%02d".format(s.level) + " - " + levelName)
    sb.appendLine("Registered: " + fmtTime(s.createdAt))
    sb.appendLine("Categories: " + s.categories.size + " | Projects: " + s.projects.size +
        " | Upgrades: " + s.records.count { it.type != "REGISTER" })
    sb.appendLine()
    for (c in s.categories) {
        sb.appendLine("== " + c.code + " - " + c.name + " ==")
        for (p in s.projects.filter { it.categoryCode == c.code }) {
            sb.appendLine("  Project " + p.id + ": " + p.name + " (current " + p.versionId() + ")")
            for (r in s.records.filter { it.projectId == p.id }) {
                sb.appendLine("    " + r.prevVersion + " -> " + r.newVersion + " [" + r.type + "] " + r.title)
                sb.appendLine("      Reason: " + r.reason)
                sb.appendLine("      Awarded: " + fmtTime(r.awardedAt) + " | Since previous: " + fmtElapsed(r.elapsedMs))
            }
        }
        sb.appendLine()
    }
    return sb.toString()
}
