package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
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
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.*

fun uriToBase64(context: Context, uri: Uri): String {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val originalBitmap = BitmapFactory.decodeStream(inputStream)
        val scale = 200f / maxOf(originalBitmap.width, originalBitmap.height)
        val scaledBitmap = Bitmap.createScaledBitmap(
            originalBitmap,
            (originalBitmap.width * scale).toInt(),
            (originalBitmap.height * scale).toInt(),
            true
        )
        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 60, outputStream)
        val byteArray = outputStream.toByteArray()
        Base64.encodeToString(byteArray, Base64.DEFAULT)
    } catch (e: Exception) {
        ""
    }
}

private fun getAdminDynamicPercentage(): Double {
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
fun AdminScreen(
    isDarkTheme: Boolean,
    onThemeToggle: () -> Unit,
    onLogout: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToTransaction: () -> Unit,
    onNavigateToReports: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf("Member") }

    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var photoBase64 by remember { mutableStateOf("") }

    var showUserList by remember { mutableStateOf(false) }
    var currentScreen by remember { mutableStateOf("Dashboard") }
    var showResetDialog by remember { mutableStateOf(false) }

    var showNoticeDialog by remember { mutableStateOf(false) }
    var adminNoticeMessage by remember { mutableStateOf("") }
    var globalNoticeMessage by remember { mutableStateOf("") }

    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()

    val currentUserEmail = auth.currentUser?.email?.trim()?.lowercase()
    val systemAdminEmail = "ramabhadra900@gmail.com".lowercase()
    val isSystemAdmin = (currentUserEmail == systemAdminEmail)

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val privacyPolicyUrl = "https://sites.google.com/view/katwf-privacy-policy"

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
            photoBase64 = uriToBase64(context, uri)
        }
    }

    DisposableEffect(Unit) {
        val noticeListener = db.collection("app_settings").document("notice_board").addSnapshotListener { snapshot, _ ->
            if (snapshot != null && snapshot.exists() && snapshot.getBoolean("isActive") == true) {
                globalNoticeMessage = snapshot.getString("message") ?: ""
                adminNoticeMessage = globalNoticeMessage
            } else {
                globalNoticeMessage = ""
                adminNoticeMessage = ""
            }
        }
        onDispose { noticeListener.remove() }
    }

    if (showNoticeDialog) {
        AlertDialog(
            onDismissRequest = { showNoticeDialog = false },
            title = { Text("നോട്ടീസ് ബോർഡ്", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    if (isSystemAdmin) {
                        Text("ഇവിടെ നൽകുന്ന സന്ദേശം എല്ലാവർക്കും കാണാം.", fontSize = 13.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(value = adminNoticeMessage, onValueChange = { adminNoticeMessage = it }, label = { Text("സന്ദേശം ടൈപ്പ് ചെയ്യുക") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                    } else {
                        Text(text = globalNoticeMessage.ifEmpty { "പുതിയ സന്ദേശങ്ങൾ ഒന്നുമില്ല." }, fontSize = 16.sp)
                    }
                }
            },
            confirmButton = {
                if (isSystemAdmin) {
                    TextButton(onClick = {
                        val noticeData = hashMapOf("message" to adminNoticeMessage, "timestamp" to FieldValue.serverTimestamp(), "isActive" to adminNoticeMessage.isNotBlank())
                        db.collection("app_settings").document("notice_board").set(noticeData).addOnSuccessListener { Toast.makeText(context, "നോട്ടീസ് സേവ് ചെയ്തു!", Toast.LENGTH_SHORT).show(); showNoticeDialog = false }
                    }) { Text("സേവ് ചെയ്യുക", fontWeight = FontWeight.Bold) }
                } else {
                    TextButton(onClick = { showNoticeDialog = false }) { Text("OK") }
                }
            },
            dismissButton = {
                if (isSystemAdmin) {
                    TextButton(onClick = { db.collection("app_settings").document("notice_board").update("isActive", false); Toast.makeText(context, "പഴയ നോട്ടീസ് നീക്കം ചെയ്തു", Toast.LENGTH_SHORT).show(); showNoticeDialog = false }) { Text("Clear Notice", color = Color.Red) }
                }
            }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
                Spacer(modifier = Modifier.height(24.dp))
                Text("Admin Menu", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                NavigationDrawerItem(
                    label = { Text("DASHBOARD (ഹോം)", fontWeight = FontWeight.Bold) },
                    selected = currentScreen == "Dashboard",
                    onClick = { currentScreen = "Dashboard"; scope.launch { drawerState.close() } },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                if (isSystemAdmin) {
                    NavigationDrawerItem(
                        label = { Text("TRANSACTION HISTORY", fontWeight = FontWeight.Bold) },
                        selected = currentScreen == "History",
                        onClick = { currentScreen = "History"; scope.launch { drawerState.close() } },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )

                    NavigationDrawerItem(
                        label = { Text("App Settings (Limits & %)", fontWeight = FontWeight.Bold) },
                        selected = currentScreen == "Settings",
                        onClick = { currentScreen = "Settings"; scope.launch { drawerState.close() } },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )

                    NavigationDrawerItem(
                        label = { Text("YEARLY RESET (റീസെറ്റ്)", color = Color.Red, fontWeight = FontWeight.Bold) },
                        selected = false,
                        onClick = { scope.launch { drawerState.close() }; showResetDialog = true },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

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
                    title = {
                        Text(
                            when(currentScreen) {
                                "History" -> "Transaction History"
                                "Settings" -> "App Settings & Limits"
                                else -> "Admin Dashboard"
                            }
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showNoticeDialog = true }) {
                            Icon(Icons.Default.Notifications, contentDescription = "Notice Board", tint = Color(0xFFFFD700))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .imePadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                if (globalNoticeMessage.isNotEmpty() && currentScreen == "Dashboard") {
                    Box(modifier = Modifier.fillMaxWidth().background(Color(0xFFFFF9C4), RoundedCornerShape(8.dp)).padding(vertical = 8.dp, horizontal = 12.dp)) {
                        Text(text = "🔔 $globalNoticeMessage", color = Color(0xFFE65100), fontWeight = FontWeight.Bold, modifier = Modifier.basicMarquee())
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                when (currentScreen) {
                    "History" -> {
                        if (isSystemAdmin) TransactionHistoryList(db, context)
                    }
                    "Settings" -> {
                        if (isSystemAdmin) AppSettingsSection(db, context)
                    }
                    else -> {
                        Button(
                            onClick = { onNavigateToTransaction() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth().height(60.dp)
                        ) { Text("TRANSACTION ENTRY ഫോം തുറക്കുക", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimary) }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { onNavigateToReports() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                            modifier = Modifier.fillMaxWidth().height(60.dp)
                        ) { Text("VIEW REPORTS (റിപ്പോർട്ടുകൾ)", style = MaterialTheme.typography.titleMedium, color = Color.White) }

                        Spacer(modifier = Modifier.height(20.dp))

                        if (isSystemAdmin) {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("SYSTEM ADMIN ONLY", color = Color.Red, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Button(
                                        onClick = { showUserList = !showUserList },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF006400)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text(if (showUserList) "അക്കൗണ്ട് മാനേജ്മെന്റ് മറയ്ക്കുക" else "പുതിയ അക്കൗണ്ട് ഉണ്ടാക്കുക / കാണുക", color = Color.White) }
                                }
                            }

                            if (showUserList) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                            RadioButton(selected = selectedRole == "Admin", onClick = { selectedRole = "Admin" })
                                            Text("Admin")
                                            Spacer(modifier = Modifier.width(16.dp))
                                            RadioButton(selected = selectedRole == "Member", onClick = { selectedRole = "Member" })
                                            Text("Member")
                                        }
                                        Spacer(modifier = Modifier.height(16.dp))

                                        Box(
                                            modifier = Modifier
                                                .size(100.dp)
                                                .clip(CircleShape)
                                                .background(Color.LightGray)
                                                .clickable { imagePickerLauncher.launch("image/*") },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            val bitmap = remember(photoBase64) {
                                                if (photoBase64.isNotEmpty()) {
                                                    try {
                                                        val bytes = Base64.decode(photoBase64, Base64.DEFAULT)
                                                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size).asImageBitmap()
                                                    } catch (e: Exception) { null }
                                                } else null
                                            }

                                            if (bitmap != null) {
                                                Image(bitmap = bitmap, contentDescription = "Profile Photo", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                            } else {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Icon(Icons.Default.Person, contentDescription = "Add Photo", tint = Color.Gray, modifier = Modifier.size(40.dp))
                                                    Text("ഫോട്ടോ", fontSize = 12.sp, color = Color.Gray)
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(16.dp))

                                        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("പേര് (Name)") }, modifier = Modifier.fillMaxWidth())
                                        Spacer(modifier = Modifier.height(8.dp))
                                        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("ഇമെയിൽ (Email)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
                                        Spacer(modifier = Modifier.height(8.dp))
                                        OutlinedTextField(value = mobile, onValueChange = { mobile = it }, label = { Text("മൊബൈൽ നമ്പർ (Mobile)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
                                        Spacer(modifier = Modifier.height(8.dp))
                                        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("പാസ്‌വേഡ് (Password)") }, modifier = Modifier.fillMaxWidth())

                                        TextButton(onClick = { password = (100000..999999).random().toString() }) { Text("പാസ്‌വേഡ് സ്വയം നിർമ്മിക്കുക") }
                                        Spacer(modifier = Modifier.height(16.dp))

                                        Button(
                                            onClick = {
                                                if (email.isNotEmpty() && password.isNotEmpty()) {
                                                    auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener { task ->
                                                        if (task.isSuccessful) {
                                                            val userMap = hashMapOf(
                                                                "name" to name,
                                                                "role" to selectedRole,
                                                                "email" to email,
                                                                "mobile" to mobile,
                                                                "password" to password,
                                                                "photo" to photoBase64,
                                                                "current_balance" to 0.0,
                                                                "last_transaction_date" to ""
                                                            )
                                                            db.collection("users").document(email).set(userMap)
                                                            Toast.makeText(context, "$selectedRole അക്കൗണ്ട് ഉണ്ടാക്കി!", Toast.LENGTH_SHORT).show()
                                                            shareDetails(context, name, selectedRole, email, mobile, password)
                                                            name = ""; email = ""; mobile = ""; password = ""; photoBase64 = ""; selectedImageUri = null
                                                        } else { Toast.makeText(context, "Error: ${task.exception?.message}", Toast.LENGTH_LONG).show() }
                                                    }
                                                } else { Toast.makeText(context, "ഇമെയിലും പാസ്‌വേഡും നൽകുക!", Toast.LENGTH_SHORT).show() }
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) { Text("അക്കൗണ്ട് ഉണ്ടാക്കുക") }
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))

                                RegisteredUsersList(db, context)
                            }
                        } else {
                            Text("💡 നിങ്ങൾക്ക് ട്രാൻസാക്ഷൻ എൻട്രി ചെയ്യാവുന്നതാണ്.", color = Color.Gray, modifier = Modifier.padding(top = 16.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(60.dp))
            }
        }

        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                title = { Row(verticalAlignment = Alignment.CenterVertically) { Text("⚠️ മുന്നറിയിപ്പ്!", color = Color.Red, fontWeight = FontWeight.Bold) }},
                text = { Text("നിങ്ങൾ ഈ വർഷത്തെ കണക്കുകൾ പൂർണ്ണമായും മായ്ച്ചു കളയാൻ പോകുകയാണ്. മുന്നോട്ട് പോകുന്നതിന് മുൻപ് ഈ വർഷത്തെ സമ്മറി ഷെയർ ചെയ്യുക.") },
                confirmButton = {
                    Button(
                        onClick = {
                            Toast.makeText(context, "റീസെറ്റ് ആരംഭിക്കുന്നു... ദയവായി കാത്തിരിക്കുക.", Toast.LENGTH_LONG).show()

                            db.collection("users").whereEqualTo("role", "Member").get().addOnSuccessListener { users ->
                                var summaryText = "KATWF വാർഷിക കണക്കുകൾ (Yearly Summary)\n\n"
                                val batch = db.batch()

                                for(u in users) {
                                    val memberName = u.getString("name") ?: "Unknown"
                                    val balance = u.getDouble("current_balance") ?: 0.0
                                    summaryText += "$memberName: ബാക്കി ₹$balance\n"
                                    batch.update(u.reference, "current_balance", 0.0)
                                    batch.update(u.reference, "last_transaction_date", "")
                                }

                                db.collection("transactions").get().addOnSuccessListener { txs ->
                                    for(tx in txs) { batch.delete(tx.reference) }

                                    batch.commit().addOnSuccessListener {
                                        Toast.makeText(context, "റീസെറ്റ് വിജയകരമായി പൂർത്തിയായി!", Toast.LENGTH_LONG).show()
                                        showResetDialog = false
                                        val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, summaryText) }
                                        context.startActivity(Intent.createChooser(intent, "സമ്മറി ഷെയർ ചെയ്യുക"))
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) { Text("RESET NOW") }
                },
                dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text("CANCEL") } }
            )
        }
    }
}

// ===============================================
// APP SETTINGS SECTION (DYNAMIC PERCENTAGE & LIMITS)
// ===============================================
@Composable
fun AppSettingsSection(db: FirebaseFirestore, context: Context) {
    var isDynamicEnabled by remember { mutableStateOf(true) }
    var customPercentage by remember { mutableStateOf("150") }

    var maxMonthlyPaid by remember { mutableStateOf("3000.0") }
    var maxDailyPaid by remember { mutableStateOf("500.0") }
    var minScPercent by remember { mutableStateOf("0.50") }
    var dailyScPercent by remember { mutableStateOf("0.07142857") }
    var serviceChargeDueDays by remember { mutableStateOf("7") }
    var loanBlockDays by remember { mutableStateOf("21") }
    var minRepaymentAmount by remember { mutableStateOf("100.0") } // പുതിയ വേരിയബിൾ

    var isLoading by remember { mutableStateOf(true) }

    val currentDynamicPercent = (getAdminDynamicPercentage() * 100).toInt()

    LaunchedEffect(Unit) {
        db.collection("app_settings").document("limits").get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                isDynamicEnabled = doc.getBoolean("isDynamicPercentageEnabled") ?: true
                customPercentage = (doc.getDouble("customPercentage")?.times(100))?.toInt()?.toString() ?: "150"

                maxMonthlyPaid = doc.getDouble("maxMonthlyPaid")?.toString() ?: "3000.0"
                maxDailyPaid = doc.getDouble("maxDailyPaid")?.toString() ?: "500.0"
                minScPercent = doc.getDouble("minScPercent")?.toString() ?: "0.50"
                dailyScPercent = doc.getDouble("dailyScPercent")?.toString() ?: "0.07142857"
                serviceChargeDueDays = doc.getLong("serviceChargeDueDays")?.toString() ?: "7"
                loanBlockDays = doc.getLong("loanBlockDays")?.toString() ?: "21"
                minRepaymentAmount = doc.getDouble("minRepaymentAmount")?.toString() ?: "100.0"
            }
            isLoading = false
        }
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        Column(modifier = Modifier.fillMaxWidth()) {
            Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("അഡ്വാൻസ് എലിജിബിലിറ്റി സെറ്റിംഗ്സ്", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 18.sp)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Dynamic Percentage ഉപയോഗിക്കുക", fontWeight = FontWeight.Bold)
                            Text("(തിയതിക്കനുസരിച്ച് തനിയെ മാറുന്ന ശതമാനം)", fontSize = 12.sp, color = Color.Gray)
                        }
                        Switch(checked = isDynamicEnabled, onCheckedChange = { isDynamicEnabled = it })
                    }

                    Spacer(modifier = Modifier.height(16.dp))

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
                            value = customPercentage,
                            onValueChange = { customPercentage = it },
                            label = { Text("Custom Percentage (%) നൽകുക") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("ലിമിറ്റുകൾ & സർവീസ് ചാർജ്", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 18.sp)
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
                    OutlinedTextField(value = loanBlockDays, onValueChange = { loanBlockDays = it }, label = { Text("ADVANCE BLOCKING DAYS") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = minRepaymentAmount, onValueChange = { minRepaymentAmount = it }, label = { Text("MINIMUM REPAYMENT AMOUNT (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    val pct = customPercentage.toDoubleOrNull()?.div(100.0) ?: 1.5
                    val updates = hashMapOf<String, Any>(
                        "isDynamicPercentageEnabled" to isDynamicEnabled,
                        "customPercentage" to pct,
                        "maxMonthlyPaid" to (maxMonthlyPaid.toDoubleOrNull() ?: 0.0),
                        "maxDailyPaid" to (maxDailyPaid.toDoubleOrNull() ?: 0.0),
                        "minScPercent" to (minScPercent.toDoubleOrNull() ?: 0.50),
                        "dailyScPercent" to (dailyScPercent.toDoubleOrNull() ?: 0.07142857),
                        "serviceChargeDueDays" to (serviceChargeDueDays.toLongOrNull() ?: 7L),
                        "loanBlockDays" to (loanBlockDays.toLongOrNull() ?: 21L),
                        "minRepaymentAmount" to (minRepaymentAmount.toDoubleOrNull() ?: 100.0)
                    )

                    db.collection("app_settings").document("limits").set(updates)
                        .addOnSuccessListener { Toast.makeText(context, "സെറ്റിംഗ്സ് വിജയകരമായി സേവ് ചെയ്തു!", Toast.LENGTH_SHORT).show() }
                        .addOnFailureListener { e -> Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show() }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) { Text("SAVE SETTINGS", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        }
    }
}

@Composable
fun RegisteredUsersList(db: FirebaseFirestore, context: Context) {
    var usersList by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var userToEdit by remember { mutableStateOf<Map<String, Any>?>(null) }
    var userToDelete by remember { mutableStateOf<Map<String, Any>?>(null) }

    DisposableEffect(Unit) {
        val listener = db.collection("users").addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                val list = mutableListOf<Map<String, Any>>()
                for (document in snapshot) { list.add(document.data) }
                usersList = list
            }
        }
        onDispose { listener.remove() }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("രജിസ്റ്റർ ചെയ്ത ആളുകൾ:", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
        usersList.forEach { user ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val photoBase64 = user["photo"]?.toString() ?: ""
                        val bitmap = remember(photoBase64) {
                            if (photoBase64.isNotEmpty()) {
                                try {
                                    val bytes = Base64.decode(photoBase64, Base64.DEFAULT)
                                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size).asImageBitmap()
                                } catch (e: Exception) { null }
                            } else null
                        }

                        Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.LightGray), contentAlignment = Alignment.Center) {
                            if (bitmap != null) Image(bitmap = bitmap, contentDescription = "Photo", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            else Icon(Icons.Default.Person, contentDescription = "No Photo", tint = Color.Gray)
                        }
                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text("${user["name"]}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("${user["role"]} | ${user["mobile"]}", color = Color.Gray, fontSize = 12.sp)
                            Text("${user["email"]}", fontSize = 12.sp)
                            Text("Pass: ${user["password"]}", color = Color.Red, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(
                            onClick = { shareDetails(context, user["name"].toString(), user["role"].toString(), user["email"].toString(), user["mobile"].toString(), user["password"].toString()) },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF006400))
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("SHARE")
                        }

                        Row {
                            TextButton(onClick = { userToEdit = user }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("EDIT")
                            }
                            TextButton(onClick = { userToDelete = user }, colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("DELETE")
                            }
                        }
                    }
                }
            }
        }
    }

    userToEdit?.let { user ->
        EditUserDialog(
            user = user,
            onDismiss = { userToEdit = null },
            onSave = { updatedUser ->
                val email = updatedUser["email"].toString()
                db.collection("users").document(email).update(updatedUser).addOnSuccessListener {
                    Toast.makeText(context, "വിവരങ്ങൾ വിജയകരമായി പുതുക്കി!", Toast.LENGTH_SHORT).show()
                    userToEdit = null
                }
            }
        )
    }

    userToDelete?.let { user ->
        val email = user["email"].toString()
        val name = user["name"].toString()
        AlertDialog(
            onDismissRequest = { userToDelete = null },
            title = { Text("ഡിലീറ്റ് ചെയ്യണോ?", color = Color.Red) },
            text = { Text("നിങ്ങൾ '$name' എന്ന യൂസറെ പൂർണ്ണമായും മായ്ച്ചു കളയാൻ പോകുകയാണ്. തുടരണോ?") },
            confirmButton = {
                Button(onClick = {
                    db.collection("users").document(email).delete().addOnSuccessListener {
                        Toast.makeText(context, "അക്കൗണ്ട് ഡിലീറ്റ് ചെയ്തു!", Toast.LENGTH_SHORT).show()
                        userToDelete = null
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("അതെ, DELETE") }
            },
            dismissButton = { TextButton(onClick = { userToDelete = null }) { Text("ക്യാൻസൽ") } }
        )
    }
}

@Composable
fun EditUserDialog(user: Map<String, Any>, onDismiss: () -> Unit, onSave: (Map<String, Any>) -> Unit) {
    var editName by remember { mutableStateOf(user["name"]?.toString() ?: "") }
    var editMobile by remember { mutableStateOf(user["mobile"]?.toString() ?: "") }
    var editPassword by remember { mutableStateOf(user["password"]?.toString() ?: "") }
    var editRole by remember { mutableStateOf(user["role"]?.toString() ?: "Member") }
    var editPhotoBase64 by remember { mutableStateOf(user["photo"]?.toString() ?: "") }

    val context = LocalContext.current
    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) { editPhotoBase64 = uriToBase64(context, uri) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("അക്കൗണ്ട് തിരുത്തുക") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier.size(80.dp).clip(CircleShape).background(Color.LightGray).clickable { imagePickerLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    val bitmap = remember(editPhotoBase64) {
                        if (editPhotoBase64.isNotEmpty()) {
                            try {
                                val bytes = Base64.decode(editPhotoBase64, Base64.DEFAULT)
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size).asImageBitmap()
                            } catch (e: Exception) { null }
                        } else null
                    }
                    if (bitmap != null) Image(bitmap = bitmap, contentDescription = "Profile", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Icon(Icons.Default.Person, contentDescription = "Add Photo", tint = Color.Gray)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("ഫോട്ടോ മാറ്റാൻ മുകളിൽ ക്ലിക്ക് ചെയ്യുക", fontSize = 10.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    RadioButton(selected = editRole == "Admin", onClick = { editRole = "Admin" })
                    Text("Admin")
                    Spacer(modifier = Modifier.width(16.dp))
                    RadioButton(selected = editRole == "Member", onClick = { editRole = "Member" })
                    Text("Member")
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = editName, onValueChange = { editName = it }, label = { Text("പേര്") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = editMobile, onValueChange = { editMobile = it }, label = { Text("മൊബൈൽ നമ്പർ") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = editPassword, onValueChange = { editPassword = it }, label = { Text("പാസ്‌വേഡ്") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = {
                val updatedUser = user.toMutableMap()
                updatedUser["name"] = editName
                updatedUser["mobile"] = editMobile
                updatedUser["role"] = editRole
                updatedUser["password"] = editPassword
                updatedUser["photo"] = editPhotoBase64
                onSave(updatedUser)
            }) { Text("SAVE CHANGES") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } }
    )
}

@Composable
fun TransactionHistoryList(db: FirebaseFirestore, context: Context) {
    var transactions by remember { mutableStateOf<List<com.google.firebase.firestore.DocumentSnapshot>>(emptyList()) }
    var showDeleteDialog by remember { mutableStateOf<com.google.firebase.firestore.DocumentSnapshot?>(null) }

    LaunchedEffect(Unit) {
        db.collection("transactions").addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                transactions = snapshot.documents.sortedByDescending { it.getTimestamp("timestamp")?.seconds ?: 0L }
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("അവസാനത്തെ ഇടപാടുകൾ (History):", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))

        if (transactions.isEmpty()) Text("നിലവിൽ എൻട്രികൾ ഒന്നുമില്ല.", color = Color.Gray)

        transactions.forEach { doc ->
            val date = doc.getString("date") ?: ""
            val admin = doc.getString("adminName") ?: ""
            val member = doc.getString("memberName") ?: ""
            val type = doc.getString("type") ?: ""
            val description = doc.getString("description") ?: ""

            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("തീയതി: $date", fontWeight = FontWeight.Bold)
                    Text("മെമ്പർ: $member | അഡ്മിൻ: $admin", color = Color.DarkGray, fontSize = 14.sp)
                    Text("ടൈപ്പ്: $type", color = if (type == "RECEIVED") Color(0xFF006400) else Color.Red, fontWeight = FontWeight.Bold)

                    Spacer(modifier = Modifier.height(4.dp))

                    if (type == "RECEIVED") {
                        Text("Daily: ₹${doc.getDouble("dailyPaidReceived") ?: 0.0} | Adv Repayment: ₹${doc.getDouble("advanceRepayment") ?: 0.0}", fontSize = 14.sp)
                        Text("SC: ₹${doc.getDouble("serviceCharge") ?: 0.0} | Other: ₹${doc.getDouble("otherIncome") ?: 0.0}", fontSize = 14.sp)
                    } else {
                        Text("Advance: ₹${doc.getDouble("advancePaid") ?: 0.0} | Expense: ₹${doc.getDouble("expensePaid") ?: 0.0}", fontSize = 14.sp)
                    }

                    if (description.isNotEmpty()) Text("വിവരണം: $description", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { showDeleteDialog = doc },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        modifier = Modifier.fillMaxWidth().height(40.dp)
                    ) { Text("ഡിലീറ്റ് ചെയ്യുക (DELETE)") }
                }
            }
        }
    }

    showDeleteDialog?.let { doc ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("ഡിലീറ്റ് ചെയ്യണോ?", color = Color.Red, fontWeight = FontWeight.Bold) },
            text = { Text("ഈ എൻട്രി പൂർണ്ണമായും ഡിലീറ്റ് ചെയ്യപ്പെടും. മെമ്പറുടെ അഡ്വാൻസ് (Advance) ബാലൻസും അഡ്മിന്റെ കയ്യിലുള്ള തുകയും ഓട്ടോമാറ്റിക് ആയി പഴയതുപോലെ ആകും.\n\nശ്രദ്ധിക്കുക: ഗൂഗിൾ ഷീറ്റിൽ നിന്നും നിങ്ങൾ ഈ എൻട്രി മാനുവൽ ആയി ഡിലീറ്റ് ചെയ്യണം. തുടരണോ?") },
            confirmButton = {
                Button(
                    onClick = {
                        val memberName = doc.getString("memberName") ?: ""
                        val type = doc.getString("type") ?: ""
                        val repaymentLoan = doc.getDouble("advanceRepayment") ?: 0.0
                        val loanPaid = doc.getDouble("advancePaid") ?: 0.0

                        db.collection("users").whereEqualTo("name", memberName).get().addOnSuccessListener { res ->
                            if (!res.isEmpty) {
                                val userDoc = res.documents[0]
                                val currentBal = userDoc.getDouble("current_balance") ?: 0.0

                                var newBal = currentBal
                                if (type == "RECEIVED") newBal += repaymentLoan
                                else if (type == "PAID") newBal -= loanPaid

                                if (newBal < 0) newBal = 0.0

                                db.collection("users").document(userDoc.id).update("current_balance", newBal).addOnSuccessListener {
                                    db.collection("transactions").document(doc.id).delete().addOnSuccessListener {
                                        Toast.makeText(context, "വിജയകരമായി ഡിലീറ്റ് ചെയ്തു! ഗൂഗിൾ ഷീറ്റിൽ നിന്നും മാറ്റാൻ മറക്കരുത്.", Toast.LENGTH_LONG).show()
                                        showDeleteDialog = null
                                    }
                                }
                            } else {
                                db.collection("transactions").document(doc.id).delete().addOnSuccessListener {
                                    Toast.makeText(context, "ട്രാൻസാക്ഷൻ ഡിലീറ്റ് ചെയ്തു!", Toast.LENGTH_SHORT).show()
                                    showDeleteDialog = null
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) { Text("അതെ, DELETE ചെയ്യുക") }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = null }) { Text("CANCEL") } }
        )
    }
}

fun shareDetails(context: Context, name: String, role: String, email: String, mobile: String, pass: String) {
    val message = "നമസ്കാരം $name,\nനിങ്ങളെ KATWF ആപ്പിൽ $role ആയി ചേർത്തിട്ടുണ്ട്.\n\nലോഗിൻ വിവരങ്ങൾ:\nഇമെയിൽ: $email\nമൊബൈൽ: $mobile\nപാസ്‌വേഡ്: $pass"
    val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, message) }
    val chooser = Intent.createChooser(intent, "വിവരങ്ങൾ ഷെയർ ചെയ്യുക")
    try { context.startActivity(chooser) } catch (e: Exception) { Toast.makeText(context, "ഷെയർ ചെയ്യാൻ കഴിഞ്ഞില്ല!", Toast.LENGTH_SHORT).show() }
}