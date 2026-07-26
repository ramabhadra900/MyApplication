package com.example.myapplication

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

private fun fmt(v: Double): String = "${Math.round(v)}.00"

private fun getMemberDynamicPercentage(): Double {
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
fun MembersScreen(
    userEmail: String,
    isDarkTheme: Boolean,
    onThemeToggle: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()

    var loggedInMemberName by remember { mutableStateOf("") }
    var memberNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedMember by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    var totalDailyPaid by remember { mutableDoubleStateOf(0.0) }
    var totalLoanTaken by remember { mutableDoubleStateOf(0.0) }
    var totalLoanRepaid by remember { mutableDoubleStateOf(0.0) }
    var memberTotalExpense by remember { mutableDoubleStateOf(0.0) }

    var grandTotalDailyPaid by remember { mutableDoubleStateOf(0.0) }
    var grandTotalIncome by remember { mutableDoubleStateOf(0.0) }
    var globalTotalLoanTaken by remember { mutableDoubleStateOf(0.0) }
    var globalTotalLoanRepaid by remember { mutableDoubleStateOf(0.0) }

    var transactions by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    var activeMultiplier by remember { mutableDoubleStateOf(1.5) }
    var globalNoticeMessage by remember { mutableStateOf("") } // നോട്ടീസ് ബോർഡിനായി

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val privacyPolicyUrl = "https://sites.google.com/view/katwf-privacy-policy" // പ്രൈവസി പോളിസി ലിങ്ക്

    DisposableEffect(Unit) {
        val txListener = db.collection("transactions").addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                var tempGrandDaily = 0.0
                var tempGrandIncome = 0.0
                var tempGlobalLoanTaken = 0.0
                var tempGlobalLoanRepaid = 0.0

                for (doc in snapshot.documents) {
                    val type = doc.getString("type") ?: ""
                    if (type == "RECEIVED") {
                        tempGrandDaily += (doc.getDouble("dailyPaidReceived") ?: 0.0)
                        tempGrandIncome += (doc.getDouble("serviceCharge") ?: 0.0) + (doc.getDouble("otherIncome") ?: 0.0)
                        tempGlobalLoanRepaid += (doc.getDouble("repaymentLoan") ?: 0.0)
                    } else if (type == "PAID") {
                        tempGlobalLoanTaken += (doc.getDouble("loanPaid") ?: 0.0)
                    }
                }
                grandTotalDailyPaid = tempGrandDaily
                grandTotalIncome = tempGrandIncome
                globalTotalLoanTaken = tempGlobalLoanTaken
                globalTotalLoanRepaid = tempGlobalLoanRepaid
            }
        }

        val userListener = db.collection("users").whereEqualTo("role", "Member").addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                memberNames = snapshot.documents.mapNotNull { it.getString("name") }
            }
        }

        val settingsListener = db.collection("app_settings").document("limits").addSnapshotListener { snapshot, _ ->
            if (snapshot != null && snapshot.exists()) {
                val isDynamic = snapshot.getBoolean("isDynamicPercentageEnabled") ?: true
                if (isDynamic) {
                    activeMultiplier = getMemberDynamicPercentage()
                } else {
                    activeMultiplier = snapshot.getDouble("customPercentage") ?: 1.5
                }
            } else {
                activeMultiplier = getMemberDynamicPercentage()
            }
        }

        val noticeListener = db.collection("app_settings").document("notice_board").addSnapshotListener { snapshot, _ ->
            if (snapshot != null && snapshot.exists() && snapshot.getBoolean("isActive") == true) {
                globalNoticeMessage = snapshot.getString("message") ?: ""
            } else {
                globalNoticeMessage = ""
            }
        }

        onDispose {
            txListener.remove()
            userListener.remove()
            settingsListener.remove()
            noticeListener.remove()
        }
    }

    LaunchedEffect(userEmail) {
        db.collection("users").document(userEmail).get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                val name = doc.getString("name") ?: "Member"
                loggedInMemberName = name
                if (selectedMember.isEmpty()) { selectedMember = name }
            }
        }
    }

    DisposableEffect(selectedMember) {
        if (selectedMember.isEmpty()) return@DisposableEffect onDispose {}

        isLoading = true
        val listener = db.collection("transactions")
            .whereEqualTo("memberName", selectedMember)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    var dailyPaid = 0.0
                    var loanTaken = 0.0
                    var loanRepaid = 0.0
                    var expense = 0.0
                    val list = mutableListOf<Map<String, Any>>()

                    for (doc in snapshot.documents) {
                        val data = doc.data ?: continue
                        list.add(data)
                        val type = data["type"]?.toString() ?: ""
                        if (type == "RECEIVED") {
                            dailyPaid += (data["dailyPaidReceived"].toString().toDoubleOrNull() ?: 0.0)
                            loanRepaid += (data["repaymentLoan"].toString().toDoubleOrNull() ?: 0.0)
                        } else if (type == "PAID") {
                            loanTaken += (data["loanPaid"].toString().toDoubleOrNull() ?: 0.0)
                            expense += (data["expensePaid"].toString().toDoubleOrNull() ?: 0.0)
                        }
                    }
                    totalDailyPaid = dailyPaid; totalLoanTaken = loanTaken; totalLoanRepaid = loanRepaid; memberTotalExpense = expense
                    transactions = list.sortedByDescending { it["date"].toString() }
                    isLoading = false
                } else {
                    isLoading = false
                }
            }

        onDispose { listener.remove() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
                Spacer(modifier = Modifier.height(24.dp))
                Text("App Menu", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                NavigationDrawerItem(
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Day / Night Mode")
                            Spacer(modifier = Modifier.weight(1f))
                            Switch(checked = isDarkTheme, onCheckedChange = { onThemeToggle() })
                        }
                    },
                    selected = false,
                    onClick = { onThemeToggle() },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                // പ്രൈവസി പോളിസി (Privacy Policy) മെനുവിൽ നൽകിയത്
                NavigationDrawerItem(
                    label = { Text("Privacy Policy") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(privacyPolicyUrl)))
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                NavigationDrawerItem(
                    label = { Text("Logout") },
                    selected = false,
                    onClick = { scope.launch { drawerState.close() }; onLogout() },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    colors = NavigationDrawerItemDefaults.colors(unselectedTextColor = Color.Red)
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("MY ACCOUNT", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                )
            }
        ) { paddingValues ->
            Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {

                // സ്ക്രോൾ ചെയ്യുന്ന നോട്ടീസ് ബോർഡ് (Marquee)
                if (globalNoticeMessage.isNotEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().background(Color(0xFFFFF9C4)).padding(vertical = 8.dp, horizontal = 16.dp)) {
                        Text(
                            text = "🔔 $globalNoticeMessage",
                            color = Color(0xFFE65100),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.basicMarquee() // സ്ക്രോളിംഗ് ഇഫക്റ്റ്
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    item {
                        Text("നമസ്കാരം, $loggedInMemberName! 👋", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(16.dp))

                        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                            OutlinedTextField(
                                value = selectedMember, onValueChange = { }, readOnly = true, label = { Text("മെമ്പറെ തിരഞ്ഞെടുക്കുക") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                memberNames.forEach { name ->
                                    DropdownMenuItem(
                                        text = { Text(name, fontWeight = if (name == loggedInMemberName) FontWeight.Bold else FontWeight.Normal) },
                                        onClick = { selectedMember = name; expanded = false }
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    if (isLoading) {
                        item { Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                    } else {
                        item {
                            val sharePercentage = if (grandTotalDailyPaid > 0) (totalDailyPaid / grandTotalDailyPaid) * 100 else 0.0
                            val myProfit = grandTotalIncome * (sharePercentage / 100)
                            val balanceLoan = if ((totalLoanTaken - totalLoanRepaid) > 0) (totalLoanTaken - totalLoanRepaid) else 0.0

                            val eligibility = (totalDailyPaid * activeMultiplier) - balanceLoan
                            val maxLoanAllowed = if (eligibility > 0) eligibility else 0.0

                            val grossTotal = totalDailyPaid + myProfit
                            val finalBalance = grossTotal - balanceLoan - memberTotalExpense

                            val globalBalanceLoan = if ((globalTotalLoanTaken - globalTotalLoanRepaid) > 0) (globalTotalLoanTaken - globalTotalLoanRepaid) else 0.0
                            val abcdRatio = if (grandTotalDailyPaid > 0) (globalBalanceLoan / grandTotalDailyPaid) else 0.0
                            val emergencyClosing = (totalDailyPaid * abcdRatio) - balanceLoan - memberTotalExpense

                            var daysSinceLastTx = 0L
                            if (transactions.isNotEmpty() && balanceLoan > 0) {
                                try {
                                    val lastDateStr = transactions.first()["date"].toString()
                                    val format = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault())
                                    val pastDate = format.parse(lastDateStr)
                                    val today = Date()
                                    if (pastDate != null) {
                                        val diff = today.time - pastDate.time
                                        daysSinceLastTx = TimeUnit.DAYS.convert(diff, TimeUnit.MILLISECONDS)
                                    }
                                } catch (e: Exception) {}
                            }

                            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), shape = RoundedCornerShape(12.dp)) {
                                Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("സംഘടനയുടെ ആകെ നിക്ഷേപം:", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    Text("₹ ${fmt(grandTotalDailyPaid)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            if (balanceLoan > 0 && daysSinceLastTx >= 21) {
                                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), shape = RoundedCornerShape(12.dp)) {
                                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Warning, contentDescription = "Warning", tint = MaterialTheme.colorScheme.error)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("ശ്രദ്ധിക്കുക: നിങ്ങളുടെ ലോൺ തിരിച്ചടവ് $daysSinceLastTx ദിവസമായി മുടങ്ങിയിരിക്കുന്നു. ഉടൻ തന്നെ അടച്ചു തീർക്കുക.", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                            }

                            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("നിങ്ങളുടെ സാമ്പത്തിക വിവരങ്ങൾ", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 16.sp)
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column {
                                            Text("ആകെ അടച്ച തുക", fontSize = 13.sp, color = Color.Gray)
                                            Text("₹ ${fmt(totalDailyPaid)}", style = MaterialTheme.typography.titleLarge, color = Color(0xFF006400), fontWeight = FontWeight.Bold)
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("ലോൺ ബാക്കി", fontSize = 13.sp, color = Color.Gray)
                                            Text("₹ ${fmt(balanceLoan)}", style = MaterialTheme.typography.titleLarge, color = Color.Red, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Box(modifier = Modifier.fillMaxWidth().background(Color(0xFFE3F2FD), RoundedCornerShape(8.dp)).padding(12.dp)) {
                                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                            Text("നിങ്ങൾക്ക് ഇനി എടുക്കാവുന്ന ലോൺ (${(activeMultiplier * 100).toInt()}%):", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF0D47A1))
                                            Text("₹ ${fmt(maxLoanAllowed)}", fontWeight = FontWeight.Bold, color = Color(0xFF0D47A1))
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(12.dp)) {
                                        Text("ഓഹരി & ലാഭവിഹിതം", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Spacer(modifier = Modifier.height(8.dp))

                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("MY SHARE:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                            Text("${String.format(Locale.US, "%.2f", sharePercentage)}%", fontWeight = FontWeight.Bold)
                                        }
                                        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("MY PROFIT:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                            Text("₹ ${fmt(myProfit)}", fontWeight = FontWeight.Bold, color = Color(0xFF006400))
                                        }

                                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("FINAL BALANCE:", fontWeight = FontWeight.Bold)
                                            Text("₹ ${fmt(finalBalance)}", fontWeight = FontWeight.Bold, color = if (finalBalance >= 0) Color(0xFF006400) else Color.Red)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Box(modifier = Modifier.fillMaxWidth().background(Color(0xFFFFF3E0), RoundedCornerShape(8.dp)).padding(12.dp)) {
                                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                            Text("EMERGENCY CLOSING:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                                            Text("₹ ${fmt(emergencyClosing)}", fontWeight = FontWeight.ExtraBold, color = Color(0xFFE65100))
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    val fromDate = transactions.lastOrNull()?.get("date")?.toString()?.take(10) ?: SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
                                    val toDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())

                                    try {
                                        generatePdfReport(
                                            context = context,
                                            reportTitle = "PERSONAL REPORT - $selectedMember",
                                            subtitle = "Period: $fromDate To $toDate",
                                            transactions = transactions.reversed()
                                        )
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "PDF Report Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("DOWNLOAD STATEMENT", fontWeight = FontWeight.Bold, color = Color.White)
                            }

                            Spacer(modifier = Modifier.height(24.dp))
                            Text("പാസ്സ്ബുക്ക് (Passbook / History)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        if (transactions.isEmpty()) {
                            item { Text("ഇതുവരെ ഇടപാടുകൾ ഒന്നും നടന്നിട്ടില്ല.", color = Color.Gray, modifier = Modifier.padding(16.dp)) }
                        } else {
                            items(transactions) { transaction ->
                                val type = transaction["type"]?.toString() ?: ""
                                val adminName = transaction["adminName"]?.toString() ?: "ADMIN"

                                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                                    Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(transaction["date"].toString(), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                            Spacer(modifier = Modifier.height(4.dp))

                                            if (type == "RECEIVED") {
                                                val dPaid = transaction["dailyPaidReceived"].toString().toDoubleOrNull() ?: 0.0
                                                val rLoan = transaction["repaymentLoan"].toString().toDoubleOrNull() ?: 0.0
                                                if (dPaid > 0) Text("സമ്പാദ്യം അടച്ചു", fontWeight = FontWeight.Medium)
                                                if (rLoan > 0) Text("ലോൺ തിരിച്ചടവ്", fontWeight = FontWeight.Medium)
                                            } else {
                                                val lPaid = transaction["loanPaid"].toString().toDoubleOrNull() ?: 0.0
                                                val ePaid = transaction["expensePaid"].toString().toDoubleOrNull() ?: 0.0
                                                if (lPaid > 0) Text("ലോൺ എടുത്തു", fontWeight = FontWeight.Medium)
                                                if (ePaid > 0) Text("മറ്റു ചിലവുകൾ", fontWeight = FontWeight.Medium)
                                            }
                                            Text("Admin: $adminName", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            if (type == "RECEIVED") {
                                                val totalRec = (transaction["dailyPaidReceived"].toString().toDoubleOrNull() ?: 0.0) + (transaction["repaymentLoan"].toString().toDoubleOrNull() ?: 0.0) + (transaction["serviceCharge"].toString().toDoubleOrNull() ?: 0.0) + (transaction["otherIncome"].toString().toDoubleOrNull() ?: 0.0)
                                                Text("+ ₹${fmt(totalRec)}", color = Color(0xFF006400), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            } else {
                                                val totalPaid = (transaction["loanPaid"].toString().toDoubleOrNull() ?: 0.0) + (transaction["expensePaid"].toString().toDoubleOrNull() ?: 0.0)
                                                Text("- ₹${fmt(totalPaid)}", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(32.dp)) }
                    }
                }
            }
        }
    }
}