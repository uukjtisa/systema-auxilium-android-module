package com.niccc2007.sysaux

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.niccc2007.sysaux.ui.theme.SystemaAuxiliumAndroidModuleTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket

// ─── Data Models ─────────────────────────────────────────────────────────────

data class ChatMessage(
    val text: String,
    val role: String,        // "user" | "ai" | "system" | "thinking"
    val timestamp: String = ""
)

// ─── Colours ─────────────────────────────────────────────────────────────────

private object C {
    val Bg             = Color(0xFF0A0A0A)
    val Surface        = Color(0xFF111111)
    val InputBg        = Color(0xFF1A1A1A)
    val SentBubble     = Color(0xFF1E1E1E)
    val SentBubbleBorder = Color(0xFF3A3A3A)
    val ReceivedBubble = Color(0xFF141414)
    val ReceivedBubbleBorder = Color(0xFF272727)
    val SystemBubble   = Color(0xFF111111)
    val ThinkingBubble = Color(0xFF141414)
    val Text           = Color(0xFFEEEEEE)
    val TextDim        = Color(0xFFAAAAAA)
    val SubText        = Color(0xFF555555)
    val Online         = Color(0xFF4CD780)
    val Offline        = Color(0xFFFF5C5C)
    val Accent         = Color(0xFFDDDDDD)
    val AccentEnd      = Color(0xFF888888)
    val DrawerBg       = Color(0xFF0D0D0D)
    val DrawerBorder   = Color(0xFF222222)
    val InputBorder    = Color(0xFF2A2A2A)
    val SendBtn        = Color(0xFFE8E8E8)
}

// ─── Connection State ─────────────────────────────────────────────────────────

enum class ConnState { DISCONNECTED, CONNECTING, CONNECTED }

// ─── Activity ────────────────────────────────────────────────────────────────

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SystemaAuxiliumAndroidModuleTheme(darkTheme = true, dynamicColor = false) {
                AppRoot()
            }
        }
    }
}

// ─── Root Composable ─────────────────────────────────────────────────────────

