package com.scheda.palestra

import android.content.Context
import android.content.Intent
import android.Manifest
import android.os.Build
import android.widget.Toast
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Margin = 16.dp
private val Italian = Locale.ITALIAN
private const val ACTION_START_TODAY = "com.scheda.palestra.START_TODAY"

class MainActivity : ComponentActivity() {

    /** Azione del tasto volume giù: la registra la schermata di allenamento finché è visibile. */
    var volumeDownAction: (() -> Unit)? = null

    /** Incrementato a ogni avvio dalla scorciatoia "Allenamento di oggi". */
    private val startTodayRequests = mutableIntStateOf(0)

    /** Incrementato quando si tocca la notifica dell'allenamento: si va alla schermata della serie. */
    private val openTrainRequests = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null && intent?.action == ACTION_START_TODAY) startTodayRequests.intValue++
        if (savedInstanceState == null && intent?.action == RestAlarm.ACTION_OPEN_TRAIN) openTrainRequests.intValue++
        val store = Store(applicationContext)
        setContent {
            MaterialTheme(colorScheme = SchedaColors, typography = SchedaType, shapes = SchedaShapes) {
                Box(Modifier.fillMaxSize().background(Background)) {
                    // Testo bianco di default su tutto lo sfondo inchiostro.
                    CompositionLocalProvider(LocalContentColor provides Color.White) {
                        App(store, startTodayRequests.intValue, openTrainRequests.intValue)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == ACTION_START_TODAY) startTodayRequests.intValue++
        if (intent.action == RestAlarm.ACTION_OPEN_TRAIN) openTrainRequests.intValue++
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val action = volumeDownAction
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && action != null) {
            if (event.repeatCount == 0) action()
            return true // durante l'allenamento il tasto non cambia il volume
        }
        return super.onKeyDown(keyCode, event)
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
private fun App(store: Store, startTodayRequest: Int, openTrainRequest: Int = 0) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var text by remember { mutableStateOf(store.loadText()) }
    val workouts = remember(text) { SchedaParser.parse(text) }
    var progress by remember {
        mutableStateOf(store.loadProgress()?.takeIf { it.isValidFor(SchedaParser.parse(text)) })
    }
    // Si apre sempre sulla Home: l'allenamento parte solo quando lo scegli tu.
    var screen by remember { mutableStateOf(Screen.Home) }
    var update by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }
    var history by remember { mutableStateOf(store.history()) }
    var weights by remember { mutableStateOf(store.allWeights()) }

    fun setProgress(p: Progress?) {
        progress = p
        store.saveProgress(p)
        if (isRobolectric) return
        // Notifica dell'allenamento (serie, peso, recupero) + sveglia di fine recupero.
        val w = p?.let { workouts.getOrNull(it.workout) }
        val kg = w?.let { p.current(it) }?.let { weights[it.name.lowercase()] } ?: 0f
        RestAlarm.sync(context, w, p, kg)
    }

    // Cambiamenti fatti dalla notifica o dalla sveglia mentre l'app era in secondo piano.
    val liveChanges = Live.changes.intValue
    LaunchedEffect(liveChanges) {
        if (liveChanges == 0) return@LaunchedEffect
        progress = store.loadProgress()?.takeIf { it.isValidFor(workouts) }
        history = store.history()
    }

    // Backup: esporta e importa scheda, pesi e storico in un file.
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = runCatching {
            context.contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use { it.write(store.exportJson()) }
        }.isSuccess
        Toast.makeText(context, if (ok) "Backup salvato" else "Backup non riuscito", Toast.LENGTH_LONG).show()
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val result = runCatching {
            val json = context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
            store.importJson(json)
        }
        if (result.isSuccess) {
            text = store.loadText()
            weights = store.allWeights()
            history = store.history()
            progress = null
            RestAlarm.cancel(context)
            Toast.makeText(context, "Backup ripristinato", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, "File non valido: serve un backup di focus", Toast.LENGTH_LONG).show()
        }
    }

    // Android 13+: serve il permesso per mostrare la notifica del timer.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun start(index: Int) {
        if (Build.VERSION.SDK_INT >= 33 && !RestAlarm.canNotify(context) && !isRobolectric) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val p = progress
        if (p == null || p.workout != index || p.isFinished(workouts[index])) {
            setProgress(Progress(index, 0, 0, startedAt = System.currentTimeMillis()))
        }
        screen = Screen.Train
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
            update = UpdateState.Available(release, "Consenti «Installa app sconosciute» a focus, poi torna qui e tocca di nuovo Aggiorna.")
            return
        }
        update = UpdateState.Downloading
        scope.launch {
            try {
                val apk = withContext(Dispatchers.IO) { Updater.download(context, release) }
                try {
                    Updater.install(context, apk)
                } catch (_: Exception) {
                    Updater.installWithSystemInstaller(context)
                }
                update = UpdateState.Available(release)
            } catch (e: Exception) {
                update = UpdateState.Failed("Download non riuscito: ${e.message ?: "errore"}")
            }
        }
    }

    // Controllo silenzioso all'avvio: se c'è una versione nuova compare un avviso in Home.
    LaunchedEffect(Unit) { if (!isRobolectric) checkUpdates(silent = true) }

    // Notifica toccata: si torna alla serie dell'allenamento in corso.
    LaunchedEffect(openTrainRequest) {
        if (openTrainRequest == 0) return@LaunchedEffect
        val p = progress
        if (p != null && workouts.getOrNull(p.workout)?.let { !p.isFinished(it) } == true) screen = Screen.Train
    }

    // Scorciatoia dall'icona: apre la Home sulla scheda di oggi, senza avviare nulla.
    LaunchedEffect(startTodayRequest) {
        if (startTodayRequest == 0) return@LaunchedEffect
        if (screen == Screen.Train) screen = Screen.Home
    }

    Box(Modifier.safeDrawingPadding()) {
        when (screen) {
            Screen.Home -> HomeScreen(
                workouts = workouts,
                progress = progress,
                update = update,
                history = history,
                weights = weights,
                onStart = ::start,
                onEdit = { screen = Screen.Edit },
                onCheckUpdates = { checkUpdates(silent = false) },
                onInstallUpdate = ::installUpdate,
                onExport = { exportLauncher.launch("focus-backup-${LocalDate.now()}.json") },
                onImport = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                showTodayRequest = startTodayRequest,
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
                    val w = workouts[p.workout]
                    TrainScreen(
                        workout = w,
                        progress = p,
                        weightOf = { weights[it.lowercase()] ?: 0f },
                        onWeight = { name, kg ->
                            store.saveWeight(name, kg)
                            weights = weights + (name.lowercase() to kg)
                        },
                        onProgress = { setProgress(it) },
                        onExit = { screen = Screen.Home },
                        onCompleted = {
                            store.markDone(w, LocalDate.now())
                            history = store.history()
                        },
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

/** Pulsante tondo con un simbolo, per +/− su sfondo colorato. */
@Composable
private fun RoundButton(label: String, onClick: () -> Unit, tint: Color = Ink) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = tint) }
}

// ---------------------------------------------------------------- Home

@Composable
private fun HomeScreen(
    workouts: List<Workout>,
    progress: Progress?,
    update: UpdateState,
    history: Map<String, LocalDate>,
    weights: Map<String, Float>,
    onStart: (Int) -> Unit,
    onEdit: () -> Unit,
    onCheckUpdates: () -> Unit,
    onInstallUpdate: (Updater.Release) -> Unit,
    onExport: () -> Unit = {},
    onImport: () -> Unit = {},
    showTodayRequest: Int = 0,
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

    val doneThisWeek: (Workout) -> Boolean = { Days.isThisWeek(history[it.displayName.lowercase()], today) }

    // All'apertura si parte dal giorno di oggi (o dal prossimo allenamento se oggi è riposo);
    // se c'è un allenamento lasciato a metà, si torna su quello.
    val unfinished = progress?.takeIf { !it.isFinished(workouts[it.workout]) }?.workout
    val todayIndex = Days.indexFor(workouts, today.dayOfWeek)
    // Dalla scorciatoia si va sempre sulla scheda di oggi.
    val pager = rememberPagerState(initialPage = if (showTodayRequest > 0) todayIndex else unfinished ?: todayIndex) { workouts.size }
    LaunchedEffect(showTodayRequest) {
        if (showTodayRequest > 0) pager.scrollToPage(todayIndex)
    }

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
                done = doneThisWeek(workouts[page]),
                weights = weights,
                onStart = onStart,
                onEdit = onEdit,
                onCheckUpdates = onCheckUpdates,
                onExport = onExport,
                onImport = onImport,
                onInstallUpdate = onInstallUpdate,
            )
        }

        GlassDayBar(
            workouts = workouts,
            selected = pager.currentPage,
            today = today,
            haze = haze,
            doneThisWeek = doneThisWeek,
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
    doneThisWeek: (Workout) -> Boolean,
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
            val done = doneThisWeek(w)
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
                val markColor = if (isSelected) Ink else palette.solid
                when {
                    // Fatto questa settimana: spunta.
                    done -> Text("✓", fontSize = 12.sp, lineHeight = 12.sp, fontWeight = FontWeight.Black, color = markColor)
                    // Oggi: pallino.
                    Days.isToday(w, today.dayOfWeek) ->
                        Box(Modifier.padding(top = 2.dp).size(5.dp).background(markColor, CircleShape))
                    else -> Spacer(Modifier.height(7.dp))
                }
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
    done: Boolean,
    weights: Map<String, Float>,
    onStart: (Int) -> Unit,
    onEdit: () -> Unit,
    onCheckUpdates: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.focus_wordmark),
                    contentDescription = "focus",
                    modifier = Modifier.height(48.dp),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (isToday) "Oggi · $dateLabel" else dateLabel.replaceFirstChar { it.uppercase() },
                    style = type.bodyLarge,
                    color = SoftText,
                )
            }
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
                    if (inProgress != null) Chip("IN CORSO", Ink, Color.White)
                    if (done && inProgress == null) Chip("FATTO ✓", Ink, Color.White)
                    if (isToday) Chip("OGGI", if (done || inProgress != null) Ink.copy(alpha = 0.12f) else Ink, if (done || inProgress != null) Ink else Color.White)
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
                        when {
                            inProgress != null -> "Riprendi · esercizio ${inProgress.exercise + 1} di ${workout.exercises.size}"
                            done -> "Rifallo"
                            else -> "Inizia allenamento"
                        },
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
                val exDone = inProgress != null && inProgress.isDone(workout, i)
                val color = paletteFor(e.group).solid
                val kg = weights[e.name.lowercase()] ?: 0f
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Surface2)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(36.dp).background(if (exDone) color else color.copy(alpha = 0.18f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (exDone) "✓" else "${i + 1}",
                            style = type.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (exDone) Ink else color,
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            e.name,
                            style = type.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (exDone) SoftText else Color.White,
                        )
                        if (kg > 0f) Text("Ultimo peso: ${formatKg(kg)}", style = type.bodyLarge, fontSize = 14.sp, color = SoftText)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${e.sets} × ${if (e.toFailure) "F" else e.reps}",
                        style = type.titleLarge,
                        color = if (exDone) SoftText else Color.White,
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
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GhostButton("Esporta backup", onExport, Modifier.weight(1f))
                GhostButton("Importa backup", onImport, Modifier.weight(1f))
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
    weightOf: (String) -> Float,
    onWeight: (String, Float) -> Unit,
    onProgress: (Progress) -> Unit,
    onExit: () -> Unit,
    onCompleted: () -> Unit,
    onFinish: () -> Unit,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val type = MaterialTheme.typography
    BackHandler(onBack = onExit)

    if (progress.isFinished(workout)) {
        FinishedContent(workout, progress.startedAt, onCompleted, onFinish)
        return
    }

    val latest by rememberUpdatedState(progress)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val resting = progress.isResting(now)

    // Avviso a schermo per qualche secondo quando il recupero finisce
    // (sia dal conto alla rovescia dell'app sia dalla sveglia di sistema).
    var justEnded by remember { mutableStateOf(false) }
    var lastRestUntil by remember { mutableLongStateOf(0L) }
    LaunchedEffect(progress.restUntil) {
        val ended = System.currentTimeMillis() - lastRestUntil
        if (progress.restUntil == 0L && lastRestUntil != 0L && ended in 0..5_000) justEnded = true
        lastRestUntil = progress.restUntil
    }
    LaunchedEffect(justEnded) {
        if (justEnded) {
            delay(6_000)
            justEnded = false
        }
    }
    LaunchedEffect(Unit) { RestAlarm.clearDone(context) }

    // Conto alla rovescia del recupero: a zero suona, vibra e torna alla serie.
    LaunchedEffect(progress.restUntil) {
        // Nei test di screenshot il recupero resta fermo (l'orologio di test non è quello reale).
        if (progress.restUntil == 0L || isRobolectric) return@LaunchedEffect
        while (true) {
            now = System.currentTimeMillis()
            if (now >= latest.restUntil) break
            delay(200)
        }
        if (latest.restUntil != 0L) {
            // Se l'app è stata riaperta molto dopo, niente avviso "in ritardo".
            if (now - latest.restUntil < 5_000) {
                RestAlarm.fire(context, latest.restUntil, restEndText(workout, latest))
            }
            onProgress(latest.noRest())
        }
    }

    val total = workout.exercises.size
    val ex = progress.current(workout)!!
    val next = progress.nextExercise(workout)
    val isLastSet = next == null && progress.setsDone == ex.sets - 1
    val palette = paletteFor(ex.group)

    val primaryLabel = when {
        resting -> "Inizia serie ${progress.setsDone + 1}"
        isLastSet -> "Fine allenamento"
        else -> "Serie fatta"
    }
    val primary: () -> Unit = {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        val p = latest
        val t = System.currentTimeMillis()
        now = t
        justEnded = false
        RestAlarm.clearDone(context)
        onProgress(if (p.isResting(t)) p.noRest() else p.completeSet(workout, t))
    }

    // Tasto volume giù = stessa azione del pulsante grande (mani sudate, telefono appoggiato).
    val activity = context as? MainActivity
    val primaryNow by rememberUpdatedState(primary)
    DisposableEffect(activity) {
        activity?.volumeDownAction = { primaryNow() }
        onDispose { activity?.volumeDownAction = null }
    }

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
            progress.orderFor(workout).forEachIndexed { i, idx ->
                val e = workout.exercises[idx]
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

        if (justEnded) {
            // Recupero finito: banda bianca ben visibile anche da lontano.
            Box(
                Modifier.fillMaxWidth().padding(vertical = 12.dp)
                    .background(Color.White, MaterialTheme.shapes.medium)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                Text(
                    "Recupero finito · vai con la serie ${progress.setsDone + 1}",
                    style = type.titleLarge, color = Ink, fontWeight = FontWeight.Bold,
                )
            }
        } else {
            Spacer(Modifier.height(16.dp))
        }

        // Card principale col colore del gruppo muscolare.
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(palette.brush)
                .padding(24.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (ex.group != null) Chip(ex.group.uppercase(), Ink.copy(alpha = 0.12f), Ink)
                if (resting) Chip("RECUPERO", Ink, Color.White)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                ex.name,
                style = if (ex.name.length > 22) type.displaySmall else type.displayMedium,
                color = Ink,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (ex.toFailure) "A cedimento" else "${ex.reps} ripetizioni",
                style = type.headlineMedium,
                color = Ink.copy(alpha = 0.8f),
            )
            if (ex.note.isNotEmpty()) Text(ex.note, style = type.titleLarge, color = Ink.copy(alpha = 0.7f))

            Spacer(Modifier.weight(1f))

            WeightRow(ex.name, weightOf, onWeight)

            Spacer(Modifier.height(16.dp))

            if (resting) {
                RestRow(progress, now, onAdjust = { delta ->
                    val t = System.currentTimeMillis()
                    now = t
                    onProgress(latest.adjustRest(delta, t))
                })
            } else {
                SetRow(progress.setsDone, ex.sets)
            }
        }

        // Prossimo esercizio, con il pallino del suo colore; a destra il promemoria del tasto volume.
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
                modifier = Modifier.weight(1f),
            )
            Text("Vol − = ok", style = type.bodyLarge, fontSize = 13.sp, color = SoftText.copy(alpha = 0.7f))
        }

        Button(
            onClick = primary,
            modifier = Modifier.fillMaxWidth().height(132.dp).testTag("cta"),
            shape = MaterialTheme.shapes.large,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Ink),
        ) {
            Text(primaryLabel, style = type.displaySmall)
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val postpone = progress.canPostpone(workout)
            if (progress.canUndo) {
                GhostButton(if (postpone) "Annulla" else "Annulla serie", { onProgress(progress.undo(workout)) }, Modifier.weight(1f))
            }
            // Macchina occupata: l'esercizio va in fondo e si passa al prossimo.
            if (postpone) GhostButton("Occupata", { onProgress(progress.postpone(workout)) }, Modifier.weight(1f).testTag("postpone"))
            GhostButton(if (postpone) "Salta" else "Salta esercizio", { onProgress(progress.skip(workout)) }, Modifier.weight(1f))
        }
    }
}

