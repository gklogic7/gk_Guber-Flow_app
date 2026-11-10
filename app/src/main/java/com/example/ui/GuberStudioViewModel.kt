package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.core.editor.EditorLanguage
import com.example.core.editor.EditorStateController
import com.example.core.export.ExportConfig
import com.example.core.export.ExportEngine
import com.example.core.graph.CharacterProfile
import com.example.core.graph.DialogueGraph
import com.example.core.graph.FlowGraph
import com.example.core.graph.LayoutOrientation
import com.example.core.graph.SceneGraph
import com.example.core.parser.DialogueParser
import com.example.core.parser.FlowchartParser
import com.example.core.parser.SceneParser
import com.example.data.CharacterEntity
import com.example.data.GuberDatabase
import com.example.data.GuberRepository
import com.example.data.ProjectEntity
import com.example.data.StudioSettings
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class StudioDestination(val title: String) {
    HOME("Studio Home"),
    FLOWCHART("Flowchart Editor"),
    DIALOGUE("Dialogue Writer"),
    SCENE("Scene Writer"),
    CHARACTERS("Characters"),
    PROJECTS("Projects"),
    SETTINGS("Settings")
}

enum class WorkspaceViewMode(val label: String) {
    SPLIT("Split"),
    EDITOR_ONLY("Editor"),
    VIEWER_ONLY("Visual")
}