@Composable
fun AppRoot() {
    // ── State ──────────────────────────────────────────────────────────────
    val context       = LocalContext.current
    val prefs         = remember { context.getSharedPreferences("sysaux_prefs", Context.MODE_PRIVATE) }
    val messages      = remember { mutableStateListOf<ChatMessage>() }
    var inputText     by remember { mutableStateOf("") }
    var drawerOpen    by remember { mutableStateOf(false) }
    var hostInput     by remember { mutableStateOf(prefs.getString("host_input", "") ?: "") }
    var connState     by remember { mutableStateOf(ConnState.DISCONNECTED) }
    var connLabel     by remember { mutableStateOf("") }           // shown in header when connected

    val listState     = rememberLazyListState()
    val scope         = rememberCoroutineScope()

    // ── Socket references held in state so we can write from input bar ─────
    var socketWriter  by remember { mutableStateOf<PrintWriter?>(null) }

    // ── Feature panel state ────────────────────────────────────────────────
    var sessionsOpen      by remember { mutableStateOf(false) }
    var skillsOpen        by remember { mutableStateOf(false) }
    var instructionsOpen  by remember { mutableStateOf(false) }
    var namesOpen         by remember { mutableStateOf(false) }
    var sessionsList      by remember { mutableStateOf<List<Map<String, String>>>(emptyList()) }
    var sessionsPage      by remember { mutableStateOf(0) }
    var sessionsPages     by remember { mutableStateOf(1) }
    var sessionsCurrentId by remember { mutableStateOf("") }
    var skillsLoaded      by remember { mutableStateOf<List<String>>(emptyList()) }
    var skillsAvailable   by remember { mutableStateOf<List<String>>(emptyList()) }
    var instructionsText  by remember { mutableStateOf("") }
    var instructionsEdit  by remember { mutableStateOf("") }
    var userName          by remember { mutableStateOf("") }
    var asstName          by remember { mutableStateOf("") }
    var userNameEdit      by remember { mutableStateOf("") }
    var asstNameEdit      by remember { mutableStateOf("") }
    var memoriesOpen             by remember { mutableStateOf(false) }
    var memoriesList             by remember { mutableStateOf<List<Map<String, String>>>(emptyList()) }
    var codeApprovalOpen         by remember { mutableStateOf(false) }
    var codeApprovalCode         by remember { mutableStateOf("") }
    var codeApprovalType         by remember { mutableStateOf("") }
    var manualResponseOpen       by remember { mutableStateOf(false) }
    var manualResponseContext    by remember { mutableStateOf("") }
    var manualResponseWorkMode   by remember { mutableStateOf(false) }
    var manualResponseWorkOutput by remember { mutableStateOf("") }
    var manualResponseText       by remember { mutableStateOf("") }

    // ── Helper: send JSON to PC ────────────────────────────────────────────
    fun sendToPC(cmd: Map<String, Any>) {
        val writer = socketWriter ?: return
        scope.launch(Dispatchers.IO) {
            try {
                val json = JSONObject(cmd).toString()
                writer.println(json)
            } catch (_: Exception) {}
        }
    }

    // ── Helper: handle a command received from PC ──────────────────────────
    fun handlePCCommand(obj: JSONObject) {
        when (obj.optString("cmd")) {
            "add_user" -> {
                val text = obj.optString("text")
                if (text.isNotBlank()) messages.add(ChatMessage(text, "user"))
            }
            "add_ai" -> {
                val text = obj.optString("text")
                // Remove any leftover "thinking" bubble when AI reply arrives
                messages.removeAll { it.role == "thinking" }
                if (text.isNotBlank()) messages.add(ChatMessage(text, "ai"))
            }
            "add_system" -> {
                val text = obj.optString("text")
                if (text.isNotBlank()) messages.add(ChatMessage(text, "system"))
            }
            "show_thinking" -> {
                if (messages.none { it.role == "thinking" })
                    messages.add(ChatMessage("Working…", "thinking"))
            }
            "hide_thinking" -> {
                messages.removeAll { it.role == "thinking" }
            }
            "clear" -> messages.clear()
            "quit"  -> {
                socketWriter = null
                connState = ConnState.DISCONNECTED
                connLabel = ""
                messages.add(ChatMessage("PC disconnected the session.", "system"))
            }
            "sessions_data" -> {
                val arr  = obj.optJSONArray("sessions")
                val list = mutableListOf<Map<String, String>>()
                if (arr != null) for (i in 0 until arr.length()) {
                    val s = arr.optJSONObject(i) ?: continue
                    list.add(mapOf("id" to s.optString("id"), "name" to s.optString("name")))
                }
                sessionsList      = list
                sessionsPage      = obj.optInt("page", 0)
                sessionsPages     = obj.optInt("pages", 1)
                sessionsCurrentId = obj.optString("current_session_id", "")
            }
            "skills_data" -> {
                val la = obj.optJSONArray("loaded")
                val aa = obj.optJSONArray("available")
                skillsLoaded    = (0 until (la?.length() ?: 0)).map { la!!.optString(it) }
                skillsAvailable = (0 until (aa?.length() ?: 0)).map { aa!!.optString(it) }
            }
            "instructions_data" -> {
                instructionsText = obj.optString("text", "")
                instructionsEdit = instructionsText
            }
            "memories_data" -> {
                val arr = obj.optJSONArray("memories")
                val list = mutableListOf<Map<String, String>>()
                if (arr != null) for (i in 0 until arr.length()) {
                    val m = arr.optJSONObject(i) ?: continue
                    list.add(mapOf(
                        "id"         to m.optString("id"),
                        "text"       to m.optString("text"),
                        "created_at" to m.optString("created_at")
                    ))
                }
                memoriesList = list
                memoriesOpen = true
            }
            "show_code_approval" -> {
                codeApprovalCode = obj.optString("code", "")
                codeApprovalType = obj.optString("execution_type", "")
                codeApprovalOpen = true
            }
            "dismiss_code_approval" -> {
                codeApprovalOpen = false
            }
            "show_manual_response" -> {
                manualResponseContext    = obj.optString("context", "")
                manualResponseWorkMode  = obj.optBoolean("work_mode", false)
                manualResponseWorkOutput = obj.optString("work_output", "")
                manualResponseText      = ""
                manualResponseOpen      = true
            }
            "dismiss_manual_response" -> {
                manualResponseOpen = false
            }
            "names_data" -> {
                userName     = obj.optString("user_name", "")
                asstName     = obj.optString("asst_name", "")
                userNameEdit = userName
                asstNameEdit = asstName
            }
        }
    }

    // ── Connect logic ──────────────────────────────────────────────────────
    fun connect() {
        val raw   = hostInput.trim()
        prefs.edit().putString("host_input", raw).apply()
        val parts = raw.split(":")
        val host  = parts.getOrElse(0) { "" }
        val port  = parts.getOrElse(1) { "2873" }.toIntOrNull() ?: 2873

        if (host.isBlank()) return

        connState = ConnState.CONNECTING

        scope.launch(Dispatchers.IO) {
            try {
                val socket = Socket(host, port)
                val reader = BufferedReader(InputStreamReader(socket.inputStream))
                val writer = PrintWriter(socket.outputStream, true)

                withContext(Dispatchers.Main) {
                    socketWriter = writer
                    connState    = ConnState.CONNECTED
                    connLabel    = "$host:$port"
                    drawerOpen   = false
                }

                // Read loop — blocks until disconnected
                try {
                    var line: String?
                    while (socket.isConnected) {
                        line = reader.readLine() ?: break
                        val trimmed = line.trim()
                        if (trimmed.isEmpty()) continue
                        val obj = JSONObject(trimmed)
                        withContext(Dispatchers.Main) { handlePCCommand(obj) }
                    }
                } catch (_: Exception) {}

                withContext(Dispatchers.Main) {
                    socketWriter = null
                    connState    = ConnState.DISCONNECTED
                    connLabel    = ""
                    messages.add(ChatMessage("Connection lost.", "system"))
                }
                try { socket.close() } catch (_: Exception) {}

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    connState = ConnState.DISCONNECTED
                    messages.add(ChatMessage("Could not connect: ${e.message}", "system"))
                }
            }
        }
    }

    // ── Disconnect logic ───────────────────────────────────────────────────
    fun disconnect() {
        sendToPC(mapOf("cmd" to "closed"))
        socketWriter = null
        connState = ConnState.DISCONNECTED
        connLabel = ""
    }

    // ── Send chat message ──────────────────────────────────────────────────
    fun sendMessage() {
        val text = inputText.trim()
        if (text.isBlank()) return
        inputText = ""
        sendToPC(mapOf("cmd" to "send_message", "text" to text))
        scope.launch { listState.animateScrollToItem(messages.lastIndex) }
    }

    // ── Scroll to bottom on new messages ──────────────────────────────────
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    // ── UI ────────────────────────────────────────────────────────────────
    Box(modifier = Modifier.fillMaxSize().background(C.Bg)) {

        // Main chat column
        Column(modifier = Modifier.fillMaxSize()) {

            // Top bar
            TopBar(
                connState   = connState,
                connLabel   = connLabel,
                onMenuClick = { drawerOpen = true },
                onDisconnect= ::disconnect
            )

            // Message list
            LazyColumn(
                state          = listState,
                modifier       = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { msg -> MessageBubble(msg) }
            }

            // Input bar
            InputBar(
                value     = inputText,
                onChange  = { inputText = it },
                onSend    = ::sendMessage,
                enabled   = connState == ConnState.CONNECTED
            )
        }

        // Scrim behind drawer
        if (drawerOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x88000000))
                    .clickable { drawerOpen = false }
            )
        }

        // Connection drawer overlay
        AnimatedVisibility(
            visible = drawerOpen,
            enter   = slideInHorizontally { -it },
            exit    = slideOutHorizontally { -it }
        ) {
            ConnectionDrawer(
                hostInput          = hostInput,
                onHostChange       = { hostInput = it },
                connState          = connState,
                onConnect          = ::connect,
                onClose            = { drawerOpen = false },
                onOpenSessions     = { sendToPC(mapOf("cmd" to "get_sessions", "page" to 0, "search" to "")); sessionsOpen = true },
                onOpenSkills       = { sendToPC(mapOf("cmd" to "get_skills")); skillsOpen = true },
                onOpenMemory       = { sendToPC(mapOf("cmd" to "open_memory")); messages.add(ChatMessage("Memory Manager opened on PC.", "system")) },
                onOpenInstructions = { instructionsEdit = instructionsText; sendToPC(mapOf("cmd" to "open_instructions")); instructionsOpen = true },
                onOpenNames        = { userNameEdit = userName; asstNameEdit = asstName; sendToPC(mapOf("cmd" to "open_names")); namesOpen = true }
            )
        }

        // ── Feature panel overlays ─────────────────────────────────────────
        if (sessionsOpen) {
            SessionsPanel(
                sessions  = sessionsList,
                page      = sessionsPage,
                pages     = sessionsPages,
                currentId = sessionsCurrentId,
                onLoadSession = { sid -> sendToPC(mapOf("cmd" to "load_session", "session_id" to sid)); sessionsOpen = false },
                onNewSession  = { sendToPC(mapOf("cmd" to "new_session")); sessionsOpen = false },
                onPrevPage    = { sendToPC(mapOf("cmd" to "get_sessions", "page" to maxOf(0, sessionsPage - 1), "search" to "")) },
                onNextPage    = { sendToPC(mapOf("cmd" to "get_sessions", "page" to minOf(sessionsPages - 1, sessionsPage + 1), "search" to "")) },
                onClose       = { sessionsOpen = false }
            )
        }
        if (skillsOpen) {
            SkillsPanel(
                loaded    = skillsLoaded,
                available = skillsAvailable,
                onToggle  = { name, action ->
                    sendToPC(mapOf("cmd" to "toggle_skill", "skill_name" to name, "action" to action))
                    scope.launch { kotlinx.coroutines.delay(500); sendToPC(mapOf("cmd" to "get_skills")) }
                },
                onClose = { skillsOpen = false }
            )
        }
        if (instructionsOpen) {
            InstructionsPanel(
                text         = instructionsEdit,
                onTextChange = { instructionsEdit = it },
                onSave       = { sendToPC(mapOf("cmd" to "set_instructions", "text" to instructionsEdit)); instructionsOpen = false },
                onClose      = { instructionsOpen = false }
            )
        }
        if (namesOpen) {
            NamesPanel(
                userName     = userNameEdit,
                asstName     = asstNameEdit,
                onUserChange = { userNameEdit = it },
                onAsstChange = { asstNameEdit = it },
                onSave       = { sendToPC(mapOf("cmd" to "set_names", "user_name" to userNameEdit, "asst_name" to asstNameEdit)); namesOpen = false },
                onClose      = { namesOpen = false }
            )
        }
        if (memoriesOpen) {
            MemoriesPanel(
                memories = memoriesList,
                onClose  = { memoriesOpen = false }
            )
        }
        if (codeApprovalOpen) {
            CodeApprovalPhonePanel(
                code          = codeApprovalCode,
                executionType = codeApprovalType,
                onApprove = {
                    sendToPC(mapOf("cmd" to "code_approval_result", "approved" to true,  "modified_code" to codeApprovalCode))
                    codeApprovalOpen = false
                },
                onReject  = {
                    sendToPC(mapOf("cmd" to "code_approval_result", "approved" to false, "modified_code" to ""))
                    codeApprovalOpen = false
                }
            )
        }
        if (manualResponseOpen) {
            ManualResponsePhonePanel(
                context      = manualResponseContext,
                workMode     = manualResponseWorkMode,
                workOutput   = manualResponseWorkOutput,
                responseText = manualResponseText,
                onTextChange = { manualResponseText = it },
                onSubmit = {
                    sendToPC(mapOf("cmd" to "manual_response_result", "text" to manualResponseText))
                    manualResponseOpen = false
                },
                onCancel = {
                    sendToPC(mapOf("cmd" to "manual_response_result", "text" to ""))
                    manualResponseOpen = false
                }
            )
        }
    }
}

