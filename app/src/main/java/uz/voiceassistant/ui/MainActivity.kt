package uz.voiceassistant.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import uz.voiceassistant.BuildConfig
import uz.voiceassistant.VoiceAssistantApp
import uz.voiceassistant.data.AiProvider
import uz.voiceassistant.service.ScreenAgentService
import uz.voiceassistant.service.WakeWordService
import uz.voiceassistant.ui.theme.UzbekVoiceAssistantTheme
import uz.voiceassistant.updater.AppUpdater
import uz.voiceassistant.updater.UpdateInfo

class MainActivity : ComponentActivity() {

    private val requiredPermissions = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        add(Manifest.permission.CALL_PHONE)
        add(Manifest.permission.READ_CONTACTS)
        add(Manifest.permission.SEND_SMS)
        add(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = applicationContext as VoiceAssistantApp

        setContent {
            UzbekVoiceAssistantTheme {
                MainSettingsScreen(
                    settingsManager = app.settingsManager,
                    onOpenVoiceSettings = { openVoiceInputSettings() },
                    onOpenAccessibilitySettings = { openAccessibilitySettings() },
                    onTestAssistant = { launchTestAssistant() }
                )
            }
        }
    }

    private fun openVoiceInputSettings() {
        try {
            val intent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            val fallbackIntent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            startActivity(fallbackIntent)
        }
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }

    private fun launchTestAssistant() {
        val intent = Intent(this, AssistantSessionActivity::class.java).apply {
            putExtra(AssistantSessionActivity.EXTRA_AUTO_START_LISTENING, true)
        }
        startActivity(intent)
    }

