package com.example.n // <- Hover over this line and hit Alt+Enter / Option+Return to fix your directory match!

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import android.telephony.SmsManager

// -------------------- DATA MODELS --------------------

data class Contact(val name: String, val phoneNumber: String)

enum class SpeechAction { NONE, DELETE_CONTACT, CONFIRM_CALL }

enum class PhoneTab { KEYPAD, CONTACTS }

data class GuardianStats(
    var lastInteractionTime: Long,
    var dailyUsageSeconds: Long,
    var lastUsageDate: String,
    var lastActiveAlertSentMs: Long,   // 0 = never sent; hourly resend while active 3+ h
    var lastInactiveAlertSentMs: Long  // 0 = never sent; hourly resend while inactive 3+ h (7AM-9PM)
)

// -------------------- STORAGE ENGINE (JSON) --------------------

fun saveContactsToStorage(context: Context, contacts: List<Contact>) {
    val sharedPreferences = context.getSharedPreferences("nexus_prefs", Context.MODE_PRIVATE)
    val jsonArray = JSONArray()
    contacts.forEach { contact ->
        val jsonObject = JSONObject().apply {
            put("name", contact.name)
            put("phone", contact.phoneNumber)
        }
        jsonArray.put(jsonObject)
    }
    sharedPreferences.edit {
        putString("saved_contacts", jsonArray.toString())
    }
}

fun loadContactsFromStorage(context: Context): List<Contact> {
    val sharedPreferences = context.getSharedPreferences("nexus_prefs", Context.MODE_PRIVATE)
    val jsonString = sharedPreferences.getString("saved_contacts", null) ?: return emptyList()
    val list = mutableListOf<Contact>()
    try {
        val jsonArray = JSONArray(jsonString)
        for (i in 0 until jsonArray.length()) {
            val jsonObject = jsonArray.getJSONObject(i)
            list.add(
                Contact(
                    name = jsonObject.getString("name"),
                    phoneNumber = jsonObject.getString("phone")
                )
            )
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return list
}

fun saveThemeToStorage(context: Context, isDarkMode: Boolean) {
    val sharedPreferences = context.getSharedPreferences("nexus_prefs", Context.MODE_PRIVATE)
    sharedPreferences.edit {
        putBoolean("is_dark_mode", isDarkMode)
    }
}

fun loadThemeFromStorage(context: Context): Boolean {
    val sharedPreferences = context.getSharedPreferences("nexus_prefs", Context.MODE_PRIVATE)
    return sharedPreferences.getBoolean("is_dark_mode", true)
}

fun saveGuardianNumberToStorage(context: Context, number: String) {
    val sharedPreferences = context.getSharedPreferences("nexus_prefs", Context.MODE_PRIVATE)
    sharedPreferences.edit {
        putString("guardian_number", number)
    }
}

fun loadGuardianNumberFromStorage(context: Context): String {
    val sharedPreferences = context.getSharedPreferences("nexus_prefs", Context.MODE_PRIVATE)
    return sharedPreferences.getString("guardian_number", "") ?: ""
}



fun saveGuardianStats(context: Context, stats: GuardianStats) {
    val sharedPreferences = context.getSharedPreferences("nexus_prefs", Context.MODE_PRIVATE)
    sharedPreferences.edit {
        putLong("last_interaction_time", stats.lastInteractionTime)
        putLong("daily_usage_seconds", stats.dailyUsageSeconds)
        putString("last_usage_date", stats.lastUsageDate)
        putLong("last_active_alert_sent_ms", stats.lastActiveAlertSentMs)
        putLong("last_inactive_alert_sent_ms", stats.lastInactiveAlertSentMs)
    }
}

fun loadGuardianStats(context: Context): GuardianStats {
    val sharedPreferences = context.getSharedPreferences("nexus_prefs", Context.MODE_PRIVATE)
    val now = LocalDateTime.now()
    val todayStr = now.format(DateTimeFormatter.ISO_LOCAL_DATE)
    val lastUsageDate = sharedPreferences.getString("last_usage_date", todayStr) ?: todayStr

    val dailyUsageSeconds = if (lastUsageDate != todayStr) 0L
        else sharedPreferences.getLong("daily_usage_seconds", 0L)

    val lastInteractionTime = sharedPreferences.getLong("last_interaction_time", 0L)
    val finalInteractionTime = if (lastInteractionTime == 0L) System.currentTimeMillis() else lastInteractionTime

    return GuardianStats(
        lastInteractionTime = finalInteractionTime,
        dailyUsageSeconds = dailyUsageSeconds,
        lastUsageDate = todayStr,
        lastActiveAlertSentMs = sharedPreferences.getLong("last_active_alert_sent_ms", 0L),
        lastInactiveAlertSentMs = sharedPreferences.getLong("last_inactive_alert_sent_ms", 0L)
    ).also {
        if (lastInteractionTime == 0L) {
            sharedPreferences.edit {
                putLong("last_interaction_time", finalInteractionTime)
            }
        }
    }
}

// -------------------- GLOBAL INTENT UTILS --------------------

fun openWhatsAppSimple(context: Context) {
    val whatsappPackage = "com.whatsapp"
    val launchIntent = context.packageManager.getLaunchIntentForPackage(whatsappPackage)
    if (launchIntent != null) {
        context.startActivity(launchIntent)
    } else {
        Toast.makeText(context, "WhatsApp is not installed on this device", Toast.LENGTH_LONG).show()
    }
}

fun openCameraSimple(context: Context) {
    try {
        val intent = Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "Camera app is not available", Toast.LENGTH_LONG).show()
    }
}

fun openPhotosSimple(context: Context) {
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_GALLERY)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    type = "image/*"
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(context, "Photos app is not available", Toast.LENGTH_LONG).show()
            }
        }
    }
}