// ─── Top Bar ─────────────────────────────────────────────────────────────────

@Composable
fun TopBar(
    connState:    ConnState,
    connLabel:    String,
    onMenuClick:  () -> Unit,
    onDisconnect: () -> Unit
) {
    val dotColor = when (connState) {
        ConnState.CONNECTED   -> C.Online
        ConnState.CONNECTING  -> Color(0xFFFFC107)
        ConnState.DISCONNECTED-> C.Offline
    }
    val statusText = when (connState) {
        ConnState.CONNECTED    -> connLabel
        ConnState.CONNECTING   -> "Connecting…"
        ConnState.DISCONNECTED -> "Not connected"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(C.Surface)
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Menu / drawer button
        IconButton(
            onClick  = onMenuClick,
            modifier = Modifier.size(36.dp)
        ) {
            Text("☰", color = C.SubText, fontSize = 20.sp)
        }

        Spacer(modifier = Modifier.width(10.dp))

        // App name + status
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = "Systema Auxilium",
                color      = C.Text,
                fontSize   = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(text = statusText, color = dotColor, fontSize = 11.sp)
            }
        }

        // Disconnect button (only shown when connected)
        if (connState == ConnState.CONNECTED) {
            TextButton(onClick = onDisconnect) {
                Text("Disconnect", color = C.Offline, fontSize = 12.sp)
            }
        }
    }
}