/** Peso dell'esercizio: si ricorda l'ultimo usato, si regola a passi di 2,5 kg. */
@Composable
private fun WeightRow(exercise: String, weightOf: (String) -> Float, onWeight: (String, Float) -> Unit) {
    var kg by remember(exercise) { mutableFloatStateOf(weightOf(exercise)) }
    fun set(v: Float) {
        kg = v.coerceIn(0f, 500f)
        onWeight(exercise, kg)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("PESO", style = MaterialTheme.typography.labelLarge, color = Ink.copy(alpha = 0.7f))
            Text(
                if (kg > 0f) formatKg(kg) else "Corpo libero",
                style = MaterialTheme.typography.headlineMedium,
                color = Ink,
            )
        }
        RoundButton("−", { set(kg - 2.5f) })
        Spacer(Modifier.width(10.dp))
        RoundButton("+", { set(kg + 2.5f) })
    }
}

@Composable
private fun RowScope.Ring(fraction: Float, center: String) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
        CircularProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            modifier = Modifier.size(100.dp),
            color = Ink,
            trackColor = Ink.copy(alpha = 0.15f),
            strokeWidth = 11.dp,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
        )
        Text(center, style = MaterialTheme.typography.headlineMedium, color = Ink)
    }
}

@Composable
private fun SetRow(setsDone: Int, sets: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Ring(setsDone.toFloat() / sets, "${setsDone + 1}")
        Spacer(Modifier.width(16.dp))
        Column {
            Text("SERIE", style = MaterialTheme.typography.labelLarge, color = Ink.copy(alpha = 0.7f))
            Text("${setsDone + 1} di $sets", style = MaterialTheme.typography.displaySmall, color = Ink)
        }
    }
}

