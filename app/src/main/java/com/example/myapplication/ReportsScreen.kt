package com.example.myapplication

import android.content.ContentValues
import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

private fun fmt(v: Double): String = "${Math.round(v)}.00"

// നടപ്പു വർഷത്തെ ജനുവരി 1 തനിയെ കണ്ടുപിടിക്കുന്ന ഫംഗ്ഷൻ
private fun getCurrentYearStartDate(): String {
    val year = Calendar.getInstance().get(Calendar.YEAR)
    return "01/01/$year"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(onNavigateBack: () -> Unit) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("SUMMARY", "DAILY", "MONTHLY", "MEMBER", "FULL HISTORY", "CLOSING")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("REPORTS", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { onNavigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                edgePadding = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                text = title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (selectedTabIndex) {
                0 -> OverallSummarySection()
                1 -> DailyReportSection()
                2 -> MonthlySummarySection()
                3 -> MemberSummarySection()
                4 -> FullHistorySection()
                5 -> ClosingReportSection()
            }
        }
    }
}

// ==========================================
// REUSABLE COMPONENTS
// ==========================================
@Composable
fun UniformSummaryCard(
    title: String,
    totalDailyPaid: Double,
    totalServiceCharge: Double,
    totalAdvanceRepayment: Double,
    totalOtherIncome: Double,
    totalAdvancePaid: Double,
    totalExpense: Double,
    netBalance: Double
) {
    var expanded by remember { mutableStateOf(false) }
    val totalReceived = totalDailyPaid + totalServiceCharge + totalAdvanceRepayment + totalOtherIncome
    val totalPaid = totalAdvancePaid + totalExpense

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = "Expand",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("TOTAL RECEIVED :", fontWeight = FontWeight.Bold, color = Color.Gray)
                Text("₹${fmt(totalReceived)}", color = Color(0xFF006400), fontWeight = FontWeight.ExtraBold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("TOTAL PAID :", fontWeight = FontWeight.Bold, color = Color.Gray)
                Text("₹${fmt(totalPaid)}", color = Color.Red, fontWeight = FontWeight.ExtraBold)
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Text("വിശദാംശങ്ങൾ", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge, color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Daily Paid:"); Text("₹${fmt(totalDailyPaid)}") }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Service Charge:"); Text("₹${fmt(totalServiceCharge)}") }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Adv Repayment:"); Text("₹${fmt(totalAdvanceRepayment)}") }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Other Income:"); Text("₹${fmt(totalOtherIncome)}") }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Advance Issued:"); Text("₹${fmt(totalAdvancePaid)}", color = Color.Red) }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Expense:"); Text("₹${fmt(totalExpense)}", color = Color.Red) }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("NET BALANCE:", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("₹${fmt(netBalance)}", fontWeight = FontWeight.ExtraBold, color = if(netBalance >= 0) Color(0xFF006400) else Color.Red, style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
fun UniformTransactionCard(transaction: Map<String, Any>) {
    var expanded by remember { mutableStateOf(false) }

    val date = transaction["date"]?.toString() ?: ""
    val type = transaction["type"]?.toString() ?: ""
    val memberName = transaction["memberName"]?.toString() ?: ""
    val adminName = transaction["adminName"]?.toString() ?: "ADMIN"

    val dp = transaction["dailyPaidReceived"].toString().toDoubleOrNull() ?: 0.0
    val sc = transaction["serviceCharge"].toString().toDoubleOrNull() ?: 0.0
    val rep = transaction["advanceRepayment"].toString().toDoubleOrNull() ?: 0.0
    val oi = transaction["otherIncome"].toString().toDoubleOrNull() ?: 0.0
    val lp = transaction["advancePaid"].toString().toDoubleOrNull() ?: 0.0
    val ep = transaction["expensePaid"].toString().toDoubleOrNull() ?: 0.0

    val totalAmount = if (type == "RECEIVED") (dp + sc + rep + oi) else (lp + ep)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { expanded = !expanded },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(date, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text("M: $memberName", fontWeight = FontWeight.Bold)
                    Text("A: $adminName", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(type, fontWeight = FontWeight.Bold, color = if (type == "RECEIVED") Color(0xFF006400) else Color.Red)
                    Text("₹${fmt(totalAmount)}", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
                    if (dp > 0) Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Daily Paid:"); Text("₹${fmt(dp)}") }
                    if (sc > 0) Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Service Charge:"); Text("₹${fmt(sc)}") }
                    if (rep > 0) Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Adv Repayment:"); Text("₹${fmt(rep)}") }
                    if (oi > 0) Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Other Income:"); Text("₹${fmt(oi)}") }
                    if (lp > 0) Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Advance:"); Text("₹${fmt(lp)}", color = Color.Red) }
                    if (ep > 0) Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Expense:"); Text("₹${fmt(ep)}", color = Color.Red) }
                }
            }
        }
    }
}

// ==========================================
// 1. OVERALL SUMMARY SECTION
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverallSummarySection() {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()

    var transactions by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var totalDailyPaid by remember { mutableDoubleStateOf(0.0) }
    var totalAdvanceRepayment by remember { mutableDoubleStateOf(0.0) }
    var totalServiceCharge by remember { mutableDoubleStateOf(0.0) }
    var totalOtherIncome by remember { mutableDoubleStateOf(0.0) }
    var totalAdvancePaid by remember { mutableDoubleStateOf(0.0) }
    var totalExpense by remember { mutableDoubleStateOf(0.0) }
    var netBalance by remember { mutableDoubleStateOf(0.0) }

    var adminBalances by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }

    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    // ഇവിടെ നടപ്പുവർഷം ജനുവരി 1 എടുക്കുന്നു
    var fromDate by remember { mutableStateOf(getCurrentYearStartDate()) }
    var toDate by remember { mutableStateOf(dateFormat.format(Date())) }

    var showFromDatePicker by remember { mutableStateOf(false) }
    var showToDatePicker by remember { mutableStateOf(false) }
    val fromDatePickerState = rememberDatePickerState()
    val toDatePickerState = rememberDatePickerState()

    LaunchedEffect(fromDate, toDate) {
        isLoading = true
        db.collection("users").whereEqualTo("role", "Admin").get().addOnSuccessListener { adminResult ->
            val validAdmins = adminResult.documents.mapNotNull { it.getString("name")?.uppercase() }

            db.collection("transactions").get().addOnSuccessListener { result ->
                var dp = 0.0; var rl = 0.0; var sc = 0.0; var oi = 0.0
                var lp = 0.0; var ep = 0.0
                val adminBals = mutableMapOf<String, Double>()
                val filteredList = mutableListOf<Map<String, Any>>()

                val fromD = try { dateFormat.parse(fromDate) } catch (e: Exception) { null }
                val toD = try { dateFormat.parse(toDate) } catch (e: Exception) { null }

                for (doc in result) {
                    val trDate = try { dateFormat.parse(doc.getString("date")?.take(10) ?: "") } catch(e: Exception) { null }

                    if (trDate != null && fromD != null && toD != null && !trDate.before(fromD) && !trDate.after(toD)) {
                        filteredList.add(doc.data)

                        val adminName = doc.getString("adminName")?.uppercase() ?: "UNKNOWN"
                        val type = doc.getString("type") ?: ""

                        val tDp = doc.getDouble("dailyPaidReceived") ?: 0.0
                        val tRl = doc.getDouble("advanceRepayment") ?: 0.0
                        val tSc = doc.getDouble("serviceCharge") ?: 0.0
                        val tOi = doc.getDouble("otherIncome") ?: 0.0
                        val tLp = doc.getDouble("advancePaid") ?: 0.0
                        val tEp = doc.getDouble("expensePaid") ?: 0.0

                        var txNet = 0.0
                        if (type == "RECEIVED") {
                            dp += tDp; rl += tRl; sc += tSc; oi += tOi
                            txNet = tDp + tRl + tSc + tOi
                        } else if (type == "PAID") {
                            lp += tLp; ep += tEp
                            txNet = -(tLp + tEp)
                        }

                        if (validAdmins.contains(adminName)) {
                            adminBals[adminName] = (adminBals[adminName] ?: 0.0) + txNet
                        }
                    }
                }

                totalDailyPaid = dp
                totalAdvanceRepayment = rl
                totalServiceCharge = sc
                totalOtherIncome = oi
                totalAdvancePaid = lp
                totalExpense = ep
                netBalance = (dp + rl + sc + oi) - (lp + ep)
                adminBalances = adminBals
                transactions = filteredList.sortedBy { it["date"].toString() }
                isLoading = false
            }
        }
    }

    if (showFromDatePicker) { DatePickerDialog(onDismissRequest = { showFromDatePicker = false }, confirmButton = { TextButton(onClick = { fromDatePickerState.selectedDateMillis?.let { fromDate = dateFormat.format(Date(it)) }; showFromDatePicker = false }) { Text("OK") } }) { DatePicker(state = fromDatePickerState) } }
    if (showToDatePicker) { DatePickerDialog(onDismissRequest = { showToDatePicker = false }, confirmButton = { TextButton(onClick = { toDatePickerState.selectedDateMillis?.let { toDate = dateFormat.format(Date(it)) }; showToDatePicker = false }) { Text("OK") } }) { DatePicker(state = toDatePickerState) } }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(onClick = { showFromDatePicker = true }, modifier = Modifier.weight(1f).padding(end = 4.dp)) { Text("From:\n$fromDate", style = MaterialTheme.typography.bodySmall) }
            OutlinedButton(onClick = { showToDatePicker = true }, modifier = Modifier.weight(1f).padding(start = 4.dp)) { Text("To:\n$toDate", style = MaterialTheme.typography.bodySmall) }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            Button(
                onClick = { generatePdfReport(context, "SUMMARY REPORT", "Period: $fromDate To $toDate", transactions) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF006400))
            ) { Text("DOWNLOAD SUMMARY PDF", style = MaterialTheme.typography.titleMedium, color = Color.White) }

            Spacer(modifier = Modifier.height(16.dp))

            UniformSummaryCard("ആകെ സാമ്പത്തിക റിപ്പോർട്ട്", totalDailyPaid, totalServiceCharge, totalAdvanceRepayment, totalOtherIncome, totalAdvancePaid, totalExpense, netBalance)

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("അഡ്മിന്മാരുടെ കയ്യിലുള്ള ബാലൻസ്", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    var index = 1
                    for ((admin, bal) in adminBalances) {
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("$index. $admin", fontWeight = FontWeight.Medium)
                            Text("₹${fmt(bal)}", fontWeight = FontWeight.Bold, color = if(bal >= 0) Color(0xFF006400) else Color.Red)
                        }
                        index++
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("KATWF BALANCE:", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        Text("₹${fmt(netBalance)}", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = if(netBalance >= 0) Color(0xFF006400) else Color.Red)
                    }
                }
            }
        }
    }
}

