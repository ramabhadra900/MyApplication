package com.example.myapplication

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.Calendar

@Composable
fun WelcomeScreen(onNavigate: (String) -> Unit) {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val currentUserEmail = auth.currentUser?.email?.trim()?.lowercase() ?: ""
    val systemAdminEmail = "ramabhadra900@gmail.com"

    val currentYear = remember { Calendar.getInstance().get(Calendar.YEAR) }

    // അനിമേഷന് വേണ്ടി വലുപ്പം 0 ആയി സെറ്റ് ചെയ്യുന്നു
    val scale = remember { Animatable(0f) }

    var userName by remember { mutableStateOf("") }
    var userRole by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        // 1. പേര് വലുതാകുന്ന അനിമേഷൻ (1 സെക്കൻഡ് എടുക്കും)
        scale.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1000)
        )

        // 2. ഫയർബേസിൽ നിന്ന് ആളുടെ പേരും റോളും എടുക്കുന്നു
        if (currentUserEmail.isNotEmpty()) {
            if (currentUserEmail == systemAdminEmail) {
                userName = "System Admin"
                userRole = "Admin"
                isLoading = false
            } else {
                try {
                    val document = db.collection("users").document(currentUserEmail).get().await()
                    if (document.exists()) {
                        userName = document.getString("name") ?: "Member"
                        userRole = document.getString("role") ?: "Member"
                    }
                } catch (e: Exception) {
                    userName = "Member"
                    userRole = "Member"
                } finally {
                    isLoading = false
                }
            }
        } else {
            onNavigate("login")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ആനിമേറ്റ് ചെയ്യുന്ന KATWF ടെക്സ്റ്റ്
        Text(
            text = "KATWF $currentYear",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.scale(scale.value)
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (isLoading) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text("വിവരങ്ങൾ പരിശോധിക്കുന്നു...", style = MaterialTheme.typography.bodyMedium)
        } else {
            // ആളുടെ പേരും റോളും വെച്ചുള്ള സ്വാഗത വാചകം
            Text(
                text = "നമസ്കാരം, $userName! 🙏",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "($userRole)",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(40.dp))

            // ആപ്പിലേക്ക് പ്രവേശിക്കാനുള്ള ബട്ടൺ
            Button(
                onClick = {
                    if (userRole == "Admin" || currentUserEmail == systemAdminEmail) {
                        onNavigate("admin")
                    } else {
                        onNavigate("home/$currentUserEmail")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(55.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("ആപ്പിലേക്ക് പ്രവേശിക്കുക", fontSize = 18.sp)
            }
        }
    }
}