package com.cordobot.cuentaclara.presentation.group

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cordobot.cuentaclara.domain.AddExpenseUseCase
import com.cordobot.cuentaclara.domain.AuthRepository
import com.cordobot.cuentaclara.domain.Balances
import com.cordobot.cuentaclara.domain.GroupRepository
import com.cordobot.cuentaclara.domain.SessionStore
import com.cordobot.cuentaclara.domain.model.Expense
import com.cordobot.cuentaclara.domain.model.Group
import com.cordobot.cuentaclara.domain.model.Settlement
import com.cordobot.cuentaclara.presentation.Negative
import com.cordobot.cuentaclara.presentation.Positive
import com.cordobot.cuentaclara.presentation.SectionTitle
import com.cordobot.cuentaclara.presentation.formatMoney
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

data class GroupUiState(
    val group: Group? = null,
    val myUid: String = "",
    val expenses: List<Expense> = emptyList(),
    val balances: Map<String, Long> = emptyMap(),
    val settlements: List<Settlement> = emptyList()
) {
    val total: Long get() = expenses.sumOf { it.amountCents }
    val myBalance: Long get() = balances[myUid] ?: 0
}

@HiltViewModel
class GroupViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    auth: AuthRepository,
    private val groups: GroupRepository,
    private val addExpenseUseCase: AddExpenseUseCase,
    session: SessionStore
) : ViewModel() {

    private val groupId: String = checkNotNull(savedStateHandle["groupId"])

    init {
        session.lastGroupId = groupId
    }

    val uiState: StateFlow<GroupUiState> = combine(
        groups.observeGroup(groupId),
        groups.observeExpenses(groupId),
        auth.currentUser
    ) { group, expenses, user ->
        val net = Balances.net(group?.members?.map { it.uid }.orEmpty(), expenses)
        GroupUiState(group, user?.uid.orEmpty(), expenses, net, Balances.settle(net))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupUiState())

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun addExpense(description: String, amount: String, paidBy: String, split: List<String>, onDone: () -> Unit) {
        viewModelScope.launch {
            addExpenseUseCase(groupId, description, amount, paidBy, split, uiState.value.myUid)
                .onSuccess {
                    _error.value = null
                    onDone()
                }
                .onFailure { _error.value = it.message }
        }
    }

    fun deleteExpense(id: String) = viewModelScope.launch {
        groups.deleteExpense(groupId, id).onFailure { _error.value = it.message }
    }

    fun clearError() {
        _error.value = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupScreen(onBack: () -> Unit, viewModel: GroupViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) }
    var adding by rememberSaveable { mutableStateOf(false) }
    val group = state.group ?: return

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Header(group, state, onBack)
            SecondaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Gastos (${state.expenses.size})") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Saldos") })
            }
            AnimatedContent(targetState = tab, label = "tab") { selected ->
                if (selected == 0) ExpenseList(state, group, viewModel::deleteExpense) else BalanceList(state, group)
            }
        }
        ExtendedFloatingActionButton(
            onClick = { adding = true },
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text("Agregar gasto") },
            containerColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp)
        )
    }

    if (adding) {
        ModalBottomSheet(
            onDismissRequest = { adding = false; viewModel.clearError() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            AddExpenseForm(group, state.myUid, error) { description, amount, paidBy, split ->
                viewModel.addExpense(description, amount, paidBy, split) { adding = false }
            }
        }
    }
}