// ==========================================
// 2. DAILY REPORT SECTION
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyReportSection() {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    var transactions by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    var selectedDate by remember { mutableStateOf(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())) }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()

    fun fetchDailyData(dateToFetch: String) {
        isLoading = true
        db.collection("transactions").orderBy("timestamp", Query.Direction.DESCENDING).get().addOnSuccessListener { result ->
            val list = mutableListOf<Map<String, Any>>()
            for (doc in result) {
                if (doc.getString("date")?.startsWith(dateToFetch) == true) list.add(doc.data)
            }
            transactions = list
            isLoading = false
        }.addOnFailureListener { isLoading = false }
    }

    LaunchedEffect(selectedDate) { fetchDailyData(selectedDate) }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        val cal = Calendar.getInstance().apply { timeInMillis = it }
                        selectedDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(cal.time)
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = datePickerState) }
    }

    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.DateRange, contentDescription = "Select Date")
            Spacer(modifier = Modifier.width(8.dp))
            Text("തീയതി മാറ്റുക: $selectedDate", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else if (transactions.isEmpty()) {
            Text("ഈ തീയതിയിൽ ഇടപാടുകൾ ഒന്നും നടന്നിട്ടില്ല.", color = Color.Gray)
        } else {
            Button(
                onClick = { generatePdfReport(context, "DAILY REPORT", "Date: $selectedDate", transactions) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF006400))
            ) { Text("DOWNLOAD DAILY PDF", style = MaterialTheme.typography.titleMedium, color = Color.White) }

            Spacer(modifier = Modifier.height(16.dp))
            LazyColumn { items(transactions) { UniformTransactionCard(it) } }
        }
    }
}

