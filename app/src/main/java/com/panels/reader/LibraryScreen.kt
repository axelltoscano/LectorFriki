package com.panels.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanelsLibraryView(
    comics: List<ComicBook>,
    collections: List<BookCollection>,
    onCollectionsChange: (List<BookCollection>) -> Unit,
    dark: Boolean,
    onDarkChange: (Boolean) -> Unit,
    onOpenComic: (ComicBook) -> Unit,
    onDeleteComic: (ComicBook) -> Unit,
    onImportClick: () -> Unit
) {
    var selectedId by rememberSaveable { mutableStateOf("") }
    var nameDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<BookCollection?>(null) }
    var deleting by remember { mutableStateOf<BookCollection?>(null) }
    var organizing by remember { mutableStateOf<ComicBook?>(null) }
    var membership by remember { mutableStateOf(setOf<String>()) }
    var menuOpen by remember { mutableStateOf(false) }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val selected = collections.firstOrNull { it.id == selectedId }
    val filedIds = collections.flatMap { it.bookIds }.toSet()
    val filtered = when {
        selected != null -> comics.filter { it.id in selected.bookIds }
        selectedId == "@unfiled" -> comics.filter { it.id !in filedIds }
        else -> comics
    }
    val title = selected?.name ?: if (selectedId == "@unfiled") "Sin colección" else "Biblioteca"
    fun organize(book: ComicBook) {
        organizing = book
        membership = collections.filter { book.id in it.bookIds }.map { it.id }.toSet()
    }
    fun createCollection() {
        editing = null
        nameDialog = true
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 900.dp
            BackHandler(!wide && drawer.isOpen) { scope.launch { drawer.close() } }
            val sidebar: @Composable () -> Unit = {
                Column(Modifier.fillMaxHeight().padding(horizontal = 16.dp)) {
                    Text("LectorFriki", fontSize = 26.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 24.dp, bottom = 4.dp))
                    Text("Tu espacio de lectura", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 24.dp))
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        item {
                            NavigationDrawerItem(label = { Text("Todos los títulos") },
                                badge = { Text(comics.size.toString()) }, selected = selectedId == "",
                                onClick = { selectedId = ""; scope.launch { drawer.close() } })
                        }
                        item {
                            NavigationDrawerItem(label = { Text("Sin colección") },
                                badge = { Text(comics.count { it.id !in filedIds }.toString()) },
                                selected = selectedId == "@unfiled",
                                onClick = { selectedId = "@unfiled"; scope.launch { drawer.close() } })
                        }
                        item {
                            Text("COLECCIONES", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp))
                        }
                        items(collections, key = { it.id }) { collection ->
                            NavigationDrawerItem(
                                label = { Text(collection.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                                badge = { Text(comics.count { it.id in collection.bookIds }.toString()) },
                                selected = selectedId == collection.id,
                                onClick = { selectedId = collection.id; scope.launch { drawer.close() } })
                        }
                        item {
                            TextButton(onClick = { createCollection() }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Add, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Nueva colección")
                            }
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    Row(Modifier.fillMaxWidth().padding(bottom = 16.dp)
                        .toggleable(value = dark, role = Role.Switch, onValueChange = onDarkChange),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("Modo noche", modifier = Modifier.weight(1f))
                        Switch(checked = dark, onCheckedChange = null)
                    }
                }
            }
            val library: @Composable () -> Unit = {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    topBar = {
                        TopAppBar(
                            title = {
                                Column {
                                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        fontWeight = FontWeight.Bold)
                                    Text(if (filtered.size == 1) "1 título" else "${filtered.size} títulos", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            navigationIcon = {
                                if (!wide) IconButton(onClick = { scope.launch { drawer.open() } }) {
                                    Icon(Icons.Default.Menu, "Abrir colecciones")
                                }
                            },
                            actions = {
                                if (selected != null) {
                                    Box {
                                        IconButton(onClick = { menuOpen = true }) {
                                            Icon(Icons.Default.MoreVert, "Opciones de colección")
                                        }
                                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                            DropdownMenuItem(text = { Text("Renombrar colección") }, onClick = {
                                                menuOpen = false; editing = selected; nameDialog = true
                                            })
                                            DropdownMenuItem(text = { Text("Eliminar colección") }, onClick = {
                                                menuOpen = false; deleting = selected
                                            })
                                        }
                                    }
                                }
                                FilledTonalButton(onClick = onImportClick, modifier = Modifier.padding(end = 12.dp)) {
                                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Importar")
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background,
                                scrolledContainerColor = MaterialTheme.colorScheme.background))
                    }
                ) { padding ->
                    if (filtered.isEmpty()) {
                        Column(Modifier.fillMaxSize().padding(padding).padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center) {
                            Text(if (comics.isEmpty()) "Tu próxima historia empieza aquí" else "Todavía no hay títulos aquí",
                                style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.height(12.dp))
                            Text(if (comics.isEmpty()) "Importa un CBZ, CBR o PDF para empezar."
                                else "En Biblioteca, abre el menú ⋮ de un título para organizarlo.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(20.dp))
                            if (comics.isEmpty()) Button(onClick = onImportClick) { Text("Importar archivos") }
                            else TextButton(onClick = { selectedId = "" }) { Text("Ver biblioteca") }
                        }
                    } else {
                        key(selectedId) {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(150.dp),
                                modifier = Modifier.fillMaxSize().padding(padding),
                                contentPadding = PaddingValues(20.dp),
                                horizontalArrangement = Arrangement.spacedBy(20.dp),
                                verticalArrangement = Arrangement.spacedBy(24.dp)) {
                                items(filtered, key = { it.id }) { book ->
                                    LibraryBookCard(book, onOpen = { onOpenComic(book) },
                                        onOrganize = { organize(book) })
                                }
                            }
                        }
                    }
                }
            }
            if (wide) {
                PermanentNavigationDrawer(drawerContent = {
                    PermanentDrawerSheet(modifier = Modifier.width(260.dp), drawerTonalElevation = 0.dp,
                        drawerContainerColor = MaterialTheme.colorScheme.surface) { sidebar() }
                }) { library() }
            } else {
                ModalNavigationDrawer(drawerState = drawer, drawerContent = {
                    ModalDrawerSheet(modifier = Modifier.width(290.dp), drawerTonalElevation = 0.dp,
                        drawerContainerColor = MaterialTheme.colorScheme.surface) { sidebar() }
                }) { library() }
            }
        }
    }

    organizing?.let { book ->
        AlertDialog(onDismissRequest = { organizing = null },
            title = { Text("Organizar título") },
            text = {
                Column {
                    Text(book.title, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    if (collections.isEmpty()) Text("Crea tu primera colección para guardar este título.")
                    LazyColumn(Modifier.heightIn(max = 300.dp)) {
                        items(collections, key = { it.id }) { collection ->
                            Row(Modifier.fillMaxWidth().clickable {
                                membership = if (collection.id in membership) membership - collection.id else membership + collection.id
                            }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = collection.id in membership, onCheckedChange = { checked ->
                                    membership = if (checked) membership + collection.id else membership - collection.id
                                })
                                Text(collection.name, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    TextButton(onClick = { createCollection() }) { Text("+ Nueva colección") }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    TextButton(onClick = { organizing = null; onDeleteComic(book) }) {
                        Text("Quitar de la biblioteca", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = { TextButton(onClick = {
                onCollectionsChange(CollectionRules.assign(collections, book.id, membership))
                organizing = null
            }) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { organizing = null }) { Text("Cancelar") } })
    }
    if (nameDialog) {
        CollectionNameDialog(editing, collections, onDismiss = { nameDialog = false }) { name ->
            val existing = editing
            if (existing == null) {
                val collection = BookCollection(UUID.randomUUID().toString(), name)
                onCollectionsChange(collections + collection)
                if (organizing != null) membership = membership + collection.id
                else selectedId = collection.id
            } else {
                onCollectionsChange(collections.map { if (it.id == existing.id) it.copy(name = name) else it })
            }
            nameDialog = false
        }
    }
    deleting?.let { collection ->
        AlertDialog(onDismissRequest = { deleting = null },
            title = { Text("¿Eliminar «${collection.name}»?") },
            text = { Text("Los títulos seguirán en tu biblioteca. Solo se elimina esta colección.") },
            confirmButton = { TextButton(onClick = {
                onCollectionsChange(collections.filterNot { it.id == collection.id })
                if (selectedId == collection.id) selectedId = ""
                deleting = null
            }) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } })
    }
}

@Composable
private fun CollectionNameDialog(
    editing: BookCollection?, collections: List<BookCollection>,
    onDismiss: () -> Unit, onSave: (String) -> Unit
) {
    var name by rememberSaveable(editing?.id) { mutableStateOf(editing?.name ?: "") }
    val valid = CollectionRules.validName(name, collections, editing?.id)
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(if (editing == null) "Nueva colección" else "Renombrar colección") },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it.take(60) },
                label = { Text("Nombre") }, singleLine = true,
                isError = name.isNotBlank() && !valid,
                supportingText = { Text(if (name.isNotBlank() && !valid) "Ya existe una colección con ese nombre." else "Por ejemplo: Manga, Pendientes, Favoritos") })
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onSave(name.trim()) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryBookCard(book: ComicBook, onOpen: () -> Unit, onOrganize: () -> Unit) {
    var cover by remember(book.coverPath) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(book.coverPath) {
        cover = withContext(Dispatchers.IO) { book.coverPath?.let { BitmapFactory.decodeFile(it) } }
    }
    Column(Modifier.fillMaxWidth()) {
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth().aspectRatio(0.68f)
                .combinedClickable(onClick = onOpen, onLongClick = onOrganize)) {
            Box {
                val bitmap = cover
                if (bitmap != null) Image(bitmap.asImageBitmap(), book.title,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Text("LF", fontWeight = FontWeight.Bold, fontSize = 32.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.Center))
                Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                    IconButton(onClick = onOrganize) {
                        Icon(Icons.Default.MoreVert, "Organizar " + book.title)
                    }
                }
                LinearProgressIndicator(progress = { book.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(4.dp).align(Alignment.BottomCenter))
            }
        }
        Text(book.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp))
        Text("Página ${book.currentPage + 1} de ${book.totalPages}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
