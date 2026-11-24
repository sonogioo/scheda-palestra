package com.scheda.palestra

import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------- Tema
// Il lime è riservato all'azione principale e allo stato "in corso / fatto".

private val Accent = Color(0xFFC6F432)
private val Muted = Color(0xFF9AA4AE)
private val Card = Color(0xFF1B2129)
private val Track = Color(0xFF2A323C)

private val SchedaColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Card,
    onSurfaceVariant = Muted,
)

/** Cinque gradini in tutto: display, headline, title, body, label. */
private val SchedaType = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Black),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp),
)

/** Due raggi: 24dp per card e pulsante principale, 16dp per i campi; i pulsanti piccoli restano a pillola. */
private val SchedaShapes = Shapes(
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

private val Margin = 16.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = Store(applicationContext)
        setContent {
            MaterialTheme(colorScheme = SchedaColors, typography = SchedaType, shapes = SchedaShapes) {
                Surface(Modifier.fillMaxSize(), color = Color.Black) {
                    App(store)
                }
            }
        }
    }
}

private enum class Screen { Home, Edit, Train }

// ---------------------------------------------------------------- Stato aggiornamenti

private sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Updater.Release, val hint: String? = null) : UpdateState
    data object Downloading : UpdateState
    data class Failed(val message: String) : UpdateState
}

private val isRobolectric = Build.FINGERPRINT == "robolectric"

