package com.eo.versionos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = Store(applicationContext)
        setContent { EoApp(store) }
    }
}

@Composable
fun EoApp(store: Store) {
    var state by remember { mutableStateOf(store.load()) }
    var tab by remember { mutableStateOf(0) }
    var openProject by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("") }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }

    fun commit(op: Op): Boolean {
        val err = op.error
        if (err != null) {
            message = err
            return false
        }
        return try {
            store.save(op.state)
            state = op.state
            message = ""
            true
        } catch (e: Exception) {
            message = "Save nahi hua: " + e.message
            false
        }
    }

    val colors = darkColorScheme(
        primary = Color(0xFF10B981),
        onPrimary = Color.Black,
        secondary = Color(0xFF3B82F6),
        background = Color(0xFF050505),
        onBackground = Color.White,
        surface = Color(0xFF121212),
        onSurface = Color.White,
        surfaceVariant = Color(0xFF1C1C1C),
        onSurfaceVariant = Color(0xFFCCCCCC)
    )

    MaterialTheme(colorScheme = colors) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF0D0D0D)) {
                    val names = listOf("Home", "Category", "Project", "History", "More")
                    val icons = listOf("🏠", "🗂", "📁", "🕘", "⋯")
                    names.forEachIndexed { i, n ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = {
                                tab = i
                                openProject = null
                            },
                            icon = { Text(icons[i]) },
                            label = { Text(n, fontSize = 11.sp) }
                        )
                    }
                }
            }
        ) { pad ->
            Column(
                modifier = Modifier
                    .padding(pad)
                    .fillMaxSize()
                    .padding(horizontal = 14.dp)
            ) {
                Spacer(Modifier.height(8.dp))
                if (message.isNotEmpty()) {
                    Text(message, color = Color(0xFFFF6B6B))
                }
                when (tab) {
                    0 -> DashboardScreen(state, now)
                    1 -> CategoryScreen(
                        state,
                        { c, n -> commit(addCategory(state, c, n)) },
                        { commit(seedCategories(state)) }
                    )
                    2 -> {
                        val pid = openProject
                        if (pid == null) {
                            ProjectListScreen(
                                state,
                                { openProject = it },
                                { c, n -> commit(addProject(state, c, n)) }
                            )
                        } else {
                            ProjectDetailScreen(
                                state, pid, now,
                                { openProject = null },
                                { t, r, major -> commit(addAchievement(state, pid, t, r, major)) }
                            )
                        }
                    }
                    3 -> HistoryScreen(state)
                    else -> MoreScreen(state)
                }
            }
        }
    }
}

@Composable
fun InfoCard(title: String, body: String = "") {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, color = MaterialTheme.colorScheme.primary, fontSize = 15.sp)
            if (body.isNotEmpty()) Text(body, fontSize = 13.sp)
        }
    }
}

@Composable
fun RecordCard(r: VersionRecord) {
    InfoCard(
        r.projectId + "  " + r.prevVersion + " -> " + r.newVersion + "  [" + r.type + "]",
        r.title + "\nReason: " + r.reason +
            "\nAwarded: " + fmtTime(r.awardedAt) +
            "\nSince previous: " + fmtElapsed(r.elapsedMs)
    )
}

@Composable
fun DashboardScreen(s: AppState, now: Long) {
    val ups = s.records.filter { it.type != "REGISTER" }
    val last = ups.maxByOrNull { it.awardedAt }
    val levelName = LEVELS.getOrElse(s.level) { "?" }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(".EO", color = MaterialTheme.colorScheme.primary, fontSize = 44.sp)
        Text(".EO.V0.0  |  " + "%02d".format(s.level) + " - " + levelName, fontSize = 16.sp)
        InfoCard("Registered", fmtTime(s.createdAt))
        InfoCard(
            "Totals",
            "Categories: ${s.categories.size}\nProjects: ${s.projects.size}\nUpgrades: ${ups.size}"
        )
        if (last != null) {
            InfoCard(
                "Latest upgrade",
                last.projectId + "  " + last.prevVersion + " -> " + last.newVersion +
                    "\n" + fmtTime(last.awardedAt) +
                    "\nSince: " + fmtElapsed(now - last.awardedAt)
            )
        } else {
            InfoCard("Latest upgrade", "Abhi koi upgrade nahi")
        }
        InfoCard(
            "Active projects",
            if (s.projects.isEmpty()) "Koi project nahi"
            else s.projects.joinToString("\n") { it.name + "  -  " + it.versionId() }
        )
    }
}