@Suppress("SpellCheckingInspection")
fun openMessagingSimple(context: Context) {
    try {
        val defaultSmsPackage = android.provider.Telephony.Sms.getDefaultSmsPackage(context)
        if (defaultSmsPackage != null) {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(defaultSmsPackage)
            if (launchIntent != null) {
                context.startActivity(launchIntent)
                return
            }
        }
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_APP_MESSAGING)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            val fallback = Intent(Intent.ACTION_SENDTO).apply {
                data = "smsto:".toUri()
            }
            context.startActivity(fallback)
        } catch (_: Exception) {
            Toast.makeText(context, "Messaging app is not available", Toast.LENGTH_LONG).show()
        }
    }
}

// -------------------- GUARDIAN CONFIGURATION & SERVICE --------------------
const val GUARDIAN_THRESHOLD_MS: Long = 10800_000L  // 3 hours in ms
const val ALERT_RESEND_INTERVAL_MS: Long = 3600_000L // resend every 1 hour
var currentSessionStartTime: Long = 0L
var lastActiveAlertSentMs: Long = 0L  // in-memory; persisted via GuardianStats

fun formatTimestamp(timestampMs: Long): Pair<String, String> {
    val instant = Instant.ofEpochMilli(timestampMs)
    val dateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
    val timeStr = dateTime.format(DateTimeFormatter.ofPattern("hh:mm a"))
    val dateStr = dateTime.format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))
    return Pair(timeStr, dateStr)
}

