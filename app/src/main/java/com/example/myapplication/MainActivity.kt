package com.example.myapplication

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    // 1. ടൈമർ റീസെറ്റ് ചെയ്യാൻ വേണ്ടിയുള്ള വേരിയബിൾ
    private val lastInteractionTime = mutableLongStateOf(System.currentTimeMillis())

    // 2. സ്ക്രീനിൽ എവിടെ തൊട്ടാലും ഈ ഫംഗ്ഷൻ ടൈമർ റീസെറ്റ് ചെയ്യും
    override fun dispatchTouchEvent(ev: android.view.MotionEvent?): Boolean {
        lastInteractionTime.value = System.currentTimeMillis()
        return super.dispatchTouchEvent(ev)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        FirebaseApp.initializeApp(this)

        FirebaseMessaging.getInstance().subscribeToTopic("all_members")
            .addOnCompleteListener { task ->
                if (task.isSuccessful) Log.d("FCM", "Successfully subscribed to all_members topic")
            }

        val auth = FirebaseAuth.getInstance()
        val currentUserEmail = auth.currentUser?.email?.trim()?.lowercase()
        val sharedPrefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)

        val startDestination = when {
            currentUserEmail == null -> "login"
            else -> "welcome"
        }

        setContent {
            var isDarkTheme by remember { mutableStateOf(sharedPrefs.getBoolean("isDarkTheme", false)) }
            val toggleTheme = {
                isDarkTheme = !isDarkTheme
                sharedPrefs.edit().putBoolean("isDarkTheme", isDarkTheme).apply()
            }

            MyApplicationTheme(darkTheme = isDarkTheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val navController = rememberNavController()
                    val context = LocalContext.current

                    // 3. മൂന്ന് മിനിറ്റ് ഉപയോഗിക്കാതിരുന്നാൽ വെൽക്കം സ്ക്രീനിലേക്ക് മാറ്റാനുള്ള സിസ്റ്റം
                    val interactionTime by lastInteractionTime
                    LaunchedEffect(interactionTime) {
                        delay(3 * 60 * 1000L) // 3 മിനിറ്റ് സമയം (180,000 milliseconds)

                        val currentRoute = navController.currentDestination?.route
                        // നിലവിൽ ലോഗിൻ അല്ലെങ്കിൽ വെൽക്കം സ്ക്രീനിൽ അല്ലെങ്കിലോ മാത്രം ഇത് പ്രവർത്തിക്കും
                        if (currentRoute != "welcome" && currentRoute != "login") {
                            navController.navigate("welcome") {
                                popUpTo(0) // ബാക്ക്ഗ്രൗണ്ടിലുള്ള മറ്റു സ്ക്രീനുകൾ ക്ലിയർ ചെയ്യാൻ
                            }
                        }
                    }

                    var showDefaulterDialog by remember { mutableStateOf(false) }
                    var defaulterMessage by remember { mutableStateOf("") }

                    var showNoInternetDialog by remember { mutableStateOf(false) }
                    var showSlowInternetWarning by remember { mutableStateOf(false) }

                    // തത്സമയം ഇൻ്റർനെറ്റ് പരിശോധിക്കുന്ന ലൈവ് സിസ്റ്റം
                    DisposableEffect(Unit) {
                        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

                        val networkCallback = object : ConnectivityManager.NetworkCallback() {
                            override fun onAvailable(network: Network) {
                                showNoInternetDialog = false // ഇൻ്റർനെറ്റ് വരുമ്പോൾ വാണിംഗ് മാറ്റുക
                            }

                            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                                if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                                    showNoInternetDialog = false
                                    val downSpeed = networkCapabilities.linkDownstreamBandwidthKbps
                                    showSlowInternetWarning = downSpeed in 1..499
                                }
                            }

                            override fun onLost(network: Network) {
                                showNoInternetDialog = true // ഇൻ്റർനെറ്റ് പോകുമ്പോൾ വാണിംഗ് കാണിക്കുക
                                showSlowInternetWarning = false
                            }
                        }

                        val request = NetworkRequest.Builder()
                            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                            .build()
                        cm.registerNetworkCallback(request, networkCallback)

                        // ആപ്പ് ഓപ്പൺ ആകുമ്പോഴുള്ള പരിശോധന
                        val activeNetwork = cm.activeNetwork
                        if (activeNetwork == null) {
                            showNoInternetDialog = true
                        }

                        onDispose {
                            cm.unregisterNetworkCallback(networkCallback)
                        }
                    }

                    // ഫയർബേസ് ഡാറ്റ പരിശോധിക്കുന്ന ഭാഗം
                    LaunchedEffect(Unit) {
                        val db = FirebaseFirestore.getInstance()
                        db.collection("app_settings").document("limits").get().addOnSuccessListener { doc ->
                            val loanBlockDays = doc.getLong("loanBlockDays") ?: 21L

                            db.collection("users").whereEqualTo("role", "Member").get().addOnSuccessListener { userResult ->
                                val defaulters = mutableListOf<String>()
                                val format = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault())
                                val today = Date()

                                for (userDoc in userResult) {
                                    val mName = userDoc.getString("name") ?: continue
                                    val bal = userDoc.getDouble("current_balance") ?: 0.0
                                    val lastDateStr = userDoc.getString("last_transaction_date") ?: ""

                                    if (bal > 0 && lastDateStr.isNotEmpty()) {
                                        try {
                                            val pastDate = format.parse(lastDateStr)
                                            if (pastDate != null) {
                                                val diffInMillis = today.time - pastDate.time
                                                val daysPassed = TimeUnit.DAYS.convert(diffInMillis, TimeUnit.MILLISECONDS)
                                                if (daysPassed >= loanBlockDays) {
                                                    defaulters.add(mName)
                                                }
                                            }
                                        } catch (e: Exception) {}
                                    }
                                }

                                if (defaulters.isNotEmpty()) {
                                    val namesList = defaulters.joinToString(", ")
                                    defaulterMessage = "$namesList വായ്പ തിരിച്ചടവ് മുടക്കിയതിനാൽ പുതിയ ലോൺ കൊടുക്കുന്നത് താൽക്കാലികമായി നിർത്തിവയ്ക്കുന്നു."
                                    showDefaulterDialog = true
                                }
                            }
                        }
                    }

                    // ഇൻ്റർനെറ്റ് വാണിംഗ് ബോക്സുകൾ
                    if (showNoInternetDialog) {
                        AlertDialog(
                            onDismissRequest = { },
                            confirmButton = {
                                TextButton(onClick = { (context as? Activity)?.finish() }) {
                                    Text("Exit", fontWeight = FontWeight.Bold, color = Color.Red)
                                }
                            },
                            title = { Text("No Internet Connection", fontWeight = FontWeight.Bold) },
                            text = { Text("ഈ ആപ്പ് പ്രവർത്തിക്കാൻ ഇൻ്റർനെറ്റ് നിർബന്ധമാണ്. ദയവായി ഇൻ്റർനെറ്റ് ഓൺ ചെയ്ത് വീണ്ടും ശ്രമിക്കുക.") },
                            containerColor = Color.White
                        )
                    }

                    if (showSlowInternetWarning) {
                        AlertDialog(
                            onDismissRequest = { showSlowInternetWarning = false },
                            confirmButton = {
                                TextButton(onClick = { showSlowInternetWarning = false }) {
                                    Text("OK", fontWeight = FontWeight.Bold)
                                }
                            },
                            title = { Text("Slow Internet Warning", fontWeight = FontWeight.Bold) },
                            text = { Text("നിങ്ങളുടെ ഇൻ്റർനെറ്റ് വേഗത വളരെ കുറവാണ്. ആപ്പ് പ്രവർത്തിക്കാൻ ചിലപ്പോൾ താമസം നേരിട്ടേക്കാം.") },
                            containerColor = Color.White
                        )
                    }

                    if (showDefaulterDialog) {
                        AlertDialog(
                            onDismissRequest = { },
                            confirmButton = {
                                TextButton(onClick = { showDefaulterDialog = false }) {
                                    Text("OK", fontWeight = FontWeight.Bold, color = Color.Red)
                                }
                            },
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, contentDescription = "Warning", tint = Color.Red)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("WARNING!", color = Color.Red, fontWeight = FontWeight.Bold)
                                }
                            },
                            text = { Text(defaulterMessage, style = MaterialTheme.typography.bodyLarge, color = Color.DarkGray) },
                            containerColor = Color(0xFFFFEBEE)
                        )
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        if (defaulterMessage.isNotEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().background(Color.Red).padding(vertical = 6.dp, horizontal = 12.dp)) {
                                Text(
                                    text = "അലർട്ട്: $defaulterMessage",
                                    color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.basicMarquee()
                                )
                            }
                        }

                        NavHost(navController = navController, startDestination = startDestination, modifier = Modifier.weight(1f)) {
                            composable("login") { LoginScreen(onLoginSuccess = { navController.navigate("welcome") { popUpTo("login") { inclusive = true } } }) }
                            composable("welcome") { WelcomeScreen(onNavigate = { route -> navController.navigate(route) { popUpTo("welcome") { inclusive = true } } }) }

                            composable("admin") {
                                AdminScreen(
                                    isDarkTheme = isDarkTheme,
                                    onThemeToggle = toggleTheme,
                                    onLogout = { auth.signOut(); navController.navigate("login") { popUpTo("admin") { inclusive = true } } },
                                    onNavigateToSettings = { navController.navigate("settings") },
                                    onNavigateToTransaction = { navController.navigate("transaction") },
                                    onNavigateToReports = { navController.navigate("reports") }
                                )
                            }

                            composable("home/{email}") { backStackEntry ->
                                val email = backStackEntry.arguments?.getString("email") ?: ""
                                MembersScreen(
                                    userEmail = email,
                                    isDarkTheme = isDarkTheme,
                                    onThemeToggle = toggleTheme,
                                    onLogout = { auth.signOut(); navController.navigate("login") { popUpTo("home/{email}") { inclusive = true } } }
                                )
                            }

                            composable("settings") { SettingsScreen(onNavigateBack = { navController.popBackStack() }) }
                            composable("transaction") { TransactionScreen(onNavigateBack = { navController.popBackStack() }) }
                            composable("reports") { ReportsScreen(onNavigateBack = { navController.popBackStack() }) }
                        }
                    }
                }
            }
        }
    }
}