// ─── Connection Drawer ───────────────────────────────────────────────────────

@Composable
fun ConnectionDrawer(
    hostInput:          String,
    onHostChange:       (String) -> Unit,
    connState:          ConnState,
    onConnect:          () -> Unit,
    onClose:            () -> Unit,
    onOpenSessions:     () -> Unit = {},
    onOpenSkills:       () -> Unit = {},
    onOpenMemory:       () -> Unit = {},
    onOpenInstructions: () -> Unit = {},
    onOpenNames:        () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(300.dp)
            .background(C.DrawerBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(20.dp)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text       = "Connect to PC",
                color      = C.Text,
                fontSize   = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier   = Modifier.weight(1f)
            )
            TextButton(onClick = onClose) {
                Text("✕", color = C.SubText, fontSize = 16.sp)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text     = "💡 Must be on the same Wi-Fi network as your PC.",
            color    = C.SubText,
            fontSize = 12.sp,
            fontStyle= FontStyle.Italic
        )

        Spacer(modifier = Modifier.height(20.dp))

        // IP:Port input + Connect button
        Text("Host address and port", color = C.SubText, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(6.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value         = hostInput,
                onValueChange = onHostChange,
                modifier      = Modifier.weight(1f),
                placeholder   = {
                    Text(
                        "192.168.x.x:2873",
                        color    = C.SubText,
                        fontSize = 13.sp
                    )
                },
                singleLine    = true,
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = C.Accent,
                    unfocusedBorderColor = C.DrawerBorder,
                    focusedTextColor     = C.Text,
                    unfocusedTextColor   = C.Text,
                    cursorColor          = C.Accent
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction    = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(onGo = { onConnect() }),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick  = onConnect,
                enabled  = connState != ConnState.CONNECTING,
                shape    = RoundedCornerShape(10.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = C.Accent,
                    disabledContainerColor = C.SubText
                )
            ) {
                if (connState == ConnState.CONNECTING) {
                    CircularProgressIndicator(
                        modifier  = Modifier.size(16.dp),
                        color     = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Connect", color = Color.White, fontSize = 13.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Divider(color = C.DrawerBorder)
        Spacer(modifier = Modifier.height(16.dp))

        // Instructions
        Text(
            text       = "How to get the address:",
            color      = C.Text,
            fontSize   = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text  = "1. Run Systema Auxilium on your PC.\n" +
                    "2. Right-click the floating icon.\n" +
                    "3. Click \"📱 Open Packet\".\n" +
                    "4. The this will show your IP:port — type it above.",
            color = C.SubText,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )

        if (connState == ConnState.CONNECTED) {
            Spacer(modifier = Modifier.height(20.dp))
            Divider(color = C.DrawerBorder)
            Spacer(modifier = Modifier.height(14.dp))
            Text("Features", color = C.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(10.dp))
            val fMod    = Modifier.fillMaxWidth().padding(vertical = 3.dp)
            val fColors = ButtonDefaults.buttonColors(containerColor = C.InputBg)
            val fShape  = RoundedCornerShape(10.dp)
            Button(onClick = { onOpenSessions();     onClose() }, modifier = fMod, colors = fColors, shape = fShape) {
                Text("📁  Sessions",     color = C.Text, fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
            }
            Button(onClick = { onOpenSkills();       onClose() }, modifier = fMod, colors = fColors, shape = fShape) {
                Text("⚡  Skills",       color = C.Text, fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
            }
            Button(onClick = { onOpenMemory();       onClose() }, modifier = fMod, colors = fColors, shape = fShape) {
                Text("🧠  Memory",       color = C.Text, fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
            }
            Button(onClick = { onOpenInstructions(); onClose() }, modifier = fMod, colors = fColors, shape = fShape) {
                Text("📋  Instructions", color = C.Text, fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
            }
            Button(onClick = { onOpenNames();        onClose() }, modifier = fMod, colors = fColors, shape = fShape) {
                Text("✏️  Names",        color = C.Text, fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

// ─── Feature Panels ──────────────────────────────────────────────────────────

@Composable
fun FeaturePanelShell(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(C.Bg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(C.Surface)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Text("✕", color = C.SubText, fontSize = 16.sp)
                }
            }
            content()
        }
    }
}

@Composable
fun SessionsPanel(
    sessions: List<Map<String, String>>,
    page: Int, pages: Int, currentId: String,
    onLoadSession: (String) -> Unit,
    onNewSession: () -> Unit,
    onPrevPage: () -> Unit, onNextPage: () -> Unit,
    onClose: () -> Unit
) {
    FeaturePanelShell("📁  Sessions", onClose) {
        LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (sessions.isEmpty()) {
                item { Text("No sessions found.", color = C.SubText, fontSize = 13.sp, modifier = Modifier.padding(8.dp)) }
            } else {
                items(sessions) { s ->
                    val sid  = s["id"] ?: ""
                    val name = s["name"]?.takeIf { it.isNotBlank() } ?: sid.take(16)
                    val curr = sid == currentId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (curr) C.Accent.copy(alpha = 0.18f) else C.InputBg)
                            .clickable { onLoadSession(sid) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (curr) Text("● ", color = C.Accent, fontSize = 13.sp)
                        Text(name, color = C.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onPrevPage, enabled = page > 0) {
                Text("◀ Prev", color = if (page > 0) C.Accent else C.SubText, fontSize = 13.sp)
            }
            Text("${page + 1} / $pages", color = C.SubText, fontSize = 12.sp)
            TextButton(onClick = onNextPage, enabled = page < pages - 1) {
                Text("Next ▶", color = if (page < pages - 1) C.Accent else C.SubText, fontSize = 13.sp)
            }
        }
        Button(
            onClick = onNewSession,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = C.Online.copy(alpha = 0.8f)),
            shape = RoundedCornerShape(10.dp)
        ) { Text("＋  New Session", color = Color.White) }
    }
}

@Composable
fun SkillsPanel(
    loaded: List<String>,
    available: List<String>,
    onToggle: (String, String) -> Unit,
    onClose: () -> Unit
) {
    FeaturePanelShell("⚡  Skills", onClose) {
        LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (loaded.isEmpty() && available.isEmpty()) {
                item { Text("No skills found.", color = C.SubText, fontSize = 13.sp, modifier = Modifier.padding(8.dp)) }
            }
            if (loaded.isNotEmpty()) {
                item {
                    Text("  Loaded — tap to unload", color = C.SubText, fontSize = 11.sp,
                        fontStyle = FontStyle.Italic, modifier = Modifier.padding(vertical = 6.dp))
                }
                items(loaded) { name ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth().padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1E1A2E))
                            .clickable { onToggle(name, "unload") }
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) { Text("⚡ $name", color = Color(0xFFF0C040), fontSize = 14.sp) }
                }
            }
            val unloaded = available.filter { it !in loaded }
            if (unloaded.isNotEmpty()) {
                item {
                    Text("  Available — tap to load", color = C.SubText, fontSize = 11.sp,
                        fontStyle = FontStyle.Italic, modifier = Modifier.padding(vertical = 6.dp))
                }
                items(unloaded) { name ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth().padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(C.InputBg)
                            .clickable { onToggle(name, "load") }
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) { Text("·  $name", color = C.SubText, fontSize = 14.sp) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstructionsPanel(
    text: String, onTextChange: (String) -> Unit,
    onSave: () -> Unit, onClose: () -> Unit
) {
    FeaturePanelShell("📋  Instructions", onClose) {
        Text("Added to every prompt:", color = C.SubText, fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        TextField(
            value = text, onValueChange = onTextChange,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor   = C.InputBg,
                unfocusedContainerColor = C.InputBg,
                focusedTextColor        = C.Text,
                unfocusedTextColor      = C.Text,
                focusedIndicatorColor   = C.Accent,
                unfocusedIndicatorColor = C.DrawerBorder,
                cursorColor             = C.Accent
            ),
            shape = RoundedCornerShape(10.dp)
        )
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onSave, modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = C.Accent),
                shape = RoundedCornerShape(10.dp)
            ) { Text("💾  Save", color = Color.White) }
            OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
                Text("Cancel", color = C.SubText)
            }
        }
    }
}

@Composable
fun NamesPanel(
    userName: String, asstName: String,
    onUserChange: (String) -> Unit, onAsstChange: (String) -> Unit,
    onSave: () -> Unit, onClose: () -> Unit
) {
    FeaturePanelShell("✏️  Names", onClose) {
        Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("Your name:", color = C.SubText, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = userName, onValueChange = onUserChange,
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = C.Accent, unfocusedBorderColor = C.DrawerBorder,
                    focusedTextColor = C.Text, unfocusedTextColor = C.Text, cursorColor = C.Accent),
                shape = RoundedCornerShape(10.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("Assistant name:", color = C.SubText, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = asstName, onValueChange = onAsstChange,
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = C.Accent, unfocusedBorderColor = C.DrawerBorder,
                    focusedTextColor = C.Text, unfocusedTextColor = C.Text, cursorColor = C.Accent),
                shape = RoundedCornerShape(10.dp)
            )
        }
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onSave, modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = C.Accent),
                shape = RoundedCornerShape(10.dp)
            ) { Text("💾  Save", color = Color.White) }
            OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
                Text("Cancel", color = C.SubText)
            }
        }
    }
}

// ─── Memories Panel ───────────────────────────────────────────────────────────

@Composable
fun MemoriesPanel(memories: List<Map<String, String>>, onClose: () -> Unit) {
    FeaturePanelShell("🧠  Memories", onClose) {
        if (memories.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No memories stored yet.", color = C.SubText, fontSize = 13.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(memories) { mem ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(C.InputBg)
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text     = mem["text"] ?: "",
                            color    = C.Text,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text     = mem["created_at"]?.take(16)?.replace("T", "  ") ?: "",
                            color    = C.SubText,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

// ─── Code Approval Phone Panel ────────────────────────────────────────────────

@Composable
fun CodeApprovalPhonePanel(
    code: String,
    executionType: String,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(C.Bg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(C.Surface)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "⚠️  Code Approval",
                    color = C.Text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }

            // Execution type badge
            val badgeText = if (executionType == "work_environment") "Work Environment" else "Direct Execution"
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF1E1A2E))
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Text(badgeText, color = Color(0xFFF0C040), fontSize = 12.sp)
            }

            // Warning
            Text(
                "Review the code before allowing execution:",
                color = C.SubText,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            // Code display
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF0D1117))
            ) {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(12.dp)
                ) {
                    item {
                        Text(
                            text = code,
                            color = Color(0xFFE6EDF3),
                            fontSize = 12.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            // Safety warning
            Text(
                "⚠️ Only approve code you understand and trust",
                color = Color(0xCCF28B82),
                fontSize = 11.sp,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            // Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onReject,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x33F28B82)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("❌  Reject", color = Color(0xFFF28B82), fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = onApprove,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x3358A6FF)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("✅  Approve", color = Color(0xFF58A6FF), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ─── Manual Response Phone Panel ──────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualResponsePhonePanel(
    context: String,
    workMode: Boolean,
    workOutput: String,
    responseText: String,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(C.Bg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(C.Surface)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (workMode) "📋  System Message" else "✏️  Manual Response",
                    color = C.Text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onCancel) {
                    Text("Cancel", color = C.SubText, fontSize = 13.sp)
                }
            }

            // Context strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(C.InputBg)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = context.take(200) + if (context.length > 200) "…" else "",
                    color = C.TextDim,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }

            // System output panel (work mode only)
            if (workMode && workOutput.isNotBlank()) {
                Text(
                    "System output:",
                    color = C.SubText,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 160.dp)
                        .padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0D1117))
                        .padding(10.dp)
                ) {
                    androidx.compose.foundation.lazy.LazyColumn {
                        item {
                            Text(
                                text = workOutput,
                                color = Color(0xFFE6EDF3),
                                fontSize = 11.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Response input
            Text(
                "Your response:",
                color = C.SubText,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            TextField(
                value = responseText,
                onValueChange = onTextChange,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                placeholder = { Text("Type the AI's response here…", color = C.SubText, fontSize = 13.sp) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor   = C.InputBg,
                    unfocusedContainerColor = C.InputBg,
                    focusedTextColor        = C.Text,
                    unfocusedTextColor      = C.Text,
                    focusedIndicatorColor   = C.Accent,
                    unfocusedIndicatorColor = C.DrawerBorder,
                    cursorColor             = C.Accent
                ),
                shape = RoundedCornerShape(10.dp)
            )

            // Submit button
            Button(
                onClick  = onSubmit,
                enabled  = responseText.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor         = C.Accent,
                    disabledContainerColor = C.SubText
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Submit Response", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ─── Message Bubble ──────────────────────────────────────────────────────────

@Composable
fun MessageBubble(message: ChatMessage) {
    when (message.role) {
        "user"     -> UserBubble(message.text)
        "ai"       -> AIBubble(message.text)
        "system"   -> SystemBubble(message.text)
        "thinking" -> ThinkingBubble()
    }
}

@Composable
fun UserBubble(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 5.dp))
                    .background(C.SentBubble)
                    .then(
                        Modifier.then(
                            androidx.compose.ui.Modifier
                        )
                    )
                    .padding(1.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 5.dp))
                        .background(C.SentBubble)
                        .padding(horizontal = 16.dp, vertical = 11.dp)
                ) {
                    Text(
                        text       = parseMarkdown(text, C.Text),
                        fontSize   = 15.sp,
                        lineHeight = 23.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("You", color = C.SubText, fontSize = 10.sp, letterSpacing = 0.5.sp)
        }
    }
}

@Composable
fun AIBubble(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(end = 32.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        // Small avatar dot
        Box(
            modifier = Modifier
                .padding(top = 4.dp, end = 10.dp)
                .size(28.dp)
                .clip(CircleShape)
                .background(Color(0xFF1E1E1E)),
            contentAlignment = Alignment.Center
        ) {
            Text("✦", color = Color(0xFF666666), fontSize = 12.sp)
        }

        Column(horizontalAlignment = Alignment.Start) {
            Surface(
                shape  = RoundedCornerShape(topStart = 5.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp),
                color  = C.ReceivedBubble,
                border = androidx.compose.foundation.BorderStroke(0.5.dp, C.ReceivedBubbleBorder),
                tonalElevation = 0.dp
            ) {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        text       = parseMarkdown(text, C.Text),
                        fontSize   = 15.sp,
                        lineHeight = 23.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Assistant", color = C.SubText, fontSize = 10.sp, letterSpacing = 0.5.sp)
        }
    }
}

@Composable
fun SystemBubble(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF161616))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(C.SubText)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text      = text,
                color     = Color(0xFF666666),
                fontSize  = 11.sp,
                letterSpacing = 0.3.sp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(C.SubText)
            )
        }
    }
}

