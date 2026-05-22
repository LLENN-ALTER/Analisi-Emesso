package com.aistudio.shiftcalendar.uydzwt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.compose.material3.HorizontalDivider

enum class ShiftType { CONTROLLERIA, BIGLIETTERIA_ORIO, RIPOSO, FERIE }

data class DailyRecord(
    val date: String = "",
    val shiftType: String = "RIPOSO",
    val sanzioniEmesse: Int = 0,
    val sanzioniPagateBordo: Int = 0
) {
    val resolvedShiftType: ShiftType
        get() = try { ShiftType.valueOf(shiftType) } catch (e: Exception) { ShiftType.RIPOSO }
}

data class MonthlyStats(
    val giorniTotaliLavoro: Int = 0,
    val giorniValidiMedia: Int = 0,
    val mediaSanzioniEmesse: Double = 0.0,
    val mediaSanzioniPagate: Double = 0.0,
    val totaleSanzioniPagate: Int = 0,
    val bonusMensile: Double = 0.0
)

class ShiftViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private val _isUserLoggedIn = MutableStateFlow(auth.currentUser != null)
    val isUserLoggedIn: StateFlow<Boolean> = _isUserLoggedIn.asStateFlow()

    private val _currentDate = MutableStateFlow(LocalDate.now())
    val currentDate: StateFlow<LocalDate> = _currentDate.asStateFlow()

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _monthlyStats = MutableStateFlow(MonthlyStats())
    val monthlyStats: StateFlow<MonthlyStats> = _monthlyStats.asStateFlow()

    init { if (auth.currentUser != null) observeMonthlyRecords() }

    fun login(email: String, authCode: String) {
        auth.signInWithEmailAndPassword(email, authCode).addOnSuccessListener {
            _isUserLoggedIn.value = true
            observeMonthlyRecords()
        }
    }

    fun register(email: String, authCode: String) {
        auth.createUserWithEmailAndPassword(email, authCode).addOnSuccessListener {
            _isUserLoggedIn.value = true
            observeMonthlyRecords()
        }
    }

    fun logout() {
        auth.signOut()
        _isUserLoggedIn.value = false
        _monthlyStats.value = MonthlyStats()
    }

    fun nextMonth() { _currentDate.value = _currentDate.value.plusMonths(1); observeMonthlyRecords() }
    fun previousMonth() { _currentDate.value = _currentDate.value.minusMonths(1); observeMonthlyRecords() }
    fun selectDate(date: LocalDate) { _selectedDate.value = date }

    private fun observeMonthlyRecords() {
        val userId = auth.currentUser?.uid ?: return
        val yearMonth = _currentDate.value.format(DateTimeFormatter.ofPattern("yyyy-MM"))
        db.collection("users").document(userId).collection("giornate")
            .whereGreaterThanOrEqualTo("date", "$yearMonth-01")
            .whereLessThanOrEqualTo("date", "$yearMonth-31")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val records = snapshot.toObjects(DailyRecord::class.java)
                    var giorniLavoro = 0; var giorniControlleria = 0; var totaleEmesse = 0; var totalePagate = 0
                    for (record in records) {
                        when (record.resolvedShiftType) {
                            ShiftType.CONTROLLERIA -> { giorniLavoro++; giorniControlleria++; totaleEmesse += record.sanzioniEmesse; totalePagate += record.sanzioniPagateBordo }
                            ShiftType.BIGLIETTERIA_ORIO -> giorniLavoro++
                            else -> {}
                        }
                    }
                    _monthlyStats.value = MonthlyStats(
                        giorniTotaliLavoro = giorniLavoro,
                        giorniValidiMedia = giorniControlleria,
                        mediaSanzioniEmesse = if (giorniControlleria > 0) totaleEmesse.toDouble() / giorniControlleria else 0.0,
                        mediaSanzioniPagate = if (giorniControlleria > 0) totalePagate.toDouble() / giorniControlleria else 0.0,
                        totaleSanzioniPagate = totalePagate,
                        bonusMensile = totalePagate * 1.50
                    )
                }
            }
    }

    fun saveDailyRecord(shiftType: ShiftType, emesse: Int, pagate: Int) {
        val userId = auth.currentUser?.uid ?: return
        val dateStr = _selectedDate.value.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val record = DailyRecord(dateStr, shiftType.name, if (shiftType == ShiftType.CONTROLLERIA) emesse else 0, if (shiftType == ShiftType.CONTROLLERIA) pagate else 0)
        db.collection("users").document(userId).collection("giornate").document(dateStr).set(record)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: ShiftViewModel = viewModel()
            val isLoggedIn by viewModel.isUserLoggedIn.collectAsState()
            if (isLoggedIn) ShiftCalendarScreen(viewModel) else LoginScreen(viewModel)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftCalendarScreen(viewModel: ShiftViewModel) {
    val currentDate by viewModel.currentDate.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val stats by viewModel.monthlyStats.collectAsState()
    var showEditor by remember { mutableStateOf(false) }
    var selectedShift by remember { mutableStateOf(ShiftType.CONTROLLERIA) }
    var emesseInput by remember { mutableStateOf(0) }
    var pagateInput by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Registro Turni", fontWeight = FontWeight.Bold) },
                actions = { TextButton(onClick = { viewModel.logout() }) { Text("Esci", color = Color.Red) } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Button(onClick = { viewModel.previousMonth() }) { Text("<") }
                Text(currentDate.format(DateTimeFormatter.ofPattern("MMMM yyyy")), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Button(onClick = { viewModel.nextMonth() }) { Text(">") }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Statistiche", fontWeight = FontWeight.Bold)
                    Text("GG Lavoro: ${stats.giorniTotaliLavoro}")
                    Text("GG Controlleria: ${stats.giorniValidiMedia}")
                    Text("Media Emesse: ${String.format("%.2f", stats.mediaSanzioniEmesse)}")
                    Text("Media Pagate: ${String.format("%.2f", stats.mediaSanzioniPagate)}")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Pagate a Bordo: ${stats.totaleSanzioniPagate}")
                        Text("Bonus: €${String.format("%.2f", stats.bonusMensile)}", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { showEditor = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Gestisci Giorno: ${selectedDate.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))}")
            }
            if (showEditor) {
                AlertDialog(
                    onDismissRequest = { showEditor = false },
                    title = { Text("Inserisci Dati") },
                    text = {
                        Column {
                            ShiftType.values().forEach { type ->
                                Row(modifier = Modifier.fillMaxWidth().selectable(selected = (selectedShift == type), onClick = { selectedShift = type }), verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = (selectedShift == type), onClick = { selectedShift = type })
                                    Text(type.name, modifier = Modifier.padding(start = 8.dp))
                                }
                            }
                            if (selectedShift == ShiftType.CONTROLLERIA) {
                                Text("Emesse:")
                                Row {
                                    Button(onClick = { if (emesseInput > 0) emesseInput-- }) { Text("-") }
                                    Text("$emesseInput", modifier = Modifier.padding(horizontal = 16.dp))
                                    Button(onClick = { emesseInput++ }) { Text("+") }
                                }
                                Text("Pagate a bordo:")
                                Row {
                                    Button(onClick = { if (pagateInput > 0) pagateInput-- }) { Text("-") }
                                    Text("$pagateInput", modifier = Modifier.padding(horizontal = 16.dp))
                                    Button(onClick = { pagateInput++ }) { Text("+") }
                                }
                            }
                        }
                    },
                    confirmButton = { Button(onClick = { viewModel.saveDailyRecord(selectedShift, emesseInput, pagateInput); showEditor = false }) { Text("Salva") } }
                )
            }
        }
    }
}

@Composable
fun LoginScreen(viewModel: ShiftViewModel) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center) {
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { viewModel.login(email, password) }, modifier = Modifier.fillMaxWidth()) { Text("Accedi") }
        OutlinedButton(onClick = { viewModel.register(email, password) }, modifier = Modifier.fillMaxWidth()) { Text("Registrati") }
    }
}
