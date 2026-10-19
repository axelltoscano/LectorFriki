package com.panels.reader

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CancellationException
import java.io.InputStream
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.junrar.Junrar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipFile

class MainActivity : ComponentActivity() {
    private var readerOpen = false
    private var darkAppearance = false

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) configureSystemBars(window, readerOpen, darkAppearance)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val appearancePrefs = remember { getSharedPreferences("appearance", Context.MODE_PRIVATE) }
            var dark by remember { mutableStateOf(appearancePrefs.getBoolean("dark", false)) }
            val changeTheme: (Boolean) -> Unit = { value ->
                dark = value
                appearancePrefs.edit().putBoolean("dark", value).apply()
            }
            LectorTheme(dark) {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                var comics by remember { mutableStateOf(LibraryStorage.loadComics(context)) }
                var activeComic by remember { mutableStateOf<ComicBook?>(null) }
                var activeReader by remember { mutableStateOf<UniversalComicReader?>(null) }
                var isLoading by remember { mutableStateOf(false) }
                var pendingDelete by remember { mutableStateOf<ComicBook?>(null) }
                val collectionResult = remember { runCatching { CollectionStorage.load(context) } }
                var collections by remember { mutableStateOf(collectionResult.getOrDefault(emptyList())) }
                var errorMessage by remember { mutableStateOf<String?>(
                    if (collectionResult.isFailure) "No se pudieron cargar las colecciones." else null) }
                val reading = activeComic != null && activeReader != null
                LaunchedEffect(reading, dark) {
                    readerOpen = reading
                    darkAppearance = dark
                    configureSystemBars(window, reading, dark)
                    if (reading) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }

                val filePicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenMultipleDocuments()
                ) { uris ->
                    if (uris.isNotEmpty()) {
                        isLoading = true
                        scope.launch {
                            val imported = mutableListOf<ComicBook>()
                            try {
                            for (uri in uris.distinct()) {
                                if (comics.any { it.fileUriString == uri.toString() }) continue
                                try {
                                try {
                                    contentResolver.takePersistableUriPermission(
                                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    )
                                } catch (_: Exception) {}

                                val id = UUID.randomUUID().toString()
                                val reader = UniversalComicReader(context, uri, id)
                                val pages = reader.initialize()
                                if (pages > 0) {
                                    val cover = reader.extractCoverThumbnail()
                                    val rawName = documentName(context, uri)
                                    val cleanTitle = rawName.substringBeforeLast('.')
                                    imported.add(
                                        ComicBook(
                                            id = id,
                                            title = cleanTitle,
                                            fileUriString = uri.toString(),
                                            coverPath = cover,
                                            totalPages = pages
                                        )
                                    )
                                }
                                } catch (e: CancellationException) { throw e
                                } catch (e: Exception) {
                                    errorMessage = "No se pudo importar un archivo: " + (e.message ?: "formato no compatible")
                                }
                            }
                            comics = comics + imported
                            LibraryStorage.saveComics(context, comics)
                            } finally { isLoading = false }
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    val currentComic = activeComic
                    val currentReader = activeReader

                    if (currentComic != null && currentReader != null) {
                        PanelsReaderView(
                            comic = currentComic,
                            reader = currentReader,
                            dark = dark,
                            onDarkChange = changeTheme,
                            onPageChanged = { page ->
                                comics = comics.map {
                                    if (it.id == currentComic.id) it.copy(currentPage = page) else it
                                }
                                LibraryStorage.saveComics(context, comics)
                            },
                            onClose = {
                                activeComic = null
                                activeReader = null
                            }
                        )
                    } else {
                        PanelsLibraryView(
                            comics = comics,
                            collections = collections,
                            onCollectionsChange = { updated ->
                                CollectionStorage.save(context, updated)
                                collections = updated
                            },
                            dark = dark,
                            onDarkChange = changeTheme,
                            onOpenComic = { selected ->
                                if (!isLoading) {
                                isLoading = true
                                scope.launch {
                                    try {
                                        val r = UniversalComicReader(context, Uri.parse(selected.fileUriString), selected.id)
                                        val count = r.initialize()
                                        comics = comics.map {
                                            if (it.id == selected.id) it.copy(totalPages = count, currentPage = it.currentPage.coerceIn(0, count - 1)) else it
                                        }
                                        LibraryStorage.saveComics(context, comics)
                                        activeReader = r
                                        activeComic = selected.copy(totalPages = count, currentPage = selected.currentPage.coerceIn(0, count - 1))
                                    } catch (e: CancellationException) { throw e
                                    } catch (e: Exception) {
                                        errorMessage = "No se pudo abrir: " + (e.message ?: "revisa el archivo original")
                                    } finally { isLoading = false }
                                }
                                }
                            },
                            onDeleteComic = { toDelete ->
                                pendingDelete = toDelete
                            },
                            onImportClick = {
                                if (!isLoading) filePicker.launch(arrayOf("*/*"))
                            }
                        )
                    }

                    pendingDelete?.let { book ->
                        AlertDialog(onDismissRequest = { pendingDelete = null },
                            title = { Text("¿Quitar de la biblioteca?") },
                            text = { Text(book.title + "\nEl archivo original se conserva.") },
                            confirmButton = { TextButton(onClick = {
                                collections = CollectionRules.withoutBook(collections, book.id)
                                CollectionStorage.save(context, collections)
                                comics = comics.filterNot { it.id == book.id }
                                LibraryStorage.saveComics(context, comics)
                                pendingDelete = null
                                scope.launch(Dispatchers.IO) {
                                    File(context.filesDir, "cover_" + book.id + ".jpg").delete()
                                    File(context.cacheDir, "comic_cache_" + book.id + ".tmp").delete()
                                    File(context.cacheDir, "cbr_" + book.id).deleteRecursively()
                                }
                            }) { Text("Quitar") } },
                            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") } })
                    }
                    errorMessage?.let { message ->
                        AlertDialog(onDismissRequest = { errorMessage = null },
                            title = { Text("No se pudo completar la operación") },
                            text = { Text(message) },
                            confirmButton = { TextButton(onClick = { errorMessage = null }) { Text("Aceptar") } })
                    }
                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.65f))
                                .clickable(onClick = {}),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanelsReaderView(
    comic: ComicBook,
    reader: UniversalComicReader,
    dark: Boolean,
    onDarkChange: (Boolean) -> Unit,
    onPageChanged: (Int) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("reader_settings", Context.MODE_PRIVATE) }
    var settings by remember {
        mutableStateOf(ReaderSettings(
            readingMode = ReadingMode.entries.firstOrNull { it.name == prefs.getString("mode", null) } ?: ReadingMode.RIGHT_TO_LEFT,
            pageFit = PageFit.entries.firstOrNull { it.name == prefs.getString("fit", null) } ?: PageFit.FIT_SCREEN,
            doublePageInLandscape = prefs.getBoolean("double", true),
            offsetDoublePage = prefs.getBoolean("offset", false),
            brightnessOverlay = prefs.getFloat("brightness", 0f).coerceIn(0f, 0.75f),
            warmFilter = prefs.getBoolean("warm", false)
        ))
    }
    LaunchedEffect(settings) {
        prefs.edit().putString("mode", settings.readingMode.name).putString("fit", settings.pageFit.name)
            .putBoolean("double", settings.doublePageInLandscape).putBoolean("offset", settings.offsetDoublePage)
            .remove("crop").putFloat("brightness", settings.brightnessOverlay)
            .putBoolean("warm", settings.warmFilter).apply()
    }
    var showHud by remember { mutableStateOf(false) }
    var showTools by remember { mutableStateOf(false) }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val useDoubleSpread = isLandscape && configuration.smallestScreenWidthDp >= 600 && configuration.screenWidthDp >= 840 && settings.doublePageInLandscape && settings.readingMode != ReadingMode.VERTICAL_CONTINUOUS

    var currentPage by rememberSaveable(comic.id) { mutableIntStateOf(comic.currentPage.coerceIn(0, comic.totalPages - 1)) }
    val spreads = remember(comic.totalPages, useDoubleSpread, settings.offsetDoublePage) {
        buildSpreads(comic.totalPages, useDoubleSpread, settings.offsetDoublePage)
    }
    val spreadIndex = spreads.indexOfLast { it.first <= currentPage }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = spreadIndex, pageCount = { spreads.size })
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentPage)
    val scope = rememberCoroutineScope()
    var navigating by remember { mutableStateOf(true) }
    LaunchedEffect(spreads, settings.readingMode) {
        navigating = true
        if (settings.readingMode == ReadingMode.VERTICAL_CONTINUOUS) listState.scrollToItem(currentPage)
        else pagerState.scrollToPage(spreads.indexOfLast { it.first <= currentPage }.coerceAtLeast(0))
        navigating = false
    }
    LaunchedEffect(pagerState, listState, spreads, settings.readingMode) {
        snapshotFlow {
            if (navigating) null
            else if (settings.readingMode == ReadingMode.VERTICAL_CONTINUOUS) listState.firstVisibleItemIndex
            else spreads.getOrNull(pagerState.settledPage)?.first
        }.collect { page ->
            if (page != null) {
                currentPage = page
                onPageChanged(page)
            }
        }
    }
    BackHandler { if (showTools) showTools = false else onClose() }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (settings.readingMode == ReadingMode.VERTICAL_CONTINUOUS) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { showHud = !showHud }
            ) {
                items(comic.totalPages) { index ->
                    SingleOrDoublePageCanvas(
                        primaryPageIndex = index,
                        secondaryPageIndex = null,
                        reader = reader,
                        settings = settings,
                        onCenterTap = { showHud = !showHud },
                        onLeftTap = {},
                        onRightTap = {}
                    )
                }
            }
        } else {
            val isRtl = settings.readingMode == ReadingMode.RIGHT_TO_LEFT
            val stepSize = 1

            HorizontalPager(
                state = pagerState,
                reverseLayout = isRtl,
                beyondViewportPageCount = 0,
                modifier = Modifier.fillMaxSize()
            ) { pageIndex ->
                val spread = spreads[pageIndex]
                val secondPage = spread.second

                SingleOrDoublePageCanvas(
                    primaryPageIndex = spread.first,
                    secondaryPageIndex = secondPage,
                    reader = reader,
                    settings = settings,
                    onCenterTap = { showHud = !showHud },
                    onLeftTap = {
                        scope.launch {
                            val target = if (isRtl) pagerState.currentPage + stepSize else pagerState.currentPage - stepSize
                            pagerState.animateScrollToPage(target.coerceIn(0, spreads.lastIndex))
                        }
                    },
                    onRightTap = {
                        scope.launch {
                            val target = if (isRtl) pagerState.currentPage - stepSize else pagerState.currentPage + stepSize
                            pagerState.animateScrollToPage(target.coerceIn(0, spreads.lastIndex))
                        }
                    }
                )
            }
        }

        if (settings.brightnessOverlay > 0f || settings.warmFilter) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (settings.warmFilter) Color(0xFFFFB74D).copy(alpha = 0.14f) else Color.Transparent)
                    .background(Color.Black.copy(alpha = settings.brightnessOverlay))
            )
        }

        AnimatedVisibility(
            visible = showHud,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.displayCutoutPadding().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cerrar", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Text(
                        text = comic.title,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { showTools = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Ajustes", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showHud,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                shape = RoundedCornerShape(24.dp),
                shadowElevation = 16.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(settings.readingMode.label, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Página ${currentPage + 1} / ${comic.totalPages}",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    val isRtl = settings.readingMode == ReadingMode.RIGHT_TO_LEFT
                    Slider(
                        value = currentPage.toFloat(),
                        enabled = comic.totalPages > 1,
                        onValueChange = { value ->
                            scope.launch {
                                val target = value.toInt().coerceIn(0, comic.totalPages - 1)
                                if (settings.readingMode == ReadingMode.VERTICAL_CONTINUOUS) listState.scrollToItem(target)
                                else pagerState.scrollToPage(spreads.indexOfLast { it.first <= target }.coerceAtLeast(0))
                            }
                        },
                        valueRange = 0f..(comic.totalPages - 1).coerceAtLeast(1).toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.onSurface,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.graphicsLayer { scaleX = if (isRtl) -1f else 1f }
                    )
                }
            }
        }

        if (showTools) {
            ModalBottomSheet(
                onDismissRequest = { showTools = false },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                ImmersiveSheetWindow(dark)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 10.dp)
                        .navigationBarsPadding()
                ) {
                    Text("Herramientas de Lectura", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Modo noche", modifier = Modifier.weight(1f))
                        Switch(checked = dark, onCheckedChange = onDarkChange)
                    }
                    Spacer(Modifier.height(14.dp))

                    if (configuration.smallestScreenWidthDp < 600) {
                        CompactReaderChoices(settings) { settings = it }
                    } else {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        ReadingMode.entries.forEachIndexed { idx, mode ->
                            SegmentedButton(
                                selected = settings.readingMode == mode,
                                onClick = { settings = settings.copy(readingMode = mode) },
                                shape = SegmentedButtonDefaults.itemShape(idx, ReadingMode.entries.size)
                            ) {
                                Text(mode.label, fontSize = 11.sp, maxLines = 1)
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        PageFit.entries.forEachIndexed { idx, fit ->
                            SegmentedButton(
                                selected = settings.pageFit == fit,
                                onClick = { settings = settings.copy(pageFit = fit) },
                                shape = SegmentedButtonDefaults.itemShape(idx, PageFit.entries.size)
                            ) {
                                Text(fit.label, fontSize = 11.sp)
                            }
                        }
                    }

                    }

                    Spacer(Modifier.height(14.dp))

                    if (settings.pageFit == PageFit.FILL_SCREEN) {
                        Text("Llenar puede ocultar los lados. Desliza la página para verlos.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp,
                            modifier = Modifier.padding(top = 6.dp))
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Doble página en horizontal (Tablets)", modifier = Modifier.weight(1f).padding(end = 12.dp))
                        Switch(checked = settings.doublePageInLandscape, onCheckedChange = { settings = settings.copy(doublePageInLandscape = it) })
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Desfasar portada en doble página", modifier = Modifier.weight(1f).padding(end = 12.dp))
                        Switch(checked = settings.offsetDoublePage, onCheckedChange = { settings = settings.copy(offsetDoublePage = it) })
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Filtro cálido de lectura", modifier = Modifier.weight(1f).padding(end = 12.dp))
                        Switch(checked = settings.warmFilter, onCheckedChange = { settings = settings.copy(warmFilter = it) })
                    }

                    Spacer(Modifier.height(8.dp))
                    Text("Oscurecimiento nocturno extra", color = Color.Gray, fontSize = 12.sp)
                    Slider(
                        value = settings.brightnessOverlay,
                        onValueChange = { settings = settings.copy(brightnessOverlay = it) },
                        valueRange = 0f..0.75f
                    )
                    Spacer(Modifier.height(18.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SingleOrDoublePageCanvas(
    primaryPageIndex: Int,
    secondaryPageIndex: Int?,
    reader: UniversalComicReader,
    settings: ReaderSettings,
    onCenterTap: () -> Unit,
    onLeftTap: () -> Unit,
    onRightTap: () -> Unit
) {
    var bmp1 by remember(reader, primaryPageIndex) { mutableStateOf<Bitmap?>(null) }
    var bmp2 by remember(reader, secondaryPageIndex) { mutableStateOf<Bitmap?>(null) }
    var scale by remember(primaryPageIndex) { mutableFloatStateOf(1f) }
    var offset by remember(primaryPageIndex) { mutableStateOf(Offset.Zero) }

    var pageError by remember(reader, primaryPageIndex, secondaryPageIndex) { mutableStateOf(false) }
    LaunchedEffect(reader, primaryPageIndex, secondaryPageIndex) {
        try {
        bmp1 = reader.loadPageBitmap(primaryPageIndex)
        bmp2 = if (secondaryPageIndex != null) reader.loadPageBitmap(secondaryPageIndex) else null
        pageError = bmp1 == null || (secondaryPageIndex != null && bmp2 == null)
        } catch (e: CancellationException) { throw e } catch (_: Exception) { pageError = true }
    }

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val vertical = settings.readingMode == ReadingMode.VERTICAL_CONTINUOUS
    val image = bmp1
    val viewportWidth = canvasSize.width.toFloat()
    val viewportHeight = canvasSize.height.toFloat()
    val imageRatio = if (image != null) image.width.toFloat() / image.height else 1f
    val fittedWidth = minOf(viewportWidth, viewportHeight * imageRatio)
    val fittedHeight = minOf(viewportHeight, viewportWidth / imageRatio)
    val fillScale = if (!vertical && secondaryPageIndex == null &&
        settings.pageFit == PageFit.FILL_SCREEN && fittedWidth > 0f && fittedHeight > 0f
    ) maxOf(viewportWidth / fittedWidth, viewportHeight / fittedHeight) else 1f
    LaunchedEffect(settings.pageFit) {
        scale = 1f
        offset = Offset.Zero
    }
    val transformState = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 4f)
        val effectiveScale = scale * fillScale
        val next = if (effectiveScale > 1f) offset + pan else Offset.Zero
        val maxX = ((fittedWidth * effectiveScale - viewportWidth) / 2f).coerceAtLeast(0f)
        val maxY = ((fittedHeight * effectiveScale - viewportHeight) / 2f).coerceAtLeast(0f)
        offset = Offset(next.x.coerceIn(-maxX, maxX), next.y.coerceIn(-maxY, maxY))
    }
    val canvasModifier = if (vertical) Modifier.fillMaxWidth().aspectRatio(
        bmp1?.let { it.width.toFloat() / it.height } ?: 0.7f
    ) else Modifier.fillMaxSize()
    Box(
        contentAlignment = Alignment.Center,
        modifier = canvasModifier
            .clipToBounds()
            .onSizeChanged { canvasSize = it }
            .pointerInput(primaryPageIndex, settings.readingMode, secondaryPageIndex) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.4f
                        }
                    },
                    onTap = { tap ->
                        val w = size.width
                        when {
                            vertical -> onCenterTap()
                            tap.x < w * 0.28f -> onLeftTap()
                            tap.x > w * 0.72f -> onRightTap()
                            else -> onCenterTap()
                        }
                    }
                )
            }
            .transformable(state = transformState, canPan = { scale * fillScale > 1f })
    ) {
        val first = bmp1
        val second = bmp2
        val contentScale = when (if (vertical) PageFit.FIT_WIDTH else settings.pageFit) {
            PageFit.FIT_SCREEN -> ContentScale.Fit
            PageFit.FIT_WIDTH -> ContentScale.FillWidth
            PageFit.FIT_HEIGHT -> ContentScale.FillHeight
            PageFit.FILL_SCREEN -> ContentScale.Fit
        }

        if (pageError) {
            Text("No se pudo cargar la página. Vuelve a abrir el título.", color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(24.dp))
        } else if (first != null) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale * fillScale,
                        scaleY = scale * fillScale,
                        translationX = offset.x,
                        translationY = offset.y
                    ),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (second != null) {
                    val isRtl = settings.readingMode == ReadingMode.RIGHT_TO_LEFT
                    val leftBmp = if (isRtl) second else first
                    val rightBmp = if (isRtl) first else second
                    Image(
                        bitmap = leftBmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    Image(
                        bitmap = rightBmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                } else {
                    Image(
                        bitmap = first.asImageBitmap(),
                        contentDescription = null,
                        contentScale = contentScale,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        } else {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    }
}