@Composable
fun CategoryScreen(s: AppState, onAdd: (String, String) -> Boolean, onSeed: () -> Boolean) {
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Categories", fontSize = 22.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = code, onValueChange = { code = it },
                label = { Text("Code") }, singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Naam") }, singleLine = true,
                modifier = Modifier.weight(2f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (onAdd(code, name)) {
                    code = ""
                    name = ""
                }
            }) { Text("Add") }
            Button(onClick = { onSeed() }) { Text("Suggested add") }
        }
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(s.categories) { c ->
                InfoCard(
                    c.code + "  -  " + c.name,
                    s.projects.count { it.categoryCode == c.code }.toString() + " projects"
                )
            }
        }
    }
}

@Composable
fun ProjectListScreen(s: AppState, onOpen: (String) -> Unit, onAdd: (String, String) -> Boolean) {
    var cat by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Projects", fontSize = 22.sp)
        if (s.categories.isEmpty()) {
            Text("Pehle Category tab mein category banayein")
        } else {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                s.categories.forEach { c ->
                    FilterChip(
                        selected = cat == c.code,
                        onClick = { cat = c.code },
                        label = { Text(c.code) }
                    )
                }
            }
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Project ka naam") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = { if (onAdd(cat, name)) name = "" }) { Text("Project banayein") }
        }
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(s.projects) { p ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onOpen(p.id) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(p.id + "  " + p.name, fontSize = 16.sp)
                        Text(p.versionId(), color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
fun ProjectDetailScreen(
    s: AppState,
    projectId: String,
    now: Long,
    onBack: () -> Unit,
    onAdd: (String, String, Boolean) -> Boolean
) {
    val p = s.projects.find { it.id == projectId }
    var title by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TextButton(onClick = onBack) { Text("< Wapas") }
        if (p == null) {
            Text("Project nahi mila")
        } else {
            val mine = s.records.filter { it.projectId == p.id }
            val last = mine.maxByOrNull { it.awardedAt }
            Text(p.name, fontSize = 22.sp)
            Text(p.versionId(), color = MaterialTheme.colorScheme.primary, fontSize = 20.sp)
            if (last != null) {
                InfoCard(
                    "Last upgrade ko guzra waqt",
                    fmtElapsed(now - last.awardedAt) + "\n" + fmtTime(last.awardedAt)
                )
            }
            InfoCard("Upgrade intervals", intervalStats(s, p.id))
            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text("Achievement title") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = reason, onValueChange = { reason = it },
                label = { Text("Reason (kyun upgrade?)") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    if (onAdd(title, reason, false)) {
                        title = ""
                        reason = ""
                    }
                }) { Text("Minor upgrade") }
                Button(onClick = {
                    if (onAdd(title, reason, true)) {
                        title = ""
                        reason = ""
                    }
                }) { Text("Major upgrade") }
            }
            Text("History", fontSize = 18.sp)
            mine.sortedByDescending { it.id }.forEach { RecordCard(it) }
        }
    }
}

@Composable
fun HistoryScreen(s: AppState) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Complete History", fontSize = 22.sp)
        if (s.records.isEmpty()) Text("Abhi history khali hai")
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(s.records.sortedByDescending { it.id }) { r -> RecordCard(r) }
        }
    }
}

@Composable
fun MoreScreen(s: AppState) {
    val clip = LocalClipboardManager.current
    var info by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Export aur Audit", fontSize = 22.sp)
        Button(onClick = {
            clip.setText(AnnotatedString(exportText(s)))
            info = "Text history copy ho gayi"
        }) { Text("Copy complete history (text)") }
        Button(onClick = {
            clip.setText(AnnotatedString(stateToJson(s).toString(2)))
            info = "JSON backup copy ho gaya"
        }) { Text("Copy JSON backup") }
        if (info.isNotEmpty()) Text(info, color = MaterialTheme.colorScheme.primary)
        Text("Audit log", fontSize = 18.sp)
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(s.audit.reversed()) { a -> InfoCard(fmtTime(a.time), a.text) }
        }
    }
}