// ==========================================
// 3. MONTHLY SUMMARY SECTION
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlySummarySection() {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    var transactions by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    var totalDailyPaid by remember { mutableDoubleStateOf(0.0) }
    var totalAdvanceRepayment by remember { mutableDoubleStateOf(0.0) }
    var totalServiceCharge by remember { mutableDoubleStateOf(0.0) }
    var totalOtherIncome by remember { mutableDoubleStateOf(0.0) }
    var totalAdvancePaid by remember { mutableDoubleStateOf(0.0) }
    var totalExpense by remember { mutableDoubleStateOf(0.0) }

    val months = listOf("01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "11", "12")
    var selectedMonth by remember { mutableStateOf(SimpleDateFormat("MM", Locale.getDefault()).format(Date())) }
    var selectedYear by remember { mutableStateOf(SimpleDateFormat("yyyy", Locale.getDefault()).format(Date())) }
    var expandedMonth by remember { mutableStateOf(false) }

    LaunchedEffect(selectedMonth, selectedYear) {
        isLoading = true
        db.collection("transactions").get().addOnSuccessListener { result ->
            var dp = 0.0; var rl = 0.0; var sc = 0.0; var oi = 0.0; var lp = 0.0; var ep = 0.0
            val searchString = "/$selectedMonth/$selectedYear"
            val list = mutableListOf<Map<String, Any>>()

            for (doc in result) {
                if (doc.getString("date")?.contains(searchString) == true) {
                    list.add(doc.data)
                    val type = doc.getString("type") ?: ""
                    if (type == "RECEIVED") {
                        dp += doc.getDouble("dailyPaidReceived") ?: 0.0
                        rl += doc.getDouble("advanceRepayment") ?: 0.0
                        sc += doc.getDouble("serviceCharge") ?: 0.0
                        oi += doc.getDouble("otherIncome") ?: 0.0
                    } else if (type == "PAID") {
                        lp += doc.getDouble("advancePaid") ?: 0.0
                        ep += doc.getDouble("expensePaid") ?: 0.0
                    }
                }
            }
            totalDailyPaid = dp; totalAdvanceRepayment = rl; totalServiceCharge = sc; totalOtherIncome = oi; totalAdvancePaid = lp; totalExpense = ep
            transactions = list.sortedBy { it["date"].toString() }
            isLoading = false
        }
    }

    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ExposedDropdownMenuBox(expanded = expandedMonth, onExpandedChange = { expandedMonth = !expandedMonth }, modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = "മാസം: $selectedMonth - വർഷം: $selectedYear", onValueChange = {}, readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedMonth) }, modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = expandedMonth, onDismissRequest = { expandedMonth = false }) {
                    months.forEach { month -> DropdownMenuItem(text = { Text(month) }, onClick = { selectedMonth = month; expandedMonth = false }) }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else {
            Button(
                onClick = { generatePdfReport(context, "MONTHLY REPORT", "Month: $selectedMonth / Year: $selectedYear", transactions) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF006400))
            ) { Text("DOWNLOAD MONTHLY PDF", style = MaterialTheme.typography.titleMedium, color = Color.White) }

            Spacer(modifier = Modifier.height(16.dp))

            val netBal = (totalDailyPaid + totalAdvanceRepayment + totalServiceCharge + totalOtherIncome) - (totalAdvancePaid + totalExpense)
            UniformSummaryCard("മാസ റിപ്പോർട്ട്", totalDailyPaid, totalServiceCharge, totalAdvanceRepayment, totalOtherIncome, totalAdvancePaid, totalExpense, netBal)
        }
    }
}

