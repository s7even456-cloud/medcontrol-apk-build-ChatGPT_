package com.steveen.medcontrol

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.steveen.medcontrol.data.IntakeScheduleEntity
import com.steveen.medcontrol.data.IntakeState
import com.steveen.medcontrol.data.MedicationEntity
import com.steveen.medcontrol.data.SettingsEntity
import com.steveen.medcontrol.scheduler.AlarmScheduler
import com.steveen.medcontrol.ui.MainViewModel
import com.steveen.medcontrol.util.DateTimeUtils
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialIntakeId = intent.getLongExtra(AlarmScheduler.EXTRA_INTAKE_ID, -1L).takeIf { it > 0 }
        setContent {
            MedControlTheme {
                val app = application as MedControlApplication
                val vm: MainViewModel = viewModel(factory = MainViewModel.factory(app.repository, app.alarmScheduler))
                MedControlApp(vm = vm, initialIntakeId = initialIntakeId)
            }
        }
    }
}

@Composable
fun MedControlTheme(content: @Composable () -> Unit) {
    val scheme = lightColorScheme(
        primary = Color(0xFF2F7D6B),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD8F1EA),
        secondary = Color(0xFF4E6E67),
        background = Color(0xFFF7FAF9),
        surface = Color.White,
        error = Color(0xFFB3261E)
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

private enum class AppScreen(val title: String) {
    Home("Inicio"), Meds("Medicamentos"), History("Historial"), Stock("Stock"), Settings("Ajustes")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedControlApp(vm: MainViewModel, initialIntakeId: Long?) {
    val medications by vm.medications.collectAsState()
    val intakes by vm.intakes.collectAsState()
    val settings by vm.settings.collectAsState()
    val message by vm.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var screen by remember { mutableStateOf(AppScreen.Home) }
    var showMedForm by remember { mutableStateOf<MedicationEntity?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<MedicationEntity?>(null) }
    var activeIntakeId by remember { mutableStateOf(initialIntakeId) }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.clearMessage() }
    }

    val pendingFromIntent = activeIntakeId?.let { id -> intakes.firstOrNull { it.id == id } }
    if (pendingFromIntent != null && pendingFromIntent.estado != IntakeState.TOMADA && pendingFromIntent.estado != IntakeState.OLVIDADA_NO_TOMADA) {
        IntakeActionDialog(
            intake = pendingFromIntent,
            medication = medications.firstOrNull { it.id == pendingFromIntent.medicamentoId },
            onDismiss = { activeIntakeId = null },
            onConfirm = { vm.confirmIntake(pendingFromIntent.id); activeIntakeId = null },
            onMissed = { vm.markMissed(pendingFromIntent.id); activeIntakeId = null },
            onSnooze = { minutes -> vm.snoozeIntake(pendingFromIntent.id, minutes); activeIntakeId = null }
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(screen.title, fontWeight = FontWeight.Bold) }) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (screen == AppScreen.Meds) {
                FloatingActionButton(onClick = { showMedForm = MedicationEntity(nombre = "", unidadesPorCaja = 28, stockActual = 0, horasToma = listOf("09:00")) }) {
                    Icon(Icons.Default.Add, contentDescription = "Añadir")
                }
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = screen == AppScreen.Home, onClick = { screen = AppScreen.Home }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("Inicio") })
                NavigationBarItem(selected = screen == AppScreen.Meds, onClick = { screen = AppScreen.Meds }, icon = { Icon(Icons.Default.Medication, null) }, label = { Text("Meds") })
                NavigationBarItem(selected = screen == AppScreen.History, onClick = { screen = AppScreen.History }, icon = { Icon(Icons.Default.History, null) }, label = { Text("Historial") })
                NavigationBarItem(selected = screen == AppScreen.Stock, onClick = { screen = AppScreen.Stock }, icon = { Icon(Icons.Default.Inventory, null) }, label = { Text("Stock") })
                NavigationBarItem(selected = screen == AppScreen.Settings, onClick = { screen = AppScreen.Settings }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Ajustes") })
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)) {
            when (screen) {
                AppScreen.Home -> HomeScreen(medications, intakes, settings, onOpenIntake = { activeIntakeId = it.id }, onAddMedication = { screen = AppScreen.Meds; showMedForm = MedicationEntity(nombre = "", unidadesPorCaja = 28, stockActual = 0, horasToma = listOf("09:00")) }, onHistory = { screen = AppScreen.History }, onStock = { screen = AppScreen.Stock }, vm = vm)
                AppScreen.Meds -> MedicationScreen(medications, onEdit = { showMedForm = it }, onDelete = { showDeleteConfirm = it }, onPauseToggle = { med -> if (med.activo) vm.pauseMedication(med.id) else vm.activateMedication(med.id) })
                AppScreen.History -> HistoryScreen(intakes, medications, vm)
                AppScreen.Stock -> StockScreen(medications, settings, vm)
                AppScreen.Settings -> SettingsScreen(settings, vm)
            }
        }
    }

    showMedForm?.let { med ->
        MedicationFormDialog(
            initial = med,
            onDismiss = { showMedForm = null },
            onSave = { saved ->
                if (saved.id == 0L) vm.addMedication(saved) else vm.updateMedication(saved)
                showMedForm = null
            }
        )
    }

    showDeleteConfirm?.let { med ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Eliminar medicamento") },
            text = { Text("Esta acción eliminará ${med.nombre} y sus registros asociados. No se puede deshacer.") },
            confirmButton = {
                Button(colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), onClick = { vm.deleteMedication(med); showDeleteConfirm = null }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun HomeScreen(
    medications: List<MedicationEntity>,
    intakes: List<IntakeScheduleEntity>,
    settings: SettingsEntity,
    onOpenIntake: (IntakeScheduleEntity) -> Unit,
    onAddMedication: () -> Unit,
    onHistory: () -> Unit,
    onStock: () -> Unit,
    vm: MainViewModel
) {
    val today = DateTimeUtils.todayString()
    val todayIntakes = intakes.filter { it.fechaProgramada == today }.sortedBy { it.horaProgramada }
    val pendingToday = todayIntakes.filter { it.estado !in listOf(IntakeState.TOMADA, IntakeState.OLVIDADA_NO_TOMADA) }
    val confirmedToday = todayIntakes.filter { it.estado == IntakeState.TOMADA }
    val next = pendingToday.firstOrNull()
    val lowStock = medications.filter { vm.daysRemaining(it) <= settings.diasAvisoStock }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(DateTimeUtils.displayDate(today), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(8.dp))
            WarningCard()
        }
        item {
            SectionCard(title = "Próxima toma") {
                if (next == null) EmptyState("No hay tomas pendientes hoy.") else {
                    val med = medications.firstOrNull { it.id == next.medicamentoId }
                    Text(med?.nombre ?: "Medicamento", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Programada a las ${next.horaProgramada} · ${stateLabel(next.estado)}")
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { onOpenIntake(next) }, modifier = Modifier.fillMaxWidth()) { Text("Gestionar toma") }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                QuickButton("Añadir", onAddMedication, Modifier.weight(1f))
                QuickButton("Historial", onHistory, Modifier.weight(1f))
                QuickButton("Stock", onStock, Modifier.weight(1f))
            }
        }
        item {
            SectionCard(title = "Hoy") {
                Text("Pendientes: ${pendingToday.size}")
                Text("Confirmadas: ${confirmedToday.size}")
                Text("Olvidadas/no tomadas: ${todayIntakes.count { it.estado == IntakeState.OLVIDADA_NO_TOMADA }}")
            }
        }
        item {
            SectionCard(title = "Stock bajo") {
                if (lowStock.isEmpty()) EmptyState("Sin avisos de stock bajo.") else lowStock.forEach { med ->
                    Text("${med.nombre}: ${med.stockActual} uds · ${String.format("%.1f", vm.daysRemaining(med))} días aprox.", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun MedicationScreen(medications: List<MedicationEntity>, onEdit: (MedicationEntity) -> Unit, onDelete: (MedicationEntity) -> Unit, onPauseToggle: (MedicationEntity) -> Unit) {
    if (medications.isEmpty()) {
        EmptyFull("No hay medicamentos. Añade el primero con el botón +.")
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(medications) { med ->
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(med.nombre, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("${med.stockActual}/${med.unidadesPorCaja} uds · ${med.horasToma.joinToString(", ")}")
                            Text(if (med.activo) "Activo" else "Pausado", color = if (med.activo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                        }
                        IconButton(onClick = { onPauseToggle(med) }) { Icon(if (med.activo) Icons.Default.Pause else Icons.Default.PlayArrow, null) }
                        IconButton(onClick = { onEdit(med) }) { Icon(Icons.Default.Edit, null) }
                        IconButton(onClick = { onDelete(med) }) { Icon(Icons.Default.Delete, null) }
                    }
                    if (med.notas.isNotBlank()) Text(med.notas, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun HistoryScreen(intakes: List<IntakeScheduleEntity>, medications: List<MedicationEntity>, vm: MainViewModel) {
    var filter by remember { mutableStateOf("todo") }
    val filtered = when (filter) {
        "hoy" -> intakes.filter { it.fechaProgramada == DateTimeUtils.todayString() }
        "tomadas" -> intakes.filter { it.estado == IntakeState.TOMADA }
        "olvidadas" -> intakes.filter { it.estado == IntakeState.OLVIDADA_NO_TOMADA }
        else -> intakes
    }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChipLike("Todo", filter == "todo") { filter = "todo" }
                FilterChipLike("Hoy", filter == "hoy") { filter = "hoy" }
                FilterChipLike("Tomadas", filter == "tomadas") { filter = "tomadas" }
                FilterChipLike("Olvidadas", filter == "olvidadas") { filter = "olvidadas" }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { vm.exportCsv() }, modifier = Modifier.weight(1f)) { Text("CSV") }
                OutlinedButton(onClick = { vm.exportJson() }, modifier = Modifier.weight(1f)) { Text("Backup JSON") }
            }
        }
        if (filtered.isEmpty()) item { EmptyState("No hay registros para este filtro.") }
        items(filtered) { intake ->
            val med = medications.firstOrNull { it.id == intake.medicamentoId }
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text(med?.nombre ?: "Medicamento eliminado", fontWeight = FontWeight.Bold)
                    Text("${DateTimeUtils.displayDateShort(intake.fechaProgramada)} · ${intake.horaProgramada} · ${stateLabel(intake.estado)}")
                    Text("Alarma: ${DateTimeUtils.displayMillis(intake.alarmaLanzadaEn)}")
                    Text("Confirmación: ${DateTimeUtils.displayMillis(intake.confirmadaEn)}")
                    Text("Posposiciones: ${intake.numeroPosposiciones}")
                    if (intake.stockRestanteTrasToma != null) Text("Stock tras toma: ${intake.stockRestanteTrasToma}")
                    if (intake.observaciones.isNotBlank()) Text("Obs.: ${intake.observaciones}")
                }
            }
        }
    }
}

@Composable
private fun StockScreen(medications: List<MedicationEntity>, settings: SettingsEntity, vm: MainViewModel) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (medications.isEmpty()) item { EmptyState("No hay medicamentos.") }
        items(medications) { med ->
            var adjustText by remember(med.id, med.stockActual) { mutableStateOf(med.stockActual.toString()) }
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(med.nombre, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Stock actual: ${med.stockActual} unidades")
                    Text("Días aproximados: ${String.format("%.1f", vm.daysRemaining(med))}")
                    if (vm.daysRemaining(med) <= settings.diasAvisoStock) {
                        Text("Aviso: quedan ${settings.diasAvisoStock} días o menos.", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.addBox(med.id) }, modifier = Modifier.weight(1f)) { Text("Añadir caja") }
                        OutlinedTextField(value = adjustText, onValueChange = { adjustText = it.filter(Char::isDigit) }, label = { Text("Ajuste") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    }
                    OutlinedButton(onClick = { vm.adjustStock(med.id, adjustText.toIntOrNull() ?: med.stockActual) }, modifier = Modifier.fillMaxWidth()) { Text("Aplicar ajuste manual") }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(settings: SettingsEntity, vm: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.importJsonFromUri(context, it) }
    }
    var checkDelay by remember(settings.checkDelayMinutos) { mutableStateOf(settings.checkDelayMinutos.toString()) }
    var stockDays by remember(settings.diasAvisoStock) { mutableStateOf(settings.diasAvisoStock.toString()) }
    var notifications by remember(settings.notificacionesActivadas) { mutableStateOf(settings.notificacionesActivadas) }
    var confirmReset by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SectionCard(title = "Permisos críticos") {
                Text("Para máxima fiabilidad: notificaciones activas, alarmas exactas permitidas y batería sin restricciones para esta app.")
                Spacer(Modifier.height(8.dp))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    OutlinedButton(onClick = { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }, modifier = Modifier.fillMaxWidth()) { Text("Solicitar permiso de notificaciones") }
                }
                OutlinedButton(onClick = { openExactAlarmSettings(context) }, modifier = Modifier.fillMaxWidth()) { Text("Abrir ajustes de alarmas exactas") }
                OutlinedButton(onClick = { openBatterySettings(context) }, modifier = Modifier.fillMaxWidth()) { Text("Abrir ajustes de batería") }
            }
        }
        item {
            SectionCard(title = "Ajustes de recordatorio") {
                OutlinedTextField(value = checkDelay, onValueChange = { checkDelay = it.filter(Char::isDigit) }, label = { Text("Minutos hasta pedir check") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = stockDays, onValueChange = { stockDays = it.filter(Char::isDigit) }, label = { Text("Días mínimos para aviso de compra") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = notifications, onCheckedChange = { notifications = it })
                    Text("Notificaciones activadas")
                }
                Button(onClick = {
                    vm.updateSettings(settings.copy(checkDelayMinutos = checkDelay.toIntOrNull() ?: 2, diasAvisoStock = stockDays.toIntOrNull() ?: 3, notificacionesActivadas = notifications))
                }, modifier = Modifier.fillMaxWidth()) { Text("Guardar ajustes") }
            }
        }
        item {
            SectionCard(title = "Copias y datos") {
                OutlinedButton(onClick = { vm.exportJson() }, modifier = Modifier.fillMaxWidth()) { Text("Exportar backup JSON") }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/*", "*/*")) }, modifier = Modifier.fillMaxWidth()) { Text("Importar backup JSON") }
                OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Reiniciar todos los datos") }
            }
        }
        item {
            SectionCard(title = "Límite de responsabilidad") {
                Text("La app es una herramienta personal de recordatorio y registro. No sustituye el consejo médico, farmacéutico ni las indicaciones de un profesional sanitario. No modifiques, suspendas ni cambies dosis de medicamentos basándote solo en esta app.")
            }
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Confirmación fuerte") },
            text = { Text("Se borrarán medicamentos, historial y eventos de stock. Después se restaurarán los tres medicamentos iniciales. Exporta una copia antes si necesitas conservar datos.") },
            confirmButton = { Button(colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), onClick = { vm.deleteAllData(); confirmReset = false }) { Text("Borrar todo") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun MedicationFormDialog(initial: MedicationEntity, onDismiss: () -> Unit, onSave: (MedicationEntity) -> Unit) {
    var name by remember { mutableStateOf(initial.nombre) }
    var box by remember { mutableStateOf(initial.unidadesPorCaja.toString()) }
    var stock by remember { mutableStateOf(initial.stockActual.toString()) }
    var hours by remember { mutableStateOf(initial.horasToma.joinToString(", ")) }
    var notes by remember { mutableStateOf(initial.notas) }
    var active by remember { mutableStateOf(initial.activo) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "Añadir medicamento" else "Editar medicamento") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nombre") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = box, onValueChange = { box = it.filter(Char::isDigit) }, label = { Text("Unidades por caja") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = stock, onValueChange = { stock = it.filter(Char::isDigit) }, label = { Text("Stock actual") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = hours, onValueChange = { hours = it }, label = { Text("Horas: 14:57, 23:57") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notas") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = active, onCheckedChange = { active = it }); Text("Activo") }
            }
        },
        confirmButton = {
            Button(onClick = {
                val parsedHours = hours.split(",", ";", " ").map { it.trim() }.filter { it.isNotBlank() }
                when {
                    name.isBlank() -> error = "El nombre es obligatorio."
                    box.toIntOrNull() == null || box.toInt() <= 0 -> error = "Las unidades por caja deben ser mayores que cero."
                    stock.toIntOrNull() == null -> error = "El stock debe ser numérico."
                    parsedHours.isEmpty() || parsedHours.any { !DateTimeUtils.validTime(it) } -> error = "Usa horas válidas en formato HH:mm."
                    else -> onSave(initial.copy(nombre = name.trim(), unidadesPorCaja = box.toInt(), stockActual = stock.toInt(), horasToma = parsedHours.distinct().sorted(), notas = notes.trim(), activo = active))
                }
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun IntakeActionDialog(intake: IntakeScheduleEntity, medication: MedicationEntity?, onDismiss: () -> Unit, onConfirm: () -> Unit, onMissed: () -> Unit, onSnooze: (Int) -> Unit) {
    var snooze by remember { mutableStateOf("10") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirmar toma") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(medication?.nombre ?: "Medicamento", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Programada: ${intake.fechaProgramada} ${intake.horaProgramada}")
                Text("Estado: ${stateLabel(intake.estado)}")
                OutlinedTextField(value = snooze, onValueChange = { snooze = it.filter(Char::isDigit) }, label = { Text("Posponer minutos") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = onConfirm) { Text("Tomada") } },
        dismissButton = {
            Column {
                OutlinedButton(onClick = { onSnooze((snooze.toIntOrNull() ?: 10).coerceIn(1, 1440)) }) { Text("Posponer") }
                TextButton(onClick = onMissed) { Text("Marcar no tomada") }
                TextButton(onClick = onDismiss) { Text("Cerrar") }
            }
        }
    )
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun WarningCard() {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Text("Recordatorio: esta app registra tomas. No cambia ni sustituye indicaciones médicas.", modifier = Modifier.padding(14.dp), color = Color(0xFF12352E))
    }
}

@Composable
private fun QuickButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onClick, modifier = modifier.height(52.dp), shape = RoundedCornerShape(16.dp)) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

@Composable
private fun FilterChipLike(text: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) Button(onClick = onClick) { Text(text) } else OutlinedButton(onClick = onClick) { Text(text) }
}

@Composable
private fun EmptyState(text: String) {
    Text(text, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(8.dp))
}

@Composable
private fun EmptyFull(text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Text(text, color = MaterialTheme.colorScheme.secondary) }
}

private fun stateLabel(state: IntakeState): String = when (state) {
    IntakeState.PROGRAMADA -> "Programada"
    IntakeState.ALARMA_SONADA -> "Alarma sonada"
    IntakeState.POSPUESTA -> "Pospuesta"
    IntakeState.CHECK_PENDIENTE -> "Check pendiente"
    IntakeState.TOMADA -> "Tomada"
    IntakeState.OLVIDADA_NO_TOMADA -> "Olvidada/no tomada"
}

private fun openExactAlarmSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
    }
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun openBatterySettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