fun sendSMS(context: Context, phoneNumber: String, message: String) {
    if (phoneNumber.isEmpty()) return
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
        Toast.makeText(context, "Cannot send alert: SMS permission denied", Toast.LENGTH_LONG).show()
        return
    }
    try {
        val smsManager = context.getSystemService(SmsManager::class.java)
        smsManager.sendTextMessage(phoneNumber, null, message, null, null)
        Toast.makeText(context, "Guardian Alert Sent: $message", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to send SMS alert: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

fun checkAndIncrementGuardianStats(context: Context) {
    val guardianNumber = loadGuardianNumberFromStorage(context)
    if (guardianNumber.isEmpty()) return

    val stats = loadGuardianStats(context)
    val now = System.currentTimeMillis()
    val localNow = LocalDateTime.now()
    val todayStr = localNow.format(DateTimeFormatter.ISO_LOCAL_DATE)

    if (stats.lastUsageDate != todayStr) {
        stats.lastUsageDate = todayStr
        stats.dailyUsageSeconds = 0
    }
    stats.dailyUsageSeconds += 10

    // ---- Active alert (24/7): resend every hour while session >= 3 hours ----
    if (currentSessionStartTime > 0L) {
        val sessionMs = now - currentSessionStartTime
        if (sessionMs >= GUARDIAN_THRESHOLD_MS) {
            val sinceLastAlert = now - lastActiveAlertSentMs
            if (lastActiveAlertSentMs == 0L || sinceLastAlert >= ALERT_RESEND_INTERVAL_MS) {
                lastActiveAlertSentMs = now
                stats.lastActiveAlertSentMs = now
                val (timeStr, dateStr) = formatTimestamp(currentSessionStartTime)
                val hours = sessionMs / 3600_000L
                val message = "This device has been continuously active for ${hours}+ hours starting at $timeStr, $dateStr"
                sendSMS(context, guardianNumber, message)
            }
        }
    }

    // ---- Inactive alert (7 AM–9 PM only): resend every hour while inactive >= 3 hours ----
    val currentHour = localNow.hour
    if (currentHour in 7..20) {
        val inactiveMs = now - stats.lastInteractionTime
        if (inactiveMs >= GUARDIAN_THRESHOLD_MS) {
            val sinceLastAlert = now - stats.lastInactiveAlertSentMs
            if (stats.lastInactiveAlertSentMs == 0L || sinceLastAlert >= ALERT_RESEND_INTERVAL_MS) {
                stats.lastInactiveAlertSentMs = now
                val (timeStr, dateStr) = formatTimestamp(stats.lastInteractionTime)
                val hours = inactiveMs / 3600_000L
                val message = "This device has been continuously inactive for ${hours}+ hours starting at $timeStr, $dateStr"
                sendSMS(context, guardianNumber, message)
            }
        }
    }

    saveGuardianStats(context, stats)
}

fun updateUserInteraction(context: Context) {
    val stats = loadGuardianStats(context)
    stats.lastInteractionTime = System.currentTimeMillis()
    stats.lastInactiveAlertSentMs = 0L  // reset so inactivity clock starts fresh
    lastActiveAlertSentMs = 0L          // reset active alert clock on new interaction
    saveGuardianStats(context, stats)
}

// -------------------- ACTIVITY --------------------

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private var ttsEngine: TextToSpeech? = null

    override fun onUserInteraction() {
        super.onUserInteraction()
        updateUserInteraction(this)
    }

    override fun onResume() {
        super.onResume()
        currentSessionStartTime = System.currentTimeMillis()
        lastActiveAlertSentMs = 0L
        updateUserInteraction(this)
    }

    override fun onPause() {
        super.onPause()
        currentSessionStartTime = 0L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Launchers should never close on Back
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { /* swallow */ }
        })

        ttsEngine = TextToSpeech(this, this)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)

        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

        setContent {
            MainScreen(ttsEngine = ttsEngine)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ttsEngine?.let { engine ->
                engine.language = Locale.forLanguageTag("en-IN")
                engine.setSpeechRate(0.75f)
            }
        }
    }

    override fun onDestroy() {
        ttsEngine?.stop()
        ttsEngine?.shutdown()
        super.onDestroy()
    }
}

// -------------------- NAVIGATION STATE --------------------

sealed class Screen {
    object Home : Screen()
    object Settings : Screen()
    object Phone : Screen()
    object Calendar : Screen()
}

// -------------------- MAIN APP CONTROLLER --------------------

@Composable
fun MainScreen(ttsEngine: TextToSpeech?) {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
    var isDarkMode by remember { mutableStateOf(loadThemeFromStorage(context)) }

    val globalContactsList = remember {
        mutableStateListOf<Contact>().apply {
            addAll(loadContactsFromStorage(context))
        }
    }

    val transitionSpec = tween<Color>(durationMillis = 400)

    val backgroundColor by animateColorAsState(targetValue = if (isDarkMode) Color(0xFF0E0F12) else Color(0xFFF4F5F8), animationSpec = transitionSpec)
    val surfaceColor by animateColorAsState(targetValue = if (isDarkMode) Color(0xFF1D1E24) else Color(0xFFFFFFFF), animationSpec = transitionSpec)
    val textColor by animateColorAsState(targetValue = if (isDarkMode) Color(0xFFFFFFFF) else Color(0xFF111115), animationSpec = transitionSpec)
    val secondaryTextColor by animateColorAsState(targetValue = if (isDarkMode) Color(0xFF9BA0A6) else Color(0xFF63686E), animationSpec = tween(400))
    val brandColor by animateColorAsState(targetValue = if (isDarkMode) Color(0xFF00E5FF) else Color(0xFF006070), animationSpec = transitionSpec)
    val onBrandColor by animateColorAsState(targetValue = if (isDarkMode) Color.Black else Color.White, animationSpec = transitionSpec)

    var currentDateTime by remember { mutableStateOf(LocalDateTime.now()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentDateTime = LocalDateTime.now()
            delay(1.seconds)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(10.seconds)
            checkAndIncrementGuardianStats(context)
        }
    }

    val timeFormatter = remember { DateTimeFormatter.ofPattern("hh:mm a") }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEEE, MMMM d") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(24.dp)
    ) {
        when (currentScreen) {
            is Screen.Home -> {
                HomeScreenContent(
                    currentDateTime = currentDateTime,
                    timeFormatter = timeFormatter,
                    dateFormatter = dateFormatter,
                    surfaceColor = surfaceColor,
                    textColor = textColor,
                    secondaryTextColor = secondaryTextColor,
                    brandColor = brandColor,
                    onBrandColor = onBrandColor,
                    isDarkMode = isDarkMode,
                    onSettingsClick = { currentScreen = Screen.Settings },
                    onPhoneClick = { currentScreen = Screen.Phone },
                    onCalendarClick = { currentScreen = Screen.Calendar },
                    onMessagesClick = { openWhatsAppSimple(context) },
                    onMessagingClick = { openMessagingSimple(context) },
                    onCameraClick = { openCameraSimple(context) },
                    onPhotosClick = { openPhotosSimple(context) }
                )
            }

            is Screen.Settings -> {
                SettingsScreenContent(
                    isDarkMode = isDarkMode,
                    surfaceColor = surfaceColor,
                    textColor = textColor,
                    secondaryTextColor = secondaryTextColor,
                    brandColor = brandColor,
                    onBrandColor = onBrandColor,
                    onDarkModeSelect = {
                        isDarkMode = true
                        saveThemeToStorage(context, true)
                    },
                    onLightModeSelect = {
                        isDarkMode = false
                        saveThemeToStorage(context, false)
                    },
                    onBackClick = { currentScreen = Screen.Home }
                )
            }

            is Screen.Phone -> {
                PhoneScreenContent(
                    contactsList = globalContactsList,
                    ttsEngine = ttsEngine,
                    surfaceColor = surfaceColor,
                    textColor = textColor,
                    secondaryTextColor = secondaryTextColor,
                    brandColor = brandColor,
                    onBrandColor = onBrandColor,
                    isDarkMode = isDarkMode,
                    onBackClick = { currentScreen = Screen.Home }
                )
            }

            is Screen.Calendar -> {
                CalendarScreenContent(
                    surfaceColor = surfaceColor,
                    textColor = textColor,
                    secondaryTextColor = secondaryTextColor,
                    brandColor = brandColor,
                    onBrandColor = onBrandColor,
                    onBackClick = { currentScreen = Screen.Home }
                )
            }
        }
    }
}

