package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth

@Composable
fun AdminScreen(onLogout: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf("Member") }

    val context = LocalContext.currentth = FirebaseAuth.getInstance()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("System Admin Dashboard", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selectedRole == "Admin", onClick = { selectedRole = "Admin" })
                Text("Admin")
                Spacer(modifier = Modifier.width(16.dp))
                RadioButton(selected = selectedRole == "Member", onClick = { selectedRole = "Member" })
                Text("Member")
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("പേര് (Name)") }, modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = email, onValueChange = { email = it },
                label = { Text("ഇമെയിൽ (Email)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = mobile, onValueChange = { mobile = it },
                label = { Text("മൊബൈൽ നമ്പർ (Mobile)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = password, onValueChange = { password = it },
                label = { Text("പാസ്‌വേഡ് (Password)") }, modifier = Modifier.fillMaxWidth()
            )

            TextButton(onClick = { password = (100000..999999).random().toString() }) {
                Text("പാസ്‌വേഡ് സ്വയം നിർമ്മിക്കുക")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (email.isNotEmpty() && password.isNotEmpty()) {
                        auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                Toast.makeText(context, "$selectedRole അക്കൗണ്ട് ഉണ്ടാക്കി!", Toast.LENGTH_SHORT).show()
                                shareDetailsToWhatsApp(context, name, selectedRole, email, mobile, password)
                                name = ""; email = ""; mobile = ""; password = ""
                            } else {
                                Toast.makeText(context, "Error: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        Toast.makeText(context, "ഇമെയിലും പാസ്‌വേഡും നൽകുക!", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("അക്കൗണ്ട് ഉണ്ടാക്കുക & ഷെയർ ചെയ്യുക")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = onLogout, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                Text("Logout")
            }
        }
    }
}

fun shareDetailsToWhatsApp(context: Context, name: String, role: String, email: String, mobile: String, pass: String) {
    val message = "നമസ്കാരം $name,\nനിങ്ങളെ KATWF ആപ്പിൽ $role ആയി ചേർത്തിട്ടുണ്ട്.\n\nലോഗിൻ വിവരങ്ങൾ:\nഇമെയിൽ: $email\nമൊബൈൽ: $mobile\nപാസ്‌വേഡ്: $pass"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, message)
        setPackage("com.whatsapp")
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "വാട്‌സാപ്പ് ഇൻസ്റ്റാൾ ചെയ്തിട്ടില്ല!", Toast.LENGTH_SHORT).show()
    }
}