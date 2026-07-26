package com.example.myapplication

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlin.random.Random

class MyFirebaseMessagingService : FirebaseMessagingService() {

    // ഫയർബേസിൽ നിന്ന് വരുന്ന മെസ്സേജ് സ്വീകരിക്കുന്ന ഭാഗം
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val title = remoteMessage.notification?.title ?: remoteMessage.data["title"] ?: "KATWF Alert"
        val message = remoteMessage.notification?.body ?: remoteMessage.data["message"] ?: ""

        showNotification(title, message)
    }

    // ഫോണിൽ നോട്ടിഫിക്കേഷൻ കാണിക്കാനുള്ള കോഡ്
    private fun showNotification(title: String, message: String) {
        val channelId = "KATWF_ALERTS"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // ആൻഡ്രോയിഡ് 8 (Oreo) മുതലുള്ള ഫോണുകൾക്ക് Notification Channel നിർബന്ധമാണ്
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Loan Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        // നോട്ടിഫിക്കേഷനിൽ ക്ലിക്ക് ചെയ്താൽ ആപ്പ് തുറക്കാൻ
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert) // വേണമെങ്കിൽ ആപ്പിന്റെ ഐക്കൺ കൊടുക്കാം
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)

        val notificationId = Random.nextInt()
        notificationManager.notify(notificationId, notificationBuilder.build())
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // പുതിയ ടോക്കൺ ലഭിച്ചാൽ ലോഗ് ചെയ്യാനുള്ള ഭാഗം (നിലവിൽ ആവശ്യമില്ല)
    }
}