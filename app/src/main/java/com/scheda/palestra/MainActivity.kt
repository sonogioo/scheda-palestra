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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.platform.testTag
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

private val Margin = 16.dp
private val Italian = Locale.ITALIAN

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = Store(applicationContext)
        setContent {
            MaterialTheme(colorScheme = SchedaColors, typography = SchedaType, shapes = SchedaShapes) {
                Box(Modifier.fillMaxSize().background(Background)) {
                    // Testo bianco di default su tutto lo sfondo inchiostro.
                    CompositionLocalProvider(LocalContentColor provides Color.White) {
                        App(store)
                    }
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

// ---------------------------------------------------------------- Componenti comuni

@Composable
private fun Chip(label: String, background: Color, content: Color) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = content,
        modifier = Modifier
            .background(background, CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun GhostButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        border = BorderStroke(1.5.dp, Line),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Surface2, contentColor = Color.White),
    ) { Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold) }
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
    val today = remember { LocalDate.now() }
    val haze = rememberHazeState()
    val scope = rememberCoroutineScope()

    if (workouts.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(Margin), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("La tua scheda", style = MaterialTheme.typography.displayMedium)
            Text("Nessun esercizio trovato.", style = MaterialTheme.typography.bodyLarge, color = SoftText)
            GhostButton("Modifica scheda", onEdit, Modifier.fillMaxWidth())
        }
        return
    }

    // All'apertura si parte dal giorno di oggi (o dal prossimo allenamento se oggi è riposo);
    // se c'è un allenamento lasciato a metà, si torna su quello.
    val unfinished = progress?.takeIf { !it.isFinished(workouts[it.workout]) }?.workout
    val pager = rememberPagerState(initialPage = unfinished ?: Days.indexFor(workouts, today.dayOfWeek)) { workouts.size }

    Box(Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize().hazeSource(haze),
        ) { page ->
            DayPage(
                workout = workouts[page],
                index = page,
                today = today,
                progress = progress,
                update = update,
                onStart = onStart,
                onEdit = onEdit,
                onCheckUpdates = onCheckUpdates,
                onInstallUpdate = onInstallUpdate,
            )
        }

        GlassDayBar(
            workouts = workouts,
            selected = pager.currentPage,
            today = today,
            haze = haze,
            onSelect = { scope.launch { pager.animateScrollToPage(it) } },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** Navbar in vetro: sfoca e rifrange quello che le scorre sotto; il giorno scelto prende il suo colore. */
@Composable
private fun GlassDayBar(
    workouts: List<Workout>,
    selected: Int,
    today: LocalDate,
    haze: HazeState,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(36.dp)
    val glass = remember {
        GlassStyle.regular.then {
            shape(shape)
            tint(Color.White.copy(alpha = 0.06f))
            backgroundColor(Ink.copy(alpha = 0.35f))
        }
    }
    val scrolls = workouts.size > 5
    Row(
        modifier
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .fillMaxWidth()
            .hazeGlass(input = HazeInput.Sources(haze), style = glass)
            .border(1.dp, Color.White.copy(alpha = 0.14f), shape)
            .padding(6.dp)
            .then(if (scrolls) Modifier.horizontalScroll(rememberScrollState()) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        workouts.forEachIndexed { i, w ->
            val isSelected = i == selected
            val palette = paletteFor(w)
            Column(
                (if (scrolls) Modifier.width(72.dp) else Modifier.weight(1f))
                    .height(56.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .then(if (isSelected) Modifier.background(palette.brush) else Modifier)
                    .clickable { onSelect(i) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    w.displayName.take(3),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) Ink else Color.White.copy(alpha = 0.85f),
                )
                // Pallino sotto il giorno di oggi.
                Box(
                    Modifier.padding(top = 2.dp).size(5.dp).background(
                        when {
                            !Days.isToday(w, today.dayOfWeek) -> Color.Transparent
                            isSelected -> Ink
                            else -> palette.solid
                        },
                        CircleShape,
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayPage(
    workout: Workout,
    index: Int,
    today: LocalDate,
    progress: Progress?,
    update: UpdateState,
    onStart: (Int) -> Unit,
    onEdit: () -> Unit,
    onCheckUpdates: () -> Unit,
    onInstallUpdate: (Updater.Release) -> Unit,
) {
    val type = MaterialTheme.typography
    val palette = paletteFor(workout)
    val isToday = Days.isToday(workout, today.dayOfWeek)
    val inProgress = progress?.takeIf { it.workout == index && !it.isFinished(workout) }
    val dateLabel = remember { today.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Italian)) }

    LazyColumn(
        Modifier.fillMaxSize(),
        // Spazio in fondo: il contenuto scorre sotto la navbar in vetro.
        contentPadding = PaddingValues(start = Margin, end = Margin, top = 20.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                if (isToday) "Oggi · $dateLabel" else dateLabel.replaceFirstChar { it.uppercase() },
                style = type.bodyLarge,
                color = SoftText,
            )
        }
        if (update is UpdateState.Available || update is UpdateState.Downloading) {
            item { UpdateBanner(update, onInstallUpdate) }
        }

        // Card principale del giorno.
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .background(palette.brush)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    when {
                        inProgress != null -> Chip("IN CORSO", Ink, Color.White)
                        isToday -> Chip("OGGI", Ink, Color.White)
                    }
                    workout.exercises.mapNotNull { it.group }.distinct().forEach {
                        Chip(it.uppercase(), Ink.copy(alpha = 0.12f), Ink)
                    }
                }
                Text(workout.displayName, style = type.displayMedium, color = Ink)
                Text(
                    "${workout.exercises.size} esercizi · ${workout.totalSets} serie",
                    style = type.titleLarge,
                    color = Ink.copy(alpha = 0.75f),
                )
                if (inProgress != null) {
                    LinearProgressIndicator(
                        progress = { inProgress.exercise.toFloat() / workout.exercises.size },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = Ink,
                        trackColor = Ink.copy(alpha = 0.15f),
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                    )
                }
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = { onStart(index) },
                    modifier = Modifier.fillMaxWidth().height(64.dp).testTag("start_$index"),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Color.White),
                ) {
                    Text(
                        if (inProgress != null) "Riprendi · esercizio ${inProgress.exercise + 1} di ${workout.exercises.size}"
                        else "Inizia allenamento",
                        style = type.titleLarge,
                    )
                }
            }
        }

        // Elenco esercizi del giorno, raggruppati per muscolo.
        workout.exercises.withIndex().groupBy { it.value.group }.forEach { (group, items) ->
            item {
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(paletteFor(group).solid, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text((group ?: "Esercizi").uppercase(), style = type.labelLarge, color = SoftText)
                }
            }
            items(items.size) { k ->
                val (i, e) = items[k]
                val done = inProgress != null && i < inProgress.exercise
                val color = paletteFor(e.group).solid
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Surface2)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(36.dp).background(if (done) color else color.copy(alpha = 0.18f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (done) "✓" else "${i + 1}",
                            style = type.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (done) Ink else color,
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(
                        e.name,
                        style = type.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (done) SoftText else Color.White,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${e.sets} × ${if (e.toFailure) "F" else e.reps}",
                        style = type.titleLarge,
                        color = if (done) SoftText else Color.White,
                    )
                }
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GhostButton("Modifica scheda", onEdit, Modifier.weight(1f))
                GhostButton(
                    when (update) {
                        UpdateState.Checking -> "Controllo…"
                        UpdateState.UpToDate -> "Aggiornata ✓"
                        is UpdateState.Failed -> "Riprova"
                        else -> "Aggiornamenti"
                    },
                    onCheckUpdates,
                    Modifier.weight(1f),
                )
            }
            val footer = (update as? UpdateState.Failed)?.message ?: "Versione ${BuildConfig.VERSION_NAME}"
            Text(footer, style = type.bodyLarge, color = SoftText, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
private fun UpdateBanner(update: UpdateState, onInstall: (Updater.Release) -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .background(Surface2, MaterialTheme.shapes.large)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (update is UpdateState.Available) {
            Text("Nuova versione ${update.release.version}", style = MaterialTheme.typography.titleLarge)
            if (update.hint != null) Text(update.hint, style = MaterialTheme.typography.bodyLarge, color = SoftText)
            Button(
                onClick = { onInstall(update.release) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DefaultPalette.start, contentColor = Ink),
            ) { Text("Aggiorna", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold) }
        } else {
            Text("Download dell'aggiornamento…", style = MaterialTheme.typography.titleLarge)
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = DefaultPalette.start, trackColor = Line)
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

    if (progress.isFinished(workout)) {
        FinishedContent(workout, onFinish)
        return
    }

    val total = workout.exercises.size
    val ex = workout.exercises[progress.exercise]
    val next = workout.exercises.getOrNull(progress.exercise + 1)
    val isLastSet = next == null && progress.setsDone == ex.sets - 1
    val palette = paletteFor(ex.group)

    // Lo schermo resta acceso finché questa schermata è visibile.
    Column(Modifier.fillMaxSize().keepScreenOn().padding(Margin)) {
        // Intestazione: giorno, posizione, uscita.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${workout.displayName} · ${progress.exercise + 1}/$total",
                style = type.titleLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = onExit,
                colors = ButtonDefaults.textButtonColors(contentColor = SoftText),
            ) { Text("Esci", style = type.bodyLarge, fontWeight = FontWeight.SemiBold) }
        }
        // Un segmento per esercizio, colorato col gruppo: si vede a colpo d'occhio dove sei.
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            workout.exercises.forEachIndexed { i, e ->
                Box(
                    Modifier.weight(1f).height(6.dp).background(
                        when {
                            i < progress.exercise -> paletteFor(e.group).solid
                            i == progress.exercise -> Color.White
                            else -> Line
                        },
                        CircleShape,
                    )
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Card principale col colore del gruppo muscolare.
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(palette.brush)
                .padding(24.dp),
        ) {
            if (ex.group != null) Chip(ex.group.uppercase(), Ink.copy(alpha = 0.12f), Ink)
            Spacer(Modifier.height(12.dp))
            Text(ex.name, style = type.displayMedium, color = Ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(
                if (ex.toFailure) "A cedimento" else "${ex.reps} ripetizioni",
                style = type.headlineMedium,
                color = Ink.copy(alpha = 0.8f),
            )
            if (ex.note.isNotEmpty()) Text(ex.note, style = type.titleLarge, color = Ink.copy(alpha = 0.7f))

            Spacer(Modifier.weight(1f))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(112.dp)) {
                    CircularProgressIndicator(
                        progress = { progress.setsDone.toFloat() / ex.sets },
                        modifier = Modifier.size(112.dp),
                        color = Ink,
                        trackColor = Ink.copy(alpha = 0.15f),
                        strokeWidth = 12.dp,
                        strokeCap = StrokeCap.Round,
                        gapSize = 0.dp,
                    )
                    Text("${progress.setsDone + 1}", style = type.displayMedium, color = Ink)
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("SERIE", style = type.labelLarge, color = Ink.copy(alpha = 0.7f))
                    Text("${progress.setsDone + 1} di ${ex.sets}", style = type.displaySmall, color = Ink)
                }
            }
        }

        // Prossimo esercizio, con il pallino del suo colore.
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(10.dp).background(next?.let { paletteFor(it.group).solid } ?: SoftText, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(
                if (next != null) "Poi: ${next.name}" else "Ultimo esercizio",
                style = type.bodyLarge,
                color = SoftText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Button(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onProgress(progress.advance(workout))
            },
            modifier = Modifier.fillMaxWidth().height(150.dp),
            shape = MaterialTheme.shapes.large,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Ink),
        ) {
            Text(if (isLastSet) "Fine allenamento" else "Serie fatta", style = type.displaySmall)
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (progress.canUndo) GhostButton("Annulla serie", { onProgress(progress.undo(workout)) }, Modifier.weight(1f))
            GhostButton("Salta esercizio", { onProgress(progress.skip(workout)) }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun FinishedContent(workout: Workout, onFinish: () -> Unit) {
    val palette = paletteFor(workout)
    Column(
        Modifier.fillMaxSize().background(palette.brush).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(120.dp).background(Ink, CircleShape), contentAlignment = Alignment.Center) {
            Text("✓", fontSize = 64.sp, color = Color.White, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(24.dp))
        Text("Allenamento\ncompletato", style = MaterialTheme.typography.displayMedium, color = Ink, textAlign = TextAlign.Center)
        Text(
            "${workout.displayName} · ${workout.exercises.size} esercizi · ${workout.totalSets} serie",
            style = MaterialTheme.typography.titleLarge,
            color = Ink.copy(alpha = 0.75f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(48.dp))
        Button(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth().height(80.dp),
            shape = MaterialTheme.shapes.large,
            colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Color.White),
        ) { Text("Torna alle schede", style = MaterialTheme.typography.titleLarge) }
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

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(Margin),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Modifica scheda", style = type.displayMedium)
            Text(
                "Incolla il testo dalle note. Un titolo per giorno, «Gruppo:» per i muscoli, " +
                    "«Esercizio 3x10» o «3xF» (cedimento).",
                style = type.bodyLarge,
                color = SoftText,
            )
            GhostButton("Importa da file .txt", { openFile.launch("text/*") })
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 280.dp),
                shape = MaterialTheme.shapes.medium,
                textStyle = type.bodyLarge.copy(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Surface2,
                    unfocusedContainerColor = Surface2,
                    unfocusedBorderColor = Line,
                    focusedBorderColor = DefaultPalette.start,
                ),
            )

            Text("Anteprima", style = type.headlineMedium, modifier = Modifier.padding(top = 8.dp))
            if (parsed.isEmpty()) {
                Text("Nessun esercizio riconosciuto.", style = type.bodyLarge, color = Color(0xFFFF8A80))
            }
            parsed.forEach { w ->
                Column(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(Surface2).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("${w.displayName} · ${w.exercises.size} esercizi", style = type.titleLarge)
                    w.exercises.forEach { e ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).background(paletteFor(e.group).solid, CircleShape))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "${e.name}  ${e.sets}×${if (e.toFailure) "F" else e.reps}" +
                                    if (e.note.isNotEmpty()) "  ${e.note}" else "",
                                style = type.bodyLarge,
                                color = SoftText,
                            )
                        }
                    }
                }
            }
            Text("Salvando, l'allenamento in corso viene azzerato.", style = type.bodyLarge, color = SoftText)
        }

        // Barra fissa: Salva sempre raggiungibile, senza scorrere tutta la scheda.
        Row(
            Modifier.fillMaxWidth().background(Ink).padding(Margin),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GhostButton("Annulla", onCancel, Modifier.weight(1f))
            Button(
                onClick = { onSave(text) },
                enabled = parsed.isNotEmpty(),
                modifier = Modifier.weight(1f).height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DefaultPalette.start, contentColor = Ink),
            ) { Text("Salva", style = type.bodyLarge, fontWeight = FontWeight.Bold) }
        }
    }
}

private fun readText(context: Context, uri: android.net.Uri): String? =
    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