/** Recupero: anello che si svuota, tempo rimanente, ±15 secondi. */
@Composable
private fun RestRow(progress: Progress, now: Long, onAdjust: (Long) -> Unit) {
    val remaining = (progress.restUntil - now).coerceAtLeast(0)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Ring(if (progress.restTotal > 0) remaining.toFloat() / progress.restTotal else 0f, Rest.format(remaining))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text("RECUPERO", style = MaterialTheme.typography.labelLarge, color = Ink.copy(alpha = 0.7f))
            Text("poi serie ${progress.setsDone + 1}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = Ink)
        }
        RoundButton("−15", { onAdjust(-15_000) })
        Spacer(Modifier.width(8.dp))
        RoundButton("+15", { onAdjust(15_000) })
    }
}

@Composable
private fun FinishedContent(workout: Workout, startedAt: Long, onCompleted: () -> Unit, onFinish: () -> Unit) {
    val palette = paletteFor(workout)
    val end = remember { System.currentTimeMillis() }
    LaunchedEffect(Unit) { onCompleted() }

    val minutes = if (startedAt > 0) ((end - startedAt) / 60_000).coerceAtLeast(1) else null
    val clock = DateTimeFormatter.ofPattern("HH:mm")
    fun time(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(clock)

    Column(
        Modifier.fillMaxSize().background(palette.brush).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Spunta disegnata (il glifo ✓ cambia aspetto a seconda dei font del telefono).
        Canvas(Modifier.size(112.dp).background(Ink, CircleShape)) {
            val path = Path().apply {
                moveTo(size.width * 0.30f, size.height * 0.52f)
                lineTo(size.width * 0.45f, size.height * 0.67f)
                lineTo(size.width * 0.72f, size.height * 0.37f)
            }
            drawPath(path, Color.White, style = Stroke(width = size.width * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        Spacer(Modifier.height(24.dp))
        Text("Allenamento\ncompletato", style = MaterialTheme.typography.displayMedium, color = Ink, textAlign = TextAlign.Center)
        Text(workout.displayName, style = MaterialTheme.typography.titleLarge, color = Ink.copy(alpha = 0.75f))
        Spacer(Modifier.height(28.dp))

        // Riepilogo: durata, serie, esercizi.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(minutes?.let { "$it" } ?: "—", "minuti")
            StatTile("${workout.totalSets}", "serie")
            StatTile("${workout.exercises.size}", "esercizi")
        }
        if (startedAt > 0) {
            Spacer(Modifier.height(12.dp))
            Text("${time(startedAt)} → ${time(end)}", style = MaterialTheme.typography.titleLarge, color = Ink.copy(alpha = 0.75f))
        }

        Spacer(Modifier.height(36.dp))
        Button(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth().height(80.dp),
            shape = MaterialTheme.shapes.large,
            colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Color.White),
        ) { Text("Torna alle schede", style = MaterialTheme.typography.titleLarge) }
    }
}

@Composable
private fun RowScope.StatTile(value: String, label: String) {
    Column(
        Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(Ink.copy(alpha = 0.12f)).padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = MaterialTheme.typography.displaySmall, color = Ink)
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Ink.copy(alpha = 0.75f))
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
                    "«Esercizio 3x10» o «3xF» (cedimento). Aggiungi «rec 60» dopo un esercizio per il suo recupero (di base 90 s).",
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