// ==========================================
// 4. MEMBER SUMMARY SECTION (REPORTS ONLY)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberSummarySection() {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    var memberNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedMember by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    var transactions by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var totalDailyPaid by remember { mutableDoubleStateOf(0.0) }
    var totalAdvanceRepayment by remember { mutableDoubleStateOf(0.0) }
    var totalServiceCharge by remember { mutableDoubleStateOf(0.0) }
    var totalOtherIncome by remember { mutableDoubleStateOf(0.0) }
    var totalAdvancePaid by remember { mutableDoubleStateOf(0.0) }
    var totalExpense by remember { mutableDoubleStateOf(0.0) }

    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    // ഇവിടെ നടപ്പുവർഷം ജനുവരി 1 എടുക്കുന്നു
    var fromDate by remember { mutableStateOf(getCurrentYearStartDate()) }
    var toDate by remember { mutableStateOf(dateFormat.format(Date())) }

    var showFromDatePicker by remember { mutableStateOf(false) }
    var showToDatePicker by remember { mutableStateOf(false) }
    val fromDatePickerState = rememberDatePickerState()
    val toDatePickerState = rememberDatePickerState()

    LaunchedEffect(Unit) {
        db.collection("users").whereEqualTo("role", "Member").get().addOnSuccessListener { result ->
            memberNames = result.documents.mapNotNull { it.getString("name") }
        }
    }

    LaunchedEffect(selectedMember, fromDate, toDate) {
        if (selectedMember.isEmpty()) return@LaunchedEffect
        db.collection("transactions").whereEqualTo("memberName", selectedMember).get().addOnSuccessListener { result ->
            var dp = 0.0; var rl = 0.0; var sc = 0.0; var oi = 0.0; var lp = 0.0; var ep = 0.0
            val filteredList = mutableListOf<Map<String, Any>>()
            val fromD = dateFormat.parse(fromDate); val toD = dateFormat.parse(toDate)

            for (doc in result) {
                val trDate = try { dateFormat.parse(doc.getString("date")?.take(10) ?: "") } catch(e: Exception) { null }
                if (trDate != null && fromD != null && toD != null && !trDate.before(fromD) && !trDate.after(toD)) {
                    filteredList.add(doc.data)
                    val type = doc.getString("type") ?: ""
                    if (type == "RECEIVED") {
                        dp += doc.getDouble("dailyPaidReceived") ?: 0.0
                        rl += doc.getDouble("advanceRepayment") ?: 0.0
                        sc += doc.getDouble("serviceCharge") ?: 0.0
                        oi += doc.getDouble("otherIncome") ?: 0.0
                    } else if (type == "PAID") {
                        lp += doc.getDouble("advancePaid") ?: 0.0
                        ep += doc.getDouble("expensePaid") ?: 0.0
                    }
                }
            }
            totalDailyPaid = dp; totalAdvanceRepayment = rl; totalServiceCharge = sc; totalOtherIncome = oi; totalAdvancePaid = lp; totalExpense = ep
            transactions = filteredList.sortedBy { it["date"].toString() }
        }
    }

    if (showFromDatePicker) { DatePickerDialog(onDismissRequest = { showFromDatePicker = false }, confirmButton = { TextButton(onClick = { fromDatePickerState.selectedDateMillis?.let { fromDate = dateFormat.format(Date(it)) }; showFromDatePicker = false }) { Text("OK") } }) { DatePicker(state = fromDatePickerState) } }
    if (showToDatePicker) { DatePickerDialog(onDismissRequest = { showToDatePicker = false }, confirmButton = { TextButton(onClick = { toDatePickerState.selectedDateMillis?.let { toDate = dateFormat.format(Date(it)) }; showToDatePicker = false }) { Text("OK") } }) { DatePicker(state = toDatePickerState) } }

    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(
                value = selectedMember, onValueChange = { }, readOnly = true, label = { Text("MEMBER NAME") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                memberNames.forEach { name -> DropdownMenuItem(text = { Text(name) }, onClick = { selectedMember = name; expanded = false }) }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(onClick = { showFromDatePicker = true }, modifier = Modifier.weight(1f).padding(end = 4.dp)) { Text("From:\n$fromDate", style = MaterialTheme.typography.bodySmall) }
            OutlinedButton(onClick = { showToDatePicker = true }, modifier = Modifier.weight(1f).padding(start = 4.dp)) { Text("To:\n$toDate", style = MaterialTheme.typography.bodySmall) }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (selectedMember.isNotEmpty()) {
            Button(
                onClick = { generatePdfReport(context, "PERSONAL REPORT - $selectedMember", "Period: $fromDate To $toDate", transactions) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF006400))
            ) { Text("DOWNLOAD MEMBER PDF", style = MaterialTheme.typography.titleMedium, color = Color.White) }

            Spacer(modifier = Modifier.height(16.dp))

            val netBal = (totalDailyPaid + totalAdvanceRepayment + totalServiceCharge + totalOtherIncome) - (totalAdvancePaid + totalExpense)
            UniformSummaryCard("$selectedMember യുടെ റിപ്പോർട്ട്", totalDailyPaid, totalServiceCharge, totalAdvanceRepayment, totalOtherIncome, totalAdvancePaid, totalExpense, netBal)

            Spacer(modifier = Modifier.height(16.dp))
            LazyColumn(modifier = Modifier.weight(1f)) { items(transactions) { UniformTransactionCard(it) } }
        }
    }
}