@Composable
fun ThinkingBubble() {
    val infiniteTransition = rememberInfiniteTransition(label = "thinking")

    // Animate 3 dots independently with staggered delay feel
    val alpha1 by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "d1"
    )
    val alpha2 by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, 200, FastOutSlowInEasing), RepeatMode.Reverse),
        label = "d2"
    )
    val alpha3 by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, 400, FastOutSlowInEasing), RepeatMode.Reverse),
        label = "d3"
    )

    Row(
        modifier = Modifier.fillMaxWidth().padding(end = 32.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 4.dp, end = 10.dp)
                .size(28.dp)
                .clip(CircleShape)
                .background(Color(0xFF1E1E1E)),
            contentAlignment = Alignment.Center
        ) {
            Text("✦", color = Color(0xFF666666), fontSize = 12.sp)
        }

        Surface(
            shape  = RoundedCornerShape(topStart = 5.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp),
            color  = C.ThinkingBubble,
            border = androidx.compose.foundation.BorderStroke(0.5.dp, C.ReceivedBubbleBorder)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(alpha1, alpha2, alpha3).forEach { a ->
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFAAAAAA).copy(alpha = a))
                    )
                }
            }
        }
    }
}

// ─── Markdown Parser ─────────────────────────────────────────────────────────

fun parseMarkdown(text: String, baseColor: Color): AnnotatedString = buildAnnotatedString {
    val lines = text.split("\n")
    lines.forEachIndexed { lineIdx, rawLine ->
        var line = rawLine

        // ── Headings: # H1  ## H2  ### H3 ────────────────────────────────
        val headingMatch = Regex("""^(#{1,3})\s+(.+)$""").find(line.trim())
        if (headingMatch != null) {
            val level = headingMatch.groupValues[1].length
            val headingText = headingMatch.groupValues[2]
            val (size, weight) = when (level) {
                1    -> Pair(20f, FontWeight.ExtraBold)
                2    -> Pair(17f, FontWeight.Bold)
                else -> Pair(15f, FontWeight.SemiBold)
            }
            withStyle(SpanStyle(color = baseColor, fontWeight = weight, fontSize = size.sp)) {
                append(headingText)
            }
            if (lineIdx < lines.lastIndex) append("\n")
            return@forEachIndexed
        }

        // ── Bullet points ─────────────────────────────────────────────────
        val isBullet = line.trimStart().let { it.startsWith("- ") || it.startsWith("* ") }
        if (isBullet) {
            withStyle(SpanStyle(color = baseColor)) { append("  • ") }
            line = line.trimStart().removePrefix("- ").removePrefix("* ")
        }

        // ── Inline styles: ***bold italic***  **bold**  `code`  *italic* ──
        val pattern = Regex("""\*\*\*(.+?)\*\*\*|\*\*(.+?)\*\*|`(.+?)`|\*(.+?)\*""")
        var last = 0
        for (match in pattern.findAll(line)) {
            withStyle(SpanStyle(color = baseColor)) { append(line.substring(last, match.range.first)) }
            when {
                match.value.startsWith("***") -> withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, color = baseColor)) { append(match.groupValues[1]) }
                match.value.startsWith("**")  -> withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = baseColor)) { append(match.groupValues[2]) }
                match.value.startsWith("`")   -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = Color(0xFFFFC107), background = Color(0x22FFFFFF))) { append(match.groupValues[3]) }
                else                          -> withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = baseColor)) { append(match.groupValues[4]) }
            }
            last = match.range.last + 1
        }
        withStyle(SpanStyle(color = baseColor)) { append(line.substring(last)) }
        if (lineIdx < lines.lastIndex) append("\n")
    }
}

