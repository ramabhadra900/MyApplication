package com.example.myapplication

import android.content.Context
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

private fun fmt(v: Double): String = "${Math.round(v)}.00"

fun isInternetAvailable(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return false
    val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
    return when {
        activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
        activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
        else -> false
    }
}

private fun getCalcDynamicPercentage(): Double {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionScreen(onNavigateBack: () -> Unit) {
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val context = LocalContext.current
    val currentUserEmail = auth.currentUser?.email ?: ""

    var currentAdminName by remember { mutableStateOf("ADMIN") }
    var memberNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var memberPhotos by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var selectedMember by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    var transactionType by remember { mutableStateOf("RECEIVED") }

    var dailyPaidReceived by remember { mutableStateOf("") }
    var advanceRepayment by remember { mutableStateOf("") }
    var serviceCharge by remember { mutableStateOf("") }

    var otherIncome by remember { mutableStateOf("") }
    var otherIncomeDesc by remember { mutableStateOf("") }

    var advancePaid by remember { mutableStateOf("") }
    var expensePaid by remember { mutableStateOf("") }
    var expenseDesc by remember { mutableStateOf("") }

    var maxDailyLimit by remember { mutableDoubleStateOf(500.0) }
    var maxMonthlyLimit by remember { mutableDoubleStateOf(3000.0) }
    var activeMultiplier by remember { mutableDoubleStateOf(1.5) }

    var minScPercent by remember { mutableDoubleStateOf(0.50) }
    var dailyScPercent by remember { mutableDoubleStateOf(0.07142857) }
    var advanceBlockDays by remember { mutableLongStateOf(21L) }
    var serviceChargeDueDays by remember { mutableLongStateOf(7L) }
    var minRepaymentAmount by remember { mutableDoubleStateOf(100.0) } // പുതിയ വേരിയബിൾ

    var remainingDaily by remember { mutableDoubleStateOf(500.0) }
    var remainingMonthly by remember { mutableDoubleStateOf(3000.0) }
    var adminCurrentBalance by remember { mutableDoubleStateOf(0.0) }
    var memberMaxAdvanceAllowed by remember { mutableDoubleStateOf(0.0) }
    var memberAdvanceBalance by remember { mutableDoubleStateOf(0.0) }

    var exactScRequired by remember { mutableDoubleStateOf(0.0) }
    var isPast7Days by remember { mutableStateOf(false) } // 7 ദിവസം കഴിഞ്ഞോ എന്നറിയാൻ
    var defaultersList by remember { mutableStateOf<List<String>>(emptyList()) }
    var isGlobalAdvanceBlocked by remember { mutableStateOf(false) }
    var showExitWarning by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    var showNoticeDialog by remember { mutableStateOf(false) }
    var adminNoticeMessage by remember { mutableStateOf("") }
    var globalNoticeMessage by remember { mutableStateOf("") }

    val fullDateStr = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault()).format(Date())
    val currentDayStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
    val currentMonthStr = "/${SimpleDateFormat("MM/yyyy", Locale.getDefault()).format(Date())}"

    val hasUnsavedData = selectedMember.isNotEmpty() && (dailyPaidReceived.isNotEmpty() || advanceRepayment.isNotEmpty() || advancePaid.isNotEmpty() || expensePaid.isNotEmpty() || serviceCharge.isNotEmpty() || otherIncome.isNotEmpty())
    val isSpecialCategory = selectedMember == "OTHER INCOME" || selectedMember == "EXPENSE"

    BackHandler { if (hasUnsavedData) showExitWarning = true else onNavigateBack() }

    if (showExitWarning) {
        AlertDialog(
            onDismissRequest = { showExitWarning = false },
            title = { Text("പുറത്തു കടക്കണമെന്നുണ്ടോ?", fontWeight = FontWeight.Bold) },
            text = { Text("നിങ്ങൾ എന്റർ ചെയ്ത വിവരങ്ങൾ സേവ് ചെയ്തിട്ടില്ല. സേവ് ചെയ്യാതെ പുറത്തു കടക്കണമെന്നുണ്ടോ?") },
            confirmButton = { TextButton(onClick = { showExitWarning = false; onNavigateBack() }) { Text("അതെ (Yes)", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showExitWarning = false }) { Text("ക്യാൻസൽ") } }
        )
    }

    if (showNoticeDialog) {
        AlertDialog(
            onDismissRequest = { showNoticeDialog = false },
            title = { Text("നോട്ടീസ് ബോർഡ്", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    if (currentAdminName == "SYSTEM ADMIN") {
                        Text("ഇവിടെ നൽകുന്ന സന്ദേശം എല്ലാവർക്കും കാണാം.", fontSize = 13.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(value = adminNoticeMessage, onValueChange = { adminNoticeMessage = it }, label = { Text("സന്ദേശം ടൈപ്പ് ചെയ്യുക") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                    } else {
                        Text(text = globalNoticeMessage.ifEmpty { "പുതിയ സന്ദേശങ്ങൾ ഒന്നുമില്ല." }, fontSize = 16.sp)
                    }
                }
            },
            confirmButton = {
                if (currentAdminName == "SYSTEM ADMIN") {
                    TextButton(onClick = {
                        val noticeData = hashMapOf("message" to adminNoticeMessage, "timestamp" to FieldValue.serverTimestamp(), "isActive" to adminNoticeMessage.isNotBlank())
                        db.collection("app_settings").document("notice_board").set(noticeData).addOnSuccessListener { Toast.makeText(context, "നോട്ടീസ് സേവ് ചെയ്തു!", Toast.LENGTH_SHORT).show(); showNoticeDialog = false }
                    }) { Text("സേവ് ചെയ്യുക", fontWeight = FontWeight.Bold) }
                } else {
                    TextButton(onClick = { showNoticeDialog = false }) { Text("OK") }
                }
            },
            dismissButton = {
                if (currentAdminName == "SYSTEM ADMIN") {
                    TextButton(onClick = { db.collection("app_settings").document("notice_board").update("isActive", false); Toast.makeText(context, "പഴയ നോട്ടീസ് നീക്കം ചെയ്തു", Toast.LENGTH_SHORT).show(); showNoticeDialog = false }) { Text("Clear Notice", color = Color.Red) }
                }
            }
        )
    }

    fun getStartOfDay(dateStr: String): Long {
        return try {
            val format = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault())
            val d = format.parse(dateStr) ?: return 0L
            val cal = Calendar.getInstance().apply { time = d; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
            cal.timeInMillis
        } catch (e: Exception) { 0L }
    }

    DisposableEffect(Unit) {
        val noticeListener = db.collection("app_settings").document("notice_board").addSnapshotListener { snapshot, _ ->
            if (snapshot != null && snapshot.exists() && snapshot.getBoolean("isActive") == true) {
                globalNoticeMessage = snapshot.getString("message") ?: ""
                adminNoticeMessage = globalNoticeMessage
            } else { globalNoticeMessage = ""; adminNoticeMessage = "" }
        }
        onDispose { noticeListener.remove() }
    }

    LaunchedEffect(Unit) {
        db.collection("app_settings").document("limits").get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                activeMultiplier = if (doc.getBoolean("isDynamicPercentageEnabled") ?: true) getCalcDynamicPercentage() else (doc.getDouble("customPercentage") ?: 1.5)
                maxDailyLimit = doc.getDouble("maxDailyPaid") ?: 500.0
                maxMonthlyLimit = doc.getDouble("maxMonthlyPaid") ?: 3000.0
                minScPercent = doc.getDouble("minScPercent") ?: 0.50
                dailyScPercent = doc.getDouble("dailyScPercent") ?: 0.07142857
                advanceBlockDays = doc.getLong("loanBlockDays") ?: 21L
                serviceChargeDueDays = doc.getLong("serviceChargeDueDays") ?: 7L
                minRepaymentAmount = doc.getDouble("minRepaymentAmount") ?: 100.0 // Fetching new variable
            }
        }

        if (currentUserEmail.isNotEmpty()) {
            db.collection("users").document(currentUserEmail).get().addOnSuccessListener { doc ->
                if (doc.exists()) currentAdminName = doc.getString("name")?.uppercase() ?: "ADMIN"
            }
        }

        db.collection("users").whereEqualTo("role", "Member").get().addOnSuccessListener { userResult ->
            val members = mutableListOf<String>()
            val photos = mutableMapOf<String, String>()
            val defaulters = mutableListOf<String>()
            val todayStart = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis

            for (doc in userResult) {
                val mName = doc.getString("name") ?: continue
                members.add(mName)
                photos[mName] = doc.getString("photo") ?: ""

                val bal = doc.getDouble("current_balance") ?: 0.0
                val lastDate = doc.getString("last_transaction_date") ?: ""
                if (bal > 0 && lastDate.isNotEmpty()) {
                    val pastTime = getStartOfDay(lastDate)
                    if (pastTime > 0) {
                        val daysPassed = TimeUnit.MILLISECONDS.toDays(todayStart - pastTime)
                        if (daysPassed >= advanceBlockDays) defaulters.add(mName)
                    }
                }
            }
            memberNames = members
            memberPhotos = photos
            defaultersList = defaulters
            isGlobalAdvanceBlocked = defaulters.isNotEmpty()
        }
    }

    DisposableEffect(currentUserEmail) {
        var listener: com.google.firebase.firestore.ListenerRegistration? = null
        if (currentUserEmail.isNotEmpty()) {
            listener = db.collection("transactions").whereEqualTo("adminEmail", currentUserEmail).addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                var received = 0.0; var paid = 0.0
                for (doc in snapshot.documents) {
                    if (doc.getString("type") == "RECEIVED") received += (doc.getDouble("dailyPaidReceived") ?: 0.0) + (doc.getDouble("advanceRepayment") ?: 0.0) + (doc.getDouble("serviceCharge") ?: 0.0) + (doc.getDouble("otherIncome") ?: 0.0)
                    else paid += (doc.getDouble("advancePaid") ?: 0.0) + (doc.getDouble("expensePaid") ?: 0.0)
                }
                adminCurrentBalance = Math.round(received - paid).toDouble()
            }
        }
        onDispose { listener?.remove() }
    }

    LaunchedEffect(selectedMember, maxDailyLimit, maxMonthlyLimit) {
        if (selectedMember.isNotEmpty() && !isSpecialCategory) {
            db.collection("users").whereEqualTo("name", selectedMember).get().addOnSuccessListener { userRes ->
                val userDoc = userRes.documents.firstOrNull()
                memberAdvanceBalance = Math.round(userDoc?.getDouble("current_balance") ?: 0.0).toDouble()

                val pendingSc = userDoc?.getDouble("pending_sc") ?: 0.0
                val lastAdvanceDateStr = userDoc?.getString("last_advance_date") ?: ""
                val lastScCalcDateStr = userDoc?.getString("last_sc_calc_date") ?: ""

                var calcSc = pendingSc
                isPast7Days = false

                if (lastAdvanceDateStr.isNotEmpty() && memberAdvanceBalance > 0) {
                    val advanceDate = getStartOfDay(lastAdvanceDateStr)
                    val calcDate = if (lastScCalcDateStr.isNotEmpty()) getStartOfDay(lastScCalcDateStr) else advanceDate
                    val today = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis

                    val graceEnd = advanceDate + (serviceChargeDueDays * 24 * 60 * 60 * 1000L) // 7 days from advance
                    val chargeableStart = maxOf(graceEnd, calcDate)

                    if (today > graceEnd) {
                        isPast7Days = true // 7 ദിവസം കഴിഞ്ഞു എന്ന് മനസ്സിലാക്കാൻ
                    }

                    if (today > chargeableStart) {
                        val daysToCharge = TimeUnit.MILLISECONDS.toDays(today - chargeableStart)
                        if (daysToCharge > 0) {
                            val dailyScAmount = memberAdvanceBalance * (dailyScPercent / 100.0)
                            calcSc += (daysToCharge * dailyScAmount)
                        }
                    }
                }
                exactScRequired = Math.round(calcSc).toDouble()
            }

            db.collection("transactions").whereEqualTo("memberName", selectedMember).get().addOnSuccessListener { result ->
                var sumToday = 0.0; var sumMonth = 0.0; var totalDailyPaid = 0.0
                for (doc in result) {
                    val dateStr = doc.getString("date") ?: ""
                    val dpAmount = doc.getDouble("dailyPaidReceived") ?: 0.0
                    if (doc.getString("type") == "RECEIVED") {
                        totalDailyPaid += dpAmount
                        if (dateStr.startsWith(currentDayStr)) sumToday += dpAmount
                        if (dateStr.contains(currentMonthStr)) sumMonth += dpAmount
                    }
                }
                remainingDaily = Math.round(if (maxDailyLimit - sumToday > 0) maxDailyLimit - sumToday else 0.0).toDouble()
                remainingMonthly = Math.round(if (maxMonthlyLimit - sumMonth > 0) maxMonthlyLimit - sumMonth else 0.0).toDouble()
                val allowedAdvance = (totalDailyPaid * activeMultiplier) - memberAdvanceBalance
                memberMaxAdvanceAllowed = Math.round(if (allowedAdvance > 0) allowedAdvance else 0.0).toDouble()
            }
        } else {
            memberAdvanceBalance = 0.0; exactScRequired = 0.0; memberMaxAdvanceAllowed = 0.0; remainingDaily = maxDailyLimit; remainingMonthly = maxMonthlyLimit; isPast7Days = false
        }
    }

    val dpAmount = dailyPaidReceived.toDoubleOrNull() ?: 0.0
    val repAmount = advanceRepayment.toDoubleOrNull() ?: 0.0
    val enteredSc = serviceCharge.toDoubleOrNull() ?: 0.0
    val oiAmount = otherIncome.toDoubleOrNull() ?: 0.0
    val enteredAdvanceAmount = advancePaid.toDoubleOrNull() ?: 0.0
    val expAmount = expensePaid.toDoubleOrNull() ?: 0.0

    // Validations
    val isDpError = dpAmount > remainingDaily || dpAmount > remainingMonthly || dpAmount < 0
    val isRepAmountTooLow = isPast7Days && repAmount > 0.0 && repAmount < minRepaymentAmount
    val isRepError = repAmount > memberAdvanceBalance || repAmount < 0 || isRepAmountTooLow
    val isScError = if (exactScRequired > 0.0) { (repAmount > 0.0 && enteredSc < exactScRequired) || (enteredSc > 0.0 && enteredSc != exactScRequired) || enteredSc < 0.0 } else enteredSc != 0.0
    val isRepScError = exactScRequired > 0.0 && repAmount > 0.0 && enteredSc < exactScRequired
    val isAdvanceScError = exactScRequired > 0.0 && enteredAdvanceAmount > 0.0
    val isGlobalBlockError = isGlobalAdvanceBlocked && enteredAdvanceAmount > 0.0
    val isAdvanceError = enteredAdvanceAmount > memberMaxAdvanceAllowed || enteredAdvanceAmount < 0 || isAdvanceScError || isGlobalBlockError
    val isAdminBalanceError = (enteredAdvanceAmount + expAmount) > adminCurrentBalance || expAmount < 0
    val isOiDescError = oiAmount > 0.0 && otherIncomeDesc.isBlank()
    val isExpDescError = expAmount > 0.0 && expenseDesc.isBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TRANSACTION ENTRY", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { if (hasUnsavedData) showExitWarning = true else onNavigateBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = { showNoticeDialog = true }) { Icon(Icons.Default.Notifications, contentDescription = "Notice Board", tint = Color(0xFFFFD700)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).fillMaxSize().background(MaterialTheme.colorScheme.background)) {

            if (globalNoticeMessage.isNotEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().background(Color(0xFFFFF9C4)).padding(vertical = 8.dp, horizontal = 16.dp)) {
                    Text(text = "🔔 $globalNoticeMessage", color = Color(0xFFE65100), fontWeight = FontWeight.Bold, modifier = Modifier.basicMarquee())
                }
            }

            // 21 ദിവസത്തെ ഡിഫോൾട്ടർ വാണിംഗ് മെസ്സേജ്
            if (isGlobalAdvanceBlocked) {
                Box(modifier = Modifier.fillMaxWidth().background(Color(0xFFFFEAEA)).padding(vertical = 6.dp, horizontal = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = "Warning", tint = Color.Red, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "ശ്രദ്ധിക്കുക: ${defaultersList.joinToString(", ")} അഡ്വാൻസ് അടയ്ക്കാൻ ബാക്കിയുള്ളതിനാൽ പുതിയ അഡ്വാൻസ് നൽകുന്നത് താൽക്കാലികമായി നിർത്തിവെച്ചിരിക്കുന്നു.", color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("തീയതി: $fullDateStr", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (selectedMember.isNotEmpty() && !isSpecialCategory) {
                        val base64Str = memberPhotos[selectedMember] ?: ""
                        val bitmap = remember(base64Str) {
                            if (base64Str.isNotEmpty()) {
                                try {
                                    val bytes = Base64.decode(base64Str, Base64.DEFAULT)
                                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size).asImageBitmap()
                                } catch (e: Exception) { null }
                            } else null
                        }
                        Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(Color.LightGray), contentAlignment = Alignment.Center) {
                            if (bitmap != null) Image(bitmap = bitmap, contentDescription = "Photo", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            else Icon(Icons.Default.Person, contentDescription = "No Photo", tint = Color.Gray, modifier = Modifier.size(32.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                    }

                    val allOptions = listOf("OTHER INCOME", "EXPENSE") + memberNames
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }, modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = selectedMember, onValueChange = { }, readOnly = true, label = { Text("SELECT MEMBER / CATEGORY") },
                            placeholder = { Text("തിരഞ്ഞെടുക്കുക") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            shape = RoundedCornerShape(12.dp), modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            allOptions.forEach { option ->
                                val textColor = when (option) { "OTHER INCOME" -> Color(0xFF006400); "EXPENSE" -> Color.Red; else -> MaterialTheme.colorScheme.onSurface }
                                DropdownMenuItem(
                                    text = { Text(option, color = textColor, fontWeight = FontWeight.Bold) },
                                    onClick = {
                                        selectedMember = option
                                        expanded = false
                                        dailyPaidReceived = ""; advanceRepayment = ""; serviceCharge = ""; advancePaid = ""; expensePaid = ""; otherIncome = ""; otherIncomeDesc = ""; expenseDesc = ""
                                        if (option == "OTHER INCOME") transactionType = "RECEIVED"
                                        else if (option == "EXPENSE") transactionType = "PAID"
                                    }
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                Spacer(modifier = Modifier.height(8.dp))

                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("ഇടപാട് തരം (TRANSACTION TYPE)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = transactionType == "RECEIVED", onClick = { if (!isSpecialCategory) transactionType = "RECEIVED" })
                            Text("RECEIVED", fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.width(16.dp))
                            RadioButton(selected = transactionType == "PAID", onClick = { if (!isSpecialCategory) transactionType = "PAID" })
                            Text("PAID", fontWeight = FontWeight.Medium)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp)) {

                        if (selectedMember == "OTHER INCOME") {
                            OutlinedTextField(value = otherIncome, onValueChange = { otherIncome = it }, label = { Text("OTHER INCOME AMOUNT") }, leadingIcon = { Text("₹", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp)) }, shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(value = otherIncomeDesc, onValueChange = { otherIncomeDesc = it }, label = { Text("OTHER INCOME വിവരണം (നിർബന്ധം)") }, shape = RoundedCornerShape(12.dp), isError = isOiDescError, supportingText = { if (isOiDescError) Text("വിവരണം രേഖപ്പെടുത്തൽ നിർബന്ധമാണ്!", color = MaterialTheme.colorScheme.error) }, modifier = Modifier.fillMaxWidth())
                        } else if (selectedMember == "EXPENSE") {
                            Text("നിങ്ങളുടെ കയ്യിലുള്ള ആകെ തുക: ₹${fmt(adminCurrentBalance)}", color = Color(0xFF006400), fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
                            OutlinedTextField(value = expensePaid, onValueChange = { expensePaid = it }, label = { Text("EXPENSE AMOUNT") }, leadingIcon = { Text("₹", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp)) }, shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(value = expenseDesc, onValueChange = { expenseDesc = it }, label = { Text("EXPENSE വിവരണം (നിർബന്ധം)") }, shape = RoundedCornerShape(12.dp), isError = isExpDescError, supportingText = { if (isExpDescError) Text("വിവരണം രേഖപ്പെടുത്തൽ നിർബന്ധമാണ്!", color = MaterialTheme.colorScheme.error) }, modifier = Modifier.fillMaxWidth())
                        } else {
                            if (transactionType == "RECEIVED") {
                                OutlinedTextField(value = dailyPaidReceived, onValueChange = { dailyPaidReceived = it }, label = { Text("DAILY PAID") }, leadingIcon = { Text("₹", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp)) }, shape = RoundedCornerShape(12.dp), isError = isDpError && selectedMember.isNotEmpty(), supportingText = { if (selectedMember.isNotEmpty()) { if (dpAmount < 0) Text("മൈനസ് തുക അനുവദനീയമല്ല!", color = MaterialTheme.colorScheme.error) else if (isDpError) Text("ലിമിറ്റ് കഴിഞ്ഞു! (അഡ്മിൻ പവർ വഴി സേവ് ചെയ്യാം)", color = MaterialTheme.colorScheme.error) else Text("ബാക്കി: ഇന്ന് ₹${fmt(remainingDaily)} | മാസം ₹${fmt(remainingMonthly)}", color = MaterialTheme.colorScheme.primary) } }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedTextField(value = advanceRepayment, onValueChange = { advanceRepayment = it }, label = { Text("ADVANCE REPAYMENT") }, leadingIcon = { Text("₹", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp)) }, shape = RoundedCornerShape(12.dp), isError = (isRepError || isRepScError) && selectedMember.isNotEmpty(), supportingText = { if (selectedMember.isNotEmpty()) { if (repAmount < 0) Text("മൈനസ് തുക അനുവദനീയമല്ല!", color = MaterialTheme.colorScheme.error) else if (isRepAmountTooLow) Text("7 ദിവസം കഴിഞ്ഞതിനാൽ കുറഞ്ഞത് ₹${fmt(minRepaymentAmount)} അടക്കേണ്ടതാണ്!", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) else if (isRepError) Text("അടക്കാൻ ബാക്കിയുള്ള അഡ്വാൻസ് ₹${fmt(memberAdvanceBalance)} മാത്രമാണ്.", color = MaterialTheme.colorScheme.error) else if (memberAdvanceBalance > 0) Text("അടക്കാൻ ബാക്കിയുള്ള അഡ്വാൻസ്: ₹${fmt(memberAdvanceBalance)}", color = MaterialTheme.colorScheme.primary) else Text("നിലവിൽ അഡ്വാൻസ് ബാക്കിയില്ല.", color = MaterialTheme.colorScheme.onSurfaceVariant) } }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedTextField(value = serviceCharge, onValueChange = { serviceCharge = it }, label = { Text("SERVICE CHARGE" + if (exactScRequired > 0) " (Required: ₹${fmt(exactScRequired)})" else "") }, leadingIcon = { Text("₹", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp)) }, shape = RoundedCornerShape(12.dp), isError = isScError && selectedMember.isNotEmpty(), supportingText = { if (selectedMember.isNotEmpty()) { if (exactScRequired > 0) { if (isScError) Text("സർവീസ് ചാർജ് കൃത്യം ₹${fmt(exactScRequired)} തന്നെ അടക്കേണ്ടതാണ്.", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) else Text("അടക്കേണ്ട കൃത്യം സർവീസ് ചാർജ്: ₹${fmt(exactScRequired)}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) } } }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedTextField(value = otherIncome, onValueChange = { otherIncome = it }, label = { Text("OTHER INCOME") }, leadingIcon = { Text("₹", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp)) }, shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                                if (oiAmount > 0.0) { Spacer(modifier = Modifier.height(12.dp)); OutlinedTextField(value = otherIncomeDesc, onValueChange = { otherIncomeDesc = it }, label = { Text("OTHER INCOME വിവരണം (നിർബന്ധം)") }, shape = RoundedCornerShape(12.dp), isError = isOiDescError, supportingText = { if (isOiDescError) Text("വിവരണം രേഖപ്പെടുത്തൽ നിർബന്ധമാണ്!", color = MaterialTheme.colorScheme.error) }, modifier = Modifier.fillMaxWidth()) }
                            } else {
                                Text("നിങ്ങളുടെ കയ്യിലുള്ള ആകെ തുക: ₹${fmt(adminCurrentBalance)}", color = Color(0xFF006400), fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
                                OutlinedTextField(value = advancePaid, onValueChange = { advancePaid = it }, label = { Text("ADVANCE ISSUED") }, leadingIcon = { Text("₹", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp)) }, shape = RoundedCornerShape(12.dp), isError = isAdvanceError && selectedMember.isNotEmpty(), supportingText = { if (selectedMember.isNotEmpty()) { if (isGlobalBlockError) Text("അഡ്വാൻസ് നൽകുന്നത് നിർത്തിവെച്ചിരിക്കുന്നു!", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) else if (isAdvanceScError) Text("സർവീസ് ചാർജ് ബാക്കിയുള്ളതിനാൽ ലോൺ അനുവദിക്കില്ല!", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) else if (isAdvanceError) Text("പരമാവധി ₹${fmt(memberMaxAdvanceAllowed)} മാത്രമേ നൽകാനാകൂ.", color = MaterialTheme.colorScheme.error) else Text("നൽകാവുന്ന പരമാവധി അഡ്വാൻസ്: ₹${fmt(memberMaxAdvanceAllowed)}", color = MaterialTheme.colorScheme.primary) } }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedTextField(value = expensePaid, onValueChange = { expensePaid = it }, label = { Text("EXPENSE") }, leadingIcon = { Text("₹", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp)) }, shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                                if (expAmount > 0.0) { Spacer(modifier = Modifier.height(12.dp)); OutlinedTextField(value = expenseDesc, onValueChange = { expenseDesc = it }, label = { Text("EXPENSE വിവരണം (നിർബന്ധം)") }, shape = RoundedCornerShape(12.dp), isError = isExpDescError, supportingText = { if (isExpDescError) Text("വിവരണം രേഖപ്പെടുത്തൽ നിർബന്ധമാണ്!", color = MaterialTheme.colorScheme.error) }, modifier = Modifier.fillMaxWidth()) }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (!isInternetAvailable(context)) { Toast.makeText(context, "ഇന്റർനെറ്റ് കണക്ഷൻ ഇല്ല! ദയവായി നെറ്റ് ഓൺ ചെയ്യുക.", Toast.LENGTH_LONG).show(); return@Button }
                        if (selectedMember.isEmpty()) { Toast.makeText(context, "ദയവായി ഒരു മെമ്പറെ സെലക്ട് ചെയ്യുക!", Toast.LENGTH_SHORT).show(); return@Button }
                        val totalEntered = if (transactionType == "RECEIVED") { dpAmount + repAmount + enteredSc + oiAmount } else { enteredAdvanceAmount + expAmount }
                        if (totalEntered <= 0.0) { Toast.makeText(context, "ദയവായി ഏതെങ്കിലും ഒരു തുക നൽകുക!", Toast.LENGTH_LONG).show(); return@Button }

                        if (transactionType == "RECEIVED") {
                            if (isOiDescError) { Toast.makeText(context, "OTHER INCOME വിവരണം രേഖപ്പെടുത്തുക!", Toast.LENGTH_LONG).show(); return@Button }
                            if (isRepAmountTooLow && !isSpecialCategory) { Toast.makeText(context, "വാണിംഗ്: 7 ദിവസം കഴിഞ്ഞാൽ കുറഞ്ഞത് ₹${fmt(minRepaymentAmount)} റീപെയ്മെൻ്റ് അടക്കേണ്ടതുണ്ട്!", Toast.LENGTH_LONG).show(); return@Button }
                            if (isRepScError && !isSpecialCategory) { Toast.makeText(context, "വാണിംഗ്: സർവീസ് ചാർജ് അടക്കാതെ റീപെയ്മെൻ്റ് ചെയ്യാൻ കഴിയില്ല!", Toast.LENGTH_LONG).show(); return@Button }
                            if ((isRepError || isScError || oiAmount < 0 || dpAmount < 0) && !isSpecialCategory) { Toast.makeText(context, "തെറ്റായ തുകകൾ ദയവായി തിരുത്തുക!", Toast.LENGTH_LONG).show(); return@Button }
                        } else {
                            if (isExpDescError) { Toast.makeText(context, "EXPENSE വിവരണം രേഖപ്പെടുത്തുക!", Toast.LENGTH_LONG).show(); return@Button }
                            if (isGlobalBlockError && !isSpecialCategory) { Toast.makeText(context, "വാണിംഗ്: മെമ്പർമാർ 21 ദിവസം അഡ്വാൻസ് അടക്കാത്തതിനാൽ പുതിയ ലോൺ നൽകാൻ കഴിയില്ല!", Toast.LENGTH_LONG).show(); return@Button }
                            if (isAdvanceScError && !isSpecialCategory) { Toast.makeText(context, "വാണിംഗ്: സർവീസ് ചാർജ് കുടിശ്ശിക ഉള്ളതിനാൽ പുതിയ അഡ്വാൻസ് അനുവദിക്കില്ല.", Toast.LENGTH_LONG).show(); return@Button }
                            if ((isAdminBalanceError || expAmount < 0 || enteredAdvanceAmount < 0) && !isSpecialCategory) { Toast.makeText(context, "തെറ്റായ തുകകൾ ദയവായി തിരുത്തുക!", Toast.LENGTH_LONG).show(); return@Button }
                        }

                        if ((isDpError || isAdvanceError) && !isSpecialCategory && !isGlobalBlockError && !isAdvanceScError) { Toast.makeText(context, "അഡ്മിൻ പവർ: ലിമിറ്റ് മറികടന്ന് സേവ് ചെയ്യുന്നു...", Toast.LENGTH_SHORT).show() }

                        isSaving = true
                        val description = if (transactionType == "RECEIVED") otherIncomeDesc else expenseDesc

                        val transactionMap = hashMapOf(
                            "date" to fullDateStr, "adminEmail" to currentUserEmail, "adminName" to currentAdminName, "memberName" to selectedMember,
                            "type" to transactionType, "dailyPaidReceived" to dpAmount, "advanceRepayment" to repAmount, "serviceCharge" to enteredSc,
                            "otherIncome" to oiAmount, "advancePaid" to enteredAdvanceAmount, "expensePaid" to expAmount, "description" to description,
                            "timestamp" to FieldValue.serverTimestamp()
                        )

                        db.collection("transactions").add(transactionMap).addOnSuccessListener {
                            if (transactionType == "RECEIVED") adminCurrentBalance += (dpAmount + repAmount + enteredSc + oiAmount)
                            else adminCurrentBalance -= (enteredAdvanceAmount + expAmount)

                            if (!isSpecialCategory) {
                                val newAdvanceBalance = if (transactionType == "PAID") memberAdvanceBalance + enteredAdvanceAmount else memberAdvanceBalance - repAmount
                                val userUpdateMap = mutableMapOf<String, Any>("current_balance" to if (newAdvanceBalance > 0) newAdvanceBalance else 0.0, "last_transaction_date" to fullDateStr)

                                if (transactionType == "PAID" && enteredAdvanceAmount > 0) {
                                    val minScAdded = Math.round(enteredAdvanceAmount * (minScPercent / 100.0)).toDouble()
                                    userUpdateMap["pending_sc"] = exactScRequired + minScAdded
                                    if (memberAdvanceBalance == 0.0) { userUpdateMap["last_advance_date"] = fullDateStr; userUpdateMap["last_sc_calc_date"] = fullDateStr }
                                } else if (transactionType == "RECEIVED") {
                                    if (enteredSc > 0 && enteredSc == exactScRequired) { userUpdateMap["pending_sc"] = 0.0; userUpdateMap["last_sc_calc_date"] = fullDateStr }
                                    else if (exactScRequired > 0) { userUpdateMap["pending_sc"] = exactScRequired; userUpdateMap["last_sc_calc_date"] = fullDateStr }
                                }

                                db.collection("users").whereEqualTo("name", selectedMember).get().addOnSuccessListener { memRes ->
                                    for (mDoc in memRes) { db.collection("users").document(mDoc.id).update(userUpdateMap) }
                                }
                            }

                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    val scriptUrl = "https://script.google.com/macros/s/AKfycbwnno0X98h8C8ronxqQiSUJM-ts4iDQ96mFN-BeTsdqrqOIgTtuqNf9DtKlr1CqUETx/exec"
                                    val urlString = "$scriptUrl?date=${android.net.Uri.encode(fullDateStr)}&adminName=${android.net.Uri.encode(currentAdminName)}&memberName=${android.net.Uri.encode(selectedMember)}&type=${android.net.Uri.encode(transactionType)}&dailyPaidReceived=${android.net.Uri.encode(if (dailyPaidReceived.isEmpty()) "0" else dailyPaidReceived)}&advanceRepayment=${android.net.Uri.encode(if (advanceRepayment.isEmpty()) "0" else advanceRepayment)}&serviceCharge=${android.net.Uri.encode(if (serviceCharge.isEmpty()) "0" else serviceCharge)}&otherIncome=${android.net.Uri.encode(if (otherIncome.isEmpty()) "0" else otherIncome)}&advancePaid=${android.net.Uri.encode(if (advancePaid.isEmpty()) "0" else advancePaid)}&expensePaid=${android.net.Uri.encode(if (expensePaid.isEmpty()) "0" else expensePaid)}&description=${android.net.Uri.encode(description)}"
                                    val url = java.net.URL(urlString)
                                    val connection = url.openConnection() as java.net.HttpURLConnection
                                    connection.requestMethod = "POST"
                                    connection.connectTimeout = 5000
                                    connection.responseCode
                                } catch (e: Exception) {}
                            }
                            Toast.makeText(context, "വിവരങ്ങൾ വിജയകരമായി സേവ് ചെയ്തു!", Toast.LENGTH_LONG).show()
                            dailyPaidReceived = ""; advanceRepayment = ""; serviceCharge = ""; otherIncome = ""; advancePaid = ""; expensePaid = ""; otherIncomeDesc = ""; expenseDesc = ""; selectedMember = ""; expanded = false; isSaving = false
                        }.addOnFailureListener {
                            isSaving = false; Toast.makeText(context, "സേവ് ചെയ്യാൻ പരാജയപ്പെട്ടു!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary), enabled = !isSaving
                ) {
                    if (isSaving) CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                    else Text("SAVE TRANSACTION", fontSize = MaterialTheme.typography.titleMedium.fontSize, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}