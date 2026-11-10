package com.example.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesomeMotion
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.PeopleOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.editor.EditorLanguage
import com.example.core.export.ExportConfig
import com.example.core.export.ExportEngine
import com.example.core.export.ExportFormat
import com.example.data.CharacterEntity
import com.example.data.ProjectEntity
import com.example.data.StudioSettings
import com.example.ui.theme.CharacterPresetColors
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuberStudioApp(viewModel: GuberStudioViewModel) {
    val destination by viewModel.currentDestination.collectAsStateWithLifecycle()
    val workspaceMode by viewModel.workspaceViewMode.collectAsStateWithLifecycle()
    val activeProject by viewModel.activeProject.collectAsStateWithLifecycle()
    val allProjects by viewModel.allProjects.collectAsStateWithLifecycle()
    val characters by viewModel.projectCharacters.collectAsStateWithLifecycle()
    val settings by viewModel.studioSettings.collectAsStateWithLifecycle()
    val flowGraph by viewModel.flowGraph.collectAsStateWithLifecycle()
    val dialogueGraph by viewModel.dialogueGraph.collectAsStateWithLifecycle()
    val sceneGraph by viewModel.sceneGraph.collectAsStateWithLifecycle()
    val layoutOrientation by viewModel.layoutOrientation.collectAsStateWithLifecycle()
    val statusToast by viewModel.statusToast.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showNewProjectDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var pendingExportConfig by remember { mutableStateOf(ExportConfig()) }

    // SAF File Export Launcher
    val exportFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        if (uri != null && activeProject != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    ExportEngine.writeExportToStream(
                        outputStream = out,
                        project = activeProject!!,
                        characters = characters,
                        flowGraph = flowGraph,
                        dialogueGraph = dialogueGraph,
                        sceneGraph = sceneGraph,
                        config = pendingExportConfig
                    )
                }
                viewModel.showToast("Exported ${pendingExportConfig.format.label} successfully")
            } catch (e: Exception) {
                viewModel.showToast("Export failed: ${e.localizedMessage}")
            }
        }
    }

    // SAF File Import Launcher
    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val jsonText = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (!jsonText.isNullOrBlank()) {
                    viewModel.importProjectFromJson(jsonText)
                }
            } catch (e: Exception) {
                viewModel.showToast("Failed to read file: ${e.localizedMessage}")
            }
        }
    }

    LaunchedEffect(statusToast) {
        statusToast?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearToast()
        }
    }

    // BackHandler for non-Home screens
    BackHandler(enabled = destination != StudioDestination.HOME) {
        viewModel.navigateTo(StudioDestination.HOME)
    }

    val activeController = viewModel.activeEditorController()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        val useNavigationRail = maxWidth >= 720.dp

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                GuberTopStudioBar(
                    projectName = activeProject?.name ?: "Guber Studio",
                    destination = destination,
                    canUndo = activeController.canUndo,
                    canRedo = activeController.canRedo,
                    onNewProject = { showNewProjectDialog = true },
                    onOpenProjects = { viewModel.navigateTo(StudioDestination.PROJECTS) },
                    onSave = {
                        viewModel.saveActiveProjectNow()
                        viewModel.showToast("Saved '${activeProject?.name ?: "Project"}'")
                    },
                    onUndo = { activeController.undo() },
                    onRedo = { activeController.redo() },
                    onGenerate = { viewModel.manualGenerateCurrentModule() },
                    onExport = { showExportDialog = true },
                    onSettings = { viewModel.navigateTo(StudioDestination.SETTINGS) }
                )
            },
            bottomBar = {
                if (!useNavigationRail) {
                    GuberBottomNavigationBar(
                        current = destination,
                        onSelect = { viewModel.navigateTo(it) }
                    )
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail) {
                    GuberSideNavigationRail(
                        current = destination,
                        onSelect = { viewModel.navigateTo(it) }
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    when (destination) {
                        StudioDestination.HOME -> {
                            HomeStudioDashboard(
                                activeProject = activeProject,
                                recentProjects = allProjects,
                                flowNodeCount = flowGraph.nodes.size,
                                dialogueNodeCount = dialogueGraph.nodes.size,
                                sceneCount = sceneGraph.scenes.size,
                                characterCount = characters.size,
                                onNavigate = { viewModel.navigateTo(it) },
                                onSelectProject = { proj ->
                                    viewModel.selectProject(proj)
                                    viewModel.navigateTo(StudioDestination.FLOWCHART)
                                },
                                onCreateNewProject = { showNewProjectDialog = true }
                            )
                        }

                        StudioDestination.FLOWCHART -> {
                            GuberSplitWorkspace(
                                language = EditorLanguage.FLOWCHART,
                                controller = viewModel.flowchartEditorController,
                                flowGraph = flowGraph,
                                dialogueGraph = dialogueGraph,
                                sceneGraph = sceneGraph,
                                characters = characters,
                                characterProfiles = viewModel.characterProfilesForCanvas(),
                                layoutOrientation = layoutOrientation,
                                workspaceViewMode = workspaceMode,
                                initialSplitFraction = settings.splitFraction,
                                onSplitFractionChanged = { viewModel.updateSplitFraction(it) },
                                onWorkspaceViewModeChanged = { viewModel.setWorkspaceViewMode(it) },
                                onToggleOrientation = { viewModel.toggleFlowLayoutOrientation() },
                                onNodeDragged = { id, x, y ->
                                    viewModel.updateNodeDragOffset(EditorLanguage.FLOWCHART, id, x, y)
                                },
                                onResetLayout = { viewModel.resetVisualLayout(EditorLanguage.FLOWCHART) },
                                onMoveSceneOrder = { _, _ -> },
                                onManualGenerate = { viewModel.manualGenerateCurrentModule() },
                                onOpenCharacterSheet = { viewModel.navigateTo(StudioDestination.CHARACTERS) }
                            )
                        }

                        StudioDestination.DIALOGUE -> {
                            GuberSplitWorkspace(
                                language = EditorLanguage.DIALOGUE,
                                controller = viewModel.dialogueEditorController,
                                flowGraph = flowGraph,
                                dialogueGraph = dialogueGraph,
                                sceneGraph = sceneGraph,
                                characters = characters,
                                characterProfiles = viewModel.characterProfilesForCanvas(),
                                layoutOrientation = layoutOrientation,
                                workspaceViewMode = workspaceMode,
                                initialSplitFraction = settings.splitFraction,
                                onSplitFractionChanged = { viewModel.updateSplitFraction(it) },
                                onWorkspaceViewModeChanged = { viewModel.setWorkspaceViewMode(it) },
                                onToggleOrientation = { viewModel.toggleFlowLayoutOrientation() },
                                onNodeDragged = { id, x, y ->
                                    viewModel.updateNodeDragOffset(EditorLanguage.DIALOGUE, id, x, y)
                                },
                                onResetLayout = { viewModel.resetVisualLayout(EditorLanguage.DIALOGUE) },
                                onMoveSceneOrder = { _, _ -> },
                                onManualGenerate = { viewModel.manualGenerateCurrentModule() },
                                onOpenCharacterSheet = { viewModel.navigateTo(StudioDestination.CHARACTERS) }
                            )
                        }

                        StudioDestination.SCENE -> {
                            GuberSplitWorkspace(
                                language = EditorLanguage.SCENE,
                                controller = viewModel.sceneEditorController,
                                flowGraph = flowGraph,
                                dialogueGraph = dialogueGraph,
                                sceneGraph = sceneGraph,
                                characters = characters,
                                characterProfiles = viewModel.characterProfilesForCanvas(),
                                layoutOrientation = layoutOrientation,
                                workspaceViewMode = workspaceMode,
                                initialSplitFraction = settings.splitFraction,
                                onSplitFractionChanged = { viewModel.updateSplitFraction(it) },
                                onWorkspaceViewModeChanged = { viewModel.setWorkspaceViewMode(it) },
                                onToggleOrientation = { viewModel.toggleFlowLayoutOrientation() },
                                onNodeDragged = { id, x, y ->
                                    viewModel.updateNodeDragOffset(EditorLanguage.SCENE, id, x, y)
                                },
                                onResetLayout = { viewModel.resetVisualLayout(EditorLanguage.SCENE) },
                                onMoveSceneOrder = { from, to ->
                                    viewModel.reorderScenesInScript(from, to)
                                },
                                onManualGenerate = { viewModel.manualGenerateCurrentModule() },
                                onOpenCharacterSheet = { viewModel.navigateTo(StudioDestination.CHARACTERS) }
                            )
                        }

                        StudioDestination.CHARACTERS -> {
                            CharacterStudioScreen(
                                characters = characters,
                                onAddCharacter = { name, role, desc, color ->
                                    viewModel.addCharacter(name, role, desc, color)
                                },
                                onDeleteCharacter = { viewModel.deleteCharacter(it) },
                                onInsertDialogueForCharacter = { name ->
                                    viewModel.insertCharacterIntoDialogue(name)
                                }
                            )
                        }

                        StudioDestination.PROJECTS -> {
                            ProjectsManagerScreen(
                                projects = allProjects,
                                activeProjectId = activeProject?.id ?: -1L,
                                onSelectProject = { proj ->
                                    viewModel.selectProject(proj)
                                    viewModel.navigateTo(StudioDestination.FLOWCHART)
                                },
                                onNewProject = { showNewProjectDialog = true },
                                onDuplicateProject = { viewModel.duplicateProject(it) },
                                onRenameProject = { proj, newName, newDesc ->
                                    viewModel.renameProject(proj, newName, newDesc)
                                },
                                onDeleteProject = { viewModel.deleteProject(it) },
                                onImportJsonFile = {
                                    importFileLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                                },
                                onImportRawJson = { viewModel.importProjectFromJson(it) },
                                onExportProject = { showExportDialog = true }
                            )
                        }

                        StudioDestination.SETTINGS -> {
                            StudioSettingsScreen(
                                settings = settings,
                                onToggleDarkTheme = { viewModel.updateDarkTheme(it) },
                                onFontSizeChanged = { viewModel.updateFontSize(it) },
                                onToggleWordWrap = { viewModel.updateWordWrap(it) },
                                onToggleAutoParse = { viewModel.updateAutoParse(it) },
                                onToggleAutoSave = { viewModel.updateAutoSave(it) },
                                onResetSplitRatio = { viewModel.updateSplitFraction(0.48f) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showNewProjectDialog) {
        NewProjectDialog(
            onDismiss = { showNewProjectDialog = false },
            onCreate = { name, desc ->
                showNewProjectDialog = false
                viewModel.createNewProject(name, desc)
            }
        )
    }

    if (showExportDialog) {
        ExportStudioDialog(
            projectName = activeProject?.name ?: "guber_project",
            onGeneratePreview = { cfg -> viewModel.generateExportPreview(cfg) },
            onSaveToFile = { cfg ->
                pendingExportConfig = cfg
                val safeName = (activeProject?.name ?: "guber_project")
                    .lowercase()
                    .replace(Regex("[^a-z0-9]+"), "_")
                    .trim('_')
                exportFileLauncher.launch("${safeName}.${cfg.format.extension}")
                showExportDialog = false
            },
            onDismiss = { showExportDialog = false }
        )
    }
}

@Composable
private fun GuberTopStudioBar(
    projectName: String,
    destination: StudioDestination,
    canUndo: Boolean,
    canRedo: Boolean,
    onNewProject: () -> Unit,
    onOpenProjects: () -> Unit,
    onSave: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onGenerate: () -> Unit,
    onExport: () -> Unit,
    onSettings: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("guber_top_toolbar")
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "GUBER",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column {
                        Text(
                            text = projectName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = destination.title,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(onClick = onNewProject, modifier = Modifier.testTag("toolbar_new_button")) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = "New Project")
                    }
                    IconButton(onClick = onOpenProjects, modifier = Modifier.testTag("toolbar_open_button")) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "Open Project")
                    }
                    IconButton(onClick = onSave, modifier = Modifier.testTag("toolbar_save_button")) {
                        Icon(Icons.Default.Save, contentDescription = "Save Project")
                    }
                    IconButton(
                        onClick = onUndo,
                        enabled = canUndo,
                        modifier = Modifier.testTag("toolbar_undo_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                    }
                    IconButton(
                        onClick = onRedo,
                        enabled = canRedo,
                        modifier = Modifier.testTag("toolbar_redo_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                    }
                    IconButton(onClick = onGenerate, modifier = Modifier.testTag("toolbar_generate_button")) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Generate Visuals",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onExport, modifier = Modifier.testTag("toolbar_export_button")) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Export Project")
                    }
                    IconButton(onClick = onSettings, modifier = Modifier.testTag("toolbar_settings_button")) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        }
    }
}

private data class NavItemSpec(
    val destination: StudioDestination,
    val label: String,
    val icon: ImageVector,
    val tag: String
)

private val studioNavItems = listOf(
    NavItemSpec(StudioDestination.HOME, "Home", Icons.Default.Home, "nav_home_tab"),
    NavItemSpec(StudioDestination.FLOWCHART, "Flowchart", Icons.Default.AccountTree, "nav_flowchart_tab"),
    NavItemSpec(StudioDestination.DIALOGUE, "Dialogue", Icons.Default.ChatBubbleOutline, "nav_dialogue_tab"),
    NavItemSpec(StudioDestination.SCENE, "Scene", Icons.Default.MovieCreation, "nav_scene_tab"),
    NavItemSpec(StudioDestination.CHARACTERS, "Cast", Icons.Default.PeopleOutline, "nav_characters_tab"),
    NavItemSpec(StudioDestination.PROJECTS, "Projects", Icons.Default.FolderOpen, "nav_projects_tab")
)

@Composable
private fun GuberBottomNavigationBar(
    current: StudioDestination,
    onSelect: (StudioDestination) -> Unit
) {
    NavigationBar(
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .testTag("guber_bottom_navigation")
    ) {
        studioNavItems.forEach { item ->
            NavigationBarItem(
                selected = current == item.destination,
                onClick = { onSelect(item.destination) },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label, maxLines = 1) },
                modifier = Modifier.testTag(item.tag)
            )
        }
    }
}

@Composable
private fun GuberSideNavigationRail(
    current: StudioDestination,
    onSelect: (StudioDestination) -> Unit
) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxHeight()
            .testTag("guber_navigation_rail")
    ) {
        studioNavItems.forEach { item ->
            NavigationRailItem(
                selected = current == item.destination,
                onClick = { onSelect(item.destination) },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
                modifier = Modifier.testTag(item.tag)
            )
        }
    }
}

