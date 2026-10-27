package com.cordobot.cuentaclara.di

import com.cordobot.cuentaclara.data.AndroidConnectivityMonitor
import com.cordobot.cuentaclara.data.EncryptedSessionStore
import com.cordobot.cuentaclara.data.FirebaseAuthRepository
import com.cordobot.cuentaclara.data.FirestoreGroupRepository
import com.cordobot.cuentaclara.domain.AuthRepository
import com.cordobot.cuentaclara.domain.ConnectivityMonitor
import com.cordobot.cuentaclara.domain.GroupRepository
import com.cordobot.cuentaclara.domain.SessionStore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {
    // FirebaseSetup.init already ran in CuentaClaraApp.onCreate (emulators, offline cache)
    @Provides fun provideAuth(): FirebaseAuth = FirebaseAuth.getInstance()
    @Provides fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds abstract fun bindAuth(impl: FirebaseAuthRepository): AuthRepository
    @Binds abstract fun bindGroups(impl: FirestoreGroupRepository): GroupRepository
    @Binds abstract fun bindSession(impl: EncryptedSessionStore): SessionStore
    @Binds abstract fun bindConnectivity(impl: AndroidConnectivityMonitor): ConnectivityMonitor
}
