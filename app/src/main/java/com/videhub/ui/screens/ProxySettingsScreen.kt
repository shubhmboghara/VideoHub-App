package com.videhub.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import com.videhub.extractor.ProxyConfig
import com.videhub.extractor.ProxyManager
import java.net.Proxy

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxySettingsScreen(onBack: () -> Unit) {

    var proxyEnabled by remember { mutableStateOf(ProxyManager.isProxyEnabled()) }
    var useAutoRotate by remember { mutableStateOf(false) }
    var host by remember { mutableStateOf(ProxyManager.getSavedProxy()?.host ?: "") }
    var port by remember { mutableStateOf(ProxyManager.getSavedProxy()?.port?.toString() ?: "") }
    var username by remember { mutableStateOf(ProxyManager.getSavedProxy()?.username ?: "") }
    var password by remember { mutableStateOf(ProxyManager.getSavedProxy()?.password ?: "") }
    var proxyType by remember { mutableStateOf(Proxy.Type.HTTP) }
    var statusMessage by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                windowInsets = WindowInsets(0.dp),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val context = LocalContext.current
            val themeModeStr by com.videhub.data.SettingsManager.getThemeMode(context).collectAsStateWithLifecycle(initialValue = "SYSTEM")
            val currentThemeMode = com.videhub.ui.theme.AppThemeMode.fromString(themeModeStr)
            val isAmoledMode by com.videhub.data.SettingsManager.getIsAmoledMode(context).collectAsStateWithLifecycle(initialValue = false)
            val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val isEffectivelyDark = when (currentThemeMode) {
                com.videhub.ui.theme.AppThemeMode.SYSTEM -> isSystemDark
                com.videhub.ui.theme.AppThemeMode.LIGHT -> false
                com.videhub.ui.theme.AppThemeMode.DARK -> true
            }
            val scope = rememberCoroutineScope()
            var showThemeDialog by remember { mutableStateOf(false) }

            if (showThemeDialog) {
                AlertDialog(
                    onDismissRequest = { showThemeDialog = false },
                    title = { Text("Choose Theme", style = MaterialTheme.typography.titleLarge) },
                    text = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            val options: List<Triple<com.videhub.ui.theme.AppThemeMode, String, ImageVector>> = listOf(
                                Triple(com.videhub.ui.theme.AppThemeMode.SYSTEM, "System default", Icons.Default.SettingsBrightness),
                                Triple(com.videhub.ui.theme.AppThemeMode.LIGHT, "Light theme", Icons.Default.LightMode),
                                Triple(com.videhub.ui.theme.AppThemeMode.DARK, "Dark theme", Icons.Default.DarkMode)
                            )
                            options.forEach { (mode, title, icon) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            com.videhub.ui.theme.ThemeManager.setThemeMode(context, mode)
                                            showThemeDialog = false
                                        }
                                        .padding(vertical = 12.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = (currentThemeMode == mode),
                                        onClick = {
                                            com.videhub.ui.theme.ThemeManager.setThemeMode(context, mode)
                                            showThemeDialog = false
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showThemeDialog = false }) {
                            Text("Close")
                        }
                    }
                )
            }

            Text("Appearance", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            Card(
                onClick = { showThemeDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("App Theme", style = MaterialTheme.typography.bodyLarge)
                        val themeSubtitle = when (currentThemeMode) {
                            com.videhub.ui.theme.AppThemeMode.SYSTEM -> "System default"
                            com.videhub.ui.theme.AppThemeMode.LIGHT -> "Light theme"
                            com.videhub.ui.theme.AppThemeMode.DARK -> "Dark theme"
                        }
                        Text(themeSubtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(
                        imageVector = when (currentThemeMode) {
                            com.videhub.ui.theme.AppThemeMode.SYSTEM -> Icons.Default.SettingsBrightness
                            com.videhub.ui.theme.AppThemeMode.LIGHT -> Icons.Default.LightMode
                            com.videhub.ui.theme.AppThemeMode.DARK -> Icons.Default.DarkMode
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (isEffectivelyDark) {
                Card(onClick = { scope.launch { com.videhub.data.SettingsManager.setIsAmoledMode(context, !isAmoledMode) } }, modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("AMOLED Pure Black", style = MaterialTheme.typography.bodyLarge)
                        Switch(checked = isAmoledMode, onCheckedChange = { scope.launch { com.videhub.data.SettingsManager.setIsAmoledMode(context, it) } }, colors = SwitchDefaults.colors(uncheckedTrackColor = MaterialTheme.colorScheme.outline, uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant))
                    }
                }
            }

            Text("Proxy Configuration", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))

            // Enable proxy toggle
            Card(
                onClick = { proxyEnabled = !proxyEnabled },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text("Enable Proxy", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Route traffic through proxy server",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = proxyEnabled,
                        onCheckedChange = { proxyEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.surface,
                            checkedTrackColor = MaterialTheme.colorScheme.onSurface,
                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            uncheckedTrackColor = MaterialTheme.colorScheme.outline,
                            uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }

            if (proxyEnabled) {
                // Auto-rotate toggle
                Card(
                    onClick = { useAutoRotate = !useAutoRotate },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                            Text("Auto-Rotate Proxies", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Automatically switch proxy if blocked",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = useAutoRotate,
                            onCheckedChange = { useAutoRotate = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.surface,
                                checkedTrackColor = MaterialTheme.colorScheme.onSurface,
                                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                uncheckedTrackColor = MaterialTheme.colorScheme.outline,
                                uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }

                if (!useAutoRotate) {
                    // Proxy Type selector
                    Text("Proxy Type", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(Proxy.Type.HTTP, Proxy.Type.SOCKS).forEach { type ->
                            FilterChip(
                                selected = proxyType == type,
                                onClick = { proxyType = type },
                                label = { Text(type.name) }
                            )
                        }
                    }

                    // Host
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("Proxy Host / IP") },
                        placeholder = { Text("e.g. 103.149.162.195") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.onSurface,
                            focusedLabelColor = MaterialTheme.colorScheme.onSurface,
                            cursorColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )

                    // Port
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it.filter { c -> c.isDigit() } },
                        label = { Text("Port") },
                        placeholder = { Text("e.g. 8080") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.onSurface,
                            focusedLabelColor = MaterialTheme.colorScheme.onSurface,
                            cursorColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )

                    // Username (optional)
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.onSurface,
                            focusedLabelColor = MaterialTheme.colorScheme.onSurface,
                            cursorColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )

                    // Password (optional)
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.onSurface,
                            focusedLabelColor = MaterialTheme.colorScheme.onSurface,
                            cursorColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }

                // Save button
                Button(
                    onClick = {
                        if (useAutoRotate) {
                            // Save auto-rotate mode — uses built-in free proxies
                            ProxyManager.saveProxy(ProxyManager.getNextFreeProxy())
                            statusMessage = "✅ Auto-rotate enabled! Will switch proxy if blocked."
                        } else {
                            val portNum = port.toIntOrNull()
                            if (host.isBlank() || portNum == null) {
                                statusMessage = "❌ Please enter a valid host and port."
                            } else {
                                ProxyManager.saveProxy(
                                    ProxyConfig(
                                        host = host,
                                        port = portNum,
                                        type = proxyType,
                                        username = username,
                                        password = password
                                    )
                                )
                                // Reinit NewPipe with new proxy
                                com.videhub.extractor.ExtractorHelper.reinit()
                                statusMessage = "✅ Proxy saved! App will now use this proxy."
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onSurface,
                        contentColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Text("Save & Apply")
                }
            } else {
                // Disable proxy
                Button(
                    onClick = {
                        ProxyManager.disableProxy()
                        com.videhub.extractor.ExtractorHelper.reinit()
                        statusMessage = "✅ Proxy disabled. Using direct connection."
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Disable & Use Direct Connection")
                }
            }

            // Status message
            if (statusMessage.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Text(
                        statusMessage,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Info card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("💡 Tips", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "• Use Auto-Rotate if YouTube blocks your IP\n" +
                        "• Free proxies may be slow — use your own for best speed\n" +
                        "• SOCKS5 proxies are faster and more reliable than HTTP\n" +
                        "• Try switching WiFi ↔ Mobile Data first (free fix!)",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}