class GuberStudioViewModel(
    application: Application,
    private val repository: GuberRepository
) : AndroidViewModel(application) {

    private val _currentDestination = MutableStateFlow(StudioDestination.FLOWCHART)
    val currentDestination: StateFlow<StudioDestination> = _currentDestination.asStateFlow()

    private val _workspaceViewMode = MutableStateFlow(WorkspaceViewMode.SPLIT)
    val workspaceViewMode: StateFlow<WorkspaceViewMode> = _workspaceViewMode.asStateFlow()

    private val _activeProject = MutableStateFlow<ProjectEntity?>(null)
    val activeProject: StateFlow<ProjectEntity?> = _activeProject.asStateFlow()

    private val _layoutOrientation = MutableStateFlow(LayoutOrientation.VERTICAL)
    val layoutOrientation: StateFlow<LayoutOrientation> = _layoutOrientation.asStateFlow()

    private val _flowGraph = MutableStateFlow(FlowGraph())
    val flowGraph: StateFlow<FlowGraph> = _flowGraph.asStateFlow()

    private val _dialogueGraph = MutableStateFlow(DialogueGraph())
    val dialogueGraph: StateFlow<DialogueGraph> = _dialogueGraph.asStateFlow()

    private val _sceneGraph = MutableStateFlow(SceneGraph())
    val sceneGraph: StateFlow<SceneGraph> = _sceneGraph.asStateFlow()

    private val _statusToast = MutableStateFlow<String?>(null)
    val statusToast: StateFlow<String?> = _statusToast.asStateFlow()

    val allProjects: StateFlow<List<ProjectEntity>> = repository.allProjects.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val studioSettings: StateFlow<StudioSettings> = repository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StudioSettings()
    )

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val projectCharacters: StateFlow<List<CharacterEntity>> = _activeProject
        .flatMapLatest { proj ->
            if (proj == null) flowOf(emptyList()) else repository.observeCharacters(proj.id)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val flowNodeOffsets = mutableMapOf<String, Pair<Float, Float>>()
    private val dialogueNodeOffsets = mutableMapOf<String, Pair<Float, Float>>()
    private val sceneNodeOffsets = mutableMapOf<String, Pair<Float, Float>>()

    private var autoSaveJob: Job? = null

    // Shared EditorStateController instances for the three creative modules
    val flowchartEditorController = EditorStateController(
        initialText = GuberRepository.SAMPLE_FLOWCHART_SCRIPT,
        initialLanguage = EditorLanguage.FLOWCHART,
        onTextChangedCallback = { updatedText ->
            onModuleScriptEdited(EditorLanguage.FLOWCHART, updatedText)
        }
    )

    val dialogueEditorController = EditorStateController(
        initialText = GuberRepository.SAMPLE_DIALOGUE_SCRIPT,
        initialLanguage = EditorLanguage.DIALOGUE,
        onTextChangedCallback = { updatedText ->
            onModuleScriptEdited(EditorLanguage.DIALOGUE, updatedText)
        }
    )

    val sceneEditorController = EditorStateController(
        initialText = GuberRepository.SAMPLE_SCENE_SCRIPT,
        initialLanguage = EditorLanguage.SCENE,
        onTextChangedCallback = { updatedText ->
            onModuleScriptEdited(EditorLanguage.SCENE, updatedText)
        }
    )

    init {
        // Parse initial sample scripts right away so the visual workspace is ready immediately
        reparseFlowchart()
        reparseDialogue()
        reparseScene()

        viewModelScope.launch {
            val initialProject = repository.ensureStarterProjectsSeeded()
            loadProjectIntoEditors(initialProject, recordHistory = false)
        }

        viewModelScope.launch {
            studioSettings.collect { settings ->
                flowchartEditorController.fontSizeSp = settings.editorFontSizeSp
                dialogueEditorController.fontSizeSp = settings.editorFontSizeSp
                sceneEditorController.fontSizeSp = settings.editorFontSizeSp

                flowchartEditorController.wordWrap = settings.wordWrap
                dialogueEditorController.wordWrap = settings.wordWrap
                sceneEditorController.wordWrap = settings.wordWrap
            }
        }
    }

    fun activeEditorController(): EditorStateController = when (_currentDestination.value) {
        StudioDestination.DIALOGUE -> dialogueEditorController
        StudioDestination.SCENE -> sceneEditorController
        else -> flowchartEditorController
    }

    fun navigateTo(destination: StudioDestination) {
        _currentDestination.value = destination
    }

    fun setWorkspaceViewMode(mode: WorkspaceViewMode) {
        _workspaceViewMode.value = mode
    }

    fun toggleFlowLayoutOrientation() {
        _layoutOrientation.value = if (_layoutOrientation.value == LayoutOrientation.VERTICAL) {
            LayoutOrientation.HORIZONTAL
        } else {
            LayoutOrientation.VERTICAL
        }
        flowNodeOffsets.clear()
        reparseFlowchart()
    }

    fun updateNodeDragOffset(language: EditorLanguage, nodeId: String, newX: Float, newY: Float) {
        when (language) {
            EditorLanguage.FLOWCHART -> {
                flowNodeOffsets[nodeId] = newX to newY
                _flowGraph.value = _flowGraph.value.copy(
                    nodes = _flowGraph.value.nodes.map {
                        if (it.id == nodeId) it.copy(x = newX, y = newY) else it
                    }
                )
            }
            EditorLanguage.DIALOGUE -> {
                dialogueNodeOffsets[nodeId] = newX to newY
                _dialogueGraph.value = _dialogueGraph.value.copy(
                    nodes = _dialogueGraph.value.nodes.map {
                        if (it.id == nodeId) it.copy(x = newX, y = newY) else it
                    }
                )
            }
            EditorLanguage.SCENE, EditorLanguage.PLAIN -> {
                sceneNodeOffsets[nodeId] = newX to newY
                _sceneGraph.value = _sceneGraph.value.copy(
                    scenes = _sceneGraph.value.scenes.map {
                        if (it.id == nodeId) it.copy(x = newX, y = newY) else it
                    }
                )
            }
        }
    }

    fun resetVisualLayout(language: EditorLanguage) {
        when (language) {
            EditorLanguage.FLOWCHART -> {
                flowNodeOffsets.clear()
                reparseFlowchart()
            }
            EditorLanguage.DIALOGUE -> {
                dialogueNodeOffsets.clear()
                reparseDialogue()
            }
            EditorLanguage.SCENE, EditorLanguage.PLAIN -> {
                sceneNodeOffsets.clear()
                reparseScene()
            }
        }
        showToast("Canvas layout reset to automatic positioning")
    }

    fun reorderScenesInScript(fromIndex: Int, toIndex: Int) {
        val currentScenes = _sceneGraph.value.scenes
        if (fromIndex !in currentScenes.indices || toIndex !in currentScenes.indices) return
        val rawScript = sceneEditorController.textFieldValue.text
        val regex = Regex("(?m)^SCENE:\\s*")
        val splitStarts = regex.findAll(rawScript).map { it.range.first }.toList()
        if (splitStarts.size <= 1 || fromIndex >= splitStarts.size || toIndex >= splitStarts.size) return

        val blocks = splitStarts.mapIndexed { idx, start ->
            val end = if (idx + 1 < splitStarts.size) splitStarts[idx + 1] else rawScript.length
            rawScript.substring(start, end).trim()
        }.toMutableList()

        val moved = blocks.removeAt(fromIndex)
        blocks.add(toIndex, moved)
        val newScript = blocks.joinToString("\n\n")
        sceneEditorController.setTextProgrammatically(newScript, recordHistory = true)
        reparseScene()
    }

    fun manualGenerateCurrentModule() {
        reparseFlowchart()
        reparseDialogue()
        reparseScene()
        saveActiveProjectNow()
        showToast("Visual graph regenerated & saved")
    }

    fun selectProject(project: ProjectEntity) {
        loadProjectIntoEditors(project, recordHistory = false)
        viewModelScope.launch {
            repository.updateLastProjectId(project.id)
        }
        showToast("Opened '${project.name}'")
    }

    fun createNewProject(name: String, description: String) {
        viewModelScope.launch {
            val created = repository.createNewProject(name, description)
            loadProjectIntoEditors(created, recordHistory = false)
            _currentDestination.value = StudioDestination.FLOWCHART
            showToast("Created project '${created.name}'")
        }
    }

    fun saveActiveProjectNow() {
        val current = _activeProject.value ?: return
        val updated = current.copy(
            flowchartScript = flowchartEditorController.textFieldValue.text,
            dialogueScript = dialogueEditorController.textFieldValue.text,
            sceneScript = sceneEditorController.textFieldValue.text,
            updatedAt = System.currentTimeMillis()
        )
        _activeProject.value = updated
        viewModelScope.launch {
            repository.saveProject(updated)
        }
    }

    fun renameProject(project: ProjectEntity, newName: String, newDescription: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            val updated = project.copy(
                name = newName.trim(),
                description = newDescription.trim(),
                updatedAt = System.currentTimeMillis()
            )
            repository.saveProject(updated)
            if (_activeProject.value?.id == project.id) {
                _activeProject.value = updated
            }
            showToast("Renamed to '${updated.name}'")
        }
    }

    fun duplicateProject(project: ProjectEntity) {
        viewModelScope.launch {
            val copy = repository.duplicateProject(project)
            loadProjectIntoEditors(copy, recordHistory = false)
            showToast("Duplicated '${copy.name}'")
        }
    }

    fun deleteProject(project: ProjectEntity) {
        viewModelScope.launch {
            repository.deleteProject(project.id)
            val remaining = allProjects.value.filter { it.id != project.id }
            if (_activeProject.value?.id == project.id) {
                val next = remaining.firstOrNull() ?: repository.ensureStarterProjectsSeeded()
                loadProjectIntoEditors(next, recordHistory = false)
            }
            showToast("Deleted '${project.name}'")
        }
    }

    fun addCharacter(name: String, role: String, description: String, colorHex: Long) {
        val proj = _activeProject.value ?: return
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.addCharacter(proj.id, name, role, description, colorHex)
            showToast("Added character '${name.trim()}'")
        }
    }

    fun deleteCharacter(id: Long) {
        viewModelScope.launch {
            repository.deleteCharacter(id)
        }
    }

    fun insertCharacterIntoDialogue(characterName: String) {
        val snippet = "\nCHARACTER: $characterName\nDIALOGUE: "
        dialogueEditorController.insertSnippet(snippet)
        _currentDestination.value = StudioDestination.DIALOGUE
    }

    fun importProjectFromJson(jsonText: String) {
        val parsed = ExportEngine.parseImportedProjectJson(jsonText)
        if (parsed == null) {
            showToast("Invalid project JSON format")
            return
        }
        viewModelScope.launch {
            val (proj, chars) = parsed
            val created = repository.createNewProject(proj.name, proj.description)
            val fullUpdated = created.copy(
                flowchartScript = proj.flowchartScript.ifEmpty { created.flowchartScript },
                dialogueScript = proj.dialogueScript.ifEmpty { created.dialogueScript },
                sceneScript = proj.sceneScript.ifEmpty { created.sceneScript }
            )
            repository.saveProject(fullUpdated)
            chars.forEach { c ->
                repository.addCharacter(created.id, c.name, c.role, c.description, c.colorHex)
            }
            loadProjectIntoEditors(fullUpdated, recordHistory = false)
            showToast("Imported '${fullUpdated.name}'")
        }
    }

    fun generateExportPreview(config: ExportConfig): String {
        val proj = _activeProject.value ?: ProjectEntity(
            name = "Guber Studio",
            description = "",
            flowchartScript = flowchartEditorController.textFieldValue.text,
            dialogueScript = dialogueEditorController.textFieldValue.text,
            sceneScript = sceneEditorController.textFieldValue.text
        )
        return ExportEngine.generateTextExport(
            project = proj,
            characters = projectCharacters.value,
            flowGraph = _flowGraph.value,
            dialogueGraph = _dialogueGraph.value,
            sceneGraph = _sceneGraph.value,
            config = config
        )
    }

    fun updateDarkTheme(enabled: Boolean) {
        viewModelScope.launch { repository.updateDarkTheme(enabled) }
    }

    fun updateFontSize(sizeSp: Float) {
        viewModelScope.launch { repository.updateEditorFontSize(sizeSp) }
    }

    fun updateWordWrap(enabled: Boolean) {
        viewModelScope.launch { repository.updateWordWrap(enabled) }
    }

    fun updateAutoParse(enabled: Boolean) {
        viewModelScope.launch { repository.updateAutoParse(enabled) }
    }

    fun updateAutoSave(enabled: Boolean) {
        viewModelScope.launch { repository.updateAutoSave(enabled) }
    }

    fun updateSplitFraction(fraction: Float) {
        viewModelScope.launch { repository.updateSplitFraction(fraction) }
    }

    fun clearToast() {
        _statusToast.value = null
    }

    fun showToast(message: String) {
        _statusToast.value = message
    }

    fun characterProfilesForCanvas(): List<CharacterProfile> =
        projectCharacters.value.map {
            CharacterProfile(
                id = it.id.toString(),
                name = it.name,
                role = it.role,
                description = it.description,
                colorHex = it.colorHex,
                avatarInitials = it.avatarInitials
            )
        }

    private fun loadProjectIntoEditors(project: ProjectEntity, recordHistory: Boolean) {
        _activeProject.value = project
        flowNodeOffsets.clear()
        dialogueNodeOffsets.clear()
        sceneNodeOffsets.clear()

        flowchartEditorController.setTextProgrammatically(project.flowchartScript, recordHistory)
        dialogueEditorController.setTextProgrammatically(project.dialogueScript, recordHistory)
        sceneEditorController.setTextProgrammatically(project.sceneScript, recordHistory)

        reparseFlowchart()
        reparseDialogue()
        reparseScene()
    }

    private fun onModuleScriptEdited(language: EditorLanguage, updatedText: String) {
        if (studioSettings.value.autoParse) {
            when (language) {
                EditorLanguage.FLOWCHART -> reparseFlowchart(updatedText)
                EditorLanguage.DIALOGUE -> reparseDialogue(updatedText)
                EditorLanguage.SCENE, EditorLanguage.PLAIN -> reparseScene(updatedText)
            }
        }
        if (studioSettings.value.autoSave) {
            scheduleDebouncedAutosave()
        }
    }

    private fun scheduleDebouncedAutosave() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(800L)
            saveActiveProjectNow()
        }
    }

    private fun reparseFlowchart(script: String = flowchartEditorController.textFieldValue.text) {
        _flowGraph.value = FlowchartParser.parse(
            script = script,
            orientation = _layoutOrientation.value,
            manualOffsets = flowNodeOffsets
        )
    }

    private fun reparseDialogue(script: String = dialogueEditorController.textFieldValue.text) {
        _dialogueGraph.value = DialogueParser.parse(
            script = script,
            manualOffsets = dialogueNodeOffsets
        )
    }

    private fun reparseScene(script: String = sceneEditorController.textFieldValue.text) {
        _sceneGraph.value = SceneParser.parse(
            script = script,
            manualOffsets = sceneNodeOffsets
        )
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras
            ): T {
                val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                val db = GuberDatabase.getInstance(application)
                val repo = GuberRepository(db.guberDao(), application)
                return GuberStudioViewModel(application, repo) as T
            }
        }
    }
}