@Composable
private fun App(store: Store) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var text by remember { mutableStateOf(store.loadText()) }
    val workouts = remember(text) { SchedaParser.parse(text) }
    var progress by remember {
        mutableStateOf(store.loadProgress()?.takeIf { it.isValidFor(SchedaParser.parse(text)) })
    }
    // Se l'app era stata chiusa a metà allenamento, si riparte direttamente da lì.
    var screen by remember { mutableStateOf(if (progress != null) Screen.Train else Screen.Home) }
    var update by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }

    fun setProgress(p: Progress?) {
        progress = p
        store.saveProgress(p)
    }

    fun checkUpdates(silent: Boolean) {
        update = UpdateState.Checking
        scope.launch {
            update = try {
                val r = withContext(Dispatchers.IO) { Updater.fetchNewer(BuildConfig.VERSION_NAME) }
                if (r != null) UpdateState.Available(r) else if (silent) UpdateState.Idle else UpdateState.UpToDate
            } catch (e: Exception) {
                if (silent) UpdateState.Idle else UpdateState.Failed("Controllo non riuscito: controlla la connessione.")
            }
        }
    }

    fun installUpdate(release: Updater.Release) {
        if (!Updater.canInstall(context)) {
            Updater.openInstallPermissionSettings(context)
            update = UpdateState.Available(release, "Consenti «Installa app sconosciute» a Scheda, poi torna qui e tocca di nuovo Aggiorna.")
            return
        }
        update = UpdateState.Downloading
        scope.launch {
            try {
                val apk = withContext(Dispatchers.IO) { Updater.download(context, release) }
                Updater.install(context, apk)
                update = UpdateState.Available(release)
            } catch (e: Exception) {
                update = UpdateState.Failed("Download non riuscito: ${e.message ?: "errore"}")
            }
        }
    }

    // Controllo silenzioso all'avvio: se c'è una versione nuova compare un avviso in Home.
    LaunchedEffect(Unit) { if (!isRobolectric) checkUpdates(silent = true) }

    Box(Modifier.safeDrawingPadding()) {
        when (screen) {
            Screen.Home -> HomeScreen(
                workouts = workouts,
                progress = progress,
                update = update,
                onStart = { index ->
                    val p = progress
                    if (p == null || p.workout != index || p.isFinished(workouts[index])) {
                        setProgress(Progress(index, 0, 0))
                    }
                    screen = Screen.Train
                },
                onEdit = { screen = Screen.Edit },
                onCheckUpdates = { checkUpdates(silent = false) },
                onInstallUpdate = ::installUpdate,
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
                    LaunchedEffect(Unit) { screen = Screen.Home }
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
    update: UpdateState,
    onStart: (Int) -> Unit,
    onEdit: () -> Unit,
    onCheckUpdates: () -> Unit,
    onInstallUpdate: (Updater.Release) -> Unit,
) {
    val type = MaterialTheme.typography
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = Margin),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Scheda", style = type.displaySmall, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
        }
        if (update is UpdateState.Available || update is UpdateState.Downloading) {
            item { UpdateBanner(update, onInstallUpdate) }
        }
        if (workouts.isEmpty()) {
            item { Text("Nessun esercizio trovato. Tocca «Modifica scheda».", style = type.bodyLarge, color = Muted) }
        }
        itemsIndexed(workouts) { index, w ->
            val inProgress = progress?.takeIf { it.workout == index && !it.isFinished(w) }
            Button(
                onClick = { onStart(index) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
                shape = MaterialTheme.shapes.large,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                colors = if (inProgress != null) ButtonDefaults.buttonColors()
                else ButtonDefaults.buttonColors(containerColor = Card, contentColor = Color.White),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Text(w.displayName, style = type.headlineMedium)
                    Text(
                        if (inProgress != null) "In corso · esercizio ${inProgress.exercise + 1} di ${w.exercises.size}"
                        else listOf("${w.exercises.size} esercizi", w.groupsLabel).filter { it.isNotEmpty() }.joinToString(" · "),
                        style = type.bodyLarge,
                        color = if (inProgress != null) Color.Black else Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        item {
            Column(Modifier.padding(top = 12.dp, bottom = 24.dp)) {
                FooterLink("Modifica scheda", onEdit)
                FooterLink(
                    when (update) {
                        UpdateState.Checking -> "Controllo aggiornamenti…"
                        UpdateState.UpToDate -> "Nessun aggiornamento · versione ${BuildConfig.VERSION_NAME}"
                        is UpdateState.Failed -> update.message
                        else -> "Controlla aggiornamenti · versione ${BuildConfig.VERSION_NAME}"
                    },
                    onCheckUpdates,
                    enabled = update !is UpdateState.Checking && update !is UpdateState.Downloading,
                )
            }
        }
    }
}

/** Link testuale allineato al bordo delle card (niente rientro del TextButton). */
@Composable
private fun FooterLink(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 12.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = Color.White, disabledContentColor = Muted),
    ) { Text(label, style = MaterialTheme.typography.bodyLarge) }
}

@Composable
private fun UpdateBanner(update: UpdateState, onInstall: (Updater.Release) -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .background(Card, MaterialTheme.shapes.large)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (update is UpdateState.Available) {
            Text("Versione ${update.release.version} disponibile", style = MaterialTheme.typography.titleLarge)
            if (update.hint != null) Text(update.hint, style = MaterialTheme.typography.bodyLarge, color = Muted)
            Button(onClick = { onInstall(update.release) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("Aggiorna", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            }
        } else {
            Text("Download dell'aggiornamento…", style = MaterialTheme.typography.titleLarge)
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = Accent, trackColor = Track)
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
    val type = MaterialTheme.typography
    BackHandler(onBack = onExit)

    // Lo schermo resta acceso finché questa schermata è visibile.
    Column(Modifier.fillMaxSize().keepScreenOn().padding(Margin)) {
        if (progress.isFinished(workout)) {
            FinishedContent(workout, onFinish)
            return@Column
        }

        val total = workout.exercises.size
        val ex = workout.exercises[progress.exercise]
        val next = workout.exercises.getOrNull(progress.exercise + 1)
        val isLastSet = next == null && progress.setsDone == ex.sets - 1
        val setsBefore = workout.exercises.take(progress.exercise).sumOf { it.sets } + progress.setsDone

        // Intestazione: dove sei nella scheda.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${workout.displayName} · esercizio ${progress.exercise + 1} di $total",
                style = type.bodyLarge,
                color = Muted,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = onExit,
                contentPadding = PaddingValues(horizontal = 0.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
            ) { Text("Esci", style = type.bodyLarge) }
        }
        LinearProgressIndicator(
            progress = { setsBefore.toFloat() / workout.totalSets },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = Accent,
            trackColor = Track,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )

        // Cosa fare: esercizio e ripetizioni.
        Spacer(Modifier.height(32.dp))
        if (ex.group != null) Text(ex.group.uppercase(), style = type.labelLarge, color = Muted)
        Text(ex.name, style = type.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            if (ex.toFailure) "A cedimento" else "${ex.reps} ripetizioni",
            style = type.titleLarge,
        )
        if (ex.note.isNotEmpty()) Text(ex.note, style = type.bodyLarge, color = Muted)

        Spacer(Modifier.weight(1f))

        // A che serie sei: l'informazione che cambia a ogni tap, vicino al pulsante.
        Text("Serie ${progress.setsDone + 1} di ${ex.sets}", style = type.displaySmall)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(ex.sets) { i ->
                Box(
                    Modifier.weight(1f).height(8.dp).background(
                        when {
                            i < progress.setsDone -> Accent
                            i == progress.setsDone -> Color.White
                            else -> Track
                        },
                        RoundedCornerShape(4.dp),
                    )
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            if (next != null) "Poi: ${next.name}" else "Ultimo esercizio",
            style = type.bodyLarge,
            color = Muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onProgress(progress.advance(workout))
            },
            modifier = Modifier.fillMaxWidth().height(184.dp),
            shape = MaterialTheme.shapes.large,
        ) {
            Text(if (isLastSet) "Fine allenamento" else "Serie fatta", style = type.displaySmall)
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (progress.canUndo) {
                OutlinedButton(
                    onClick = { onProgress(progress.undo(workout)) },
                    modifier = Modifier.weight(1f).height(56.dp),
                ) { Text("Annulla serie", style = type.bodyLarge, color = Color.White) }
            }
            OutlinedButton(
                onClick = { onProgress(progress.skip(workout)) },
                modifier = Modifier.weight(1f).height(56.dp),
            ) { Text("Salta esercizio", style = type.bodyLarge, color = Color.White) }
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
        Text("Allenamento completato", style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
        Text(workout.displayName, style = MaterialTheme.typography.titleLarge, color = Muted)
        Spacer(Modifier.height(48.dp))
        Button(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth().height(96.dp),
            shape = MaterialTheme.shapes.large,
        ) { Text("Torna alle schede", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
    }
}

// ---------------------------------------------------------------- Modifica

@Composable
private fun EditScreen(initial: String, onSave: (String) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val type = MaterialTheme.typography
    var text by remember { mutableStateOf(initial) }
    val parsed = remember(text) { SchedaParser.parse(text) }
    BackHandler(onBack = onCancel)

    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) readText(context, uri)?.let { text = it }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Margin),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Modifica scheda", style = type.displaySmall)
        Text(
            "Incolla il testo dalle note. Formato: un titolo per giorno, «Gruppo:» per i muscoli, " +
                "«Esercizio 3x10» o «3xF» (cedimento). Righe vuote e puntini vengono ignorati.",
            style = type.bodyLarge,
            color = Muted,
        )
        OutlinedButton(onClick = { openFile.launch("text/*") }) {
            Text("Importa da file .txt", style = type.bodyLarge, color = Color.White)
        }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 320.dp),
            shape = MaterialTheme.shapes.medium,
            textStyle = type.bodyLarge.copy(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
        )

        Text("Anteprima", style = type.titleLarge)
        if (parsed.isEmpty()) {
            Text("Nessun esercizio riconosciuto.", style = type.bodyLarge, color = Color(0xFFFF8A80))
        }
        parsed.forEach { w ->
            Column(
                Modifier.fillMaxWidth().background(Card, MaterialTheme.shapes.large).padding(20.dp)
            ) {
                Text("${w.displayName} — ${w.exercises.size} esercizi", style = type.bodyLarge, fontWeight = FontWeight.Bold)
                w.exercises.forEach { e ->
                    Text(
                        "${e.name}  ${e.sets}×${if (e.toFailure) "F" else e.reps}" +
                            if (e.note.isNotEmpty()) "  ${e.note}" else "",
                        style = type.bodyLarge,
                        color = Muted,
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).height(56.dp)) {
                Text("Annulla", style = type.bodyLarge, color = Color.White)
            }
            Button(
                onClick = { onSave(text) },
                enabled = parsed.isNotEmpty(),
                modifier = Modifier.weight(1f).height(56.dp),
            ) { Text("Salva", style = type.bodyLarge, fontWeight = FontWeight.Bold) }
        }
        Text("Salvando, l'allenamento in corso viene azzerato.", style = type.bodyLarge, color = Muted)
    }
}

private fun readText(context: Context, uri: android.net.Uri): String? =
    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
