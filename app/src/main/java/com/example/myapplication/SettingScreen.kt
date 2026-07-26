package com.example.myapplication

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

@Composable
fun SettingsScreen(onNavigateBack: () -> Unit) {
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val context = LocalContext.current

    val currentUserEmail = auth.currentUser?.email?.trim()?.lowercase()
    val mainAdminEmail = "ramabhadra900@gmail.com"
    val isMainAdmin = (currentUserEmail == mainAdminEmail)

    // Dynamic Settings
    var isDynamicEnabled by remember { mutableStateOf(true) }
    var customPercentage by remember { mutableStateOf("150") }

    // Manual Admin Limits
    var maxMonthlyPaid by remember { mutableStateOf("0.0") }
    var maxDailyPaid by remember { mutableStateOf("0.0") }
    var minScPercent by remember { mutableStateOf("0.50") }
    var dailyScPercent by remember { mutableStateOf("0.07142857") }
    var serviceChargeDueDays by remember { mutableStateOf("7") }
    var loanBlockDays by remember { mutableStateOf("21") }
    var isLoading by remember { mutableStateOf(true) }

    // Dynamic calc function
    fun calculateDynamicPercent(): Double {
        val today = Calendar.getInstance()
        val currentYear = today.get(Calendar.YEAR)
        val jan1 = Calendar.getInstance().apply { set(currentYear, Calendar.JANUARY, 1, 0, 0, 0) }
        val nov15 = Calendar.getInstance().apply { set(currentYear, Calendar.NOVEMBER, 15, 0, 0, 0) }
        if (today.timeInMillis >= nov15.timeInMillis) return 1.0
        val totalDays = ((nov15.timeInMillis - jan1.timeInMillis) / (1000 * 60 * 60 * 24)).toDouble()
        val daysPassed = ((today.timeInMillis - jan1.timeInMillis) / (1000 * 60 * 60 * 24)).toDouble()
        val percentage = 2.0 - (daysPassed / totalDays)
        return if (percentage < 1.0) 1.0 else percentage
    }

    val currentDynamicPercent = (calculateDynamicPercent() * 100).toInt()

    LaunchedEffect(Unit) {
        db.collection("app_settings").document("limits").get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                isDynamicEnabled = doc.getBoolean("isDynamicPercentageEnabled") ?: true
                customPercentage = (doc.getDouble("customPercentage")?.times(100))?.toInt()?.toString() ?: "150"
                maxMonthlyPaid = doc.getDouble("maxMonthlyPaid")?.toString() ?: "0.0"
                maxDailyPaid = doc.getDouble("maxDailyPaid")?.toString() ?: "0.0"
                minScPercent = doc.getDouble("minScPercent")?.toString() ?: "0.50"
                dailyScPercent = doc.getDouble("dailyScPercent")?.toString() ?: "0.07142857"
                serviceChargeDueDays = doc.getLong("serviceChargeDueDays")?.toString() ?: "7"
                loanBlockDays = doc.getLong("loanBlockDays")?.toString() ?: "21"
            }
            isLoading = false
        }
    }

    if (!isMainAdmin) {
        Text("നിങ്ങൾക്ക് ഈ പേജ് കാണാനുള്ള അനുമതിയില്ല!", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
        return
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    } else {
        Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("APP SETTINGS & LIMITS", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))

            // DYNAMIC PERCENTAGE SECTION
            Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("ലോൺ എലിജിബിലിറ്റി സെറ്റിംഗ്സ്", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Dynamic Percentage ഉപയോഗിക്കുക", fontWeight = FontWeight.Bold)
                            Text("(തിയതിക്കനുസരിച്ച് തനിയെ മാറുന്ന ശതമാനം)", fontSize = 12.sp, color = Color.Gray)
                        }
                        Switch(checked = isDynamicEnabled, onCheckedChange = { isDynamicEnabled = it })
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (isDynamicEnabled) {
                        Box(modifier = Modifier.fillMaxWidth().background(Color(0xFFE8F5E9), RoundedCornerShape(8.dp)).padding(12.dp)) {
                            Column {
                                Text("ഇപ്പോഴത്തെ സിസ്റ്റം ശതമാനം:", color = Color(0xFF006400), fontWeight = FontWeight.Medium)
                                Text("$currentDynamicPercent%", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF006400))
                                Text("ജനുവരി 1-ന് 200% ആയിരിക്കുകയും നവംബർ 15 ആകുമ്പോൾ 100% ആയി മാറുകയും ചെയ്യും.", fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = customPercentage, onValueChange = { customPercentage = it },
                            label = { Text("Custom Percentage (%) നൽകുക") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // MAIN ADMIN LIMITS SECTION
            Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("ലിമിറ്റുകൾ & സർവീസ് ചാർജ്", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    OutlinedTextField(value = maxMonthlyPaid, onValueChange = { maxMonthlyPaid = it }, label = { Text("MAXIMUM MONTHLY PAID (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = maxDailyPaid, onValueChange = { maxDailyPaid = it }, label = { Text("MAXIMUM DAILY PAID (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(value = minScPercent, onValueChange = { minScPercent = it }, label = { Text("MINIMUM SC % (ഉദാ: 0.50)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = dailyScPercent, onValueChange = { dailyScPercent = it }, label = { Text("DAILY SC % (ഉദാ: 0.07142857)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(value = serviceChargeDueDays, onValueChange = { serviceChargeDueDays = it }, label = { Text("GRACE PERIOD DAYS (ഉദാ: 7)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = loanBlockDays, onValueChange = { loanBlockDays = it }, label = { Text("LOAN BLOCKING DAYS") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    val settingsMap = hashMapOf<String, Any>(
                        "isDynamicPercentageEnabled" to isDynamicEnabled,
                        "customPercentage" to (customPercentage.toDoubleOrNull()?.div(100.0) ?: 1.5),
                        "maxMonthlyPaid" to (maxMonthlyPaid.toDoubleOrNull() ?: 0.0),
                        "maxDailyPaid" to (maxDailyPaid.toDoubleOrNull() ?: 0.0),
                        "minScPercent" to (minScPercent.toDoubleOrNull() ?: 0.50),
                        "dailyScPercent" to (dailyScPercent.toDoubleOrNull() ?: 0.07142857),
                        "serviceChargeDueDays" to (serviceChargeDueDays.toLongOrNull() ?: 7L),
                        "loanBlockDays" to (loanBlockDays.toLongOrNull() ?: 21L)
                    )

                    db.collection("app_settings").document("limits").set(settingsMap)
                        .addOnSuccessListener {
                            Toast.makeText(context, "സെറ്റിംഗ്സ് വിജയകരമായി സേവ് ചെയ്തു!", Toast.LENGTH_SHORT).show()
                            onNavigateBack()
                        }
                        .addOnFailureListener { e -> Toast.makeText(context, "സേവ് ചെയ്യാൻ കഴിഞ്ഞില്ല: ${e.message}", Toast.LENGTH_LONG).show() }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) { Text("SAVE SETTINGS", fontWeight = FontWeight.Bold, fontSize = 16.sp) }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}