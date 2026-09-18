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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import uz.voiceassistant.VoiceAssistantApp
import uz.voiceassistant.service.ScreenAgentService
import uz.voiceassistant.service.WakeWordService
import uz.voiceassistant.ui.theme.UzbekVoiceAssistantTheme

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
    val scrollState = rememberScrollState()

    var apiKey by remember { mutableStateOf(settingsManager.geminiApiKey) }
    var isKeyVisible by remember { mutableStateOf(false) }
    var wakeWordEnabled by remember { mutableStateOf(settingsManager.isWakeWordEnabled) }
    var fallbackLang by remember { mutableStateOf(settingsManager.fallbackLanguage) }
    var hasAllPermissions by remember {
        mutableStateOf((context as? MainActivity)?.hasPermissions() ?: false)
    }
    var isAccessibilityActive by remember { mutableStateOf(ScreenAgentService.isEnabled()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasAllPermissions = results.values.all { it }
    }

    // Periodically update service and permission statuses
    LaunchedEffect(Unit) {
        isAccessibilityActive = ScreenAgentService.isEnabled()
        hasAllPermissions = (context as? MainActivity)?.hasPermissions() ?: false
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

            // Gemini API Key Input Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("4. Gemini API Kaliti (AI Vision)", fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Universal ekran boshqaruvchisi uchun Google AI Studio (aistudio.google.com) dan bepul olingan API kalitni kiriting. Kalit faqat qurilmangizda xavfsiz (EncryptedSharedPreferences) saqlanadi.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("Gemini API Kaliti") },
                        placeholder = { Text("AIzaSy...") },
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
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            settingsManager.geminiApiKey = apiKey
                            Toast.makeText(context, "API Kaliti xavfsiz saqlandi!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Saqlash")
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
                            text = "Diqqat: Android tizimi mikrofon fonda ishlaganda doimiy bildirishnoma (notification) ko'rsatishni majburiy talab qiladi (OS privacy requirement).",
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
                        text = "Agar qurilmangizda O'zbek tili (uz-UZ) ovoz paketi o'rnatilmagan bo'lsa, qaysi til zaxira sifatida ishlatilsin?",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = (fallbackLang == "ru-RU"),
                            onClick = {
                                fallbackLang = "ru-RU"
                                settingsManager.fallbackLanguage = "ru-RU"
                            }
                        )
                        Text("Rus tili (ru-RU)", modifier = Modifier.clickable {
                            fallbackLang = "ru-RU"
                            settingsManager.fallbackLanguage = "ru-RU"
                        })

                        Spacer(modifier = Modifier.width(20.dp))

                        RadioButton(
                            selected = (fallbackLang == "tr-TR"),
                            onClick = {
                                fallbackLang = "tr-TR"
                                settingsManager.fallbackLanguage = "tr-TR"
                            }
                        )
                        Text("Turk tili (tr-TR)", modifier = Modifier.clickable {
                            fallbackLang = "tr-TR"
                            settingsManager.fallbackLanguage = "tr-TR"
                        })
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