    fun hasPermissions(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainSettingsScreen(
    settingsManager: uz.voiceassistant.data.SettingsManager,
    onOpenVoiceSettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onTestAssistant: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var apiKey by remember { mutableStateOf(settingsManager.apiKey) }
    var selectedProvider by remember { mutableStateOf(settingsManager.aiProvider) }
    var customEndpoint by remember { mutableStateOf(settingsManager.customEndpoint) }
    var modelName by remember {
        mutableStateOf(
            if (settingsManager.aiProvider == AiProvider.GEMINI) settingsManager.geminiModel
            else settingsManager.customModel
        )
    }

    var isKeyVisible by remember { mutableStateOf(false) }
    var wakeWordEnabled by remember { mutableStateOf(settingsManager.isWakeWordEnabled) }
    var fallbackLang by remember { mutableStateOf(settingsManager.fallbackLanguage) }
    var hasAllPermissions by remember {
        mutableStateOf((context as? MainActivity)?.hasPermissions() ?: false)
    }
    var isAccessibilityActive by remember { mutableStateOf(ScreenAgentService.isEnabled()) }

    // Auto-Update States
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var availableUpdate by remember { mutableStateOf<UpdateInfo?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var testCommandText by remember { mutableStateOf("") }
    var testCommandResult by remember { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasAllPermissions = results.values.all { it }
    }

    // Auto-check for updates on launch
    LaunchedEffect(Unit) {
        isAccessibilityActive = ScreenAgentService.isEnabled()
        hasAllPermissions = (context as? MainActivity)?.hasPermissions() ?: false

        AppUpdater.checkForUpdate(BuildConfig.VERSION_NAME).onSuccess { info ->
            if (info.isNewer) {
                availableUpdate = info
                showUpdateDialog = true
            }
        }
    }

    // Update Available Dialog
    if (showUpdateDialog && availableUpdate != null) {
        val update = availableUpdate!!
        AlertDialog(
            onDismissRequest = { if (!isDownloadingUpdate) showUpdateDialog = false },
            icon = { Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Yangi versiya mavjud: v${update.versionName}") },
            text = {
                Column {
                    Text("Ilovaga yangi imkoniyatlar va tuzatishlar kiritildi:")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(update.releaseNotes, style = MaterialTheme.typography.bodySmall)
                    if (isDownloadingUpdate) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Yuklanmoqda: ${(downloadProgress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = downloadProgress,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                if (!isDownloadingUpdate) {
                    Column(horizontalAlignment = Alignment.End) {
                        Row {
                            OutlinedButton(
                                onClick = {
                                    AppUpdater.openInBrowser(context, update.downloadUrl)
                                }
                            ) {
                                Text("Brauzerda yuklash")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    isDownloadingUpdate = true
                                    scope.launch {
                                        AppUpdater.downloadAndInstall(context, update.downloadUrl) { progress ->
                                            downloadProgress = progress
                                        }.onFailure { e ->
                                            isDownloadingUpdate = false
                                            Toast.makeText(context, "Ilova ichida ulanib bo'lmadi. Brauzer ochilmoqda...", Toast.LENGTH_LONG).show()
                                            AppUpdater.openInBrowser(context, update.downloadUrl)
                                        }
                                    }
                                }
                            ) {
                                Text("Ilova ichida")
                            }
                        }
                    }
                }
            },
            dismissButton = {
                if (!isDownloadingUpdate) {
                    TextButton(onClick = { showUpdateDialog = false }) {
                        Text("Keyinroq")
                    }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "O'zbek Ovozli Yordamchi",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Test Assistant Button Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Yordamchini Sinab Ko'rish",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tugmani bosib hoziroq ovozli buyruq bering (masalan: \"soat necha\", \"fonarni yoq\", \"Telegramni och\").",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onTestAssistant,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ovozli Yordamchini Ochish")
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Yoki yozma buyruq berib sinab ko'ring:",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = testCommandText,
                            onValueChange = { testCommandText = it },
                            placeholder = { Text("Masalan: soat necha, fonarni yoq...") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (testCommandText.isNotBlank()) {
                                    val cmd = testCommandText.trim()
                                    val nativeCmd = uz.voiceassistant.command.CommandParser.parse(cmd)
                                    val actionExecutor = uz.voiceassistant.command.NativeActionExecutor(context)
                                    if (nativeCmd !is uz.voiceassistant.command.NativeCommand.Unknown) {
                                        val res = actionExecutor.execute(nativeCmd)
                                        testCommandResult = res
                                    } else {
                                        if (ScreenAgentService.isEnabled()) {
                                            testCommandResult = "Ekran boshqaruvchisi ishga tushirildi: $cmd"
                                            ScreenAgentService.executeTask(context, cmd)
                                        } else {
                                            testCommandResult = "Maxsus imkoniyatlar (Accessibility) yoqilmagan. Quyidan yoqing."
                                        }
                                    }
                                }
                            },
                            enabled = testCommandText.isNotBlank(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Bajarish")
                        }
                    }
                    if (testCommandResult.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Natija: $testCommandResult",
                                modifier = Modifier.padding(10.dp),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            // In-App Auto-Update Card (Play Market-like OTA updates)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Ilovani Yangilash (OTA)", fontWeight = FontWeight.Bold)
                        }
                        Text("v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Ilovani o'chirmasdan, sozlamalarni buzmasdan, to'g'ridan-to'g'ri bir bosishda eng so'nggi versiyaga yangilang.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            isCheckingUpdate = true
                            scope.launch {
                                AppUpdater.checkForUpdate(BuildConfig.VERSION_NAME)
                                    .onSuccess { info ->
                                        isCheckingUpdate = false
                                        if (info.isNewer) {
                                            availableUpdate = info
                                            showUpdateDialog = true
                                        } else {
                                            Toast.makeText(context, "Sizda eng so'nggi versiya o'rnatilgan (v${BuildConfig.VERSION_NAME})", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    .onFailure { e ->
                                        isCheckingUpdate = false
                                        Toast.makeText(context, "Xatolik: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                            }
                        },
                        enabled = !isCheckingUpdate,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isCheckingUpdate) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Tekshirilmoqda...")
                        } else {
                            Icon(Icons.Default.SystemUpdate, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Yangilanishlarni tekshirish")
                        }
                    }
                }
            }

            // Universal Multi-Provider AI Engine Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sun'iy Intellekt (AI) Tizimi", fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Har qanday AI (Google Gemini, OpenAI ChatGPT, OpenRouter, Groq, DeepSeek) API kalitini kiritishingiz mumkin.",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("AI Xizmatini tanlang:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)

                    // Provider selection
                    AiProvider.values().forEach { provider ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedProvider = provider
                                    modelName = when (provider) {
                                        AiProvider.GEMINI -> settingsManager.geminiModel
                                        AiProvider.OPENAI -> "gpt-4o-mini"
                                        AiProvider.CUSTOM -> settingsManager.customModel
                                    }
                                }
                        ) {
                            RadioButton(
                                selected = (selectedProvider == provider),
                                onClick = {
                                    selectedProvider = provider
                                    modelName = when (provider) {
                                        AiProvider.GEMINI -> settingsManager.geminiModel
                                        AiProvider.OPENAI -> "gpt-4o-mini"
                                        AiProvider.CUSTOM -> settingsManager.customModel
                                    }
                                }
                            )
                            Text(provider.displayName, style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    // Custom endpoint field if custom provider
                    if (selectedProvider == AiProvider.CUSTOM) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = customEndpoint,
                            onValueChange = { customEndpoint = it },
                            label = { Text("API Endpoint URL") },
                            placeholder = { Text("https://openrouter.ai/api/v1/chat/completions") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = modelName,
                        onValueChange = { modelName = it },
                        label = { Text("Model Nomi") },
                        placeholder = { Text(if (selectedProvider == AiProvider.GEMINI) "gemini-3.5-flash" else "gpt-4o-mini") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API Kalit (har qanday kalit qabul qilinadi)") },
                        placeholder = { Text("Kalitni bu yerga joylang...") },
                        visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                                Icon(
                                    imageVector = if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Kalitni ko'rsatish"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            settingsManager.aiProvider = selectedProvider
                            settingsManager.apiKey = apiKey
                            if (selectedProvider == AiProvider.GEMINI) {
                                settingsManager.geminiModel = modelName
                            } else {
                                settingsManager.customModel = modelName
                                settingsManager.customEndpoint = customEndpoint
                            }
                            Toast.makeText(context, "AI sozlamalari va kalit saqlandi!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Saqlash")
                    }
                }
            }

            // System Default Assistant Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("1. Standart Yordamchi", fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Ilovani tizimning asosiy yordamchisi qilib belgilang. Shunda istalgan ilovada turib Asosiy ekranni (Home tugmasini) uzoq bosganda ishlaydi.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onOpenVoiceSettings,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Tizim Sozlamalarida Belgilash")
                    }
                }
            }

            // Accessibility Service Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Accessibility, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("2. Ekranni Boshqarish (Maxsus Imkoniyatlar)", fontWeight = FontWeight.Bold)
                        }
                        Icon(
                            imageVector = if (isAccessibilityActive) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isAccessibilityActive) Color(0xFF2E7D32) else Color(0xFFE65100)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isAccessibilityActive) "Maxsus imkoniyatlar xizmati FAOL. Murakkab vazifalarda ekranni ko'rib avtomatik boshqara oladi."
                        else "NOFAOL. Sun'iy intellekt ekranni ko'rib tugmalarni bosishi uchun Maxsus imkoniyatlarni yoqishingiz shart.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isAccessibilityActive) Color(0xFF2E7D32) else Color(0xFFD84315)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onOpenAccessibilitySettings,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isAccessibilityActive) "Sozlamalarni ko'rish" else "Maxsus Imkoniyatlarni Yoqish")
                    }
                }
            }

            // Runtime Permissions Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("3. Tizim Ruxsatlari", fontWeight = FontWeight.Bold)
                        }
                        Icon(
                            imageVector = if (hasAllPermissions) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (hasAllPermissions) Color(0xFF2E7D32) else Color(0xFFE65100)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Mikrofon, Telefon qilish, SMS, Kontaktlar va Kamera ruxsatlari tezkor buyruqlarni bajarish uchun kerak.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (!hasAllPermissions) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                val perms = (context as? MainActivity)?.let {
                                    listOf(
                                        Manifest.permission.RECORD_AUDIO,
                                        Manifest.permission.CALL_PHONE,
                                        Manifest.permission.READ_CONTACTS,
                                        Manifest.permission.SEND_SMS,
                                        Manifest.permission.CAMERA
                                    ).toTypedArray()
                                } ?: emptyArray()
                                permissionLauncher.launch(perms)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Ruxsatlarni Berish")
                        }
                    }
                }
            }

