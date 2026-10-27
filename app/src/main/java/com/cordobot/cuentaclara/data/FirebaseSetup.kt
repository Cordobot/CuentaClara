package com.cordobot.cuentaclara.data

import android.content.Context
import com.cordobot.cuentaclara.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings

object FirebaseSetup {

    // "demo-" projects only exist in the Local Emulator Suite: nothing reaches Google servers
    private const val DEMO_PROJECT = "demo-cuentaclara"

    fun init(context: Context) {
        if (FirebaseApp.getApps(context).isEmpty()) {
            // No google-services.json: start a demo app that only talks to the emulators
            FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setProjectId(DEMO_PROJECT)
                    .setApplicationId("1:000000000000:android:0000000000000000")
                    .setApiKey("demo-api-key")
                    .build()
            )
        }
        val firestore = FirebaseFirestore.getInstance()
        if (BuildConfig.USE_FIREBASE_EMULATOR) {
            FirebaseAuth.getInstance().useEmulator(BuildConfig.EMULATOR_HOST, 9099)
            firestore.useEmulator(BuildConfig.EMULATOR_HOST, 8080)
        }
        // Offline cache: groups and expenses stay readable and new expenses are queued without network
        firestore.firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
            .build()
    }
}