@Composable
private fun Header(group: Group, state: GroupUiState, onBack: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.padding(horizontal = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") }
            Text("${group.emoji} ${group.name}", style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp), modifier = Modifier.weight(1f))
            IconButton(onClick = {
                val text = "Únete a \"${group.name}\" en CuentaClara con el código ${group.inviteCode}"
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Invitar"))
            }) { Icon(Icons.Default.Share, contentDescription = "Invitar") }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Tu saldo", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Text(
                    when {
                        state.myBalance > 0 -> "Te deben ${formatMoney(state.myBalance)}"
                        state.myBalance < 0 -> "Debes ${formatMoney(-state.myBalance)}"
                        else -> "Estás a paz y salvo"
                    },
                    color = when {
                        state.myBalance > 0 -> Positive
                        state.myBalance < 0 -> Negative
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text("Total del grupo: ${formatMoney(state.total)}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Código", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                Text(group.inviteCode, fontWeight = FontWeight.Black, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

private val dateFormat = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.forLanguageTag("es"))

@Composable
private fun ExpenseList(state: GroupUiState, group: Group, onDelete: (String) -> Unit) {
    if (state.expenses.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("Aún no hay gastos. Agrega el primero ⬇️", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(state.expenses, key = { it.id }) { expense ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(expense.description, style = MaterialTheme.typography.titleMedium)
                        if (expense.isPending) {
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Default.CloudUpload, contentDescription = "Pendiente de sincronizar", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(
                        "Pagó ${group.nameOf(expense.paidBy).substringBefore(' ')} · entre ${expense.splitAmong.size} · ${dateFormat.format(expense.createdAt.atZone(ZoneId.systemDefault()))}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
                Text(formatMoney(expense.amountCents), fontWeight = FontWeight.Bold)
                if (expense.createdBy == state.myUid) {
                    IconButton(onClick = { onDelete(expense.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Eliminar gasto", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun BalanceList(state: GroupUiState, group: Group) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { SectionTitle("Saldo de cada persona") }
        items(group.members, key = { it.uid }) { member ->
            val balance = state.balances[member.uid] ?: 0
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(36.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
                    Text(member.name.take(1).uppercase(), fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Text(if (member.uid == state.myUid) "${member.name} (tú)" else member.name, modifier = Modifier.weight(1f))
                Text(
                    (if (balance > 0) "+" else "") + formatMoney(balance),
                    color = if (balance > 0) Positive else if (balance < 0) Negative else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        item { Spacer(Modifier.height(8.dp)); SectionTitle("Cómo quedar a paz y salvo") }
        if (state.settlements.isEmpty()) {
            item { Text("No hay deudas pendientes 🎉", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(state.settlements) { settlement ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(group.nameOf(settlement.from).substringBefore(' '), fontWeight = FontWeight.SemiBold)
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "le paga a", modifier = Modifier.padding(horizontal = 8.dp).size(18.dp))
                Text(group.nameOf(settlement.to).substringBefore(' '), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(formatMoney(settlement.amountCents), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddExpenseForm(
    group: Group,
    myUid: String,
    error: String?,
    onSave: (description: String, amount: String, paidBy: String, split: List<String>) -> Unit
) {
    var description by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var paidBy by rememberSaveable { mutableStateOf(myUid) }
    var split by remember { mutableStateOf(group.members.map { it.uid }.toSet()) }

    Column(
        Modifier.fillMaxWidth().imePadding().padding(horizontal = 20.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Nuevo gasto", style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp))
        OutlinedTextField(description, { description = it }, label = { Text("¿En qué?") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
            label = { Text("Monto") },
            prefix = { Text("$ ") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        Text("¿Quién pagó?", color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            group.members.forEach { member ->
                FilterChip(selected = paidBy == member.uid, onClick = { paidBy = member.uid }, label = { Text(member.name.substringBefore(' ')) })
            }
        }
        Text("¿Entre quiénes se divide?", color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            group.members.forEach { member ->
                val selected = member.uid in split
                FilterChip(
                    selected = selected,
                    onClick = { split = if (selected) split - member.uid else split + member.uid },
                    label = { Text(member.name.substringBefore(' ')) }
                )
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = { onSave(description, amount, paidBy, group.members.map { it.uid }.filter { it in split }) },
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("Guardar gasto") }
        Text(
            "Se divide en partes iguales. Funciona sin conexión: se sincroniza al volver.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clickable(enabled = false) {}
        )
    }
}