// ==========================================
// 5. FULL HISTORY SECTION
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullHistorySection() {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()

    var allTransactions by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var displayedTransactions by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    // ഇവിടെ നടപ്പുവർഷം ജനുവരി 1 എടുക്കുന്നു
    var fromDate by remember { mutableStateOf(getCurrentYearStartDate()) }
    var toDate by remember { mutableStateOf(dateFormat.format(Date())) }

    var showFromDatePicker by remember { mutableStateOf(false) }
    var showToDatePicker by remember { mutableStateOf(false) }
    val fromDatePickerState = rememberDatePickerState()
    val toDatePickerState = rememberDatePickerState()

    var allMemberNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var allAdminNames by remember { mutableStateOf<List<String>>(emptyList()) }

    var selectedMembers by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedAdmins by remember { mutableStateOf<Set<String>>(emptySet()) }

    var tempSelectedMembers by remember { mutableStateOf<Set<String>>(emptySet()) }
    var tempSelectedAdmins by remember { mutableStateOf<Set<String>>(emptySet()) }

    var showAdminDialog by remember { mutableStateOf(false) }
    var showMemberDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        db.collection("users").get().addOnSuccessListener { result ->
            val mList = mutableListOf<String>()
            val aList = mutableListOf<String>()
            for (doc in result) {
                val role = doc.getString("role") ?: ""
                val name = doc.getString("name") ?: ""
                if (role == "Member" && name.isNotEmpty()) mList.add(name)
                if (role == "Admin" && name.isNotEmpty()) aList.add(name.uppercase())
            }
            allMemberNames = mList
            allAdminNames = aList
        }

        db.collection("transactions").orderBy("timestamp", Query.Direction.DESCENDING).get().addOnSuccessListener { result ->
            val list = mutableListOf<Map<String, Any>>()
            for (doc in result) {
                list.add(doc.data)
            }
            allTransactions = list
            isLoading = false
        }.addOnFailureListener { isLoading = false }
    }

    LaunchedEffect(allTransactions, fromDate, toDate, selectedMembers, selectedAdmins) {
        val fromD = try { dateFormat.parse(fromDate) } catch (e: Exception) { null }
        val toD = try { dateFormat.parse(toDate) } catch (e: Exception) { null }

        val filtered = allTransactions.filter { tr ->
            var dateMatches = false
            val trDateStr = tr["date"]?.toString()?.take(10) ?: ""
            val trDate = try { dateFormat.parse(trDateStr) } catch(e: Exception) { null }

            if (trDate != null && fromD != null && toD != null) {
                if (!trDate.before(fromD) && !trDate.after(toD)) {
                    dateMatches = true
                }
            }

            val trMem = tr["memberName"]?.toString() ?: ""
            val trAdm = tr["adminName"]?.toString()?.uppercase() ?: ""

            val memMatches = if (selectedMembers.isEmpty()) true else selectedMembers.contains(trMem)
            val admMatches = if (selectedAdmins.isEmpty()) true else selectedAdmins.contains(trAdm)

            dateMatches && memMatches && admMatches
        }
        displayedTransactions = filtered
    }

    if (showFromDatePicker) { DatePickerDialog(onDismissRequest = { showFromDatePicker = false }, confirmButton = { TextButton(onClick = { fromDatePickerState.selectedDateMillis?.let { fromDate = dateFormat.format(Date(it)) }; showFromDatePicker = false }) { Text("OK") } }) { DatePicker(state = fromDatePickerState) } }
    if (showToDatePicker) { DatePickerDialog(onDismissRequest = { showToDatePicker = false }, confirmButton = { TextButton(onClick = { toDatePickerState.selectedDateMillis?.let { toDate = dateFormat.format(Date(it)) }; showToDatePicker = false }) { Text("OK") } }) { DatePicker(state = toDatePickerState) } }

    if (showAdminDialog) {
        AlertDialog(onDismissRequest = { showAdminDialog = false }, title = { Text("അഡ്മിന്മാരെ സെലക്ട് ചെയ്യുക", fontSize = 16.sp, fontWeight = FontWeight.Bold) }, text = { LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) { items(allAdminNames) { admin -> val isChecked = tempSelectedAdmins.contains(admin); Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { tempSelectedAdmins = if (isChecked) tempSelectedAdmins - admin else tempSelectedAdmins + admin }) { Checkbox(checked = isChecked, onCheckedChange = null); Text(admin, modifier = Modifier.padding(start = 8.dp)) } } } }, confirmButton = { TextButton(onClick = { selectedAdmins = tempSelectedAdmins; showAdminDialog = false }) { Text("APPLY") } }, dismissButton = { TextButton(onClick = { tempSelectedAdmins = emptySet(); selectedAdmins = emptySet(); showAdminDialog = false }) { Text("CLEAR ALL", color = Color.Red) } })
    }

    if (showMemberDialog) {
        AlertDialog(onDismissRequest = { showMemberDialog = false }, title = { Text("മെമ്പർമാരെ സെലക്ട് ചെയ്യുക", fontSize = 16.sp, fontWeight = FontWeight.Bold) }, text = { LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) { items(allMemberNames) { member -> val isChecked = tempSelectedMembers.contains(member); Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { tempSelectedMembers = if (isChecked) tempSelectedMembers - member else tempSelectedMembers + member }) { Checkbox(checked = isChecked, onCheckedChange = null); Text(member, modifier = Modifier.padding(start = 8.dp)) } } } }, confirmButton = { TextButton(onClick = { selectedMembers = tempSelectedMembers; showMemberDialog = false }) { Text("APPLY") } }, dismissButton = { TextButton(onClick = { tempSelectedMembers = emptySet(); selectedMembers = emptySet(); showMemberDialog = false }) { Text("CLEAR ALL", color = Color.Red) } })
    }

    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(onClick = { showFromDatePicker = true }, modifier = Modifier.weight(1f).padding(end = 4.dp)) { Text("From:\n$fromDate", style = MaterialTheme.typography.bodySmall) }
            OutlinedButton(onClick = { showToDatePicker = true }, modifier = Modifier.weight(1f).padding(start = 4.dp)) { Text("To:\n$toDate", style = MaterialTheme.typography.bodySmall) }
        }
        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val adminBtnText = if (selectedAdmins.isEmpty()) "Admins: ALL" else "Admins: ${selectedAdmins.size} Selected"
            OutlinedButton(
                onClick = { tempSelectedAdmins = selectedAdmins; showAdminDialog = true },
                modifier = Modifier.weight(1f).padding(end = 4.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = if (selectedAdmins.isNotEmpty()) MaterialTheme.colorScheme.primary else Color.Gray)
            ) { Text(adminBtnText, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold) }

            val memberBtnText = if (selectedMembers.isEmpty()) "Members: ALL" else "Members: ${selectedMembers.size} Selected"
            OutlinedButton(
                onClick = { tempSelectedMembers = selectedMembers; showMemberDialog = true },
                modifier = Modifier.weight(1f).padding(start = 4.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = if (selectedMembers.isNotEmpty()) MaterialTheme.colorScheme.primary else Color.Gray)
            ) { Text(memberBtnText, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold) }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else if (displayedTransactions.isEmpty()) {
            Text("ഈ ഫിൽറ്ററുകളിൽ ഇടപാടുകൾ ഒന്നുമില്ല.", color = Color.Gray)
        } else {
            Button(
                onClick = {
                    val admText = if (selectedAdmins.isEmpty()) "ALL" else selectedAdmins.joinToString(", ")
                    val memText = if (selectedMembers.isEmpty()) "ALL" else selectedMembers.joinToString(", ")
                    val filterDetails = "From: $fromDate To: $toDate | Admins: $admText | Members: $memText"

                    generatePdfReport(context, "FILTERED HISTORY REPORT", filterDetails, displayedTransactions)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF006400))
            ) {
                Text("DOWNLOAD FILTERED PDF", style = MaterialTheme.typography.titleMedium, color = Color.White)
            }

            Spacer(modifier = Modifier.height(16.dp))
            LazyColumn(modifier = Modifier.weight(1f)) { items(displayedTransactions) { UniformTransactionCard(it) } }
        }
    }
}