// ─── Input Bar ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InputBar(
    value:   String,
    onChange:(String) -> Unit,
    onSend:  () -> Unit,
    enabled: Boolean
) {
    val hasText = value.isNotBlank()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(C.Bg)
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(C.InputBg)
                .then(
                    Modifier.padding(0.dp)
                ),
            verticalAlignment = Alignment.Bottom
        ) {
            TextField(
                value         = value,
                onValueChange = onChange,
                enabled       = enabled,
                modifier      = Modifier
                    .weight(1f)
                    .padding(start = 6.dp),
                placeholder   = {
                    Text(
                        text     = if (enabled) "Message…" else "Connect to PC to chat",
                        color    = C.SubText,
                        fontSize = 15.sp
                    )
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor   = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor  = Color.Transparent,
                    focusedTextColor        = C.Text,
                    unfocusedTextColor      = C.Text,
                    disabledTextColor       = C.SubText,
                    focusedIndicatorColor   = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor  = Color.Transparent,
                    cursorColor             = C.Text
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (hasText) onSend() }),
                maxLines        = 6,
                textStyle       = androidx.compose.ui.text.TextStyle(
                    fontSize   = 15.sp,
                    lineHeight = 22.sp
                )
            )

            // Send button — shows only when there's text
            AnimatedVisibility(
                visible = hasText && enabled,
                enter   = scaleIn(tween(150)) + fadeIn(tween(150)),
                exit    = scaleOut(tween(100)) + fadeOut(tween(100))
            ) {
                Box(
                    modifier = Modifier
                        .padding(6.dp)
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(C.SendBtn)
                        .clickable { onSend() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text       = "↑",
                        color      = Color(0xFF0A0A0A),
                        fontSize   = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Placeholder spacer so height doesn't jump when button appears
            if (!hasText || !enabled) {
                Spacer(modifier = Modifier.size(50.dp))
            }
        }
    }
}