// -------------------- HOME MODULE --------------------

@Composable
fun HomeScreenContent(
    currentDateTime: LocalDateTime,
    timeFormatter: DateTimeFormatter,
    dateFormatter: DateTimeFormatter,
    surfaceColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    brandColor: Color,
    onBrandColor: Color,
    isDarkMode: Boolean,
    onSettingsClick: () -> Unit,
    onPhoneClick: () -> Unit,
    onCalendarClick: () -> Unit,
    onMessagesClick: () -> Unit,
    onMessagingClick: () -> Unit,
    onCameraClick: () -> Unit,
    onPhotosClick: () -> Unit
) {
    val context = LocalContext.current
    var hasRequiredPermissions by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        hasRequiredPermissions = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasRequiredPermissions = permissions[Manifest.permission.CALL_PHONE] == true
    }

    val textGradient = remember(brandColor, isDarkMode) {
        Brush.horizontalGradient(colors = listOf(brandColor, if (isDarkMode) Color.White else brandColor))
    }

    val dynamicPhoneBlue by animateColorAsState(targetValue = if (isDarkMode) Color(0xFF0D47A1) else Color(0xFF1976D2), animationSpec = tween(400))
    val dynamicWhatsappGreen by animateColorAsState(targetValue = if (isDarkMode) Color(0xFF1E6F31) else Color(0xFF2E7D32), animationSpec = tween(400))
    val dynamicMessagingOrange by animateColorAsState(targetValue = if (isDarkMode) Color(0xFFB23C00) else Color(0xFFD84315), animationSpec = tween(400))
    val dynamicCameraPurple by animateColorAsState(targetValue = if (isDarkMode) Color(0xFF6A1B9A) else Color(0xFF8E24AA), animationSpec = tween(400))
    val dynamicPhotosPink by animateColorAsState(targetValue = if (isDarkMode) Color(0xFFC2185B) else Color(0xFFE91E63), animationSpec = tween(400))

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 16.dp)) {
            Text(text = "Nexus", fontSize = 48.sp, fontWeight = FontWeight.Black, style = TextStyle(brush = textGradient), letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Box(modifier = Modifier.width(100.dp).height(4.dp).background(brush = textGradient, shape = RoundedCornerShape(2.dp)))
        }

        Card(
            modifier = Modifier.fillMaxWidth().clickable { onCalendarClick() }.border(width = 3.dp, color = if (isDarkMode) brandColor.copy(alpha = 0.6f) else brandColor, shape = RoundedCornerShape(28.dp)),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = surfaceColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = currentDateTime.format(timeFormatter), fontSize = 60.sp, fontWeight = FontWeight.Bold, color = textColor)
                Spacer(modifier = Modifier.height(14.dp))
                Text(text = currentDateTime.format(dateFormatter).uppercase(), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = secondaryTextColor, letterSpacing = 1.sp, textAlign = TextAlign.Center)
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (hasRequiredPermissions) {
                Button(
                    onClick = onPhoneClick,
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = dynamicPhoneBlue),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                ) {
                    Text(text = "Phone", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, softWrap = false)
                }
            } else {
                Button(
                    onClick = { permissionLauncher.launch(arrayOf(Manifest.permission.CALL_PHONE)) },
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Text(text = "Fix Connection", fontSize = 32.sp, fontWeight = FontWeight.Black, color = Color.White, maxLines = 1, softWrap = false)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = onMessagesClick,
                    modifier = Modifier.weight(1f).height(80.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = dynamicWhatsappGreen),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                ) {
                    Text(text = "WhatsApp", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, softWrap = false)
                }

                Button(
                    onClick = onMessagingClick,
                    modifier = Modifier.weight(1f).height(80.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = dynamicMessagingOrange),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                ) {
                    Text(text = "SMS", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, softWrap = false)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = onCameraClick,
                    modifier = Modifier.weight(1f).height(80.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = dynamicCameraPurple),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                ) {
                    Text(text = "Camera", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, softWrap = false)
                }

                Button(
                    onClick = onPhotosClick,
                    modifier = Modifier.weight(1f).height(80.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = dynamicPhotosPink),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                ) {
                    Text(text = "Photos", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, softWrap = false)
                }
            }

            Button(
                onClick = onSettingsClick,
                modifier = Modifier.fillMaxWidth().height(80.dp),
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(containerColor = brandColor),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
            ) {
                Text(text = "Settings", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = onBrandColor, maxLines = 1, softWrap = false)
            }
        }
    }
}

