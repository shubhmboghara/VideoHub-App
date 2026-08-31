package com.videhub.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.videhub.data.SettingsManager
import com.videhub.extractor.ExtractorHelper
import kotlinx.coroutines.launch

@Composable
fun ContentSettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val currentLanguage by SettingsManager.getContentLanguage(context).collectAsState(initial = "en")
    val currentCountry by SettingsManager.getContentCountry(context).collectAsState(initial = "US")
    
    var selectedTab by remember { mutableStateOf(0) } // 0: Language, 1: Region
    
    val languages = listOf(
        "en" to "English",
        "hi" to "Hindi (हिन्दी)",
        "es" to "Spanish (Español)",
        "fr" to "French (Français)",
        "de" to "German (Deutsch)",
        "it" to "Italian (Italiano)",
        "pt" to "Portuguese (Português)",
        "ru" to "Russian (Русский)",
        "ja" to "Japanese (日本語)",
        "ko" to "Korean (한국어)",
        "zh" to "Chinese (中文)",
        "ar" to "Arabic (العربية)",
        "tr" to "Turkish (Türkçe)",
        "vi" to "Vietnamese (Tiếng Việt)",
        "id" to "Indonesian (Bahasa Indonesia)",
        "th" to "Thai (ไทย)",
        "bn" to "Bengali (বাংলা)",
        "pa" to "Punjabi (ਪੰਜਾਬੀ)",
        "mr" to "Marathi (मराठी)",
        "te" to "Telugu (తెలుగు)",
        "ta" to "Tamil (தமிழ்)",
        "ur" to "Urdu (اردو)",
        "gu" to "Gujarati (ગુજરાતી)",
        "kn" to "Kannada (ಕನ್ನಡ)",
        "ml" to "Malayalam (മലയാളം)"
    ).sortedBy { it.second }

    val regions = listOf(
        "US" to "United States",
        "IN" to "India",
        "GB" to "United Kingdom",
        "CA" to "Canada",
        "AU" to "Australia",
        "BR" to "Brazil",
        "MX" to "Mexico",
        "ES" to "Spain",
        "FR" to "France",
        "DE" to "Germany",
        "IT" to "Italy",
        "RU" to "Russia",
        "JP" to "Japan",
        "KR" to "South Korea",
        "CN" to "China",
        "ID" to "Indonesia",
        "PK" to "Pakistan",
        "BD" to "Bangladesh",
        "SA" to "Saudi Arabia",
        "TR" to "Turkey",
        "VN" to "Vietnam",
        "PH" to "Philippines",
        "TH" to "Thailand",
        "EG" to "Egypt",
        "NG" to "Nigeria",
        "ZA" to "South Africa"
    ).sortedBy { it.second }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp)
                .padding(16.dp),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Content Settings",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Language") },
                        icon = { Icon(Icons.Default.Language, null) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Region") },
                        icon = { Icon(Icons.Default.Public, null) }
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                LazyColumn(modifier = Modifier.weight(1f)) {
                    if (selectedTab == 0) {
                        items(languages) { (code, name) ->
                            LanguageRegionItem(
                                name = name,
                                selected = currentLanguage == code,
                                onClick = {
                                    scope.launch {
                                        SettingsManager.setContentLanguage(context, code)
                                        ExtractorHelper.updateLocalization(code, currentCountry)
                                    }
                                }
                            )
                        }
                    } else {
                        items(regions) { (code, name) ->
                            LanguageRegionItem(
                                name = name,
                                selected = currentCountry == code,
                                onClick = {
                                    scope.launch {
                                        SettingsManager.setContentCountry(context, code)
                                        ExtractorHelper.updateLocalization(currentLanguage, code)
                                    }
                                }
                            )
                        }
                    }
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Done")
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguageRegionItem(
    name: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (selected) {
                RadioButton(selected = true, onClick = null)
            }
        }
    }
}