@Composable
private fun HomeStudioDashboard(
    activeProject: ProjectEntity?,
    recentProjects: List<ProjectEntity>,
    flowNodeCount: Int,
    dialogueNodeCount: Int,
    sceneCount: Int,
    characterCount: Int,
    onNavigate: (StudioDestination) -> Unit,
    onSelectProject: (ProjectEntity) -> Unit,
    onCreateNewProject: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp)
            .testTag("home_studio_dashboard"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "GUBER CREATIVE WRITING & LOGIC STUDIO",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = activeProject?.name ?: "Welcome to Guber",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = activeProject?.description
                            ?: "Synchronized split-screen workspace with DSL code editor on the right and live graph visualization on the left.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { onNavigate(StudioDestination.FLOWCHART) },
                            modifier = Modifier.testTag("home_open_workspace_button")
                        ) {
                            Icon(Icons.Default.AutoAwesomeMotion, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open Studio Workspace")
                        }
                        OutlinedButton(onClick = onCreateNewProject) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("New Project")
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Creative Studio Modules",
                style = MaterialTheme.typography.titleLarge
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ModuleQuickCard(
                    title = "Flowchart Editor",
                    subtitle = "$flowNodeCount nodes • Decision & loop logic",
                    accent = Color(0xFF38BDF8),
                    icon = Icons.Default.AccountTree,
                    onClick = { onNavigate(StudioDestination.FLOWCHART) },
                    modifier = Modifier.weight(1f).testTag("card_flowchart_module")
                )
                ModuleQuickCard(
                    title = "Dialogue Writer",
                    subtitle = "$dialogueNodeCount blocks • Choices & branches",
                    accent = Color(0xFFA78BFA),
                    icon = Icons.Default.ChatBubbleOutline,
                    onClick = { onNavigate(StudioDestination.DIALOGUE) },
                    modifier = Modifier.weight(1f).testTag("card_dialogue_module")
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ModuleQuickCard(
                    title = "Scene Writer",
                    subtitle = "$sceneCount scenes • Camera, audio & timeline",
                    accent = Color(0xFFF59E0B),
                    icon = Icons.Default.MovieCreation,
                    onClick = { onNavigate(StudioDestination.SCENE) },
                    modifier = Modifier.weight(1f).testTag("card_scene_module")
                )
                ModuleQuickCard(
                    title = "Character Cast",
                    subtitle = "$characterCount profiles • Reusable across scenes",
                    accent = Color(0xFF10B981),
                    icon = Icons.Default.PeopleOutline,
                    onClick = { onNavigate(StudioDestination.CHARACTERS) },
                    modifier = Modifier.weight(1f).testTag("card_characters_module")
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Projects (${recentProjects.size})",
                    style = MaterialTheme.typography.titleLarge
                )
                TextButton(onClick = { onNavigate(StudioDestination.PROJECTS) }) {
                    Text("Manage All")
                }
            }
        }

        items(recentProjects, key = { it.id }) { proj ->
            val dateFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = if (proj.id == activeProject?.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { onSelectProject(proj) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = proj.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = proj.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = dateFormat.format(Date(proj.updatedAt)),
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = JetBrainsMonoFontFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ModuleQuickCard(
    title: String,
    subtitle: String,
    accent: Color,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = title, tint = accent, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CharacterStudioScreen(
    characters: List<CharacterEntity>,
    onAddCharacter: (String, String, String, Long) -> Unit,
    onDeleteCharacter: (Long) -> Unit,
    onInsertDialogueForCharacter: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }
    var newRole by remember { mutableStateOf("") }
    var newDescription by remember { mutableStateOf("") }
    var selectedColor by remember { mutableLongStateOf(CharacterPresetColors.first()) }

    val filtered = remember(characters, searchQuery) {
        if (searchQuery.isBlank()) characters
        else characters.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                it.role.contains(searchQuery, ignoreCase = true) ||
                it.description.contains(searchQuery, ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("character_studio_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Create Character Profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            label = { Text("Character Name") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("character_name_input")
                        )
                        OutlinedTextField(
                            value = newRole,
                            onValueChange = { newRole = it },
                            label = { Text("Role / Archetype") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("character_role_input")
                        )
                    }
                    OutlinedTextField(
                        value = newDescription,
                        onValueChange = { newDescription = it },
                        label = { Text("Profile & Voice Notes") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CharacterPresetColors.forEach { colorHex ->
                                val isChosen = selectedColor == colorHex
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(Color(colorHex))
                                        .border(
                                            width = if (isChosen) 3.dp else 1.dp,
                                            color = if (isChosen) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable { selectedColor = colorHex }
                                )
                            }
                        }
                        Button(
                            onClick = {
                                if (newName.isNotBlank()) {
                                    onAddCharacter(newName, newRole, newDescription, selectedColor)
                                    newName = ""
                                    newRole = ""
                                    newDescription = ""
                                }
                            },
                            enabled = newName.isNotBlank(),
                            modifier = Modifier.testTag("add_character_submit_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Character")
                        }
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text("Search characters by name, role, or notes...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        items(filtered, key = { it.id }) { char ->
            val accent = Color(char.colorHex)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(accent.copy(alpha = 0.22f))
                                .border(2.dp, accent, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = char.avatarInitials,
                                style = MaterialTheme.typography.titleMedium,
                                color = accent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column {
                            Text(text = char.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(text = char.role, style = MaterialTheme.typography.labelMedium, color = accent)
                            if (char.description.isNotBlank()) {
                                Text(
                                    text = char.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilledTonalButton(
                            onClick = { onInsertDialogueForCharacter(char.name) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Write Dialogue")
                        }
                        IconButton(onClick = { onDeleteCharacter(char.id) }) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "Delete Character",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectsManagerScreen(
    projects: List<ProjectEntity>,
    activeProjectId: Long,
    onSelectProject: (ProjectEntity) -> Unit,
    onNewProject: () -> Unit,
    onDuplicateProject: (ProjectEntity) -> Unit,
    onRenameProject: (ProjectEntity, String, String) -> Unit,
    onDeleteProject: (ProjectEntity) -> Unit,
    onImportJsonFile: () -> Unit,
    onImportRawJson: (String) -> Unit,
    onExportProject: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var editingProject by remember { mutableStateOf<ProjectEntity?>(null) }
    var showPasteJsonDialog by remember { mutableStateOf(false) }

    val filtered = remember(projects, searchQuery) {
        if (searchQuery.isBlank()) projects
        else projects.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                it.description.contains(searchQuery, ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("projects_manager_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Offline Project Manager", style = MaterialTheme.typography.headlineMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onImportJsonFile) {
                        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Import File")
                    }
                    OutlinedButton(onClick = { showPasteJsonDialog = true }) {
                        Text("Paste JSON")
                    }
                    Button(onClick = onNewProject, modifier = Modifier.testTag("projects_new_button")) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New")
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text("Search projects...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("projects_search_input")
            )
        }

        items(filtered, key = { it.id }) { proj ->
            val isActive = proj.id == activeProjectId
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = if (isActive) 2.dp else 1.dp,
                        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(14.dp)
                    )
                    .clickable { onSelectProject(proj) }
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(proj.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                if (isActive) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer)
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "ACTIVE",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = proj.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row {
                            IconButton(onClick = { editingProject = proj }) {
                                Icon(Icons.Default.Edit, contentDescription = "Rename Project")
                            }
                            IconButton(onClick = { onDuplicateProject(proj) }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate Project")
                            }
                            IconButton(onClick = onExportProject) {
                                Icon(Icons.Default.FileDownload, contentDescription = "Export Project")
                            }
                            if (projects.size > 1) {
                                IconButton(onClick = { onDeleteProject(proj) }) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = "Delete Project",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editingProject?.let { target ->
        var editedName by remember(target) { mutableStateOf(target.name) }
        var editedDesc by remember(target) { mutableStateOf(target.description) }
        AlertDialog(
            onDismissRequest = { editingProject = null },
            title = { Text("Rename Project") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editedName,
                        onValueChange = { editedName = it },
                        label = { Text("Project Name") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editedDesc,
                        onValueChange = { editedDesc = it },
                        label = { Text("Description") }
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onRenameProject(target, editedName, editedDesc)
                    editingProject = null
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingProject = null }) { Text("Cancel") }
            }
        )
    }

    if (showPasteJsonDialog) {
        var rawJson by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPasteJsonDialog = false },
            title = { Text("Import Project JSON") },
            text = {
                OutlinedTextField(
                    value = rawJson,
                    onValueChange = { rawJson = it },
                    placeholder = { Text("Paste exported Guber JSON here...") },
                    modifier = Modifier.fillMaxWidth().height(180.dp)
                )
            },
            confirmButton = {
                Button(onClick = {
                    showPasteJsonDialog = false
                    onImportRawJson(rawJson)
                }) {
                    Text("Import")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasteJsonDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun StudioSettingsScreen(
    settings: StudioSettings,
    onToggleDarkTheme: (Boolean) -> Unit,
    onFontSizeChanged: (Float) -> Unit,
    onToggleWordWrap: (Boolean) -> Unit,
    onToggleAutoParse: (Boolean) -> Unit,
    onToggleAutoSave: (Boolean) -> Unit,
    onResetSplitRatio: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
            .testTag("studio_settings_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Studio & Editor Settings", style = MaterialTheme.typography.headlineMedium)

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                SettingToggleRow(
                    title = "Dark Studio Theme",
                    subtitle = "Use high-contrast Obsidian Slate editor and canvas theme",
                    checked = settings.darkTheme,
                    onCheckedChange = onToggleDarkTheme
                )
                HorizontalDivider()
                SettingToggleRow(
                    title = "Word Wrapping in Editor",
                    subtitle = "Wrap long lines inside the BasicTextField code editor",
                    checked = settings.wordWrap,
                    onCheckedChange = onToggleWordWrap
                )
                HorizontalDivider()
                SettingToggleRow(
                    title = "Automatic Live Graph Parsing",
                    subtitle = "Update flowchart, dialogue, and scene visuals automatically as you type",
                    checked = settings.autoParse,
                    onCheckedChange = onToggleAutoParse
                )
                HorizontalDivider()
                SettingToggleRow(
                    title = "Offline Autosave & Crash Recovery",
                    subtitle = "Automatically persist changes to local Room database",
                    checked = settings.autoSave,
                    onCheckedChange = onToggleAutoSave
                )
                HorizontalDivider()
                Column {
                    Text(
                        text = "Default Editor Font Size: ${settings.editorFontSizeSp.toInt()} sp",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Slider(
                        value = settings.editorFontSizeSp,
                        onValueChange = onFontSizeChanged,
                        valueRange = 11f..24f,
                        steps = 12
                    )
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Workspace Split Arrangement", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Current split ratio: ${(settings.splitFraction * 100).toInt()}% Visual / ${((1f - settings.splitFraction) * 100).toInt()}% Editor",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(onClick = onResetSplitRatio) {
                        Text("Reset 50/50")
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun NewProjectDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Studio Project") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Project Title") },
                    placeholder = { Text("e.g., Sci-Fi Visual Novel") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("new_project_name_input")
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Project Summary") },
                    placeholder = { Text("Flowcharts, branching dialogue, and scenes") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name, description) },
                modifier = Modifier.testTag("create_project_confirm_button")
            ) {
                Text("Create Project")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ExportStudioDialog(
    projectName: String,
    onGeneratePreview: (ExportConfig) -> String,
    onSaveToFile: (ExportConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedFormat by remember { mutableStateOf(ExportFormat.JSON) }
    var transparentBg by remember { mutableStateOf(false) }
    var darkBg by remember { mutableStateOf(true) }
    var resolutionScale by remember { mutableStateOf(2) }
    var landscapePage by remember { mutableStateOf(false) }

    val clipboard = LocalClipboardManager.current
    val config = remember(selectedFormat, transparentBg, darkBg, resolutionScale, landscapePage) {
        ExportConfig(
            format = selectedFormat,
            transparentBackground = transparentBg,
            darkBackground = darkBg,
            scaleMultiplier = resolutionScale,
            landscapePage = landscapePage
        )
    }
    val previewText = remember(config) {
        if (!config.format.isBinary) onGeneratePreview(config) else "Binary ${config.format.label} ready to save."
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export '$projectName'") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Select Format:", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ExportFormat.entries.forEach { fmt ->
                        FilterChip(
                            selected = selectedFormat == fmt,
                            onClick = { selectedFormat = fmt },
                            label = { Text(fmt.extension.uppercase()) }
                        )
                    }
                }

                if (selectedFormat == ExportFormat.PNG || selectedFormat == ExportFormat.SVG) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Transparent Background", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = transparentBg, onCheckedChange = { transparentBg = it })
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Dark Canvas Palette", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = darkBg, onCheckedChange = { darkBg = it })
                }

                if (selectedFormat == ExportFormat.PNG || selectedFormat == ExportFormat.JPEG) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Resolution Scale:", style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(1, 2, 3).forEach { scale ->
                                FilterChip(
                                    selected = resolutionScale == scale,
                                    onClick = { resolutionScale = scale },
                                    label = { Text("${scale}x") }
                                )
                            }
                        }
                    }
                }

                if (selectedFormat == ExportFormat.PDF) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Landscape Orientation", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = landscapePage, onCheckedChange = { landscapePage = it })
                    }
                }

                if (!selectedFormat.isBinary) {
                    Text("Live Export Preview:", style = MaterialTheme.typography.labelLarge)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = previewText.take(1500),
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!selectedFormat.isBinary) {
                    OutlinedButton(onClick = {
                        clipboard.setText(AnnotatedString(previewText))
                        onDismiss()
                    }) {
                        Text("Copy")
                    }
                }
                Button(
                    onClick = { onSaveToFile(config) },
                    modifier = Modifier.testTag("export_save_file_button")
                ) {
                    Text("Save File")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
