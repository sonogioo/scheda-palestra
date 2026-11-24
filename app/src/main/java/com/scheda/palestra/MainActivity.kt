package com.scheda.palestra

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Accent = Color(0xFFC6F432)
private val Muted = Color(0xFF9AA4AE)
private val Card = Color(0xFF161B21)

private val SchedaColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Card,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = Store(applicationContext)
        setContent {
            MaterialTheme(colorScheme = SchedaColors) {
                Surface(Modifier.fillMaxSize(), color = Color.Black) {
                    App(store)
                }
            }
        }
    }
}

private enum class Screen { Home, Edit, Train }

@Composable
private fun App(store: Store) {
    var text by remember { mutableStateOf(store.loadText()) }
    val workouts = remember(text) { SchedaParser.parse(text) }
    var progress by remember {
        mutableStateOf(store.loadProgress()?.takeIf { it.isValidFor(SchedaParser.parse(text)) })
    }
    // Se l'app era stata chiusa a metà allenamento, si riparte direttamente da lì.
    var screen by remember { mutableStateOf(if (progress != null) Screen.Train else Screen.Home) }

    fun setProgress(p: Progress?) {
        progress = p
        store.saveProgress(p)
    }

    Box(Modifier.safeDrawingPadding()) {
        when (screen) {
            Screen.Home -> HomeScreen(
                workouts = workouts,
                progress = progress,
                onStart = { index ->
                    val p = progress
                    if (p == null || p.workout != index || p.isFinished(workouts[index])) {
                        setProgress(Progress(index, 0, 0))
                    }
                    screen = Screen.Train
                },
                onEdit = { screen = Screen.Edit },
            )

            Screen.Edit -> EditScreen(
                initial = text,
                onSave = {
                    store.saveText(it)
                    text = it
                    setProgress(null)
                    screen = Screen.Home
                },
                onCancel = { screen = Screen.Home },
            )

            Screen.Train -> {
                val p = progress
                if (p == null) {
                    screen = Screen.Home
                } else {
                    TrainScreen(
                        workout = workouts[p.workout],
                        progress = p,
                        onProgress = { setProgress(it) },
                        onExit = { screen = Screen.Home },
                        onFinish = {
                            setProgress(null)
                            screen = Screen.Home
                        },
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Home

@Composable
private fun HomeScreen(
    workouts: List<Workout>,
    progress: Progress?,
    onStart: (Int) -> Unit,
    onEdit: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "Scheda",
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
            )
        }
        if (workouts.isEmpty()) {
            item { Text("Nessun esercizio trovato. Tocca «Modifica scheda».", color = Muted) }
        }
        itemsIndexed(workouts) { index, w ->
            val inProgress = progress?.takeIf { it.workout == index && !it.isFinished(w) }
            Button(
                onClick = { onStart(index) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 104.dp),
                shape = RoundedCornerShape(20.dp),
                colors = if (inProgress != null) ButtonDefaults.buttonColors()
                else ButtonDefaults.buttonColors(containerColor = Card, contentColor = Color.White),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Text(w.displayName, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (inProgress != null) "In corso · esercizio ${inProgress.exercise + 1} di ${w.exercises.size} · tocca per riprendere"
                        else "${w.exercises.size} esercizi" + if (w.subtitle != null) " · ${w.title}" else "",
                        fontSize = 15.sp,
                        color = if (inProgress != null) Color.Black else Muted,
                    )
                }
            }
        }
        item {
            TextButton(onClick = onEdit, modifier = Modifier.padding(vertical = 16.dp)) {
                Text("Modifica scheda", fontSize = 18.sp)
            }
        }
    }
}

// ---------------------------------------------------------------- Allenamento

@Composable
private fun TrainScreen(
    workout: Workout,
    progress: Progress,
    onProgress: (Progress) -> Unit,
    onExit: () -> Unit,
    onFinish: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    BackHandler(onBack = onExit)

    // Lo schermo resta acceso finché questa schermata è visibile.
    Column(
        Modifier.fillMaxSize().keepScreenOn().padding(16.dp),
    ) {
        if (progress.isFinished(workout)) {
            FinishedContent(workout, onFinish)
            return@Column
        }

        val total = workout.exercises.size
        val ex = workout.exercises[progress.exercise]
        val next = workout.exercises.getOrNull(progress.exercise + 1)
        val isLastSet = next == null && progress.setsDone == ex.sets - 1

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${workout.displayName} · ${progress.exercise + 1}/$total",
                fontSize = 18.sp,
                color = Muted,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onExit) { Text("Esci", fontSize = 18.sp) }
        }
        LinearProgressIndicator(
            progress = { progress.exercise.toFloat() / total },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            color = Accent,
            trackColor = Card,
        )

        Column(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
        ) {
            if (ex.group != null) {
                Text(ex.group.uppercase(), color = Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Text(ex.name, fontSize = 36.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text("${ex.sets} × ${ex.repsLabel}", fontSize = 28.sp)
            if (ex.note.isNotEmpty()) Text(ex.note, fontSize = 22.sp, color = Muted)
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(ex.sets) { i ->
                    val mod = Modifier.size(28.dp).let {
                        when {
                            i < progress.setsDone -> it.background(Accent, CircleShape)
                            i == progress.setsDone -> it.border(3.dp, Accent, CircleShape)
                            else -> it.border(2.dp, Muted, CircleShape)
                        }
                    }
                    Box(mod)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Serie ${progress.setsDone + 1} di ${ex.sets}", fontSize = 22.sp)
        }

        Text(
            if (next != null) "Poi: ${next.name}" else "Ultimo esercizio",
            color = Muted,
            fontSize = 17.sp,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Button(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onProgress(progress.advance(workout))
            },
            modifier = Modifier.fillMaxWidth().height(200.dp),
            shape = RoundedCornerShape(28.dp),
        ) {
            Text(
                if (isLastSet) "FINE" else "SERIE FATTA",
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = { onProgress(progress.undo(workout)) },
                enabled = progress.canUndo,
                modifier = Modifier.weight(1f).height(64.dp),
            ) { Text("Annulla", fontSize = 18.sp) }
            OutlinedButton(
                onClick = { onProgress(progress.skip(workout)) },
                modifier = Modifier.weight(1f).height(64.dp),
            ) { Text("Salta esercizio", fontSize = 18.sp) }
        }
    }
}

@Composable
private fun FinishedContent(workout: Workout, onFinish: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Allenamento completato", fontSize = 32.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        Text(workout.displayName, fontSize = 22.sp, color = Muted)
        Spacer(Modifier.height(48.dp))
        Button(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth().height(96.dp),
            shape = RoundedCornerShape(24.dp),
        ) { Text("Torna alle schede", fontSize = 24.sp, fontWeight = FontWeight.Bold) }
    }
}

// ---------------------------------------------------------------- Modifica

@Composable
private fun EditScreen(initial: String, onSave: (String) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf(initial) }
    val parsed = remember(text) { SchedaParser.parse(text) }
    BackHandler(onBack = onCancel)

    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { text = it.readText() }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Modifica scheda", fontSize = 28.sp, fontWeight = FontWeight.Black)
        Text(
            "Incolla il testo dalle note. Formato: un titolo per giorno, «Gruppo:» per i muscoli, " +
                "«Esercizio 3x10» o «3xF» (cedimento). Righe vuote e puntini vengono ignorati.",
            color = Muted,
        )
        OutlinedButton(onClick = { openFile.launch("text/*") }) { Text("Importa da file .txt") }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 320.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        )

        Text("Anteprima", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        if (parsed.isEmpty()) {
            Text("Nessun esercizio riconosciuto.", color = Color(0xFFFF8A80))
        }
        parsed.forEach { w ->
            Column(
                Modifier.fillMaxWidth().background(Card, RoundedCornerShape(12.dp)).padding(12.dp)
            ) {
                Text("${w.displayName} — ${w.exercises.size} esercizi", fontWeight = FontWeight.Bold)
                w.exercises.forEach { e ->
                    Text(
                        "${e.name}  ${e.sets}×${if (e.toFailure) "F" else e.reps}" +
                            if (e.note.isNotEmpty()) "  ${e.note}" else "",
                        color = Muted,
                        fontSize = 14.sp,
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).height(56.dp)) { Text("Annulla") }
            Button(
                onClick = { onSave(text) },
                enabled = parsed.isNotEmpty(),
                modifier = Modifier.weight(1f).height(56.dp),
            ) { Text("Salva") }
        }
        Text("Salvando, l'allenamento in corso viene azzerato.", color = Muted, fontSize = 13.sp)
    }
}
