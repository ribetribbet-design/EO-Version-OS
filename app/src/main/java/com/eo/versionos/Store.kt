package com.eo.versionos

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class Store(private val context: Context) {
    private val file = File(context.filesDir, "eo_data.json")
    private val backup = File(context.filesDir, "eo_data.bak")

    fun load(): AppState {
        if (!file.exists()) {
            val now = System.currentTimeMillis()
            val fresh = AppState(
                createdAt = now,
                audit = listOf(AuditEntry(now, "Identity .EO registered: .EO.V0.0 / ZERO"))
            )
            try {
                save(fresh)
            } catch (e: Exception) {
            }
            return fresh
        }
        return try {
            parseState(file.readText())
        } catch (e: Exception) {
            try {
                file.copyTo(File(context.filesDir, "eo_data.corrupt"), overwrite = true)
            } catch (e2: Exception) {
            }
            try {
                parseState(backup.readText())
            } catch (e3: Exception) {
                AppState()
            }
        }
    }

    fun save(state: AppState) {
        val tmp = File(context.filesDir, "eo_data.tmp")
        tmp.writeText(stateToJson(state).toString())
        if (file.exists()) file.copyTo(backup, overwrite = true)
        tmp.copyTo(file, overwrite = true)
        tmp.delete()
    }
}

fun stateToJson(s: AppState): JSONObject {
    val o = JSONObject()
    o.put("identity", ".EO")
    o.put("masterVersion", ".EO.V0.0")
    o.put("level", s.level)
    o.put("createdAt", s.createdAt)

    val cats = JSONArray()
    for (c in s.categories) {
        cats.put(JSONObject().put("code", c.code).put("name", c.name).put("archived", c.archived))
    }
    o.put("categories", cats)

    val projs = JSONArray()
    for (p in s.projects) {
        projs.put(
            JSONObject().put("id", p.id).put("categoryCode", p.categoryCode).put("name", p.name)
                .put("major", p.major).put("minor", p.minor).put("createdAt", p.createdAt)
        )
    }
    o.put("projects", projs)

    val recs = JSONArray()
    for (r in s.records) {
        recs.put(
            JSONObject().put("id", r.id).put("projectId", r.projectId).put("title", r.title)
                .put("reason", r.reason).put("type", r.type).put("prevVersion", r.prevVersion)
                .put("newVersion", r.newVersion).put("completedAt", r.completedAt)
                .put("awardedAt", r.awardedAt).put("elapsedMs", r.elapsedMs)
        )
    }
    o.put("records", recs)

    val aud = JSONArray()
    for (a in s.audit) {
        aud.put(JSONObject().put("time", a.time).put("text", a.text))
    }
    o.put("audit", aud)
    return o
}

fun parseState(text: String): AppState {
    val o = JSONObject(text)
    val ca = o.getJSONArray("categories")
    val pa = o.getJSONArray("projects")
    val ra = o.getJSONArray("records")
    val aa = o.getJSONArray("audit")

    val cats = (0 until ca.length()).map { i ->
        val c = ca.getJSONObject(i)
        Category(c.getString("code"), c.getString("name"), c.optBoolean("archived", false))
    }
    val projs = (0 until pa.length()).map { i ->
        val p = pa.getJSONObject(i)
        Project(
            p.getString("id"), p.getString("categoryCode"), p.getString("name"),
            p.getInt("major"), p.getInt("minor"), p.getLong("createdAt")
        )
    }
    val recs = (0 until ra.length()).map { i ->
        val r = ra.getJSONObject(i)
        VersionRecord(
            r.getInt("id"), r.getString("projectId"), r.getString("title"), r.getString("reason"),
            r.getString("type"), r.getString("prevVersion"), r.getString("newVersion"),
            r.getLong("completedAt"), r.getLong("awardedAt"), r.getLong("elapsedMs")
        )
    }
    val auds = (0 until aa.length()).map { i ->
        val a = aa.getJSONObject(i)
        AuditEntry(a.getLong("time"), a.getString("text"))
    }
    return AppState(
        level = o.optInt("level", 0),
        createdAt = o.getLong("createdAt"),
        categories = cats,
        projects = projs,
        records = recs,
        audit = auds
    )
}