// ==========================================
// 6. CLOSING SETTLEMENT SECTION (NEW TAB)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClosingReportSection() {
    val db = FirebaseFirestore.getInstance()
    var memberNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedMember by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    // Member Totals
    var memberDailyPaid by remember { mutableDoubleStateOf(0.0) }
    var memberAdvanceRepayment by remember { mutableDoubleStateOf(0.0) }
    var memberAdvancePaid by remember { mutableDoubleStateOf(0.0) }
    var memberServiceChargePaid by remember { mutableDoubleStateOf(0.0) }

    // Global Totals
    var globalDailyPaid by remember { mutableDoubleStateOf(0.0) }
    var globalServiceCharge by remember { mutableDoubleStateOf(0.0) }
    var globalOtherIncome by remember { mutableDoubleStateOf(0.0) }
    var globalAdvancePaid by remember { mutableDoubleStateOf(0.0) }
    var globalAdvanceRepayment by remember { mutableDoubleStateOf(0.0) }
    var globalExpense by remember { mutableDoubleStateOf(0.0) }
    var totalMembersCount by remember { mutableIntStateOf(1) }

    // Fetch Total Members Count
    LaunchedEffect(Unit) {
        db.collection("users").whereEqualTo("role", "Member").get().addOnSuccessListener { result ->
            val names = result.documents.mapNotNull { it.getString("name") }
            memberNames = names
            totalMembersCount = if (names.isNotEmpty()) names.size else 1
        }
    }

    // Fetch Global Totals
    LaunchedEffect(Unit) {
        db.collection("transactions").get().addOnSuccessListener { result ->
            var gDp = 0.0; var gSc = 0.0; var gOi = 0.0; var gLp = 0.0; var gEp = 0.0; var gRl = 0.0
            for (doc in result) {
                val type = doc.getString("type") ?: ""
                if (type == "RECEIVED") {
                    gDp += doc.getDouble("dailyPaidReceived") ?: 0.0
                    gSc += doc.getDouble("serviceCharge") ?: 0.0
                    gRl += doc.getDouble("advanceRepayment") ?: 0.0
                    gOi += doc.getDouble("otherIncome") ?: 0.0
                } else if (type == "PAID") {
                    gLp += doc.getDouble("advancePaid") ?: 0.0
                    gEp += doc.getDouble("expensePaid") ?: 0.0
                }
            }
            globalDailyPaid = gDp; globalServiceCharge = gSc; globalOtherIncome = gOi
            globalAdvancePaid = gLp; globalAdvanceRepayment = gRl; globalExpense = gEp
        }
    }

    // Fetch Selected Member Totals
    LaunchedEffect(selectedMember) {
        if (selectedMember.isEmpty()) return@LaunchedEffect
        db.collection("transactions").whereEqualTo("memberName", selectedMember).get().addOnSuccessListener { result ->
            var dp = 0.0; var rl = 0.0; var sc = 0.0; var lp = 0.0
            for (doc in result) {
                val type = doc.getString("type") ?: ""
                if (type == "RECEIVED") {
                    dp += doc.getDouble("dailyPaidReceived") ?: 0.0
                    rl += doc.getDouble("advanceRepayment") ?: 0.0
                    sc += doc.getDouble("serviceCharge") ?: 0.0
                } else if (type == "PAID") {
                    lp += doc.getDouble("advancePaid") ?: 0.0
                }
            }
            memberDailyPaid = dp; memberAdvanceRepayment = rl; memberServiceChargePaid = sc
            memberAdvancePaid = lp
        }
    }

    // CALCULATIONS
    val memberSharePercentage = if (globalDailyPaid > 0) (memberDailyPaid / globalDailyPaid) else 0.0
    val bonus = globalServiceCharge * memberSharePercentage
    val otherIncomeShare = globalOtherIncome / totalMembersCount
    val expenseShare = globalExpense / totalMembersCount
    val memberAdvanceBalance = memberAdvancePaid - memberAdvanceRepayment
    val nowServiceCharge = 0.0

    val normalClosing = (memberDailyPaid + bonus + otherIncomeShare) - (memberAdvanceBalance + expenseShare + nowServiceCharge)

    val globalAdvanceBalance = globalAdvancePaid - globalAdvanceRepayment
    val emergencyPercentage = if (globalDailyPaid > 0) (globalAdvanceBalance / globalDailyPaid) else 0.0
    val emergencyDeductionAmount = memberDailyPaid * emergencyPercentage
    val emergencyDailyPaid = memberDailyPaid - emergencyDeductionAmount
    val emergencyClosing = (emergencyDailyPaid + otherIncomeShare) - (memberAdvanceBalance + expenseShare + nowServiceCharge)

    Column(modifier = Modifier.padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState())) {

        Text("SETTLEMENT & CLOSING", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))

        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(
                value = selectedMember, onValueChange = { }, readOnly = true, label = { Text("SELECT MEMBER") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                memberNames.forEach { name -> DropdownMenuItem(text = { Text(name) }, onClick = { selectedMember = name; expanded = false }) }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (selectedMember.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("$selectedMember - CLOSING DETAILS", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary, fontSize = 16.sp)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.primary)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Total Daily Paid:"); Text("₹${fmt(memberDailyPaid)}", fontWeight = FontWeight.Bold) }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Bonus (My Share):"); Text("+ ₹${fmt(bonus)}", color = Color(0xFF006400)) }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Other Income Share:"); Text("+ ₹${fmt(otherIncomeShare)}", color = Color(0xFF006400)) }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Expense Share:"); Text("- ₹${fmt(expenseShare)}", color = Color.Red) }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Adv. Balance to Pay:"); Text("- ₹${fmt(memberAdvanceBalance)}", color = Color.Red) }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("NORMAL CLOSING:", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                        Text("₹${fmt(normalClosing)}", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = if(normalClosing >= 0) Color(0xFF006400) else Color.Red)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEAEA))) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("EMERGENCY CLOSING:", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color.Red)
                                Text("₹${fmt(emergencyClosing)}", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Color.Red)
                            }
                            Text("(*Deduction: ${fmt(emergencyPercentage * 100)}% applied on Daily Paid)", fontSize = 11.sp, color = Color.DarkGray, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
    }
}