            // Hands-free Wake Word Card (openWakeWord)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Doimiy Fon Mikrofoni (Wake Word)", fontWeight = FontWeight.Bold)
                            Text(
                                text = "openWakeWord (TFLite) orqali fon rejimida \"Salom Yordamchi\" so'zini kutish.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(
                            checked = wakeWordEnabled,
                            onCheckedChange = { isChecked ->
                                wakeWordEnabled = isChecked
                                settingsManager.isWakeWordEnabled = isChecked
                                if (isChecked) {
                                    WakeWordService.start(context)
                                    Toast.makeText(context, "Fon xizmati ishga tushirildi", Toast.LENGTH_SHORT).show()
                                } else {
                                    WakeWordService.stop(context)
                                    Toast.makeText(context, "Fon xizmati to'xtatildi", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Diqqat: Android tizimi mikrofon fonda ishlaganda doimiy bildirishnoma ko'rsatishni majburiy talab qiladi (OS privacy requirement).",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            // Language Fallback Preferences Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ovoz Tili Zaxirasi (Fallback)", fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Agar qurilmangizda O'zbek tili (uz-UZ) ovoz paketi o'rnatilmagan yoki ishlamasa, qaysi til zaxira sifatida ishlatilsin?",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    fallbackLang = "en-US"
                                    settingsManager.fallbackLanguage = "en-US"
                                }
                        ) {
                            RadioButton(
                                selected = (fallbackLang == "en-US"),
                                onClick = {
                                    fallbackLang = "en-US"
                                    settingsManager.fallbackLanguage = "en-US"
                                }
                            )
                            Text("Ingliz tili (en-US) — Tavsiya etiladi", style = MaterialTheme.typography.bodyMedium)
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    fallbackLang = "ru-RU"
                                    settingsManager.fallbackLanguage = "ru-RU"
                                }
                        ) {
                            RadioButton(
                                selected = (fallbackLang == "ru-RU"),
                                onClick = {
                                    fallbackLang = "ru-RU"
                                    settingsManager.fallbackLanguage = "ru-RU"
                                }
                            )
                            Text("Rus tili (ru-RU)", style = MaterialTheme.typography.bodyMedium)
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    fallbackLang = "tr-TR"
                                    settingsManager.fallbackLanguage = "tr-TR"
                                }
                        ) {
                            RadioButton(
                                selected = (fallbackLang == "tr-TR"),
                                onClick = {
                                    fallbackLang = "tr-TR"
                                    settingsManager.fallbackLanguage = "tr-TR"
                                }
                            )
                            Text("Turk tili (tr-TR)", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