// -------------------- CALENDAR MODULE --------------------

@Composable
fun CalendarScreenContent(
    surfaceColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    brandColor: Color,
    onBrandColor: Color,
    onBackClick: () -> Unit
) {
    val realToday = remember { LocalDate.now() }
    var viewedMonth by remember { mutableStateOf(YearMonth.now()) }

    val shortMonthLabel = remember(viewedMonth) {
        viewedMonth.format(DateTimeFormatter.ofPattern("MMM", Locale.US))
    }
    val fullYearLabel = remember(viewedMonth) {
        viewedMonth.format(DateTimeFormatter.ofPattern("yyyy", Locale.US))
    }

    val daysInMonth = viewedMonth.lengthOfMonth()
    val firstDayOffset = viewedMonth.atDay(1).dayOfWeek.value % 7

    val daysList = remember(viewedMonth) {
        mutableListOf<String>().apply {
            repeat(firstDayOffset) { add("") }
            for (i in 1..daysInMonth) { add(i.toString()) }
            while (size % 7 != 0) { add("") }
        }
    }

    val weekdays = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { viewedMonth = viewedMonth.minusMonths(1) },
                colors = ButtonDefaults.buttonColors(containerColor = surfaceColor),
                border = BorderStroke(1.dp, textColor.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(text = "Prev", fontSize = 16.sp, color = textColor, maxLines = 1)
            }

            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = shortMonthLabel,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    color = brandColor,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = fullYearLabel,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = secondaryTextColor,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false
                )
            }

            Button(
                onClick = { viewedMonth = viewedMonth.plusMonths(1) },
                colors = ButtonDefaults.buttonColors(containerColor = surfaceColor),
                border = BorderStroke(1.dp, textColor.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(text = "Next", fontSize = 16.sp, color = textColor, maxLines = 1)
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 14.dp)
                .border(1.dp, textColor.copy(alpha = 0.08f), RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = surfaceColor)
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    weekdays.forEach { day ->
                        Text(
                            text = day,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp, // Dropped to prevent "Mon" clipping on small displays
                            fontWeight = FontWeight.Bold,
                            color = secondaryTextColor,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                val rowsCount = daysList.size / 7
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    for (rowIndex in 0 until rowsCount) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            for (columnIndex in 0 until 7) {
                                val itemIndex = (rowIndex * 7) + columnIndex
                                val day = daysList.getOrNull(itemIndex) ?: ""
                                val isToday = day.isNotEmpty() && day.toInt() == realToday.dayOfMonth && viewedMonth == YearMonth.from(realToday)

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .padding(2.dp)
                                        .clip(CircleShape)
                                        .background(if (isToday) brandColor else Color.Transparent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (day.isNotEmpty()) {
                                        Text(
                                            text = day,
                                            fontSize = 22.sp,
                                            fontWeight = if (isToday) FontWeight.Black else FontWeight.ExtraBold,
                                            color = if (isToday) onBrandColor else textColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Button(
            onClick = onBackClick,
            modifier = Modifier.fillMaxWidth().height(72.dp).padding(bottom = 4.dp),
            shape = RoundedCornerShape(22.dp),
            colors = ButtonDefaults.buttonColors(containerColor = surfaceColor),
            border = BorderStroke(2.dp, textColor.copy(alpha = 0.2f))
        ) {
            Text(text = "Back Home", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = textColor)
        }
    }
}

// -------------------- SETTINGS MODULE --------------------

@Composable
fun SettingsScreenContent(
    isDarkMode: Boolean,
    surfaceColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    brandColor: Color,
    onBrandColor: Color,
    onDarkModeSelect: () -> Unit,
    onLightModeSelect: () -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    var guardianNumber by remember { mutableStateOf(loadGuardianNumberFromStorage(context)) }
    var hasSmsPermission by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        hasSmsPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
    }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasSmsPermission = isGranted
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 16.dp)) {
            Text(text = "Settings", fontSize = 44.sp, fontWeight = FontWeight.Black, color = brandColor, maxLines = 1, softWrap = false)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = "Screen appearance choices", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = secondaryTextColor, textAlign = TextAlign.Center)
        }

        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Row(modifier = Modifier.fillMaxWidth().height(88.dp).clip(RoundedCornerShape(22.dp)).background(surfaceColor).border(width = if (isDarkMode) 3.dp else 1.dp, color = if (isDarkMode) brandColor else textColor.copy(alpha = 0.08f), shape = RoundedCornerShape(22.dp)).clickable { onDarkModeSelect() }.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "Dark Screen Mode", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = textColor)
                Box(modifier = Modifier.size(34.dp).clip(CircleShape).background(if (isDarkMode) brandColor else textColor.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) { if (isDarkMode) { Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(onBrandColor)) } }
            }
            Row(modifier = Modifier.fillMaxWidth().height(88.dp).clip(RoundedCornerShape(22.dp)).background(surfaceColor).border(width = if (!isDarkMode) 3.dp else 1.dp, color = if (!isDarkMode) brandColor else textColor.copy(alpha = 0.08f), shape = RoundedCornerShape(22.dp)).clickable { onLightModeSelect() }.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "Light Screen Mode", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = textColor)
                Box(modifier = Modifier.size(34.dp).clip(CircleShape).background(if (!isDarkMode) brandColor else textColor.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) { if (!isDarkMode) { Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(onBrandColor)) } }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 1.dp, color = textColor.copy(alpha = 0.08f), shape = RoundedCornerShape(22.dp)),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = surfaceColor)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "Guardian Alert Settings",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = brandColor
                )
                Text(
                    text = "Alert SMS is sent if phone usage runs continuously for 3+ hours straight without stopping, or if the user is inactive for 3+ hours between 7:00 AM and 9:00 PM local time.",
                    fontSize = 16.sp,
                    color = secondaryTextColor
                )

                OutlinedTextField(
                    value = guardianNumber,
                    onValueChange = {
                        guardianNumber = it
                        saveGuardianNumberToStorage(context, it)
                    },
                    label = { Text("Guardian Phone Number", color = secondaryTextColor) },
                    singleLine = true,
                    textStyle = TextStyle(color = textColor, fontSize = 20.sp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = brandColor,
                        unfocusedBorderColor = textColor.copy(alpha = 0.2f),
                        focusedLabelColor = brandColor,
                        unfocusedLabelColor = secondaryTextColor
                    )
                )



                if (!hasSmsPermission) {
                    Button(
                        onClick = { smsPermissionLauncher.launch(Manifest.permission.SEND_SMS) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                    ) {
                        Text("Grant SMS Permission", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Button(
            onClick = onBackClick,
            modifier = Modifier.fillMaxWidth().height(76.dp),
            shape = RoundedCornerShape(22.dp),
            colors = ButtonDefaults.buttonColors(containerColor = surfaceColor),
            border = BorderStroke(2.dp, textColor.copy(alpha = 0.2f)),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp)
        ) {
            Text(text = "Go Back Home", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = textColor)
        }
    }
}

// -------------------- PHONE INTEGRATED MODULE --------------------

@SuppressLint("MissingPermission")
@Composable
fun PhoneScreenContent(
    contactsList: MutableList<Contact>,
    ttsEngine: TextToSpeech?,
    surfaceColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    brandColor: Color,
    onBrandColor: Color,
    isDarkMode: Boolean,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current

    var activeTab by remember { mutableStateOf(PhoneTab.KEYPAD) }
    var inputNumber by remember { mutableStateOf("") }

    var showAddDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newNumber by remember { mutableStateOf("") }

    var currentSpeechAction by remember { mutableStateOf(SpeechAction.NONE) }
    var contactPendingDeletion by remember { mutableStateOf<Contact?>(null) }
    var targetCallInfo by remember { mutableStateOf<Contact?>(null) }
    var spokenPromptText by remember { mutableStateOf("") }

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val spokenMatches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val topSpokenPhrase = spokenMatches?.firstOrNull()?.lowercase(Locale.ROOT) ?: ""

            val affirmativeYesTriggers = listOf(
                "yes", "yeah", "yep", "yup", "sure", "ok", "okay", "correct",
                "do it", "delete", "remove", "uh huh", "please", "fine", "go ahead",
                "call", "place call", "ring", "dial"
            )

            val negativeNoTriggers = listOf(
                "no", "nope", "nah", "stop", "cancel", "don't", "don t", "wait",
                "never mind", "incorrect", "wrong"
            )

            val confirmedYes = affirmativeYesTriggers.any { topSpokenPhrase.contains(it) }
            val confirmedNo = negativeNoTriggers.any { topSpokenPhrase.contains(it) }

            if (confirmedYes && !confirmedNo) {
                when (currentSpeechAction) {
                    SpeechAction.DELETE_CONTACT -> {
                        contactPendingDeletion?.let { target ->
                            contactsList.remove(target)
                            saveContactsToStorage(context, contactsList)
                            Toast.makeText(context, "Contact Deleted", Toast.LENGTH_SHORT).show()
                        }
                    }
                    SpeechAction.CONFIRM_CALL -> {
                        targetCallInfo?.let { callTarget ->
                            val intent = Intent(Intent.ACTION_CALL, "tel:${Uri.encode(callTarget.phoneNumber)}".toUri())
                            context.startActivity(intent)
                        }
                    }
                    else -> {}
                }
            } else {
                val cancelToastMsg = if (currentSpeechAction == SpeechAction.CONFIRM_CALL) "Call Aborted" else "Deletion Canceled"
                Toast.makeText(context, cancelToastMsg, Toast.LENGTH_SHORT).show()
            }
        }

        contactPendingDeletion = null
        targetCallInfo = null
        currentSpeechAction = SpeechAction.NONE
        spokenPromptText = ""
    }

    LaunchedEffect(spokenPromptText) {
        if (spokenPromptText.isNotEmpty() && currentSpeechAction != SpeechAction.NONE) {
            ttsEngine?.speak(spokenPromptText, TextToSpeech.QUEUE_FLUSH, null, null)

            val calculatedMs = ((spokenPromptText.length * 0.075f).coerceAtLeast(2.5f) * 1000).toLong()
            delay(calculatedMs.milliseconds)

            val voiceIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.forLanguageTag("en-IN"))
                putExtra(RecognizerIntent.EXTRA_PROMPT, spokenPromptText)
            }

            try {
                speechRecognizerLauncher.launch(voiceIntent)
            } catch (_: Exception) {
                Toast.makeText(context, "Voice features unavailable.", Toast.LENGTH_SHORT).show()
                contactPendingDeletion = null
                targetCallInfo = null
                currentSpeechAction = SpeechAction.NONE
                spokenPromptText = ""
            }
        }
    }

    val dialerButtons = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#")

    val buttonBackground by animateColorAsState(targetValue = if (isDarkMode) Color(0xFF26272F) else Color(0xFFE2E2E9), animationSpec = tween(400))
    val clearButtonBackground by animateColorAsState(targetValue = if (isDarkMode) Color(0xFF3A3B45) else Color(0xFFD1D2DC), animationSpec = tween(400))
    val callButtonBackground by animateColorAsState(targetValue = if (isDarkMode) Color(0xFF1E6F31) else Color(0xFF2E7D32), animationSpec = tween(400))
    val accentOrange by animateColorAsState(targetValue = if (isDarkMode) Color(0xFFD27A15) else Color(0xFFE65100), animationSpec = tween(400))
    val destructiveRed = Color(0xFFD32F2F)

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(buttonBackground),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (activeTab == PhoneTab.KEYPAD) brandColor else Color.Transparent)
                    .clickable { activeTab = PhoneTab.KEYPAD },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Keypad",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (activeTab == PhoneTab.KEYPAD) onBrandColor else textColor
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (activeTab == PhoneTab.CONTACTS) brandColor else Color.Transparent)
                    .clickable { activeTab = PhoneTab.CONTACTS },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Contacts (${contactsList.size})",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (activeTab == PhoneTab.CONTACTS) onBrandColor else textColor
                )
            }
        }

        if (activeTab == PhoneTab.KEYPAD) {
            Card(
                modifier = Modifier.fillMaxWidth().border(width = 1.dp, color = textColor.copy(alpha = 0.08f), shape = RoundedCornerShape(22.dp)),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = surfaceColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = inputNumber.takeIf { it.isNotEmpty() } ?: "Tap Numbers Below",
                        fontSize = if (inputNumber.isEmpty()) 22.sp else 38.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (inputNumber.isEmpty()) textColor.copy(alpha = 0.4f) else brandColor,
                        maxLines = 1,
                        letterSpacing = 1.sp,
                        textAlign = TextAlign.Center
                    )

                    if (inputNumber.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Save Contact",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentOrange,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(textColor.copy(alpha = 0.08f))
                                .clickable {
                                    newNumber = inputNumber
                                    showAddDialog = true
                                }
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    } else {
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 8.dp),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(dialerButtons) { digit ->
                    Button(
                        onClick = { if (inputNumber.length < 10) inputNumber += digit },
                        modifier = Modifier.aspectRatio(1.4f),
                        shape = RoundedCornerShape(22.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = buttonBackground),
                        contentPadding = PaddingValues(0.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp)
                    ) {
                        Text(text = digit, fontSize = 32.sp, fontWeight = FontWeight.Black, color = textColor)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = { if (inputNumber.isNotEmpty()) inputNumber = inputNumber.dropLast(1) },
                    modifier = Modifier.weight(1.2f).height(68.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = clearButtonBackground),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(text = "Undo", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = textColor)
                }

                Button(
                    onClick = {
                        if (inputNumber.isNotEmpty()) {
                            targetCallInfo = Contact("Unknown Number", inputNumber)
                            currentSpeechAction = SpeechAction.CONFIRM_CALL
                            spokenPromptText = "Do you want to make this call?"
                        }
                    },
                    modifier = Modifier.weight(1.8f).height(68.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = callButtonBackground),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(text = "Call", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color.White)
                }
            }

        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "My Contacts", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = textColor)
                Button(
                    onClick = {
                        newNumber = ""
                        newName = ""
                        showAddDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentOrange),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Add New", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 12.dp).border(1.dp, textColor.copy(alpha = 0.1f), RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = surfaceColor)
            ) {
                if (contactsList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = "No Contacts Saved", fontSize = 18.sp, color = secondaryTextColor, fontWeight = FontWeight.Medium)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(contactsList) { contact ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(textColor.copy(alpha = 0.04f))
                                    .clickable {
                                        targetCallInfo = contact
                                        currentSpeechAction = SpeechAction.CONFIRM_CALL
                                        spokenPromptText = "Do you want to call ${contact.name}?"
                                    }
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = contact.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = textColor)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(text = contact.phoneNumber, fontSize = 16.sp, color = secondaryTextColor)
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Text(
                                        text = "Call",
                                        fontSize = 16.sp,
                                        color = brandColor,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable {
                                            targetCallInfo = contact
                                            currentSpeechAction = SpeechAction.CONFIRM_CALL
                                            spokenPromptText = "Do you want to call ${contact.name}?"
                                        }
                                    )
                                    Text(
                                        text = "Delete",
                                        fontSize = 16.sp,
                                        color = destructiveRed,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable {
                                            contactPendingDeletion = contact
                                            currentSpeechAction = SpeechAction.DELETE_CONTACT
                                            spokenPromptText = "Do you want to delete ${contact.name}?"
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Button(
            onClick = onBackClick,
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(bottom = 4.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            border = BorderStroke(2.dp, textColor.copy(alpha = 0.2f))
        ) {
            Text(text = "Back Home", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = textColor.copy(alpha = 0.85f))
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = surfaceColor,
            title = { Text("Save Contact", color = textColor, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Name") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textColor, unfocusedTextColor = textColor)
                    )
                    OutlinedTextField(
                        value = newNumber,
                        onValueChange = { newNumber = it },
                        label = { Text("Phone Number") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textColor, unfocusedTextColor = textColor)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotEmpty() && newNumber.isNotEmpty()) {
                            contactsList.add(Contact(newName, newNumber))
                            saveContactsToStorage(context, contactsList)
                            newName = ""
                            newNumber = ""
                            showAddDialog = false
                            activeTab = PhoneTab.CONTACTS
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = brandColor)
                ) {
                    Text("Save", color = onBrandColor)
                }
            },
            dismissButton = {
                Button(
                    onClick = { showAddDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
                ) {
                    Text("Cancel", color = textColor)
                }
            }
        )
    }
}