// ==========================================
// UNIVERSAL PDF GENERATOR
// ==========================================
fun generatePdfReport(context: Context, reportTitle: String, subtitle: String, transactions: List<Map<String, Any>>) {
    val pdfDocument = PdfDocument()
    val pageInfo = PdfDocument.PageInfo.Builder(1123, 794, 1).create() // A4 Landscape
    val page = pdfDocument.startPage(pageInfo)
    val canvas = page.canvas
    val paint = Paint()

    // 1. HEADER
    try {
        val logoBitmap = android.graphics.BitmapFactory.decodeResource(context.resources, R.drawable.katwf_logo)
        val scaledLogo = android.graphics.Bitmap.createScaledBitmap(logoBitmap, 100, 100, false)
        canvas.drawBitmap(scaledLogo, 40f, 40f, paint)
    } catch (e: Exception) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = android.graphics.Color.GRAY
        canvas.drawRect(40f, 40f, 140f, 140f, paint)
        paint.style = Paint.Style.FILL
        paint.textSize = 14f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("[ LOGO ]", 90f, 95f, paint)
    }

    paint.textAlign = Paint.Align.LEFT
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    paint.textSize = 28f
    paint.color = android.graphics.Color.parseColor("#0D47A1")
    canvas.drawText("KATWF FINANCIAL SYSTEM", 400f, 80f, paint)

    paint.textSize = 20f
    paint.color = android.graphics.Color.BLACK
    canvas.drawText(reportTitle, 400f, 110f, paint)

    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    paint.textSize = 16f

    if (subtitle.length > 70) {
        canvas.drawText(subtitle.take(70) + "...", 400f, 140f, paint)
    } else {
        canvas.drawText(subtitle, 400f, 140f, paint)
    }

    canvas.drawLine(40f, 160f, 1083f, 160f, paint)

    // 2. TABLE HEADER
    val colX = floatArrayOf(40f, 80f, 180f, 300f, 420f, 510f, 590f, 680f, 770f, 860f, 950f)
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    paint.textSize = 14f

    canvas.drawText("Sl", colX[0], 190f, paint)
    canvas.drawText("Date", colX[1], 190f, paint)
    canvas.drawText("Member Name", colX[2], 190f, paint)
    canvas.drawText("Admin", colX[3], 190f, paint)
    canvas.drawText("Type", colX[4], 190f, paint)
    canvas.drawText("D.Paid", colX[5], 190f, paint)
    canvas.drawText("S.Charge", colX[6], 190f, paint)
    canvas.drawText("Adv Rep.", colX[7], 190f, paint)
    canvas.drawText("Other Inc.", colX[8], 190f, paint)
    canvas.drawText("Advance", colX[9], 190f, paint)
    canvas.drawText("Expense", colX[10], 190f, paint)

    canvas.drawLine(40f, 205f, 1083f, 205f, paint)

    // 3. TABLE DATA
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    var yPos = 230f

    var tDp = 0.0; var tSc = 0.0; var tRl = 0.0; var tOi = 0.0; var tLp = 0.0; var tEp = 0.0

    transactions.forEachIndexed { index, tr ->
        if (yPos > 730f) return@forEachIndexed

        val date = tr["date"]?.toString()?.take(10) ?: ""
        val name = tr["memberName"]?.toString()?.take(12) ?: ""
        val admin = tr["adminName"]?.toString()?.take(12) ?: "ADMIN"
        val type = tr["type"]?.toString() ?: ""

        val dp = tr["dailyPaidReceived"].toString().toDoubleOrNull() ?: 0.0
        val sc = tr["serviceCharge"].toString().toDoubleOrNull() ?: 0.0
        val rl = tr["advanceRepayment"].toString().toDoubleOrNull() ?: 0.0
        val oi = tr["otherIncome"].toString().toDoubleOrNull() ?: 0.0
        val lp = tr["advancePaid"].toString().toDoubleOrNull() ?: 0.0
        val ep = tr["expensePaid"].toString().toDoubleOrNull() ?: 0.0

        tDp += dp; tSc += sc; tRl += rl; tOi += oi; tLp += lp; tEp += ep

        paint.color = android.graphics.Color.BLACK
        canvas.drawText("${index + 1}", colX[0], yPos, paint)
        canvas.drawText(date, colX[1], yPos, paint)
        canvas.drawText(name, colX[2], yPos, paint)
        canvas.drawText(admin, colX[3], yPos, paint)

        paint.color = if (type == "RECEIVED") android.graphics.Color.parseColor("#006400") else android.graphics.Color.RED
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(type, colX[4], yPos, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.color = android.graphics.Color.BLACK

        canvas.drawText(if(dp>0) fmt(dp) else "-", colX[5], yPos, paint)
        canvas.drawText(if(sc>0) fmt(sc) else "-", colX[6], yPos, paint)
        canvas.drawText(if(rl>0) fmt(rl) else "-", colX[7], yPos, paint)
        canvas.drawText(if(oi>0) fmt(oi) else "-", colX[8], yPos, paint)
        canvas.drawText(if(lp>0) fmt(lp) else "-", colX[9], yPos, paint)
        canvas.drawText(if(ep>0) fmt(ep) else "-", colX[10], yPos, paint)

        yPos += 30f
    }

    // 4. TOTALS ROW
    canvas.drawLine(40f, yPos, 1083f, yPos, paint)
    yPos += 25f

    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    paint.color = android.graphics.Color.parseColor("#0D47A1")
    canvas.drawText("TOTALS:", colX[3], yPos, paint)
    canvas.drawText(fmt(tDp), colX[5], yPos, paint)
    canvas.drawText(fmt(tSc), colX[6], yPos, paint)
    canvas.drawText(fmt(tRl), colX[7], yPos, paint)
    canvas.drawText(fmt(tOi), colX[8], yPos, paint)
    canvas.drawText(fmt(tLp), colX[9], yPos, paint)
    canvas.drawText(fmt(tEp), colX[10], yPos, paint)

    pdfDocument.finishPage(page)

    try {
        val timeStamp = SimpleDateFormat("ddMMyyHHmmss", Locale.getDefault()).format(Date())
        val fileName = "KATWF_${reportTitle.replace(" ", "_")}_$timeStamp.pdf"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            uri?.let { context.contentResolver.openOutputStream(it)?.use { out -> pdfDocument.writeTo(out) } }
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val file = File(downloadsDir, fileName)
            FileOutputStream(file).use { out -> pdfDocument.writeTo(out) }
        }
        Toast.makeText(context, "റിപ്പോർട്ട് Downloads-ൽ സേവ് ചെയ്തു: $fileName", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Log.e("PdfGen", "Error generating PDF: ${e.message}")
        Toast.makeText(context, "PDF Error: ${e.message}", Toast.LENGTH_LONG).show()
    } finally {
        pdfDocument.close()
    }
}