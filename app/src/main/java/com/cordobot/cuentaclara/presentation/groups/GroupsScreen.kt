package com.cordobot.cuentaclara.presentation.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cordobot.cuentaclara.domain.AuthRepository
import com.cordobot.cuentaclara.domain.GroupRepository
import com.cordobot.cuentaclara.domain.SignOutUseCase
import com.cordobot.cuentaclara.domain.model.Group
import com.cordobot.cuentaclara.domain.model.User
import com.cordobot.cuentaclara.presentation.Negative
import com.cordobot.cuentaclara.presentation.Positive
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GroupsUiState(val user: User? = null, val groups: List<Group> = emptyList(), val loading: Boolean = true)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GroupsViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val groups: GroupRepository,
    private val signOutUseCase: SignOutUseCase
) : ViewModel() {

    private val user = auth.currentUser.filterNotNull()

    val uiState: StateFlow<GroupsUiState> = user.flatMapLatest { current ->
        groups.observeGroups(current.uid).combine(MutableStateFlow(current)) { list, u -> GroupsUiState(u, list, false) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupsUiState())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun createGroup(name: String, emoji: String, onCreated: (String) -> Unit) = viewModelScope.launch {
        if (name.isBlank()) return@launch
        groups.createGroup(name.trim(), emoji, user.first())
            .onSuccess(onCreated)
            .onFailure { _message.value = it.message }
    }

    fun joinGroup(code: String, onJoined: (String) -> Unit) = viewModelScope.launch {
        groups.joinGroup(code, user.first())
            .onSuccess(onJoined)
            .onFailure { _message.value = it.message }
    }

    fun dismissMessage() {
        _message.value = null
    }

    fun signOut() = viewModelScope.launch { signOutUseCase() }
}

private val GroupEmojis = listOf("🏠", "✈️", "🍕", "🎉", "⚽", "🛒")

@Composable
fun GroupsScreen(onOpenGroup: (String) -> Unit, viewModel: GroupsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var joining by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Hola, ${state.user?.name?.substringBefore(' ').orEmpty()} 👋", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Tus grupos", style = MaterialTheme.typography.headlineLarge)
                    }
                    IconButton(onClick = { joining = true }) { Icon(Icons.Default.GroupAdd, contentDescription = "Unirme con código") }
                    IconButton(onClick = viewModel::signOut) { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Cerrar sesión") }
                }
            }
            if (!state.loading && state.groups.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🧾", fontSize = 48.sp)
                        Text("Crea un grupo para el arriendo, un viaje o el mercado,", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("o únete con el código que te compartieron.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(state.groups, key = { it.id }) { group ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { onOpenGroup(group.id) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(52.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) { Text(group.emoji, fontSize = 26.sp) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(group.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            group.members.joinToString(", ") { it.name.substringBefore(' ') },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            maxLines = 1
                        )
                    }
                    Text(
                        "${group.members.size} 👥",
                        color = if (group.members.size > 1) Positive else Negative,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { creating = true },
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text("Nuevo grupo") },
            containerColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        )
    }

    if (creating) {
        var name by remember { mutableStateOf("") }
        var emoji by remember { mutableStateOf(GroupEmojis.first()) }
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("Nuevo grupo") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Nombre (ej. Apto 302)") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GroupEmojis.forEach { option ->
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .background(
                                        if (option == emoji) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { emoji = option },
                                contentAlignment = Alignment.Center
                            ) { Text(option, fontSize = 20.sp) }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    creating = false
                    viewModel.createGroup(name, emoji, onOpenGroup)
                }, enabled = name.isNotBlank()) { Text("Crear") }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("Cancelar") } }
        )
    }

    if (joining) {
        var code by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { joining = false },
            title = { Text("Unirme a un grupo") },
            text = {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase().take(6) },
                    label = { Text("Código de 6 caracteres") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    joining = false
                    viewModel.joinGroup(code, onOpenGroup)
                }, enabled = code.length == 6) { Text("Unirme") }
            },
            dismissButton = { TextButton(onClick = { joining = false }) { Text("Cancelar") } }
        )
    }

    message?.let {
        AlertDialog(
            onDismissRequest = viewModel::dismissMessage,
            text = { Text(it) },
            confirmButton = { TextButton(onClick = viewModel::dismissMessage) { Text("Entendido") } }
        )
    }
}
