package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.studioDataStore by preferencesDataStore(name = "guber_studio_prefs")

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    val flowchartScript: String,
    val dialogueScript: String,
    val sceneScript: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "characters")
data class CharacterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val name: String,
    val role: String,
    val description: String,
    val colorHex: Long,
    val avatarInitials: String
)

@Dao
interface GuberDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun observeAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun getProjectById(id: Long): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity): Long

    @Update
    suspend fun updateProject(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteProjectById(id: Long)

    @Query("SELECT * FROM characters WHERE projectId = :projectId ORDER BY name ASC")
    fun observeCharactersForProject(projectId: Long): Flow<List<CharacterEntity>>

    @Query("SELECT * FROM characters WHERE projectId = :projectId ORDER BY name ASC")
    suspend fun getCharactersForProjectOnce(projectId: Long): List<CharacterEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCharacter(character: CharacterEntity): Long

    @Query("DELETE FROM characters WHERE id = :id")
    suspend fun deleteCharacterById(id: Long)
}

@Database(
    entities = [ProjectEntity::class, CharacterEntity::class],
    version = 1,
    exportSchema = false
)
abstract class GuberDatabase : RoomDatabase() {
    abstract fun guberDao(): GuberDao

    companion object {
        @Volatile
        private var INSTANCE: GuberDatabase? = null

        fun getInstance(context: Context): GuberDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    GuberDatabase::class.java,
                    "guber_studio.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}

data class StudioSettings(
    val darkTheme: Boolean = true,
    val editorFontSizeSp: Float = 14f,
    val wordWrap: Boolean = true,
    val autoParse: Boolean = true,
    val autoSave: Boolean = true,
    val splitFraction: Float = 0.48f,
    val lastOpenedProjectId: Long = -1L
)

class GuberRepository(
    private val dao: GuberDao,
    private val context: Context
) {
    private val KEY_DARK_THEME = booleanPreferencesKey("dark_theme")
    private val KEY_FONT_SIZE = floatPreferencesKey("editor_font_size")
    private val KEY_WORD_WRAP = booleanPreferencesKey("word_wrap")
    private val KEY_AUTO_PARSE = booleanPreferencesKey("auto_parse")
    private val KEY_AUTO_SAVE = booleanPreferencesKey("auto_save")
    private val KEY_SPLIT_FRACTION = floatPreferencesKey("split_fraction")
    private val KEY_LAST_PROJECT_ID = longPreferencesKey("last_project_id")

    val allProjects: Flow<List<ProjectEntity>> = dao.observeAllProjects()

    val settingsFlow: Flow<StudioSettings> = context.studioDataStore.data.map { prefs ->
        StudioSettings(
            darkTheme = prefs[KEY_DARK_THEME] ?: true,
            editorFontSizeSp = prefs[KEY_FONT_SIZE] ?: 14f,
            wordWrap = prefs[KEY_WORD_WRAP] ?: true,
            autoParse = prefs[KEY_AUTO_PARSE] ?: true,
            autoSave = prefs[KEY_AUTO_SAVE] ?: true,
            splitFraction = prefs[KEY_SPLIT_FRACTION] ?: 0.48f,
            lastOpenedProjectId = prefs[KEY_LAST_PROJECT_ID] ?: -1L
        )
    }

    fun observeCharacters(projectId: Long): Flow<List<CharacterEntity>> =
        dao.observeCharactersForProject(projectId)

    suspend fun ensureStarterProjectsSeeded(): ProjectEntity {
        val current = dao.observeAllProjects().first()
        if (current.isNotEmpty()) {
            return current.first()
        }

        // Seed Sample 1: Chennai Monsoon Showcase (Full Unified Project)
        val mainId = dao.insertProject(
            ProjectEntity(
                name = "Monsoon Chai & Order Studio",
                description = "Complete showcase combining Order Fulfillment Flowchart, Coffee Shop Branching Dialogue, and Chennai Rainy Evening Screenplay.",
                flowchartScript = SAMPLE_FLOWCHART_SCRIPT,
                dialogueScript = SAMPLE_DIALOGUE_SCRIPT,
                sceneScript = SAMPLE_SCENE_SCRIPT,
                updatedAt = System.currentTimeMillis()
            )
        )

        dao.insertCharacter(
            CharacterEntity(
                projectId = mainId,
                name = "Krishna",
                role = "Lead Writer / Architect",
                description = "Thoughtful storyteller who enjoys masala chai during Chennai monsoon evenings.",
                colorHex = 0xFF38BDF8L,
                avatarInitials = "KR"
            )
        )
        dao.insertCharacter(
            CharacterEntity(
                projectId = mainId,
                name = "Meera",
                role = "Game Designer",
                description = "Energetic creative director who loves rainy evenings and branching narratives.",
                colorHex = 0xFFF472B6L,
                avatarInitials = "ME"
            )
        )
        dao.insertCharacter(
            CharacterEntity(
                projectId = mainId,
                name = "Shopkeeper",
                role = "Tea Stall Owner",
                description = "Warm local vendor serving steaming cardamom tea and filter coffee.",
                colorHex = 0xFF34D399L,
                avatarInitials = "SK"
            )
        )

        // Seed Sample 2: Game Quest Logic Project
        val questId = dao.insertProject(
            ProjectEntity(
                name = "Cyber Detective — Quest Logic",
                description = "Interactive mystery investigation flowchart, suspect interrogation dialogue, and neon alleyway scenes.",
                flowchartScript = SAMPLE_GAME_FLOWCHART,
                dialogueScript = SAMPLE_GAME_DIALOGUE,
                sceneScript = SAMPLE_GAME_SCENE,
                updatedAt = System.currentTimeMillis() - 60_000L
            )
        )
        dao.insertCharacter(
            CharacterEntity(
                projectId = questId,
                name = "Inspector Rao",
                role = "Investigator",
                description = "Seasoned cyber-noir detective.",
                colorHex = 0xFFFBBF24L,
                avatarInitials = "IR"
            )
        )

        return dao.getProjectById(mainId)!!
    }

    suspend fun createNewProject(name: String, description: String): ProjectEntity {
        val id = dao.insertProject(
            ProjectEntity(
                name = name.ifBlank { "Untitled Studio Project" },
                description = description.ifBlank { "Offline Guber creative project" },
                flowchartScript = "Start\nInput: Define problem\nIs solution ready?\nyes: Build prototype\nno: Research options\nVerify output\nEnd",
                dialogueScript = "SCENE: Opening Scene\n\nCHARACTER: Krishna\nDIALOGUE: Welcome to our new story project!\n\nCHARACTER: Meera\nDIALOGUE: Let's map out the branching choices.",
                sceneScript = "SCENE: 01\n\nTITLE: Opening Sequence\n\nLOCATION: Studio Room\n\nTIME: Morning\n\nWEATHER: Clear\n\nCHARACTERS:\n  - Krishna\n  - Meera\n\nACTION:\n  Morning sunlight streams across the writing desk.\n\nDIALOGUE:\n  Krishna: Ready to begin?\n  Meera: Always.\n\nCAMERA:\n  Medium tracking shot across the desk.\n\nAUDIO:\n  Gentle morning ambience\n\nTRANSITION: Cut To"
            )
        )
        dao.insertCharacter(
            CharacterEntity(
                projectId = id,
                name = "Krishna",
                role = "Protagonist",
                description = "Lead character",
                colorHex = 0xFF38BDF8L,
                avatarInitials = "KR"
            )
        )
        dao.insertCharacter(
            CharacterEntity(
                projectId = id,
                name = "Meera",
                role = "Deuteragonist",
                description = "Co-lead character",
                colorHex = 0xFFF472B6L,
                avatarInitials = "ME"
            )
        )
        return dao.getProjectById(id)!!
    }

    suspend fun saveProject(project: ProjectEntity) {
        dao.updateProject(project.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun duplicateProject(project: ProjectEntity): ProjectEntity {
        val copyId = dao.insertProject(
            project.copy(
                id = 0,
                name = "${project.name} (Copy)",
                updatedAt = System.currentTimeMillis()
            )
        )
        val chars = dao.getCharactersForProjectOnce(project.id)
        chars.forEach { c ->
            dao.insertCharacter(c.copy(id = 0, projectId = copyId))
        }
        return dao.getProjectById(copyId)!!
    }

    suspend fun deleteProject(projectId: Long) {
        dao.deleteProjectById(projectId)
    }

    suspend fun addCharacter(
        projectId: Long,
        name: String,
        role: String,
        description: String,
        colorHex: Long
    ) {
        val initials = name.trim()
            .split(" ")
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .ifEmpty { "CH" }
        dao.insertCharacter(
            CharacterEntity(
                projectId = projectId,
                name = name.trim(),
                role = role.trim().ifEmpty { "Character" },
                description = description.trim(),
                colorHex = colorHex,
                avatarInitials = initials
            )
        )
    }

    suspend fun deleteCharacter(id: Long) {
        dao.deleteCharacterById(id)
    }

    suspend fun updateDarkTheme(enabled: Boolean) {
        context.studioDataStore.edit { it[KEY_DARK_THEME] = enabled }
    }

    suspend fun updateEditorFontSize(sizeSp: Float) {
        context.studioDataStore.edit { it[KEY_FONT_SIZE] = sizeSp }
    }

    suspend fun updateWordWrap(enabled: Boolean) {
        context.studioDataStore.edit { it[KEY_WORD_WRAP] = enabled }
    }

    suspend fun updateAutoParse(enabled: Boolean) {
        context.studioDataStore.edit { it[KEY_AUTO_PARSE] = enabled }
    }

    suspend fun updateAutoSave(enabled: Boolean) {
        context.studioDataStore.edit { it[KEY_AUTO_SAVE] = enabled }
    }

    suspend fun updateSplitFraction(fraction: Float) {
        context.studioDataStore.edit { it[KEY_SPLIT_FRACTION] = fraction.coerceIn(0.25f, 0.75f) }
    }

    suspend fun updateLastProjectId(projectId: Long) {
        context.studioDataStore.edit { it[KEY_LAST_PROJECT_ID] = projectId }
    }

    companion object {
        val SAMPLE_FLOWCHART_SCRIPT = """
            Start
            Input: Receive the order
            In stock?
            yes: Pack the order
            no: Reorder from supplier
            Note: Supplier SLA is 24h
            Print the invoice
            Ship to customer
            End
        """.trimIndent()

        val SAMPLE_DIALOGUE_SCRIPT = """
            SCENE: Coffee Shop

            CHARACTER: Krishna
            DIALOGUE: Hey, how are you?

            CHARACTER: Meera
            DIALOGUE: I'm doing great!

            CHARACTER: Krishna
            DIALOGUE: Shall we order something warm?

            NARRATION:
            Rain starts falling outside against the glass panes.

            CHARACTER: Krishna
            DIALOGUE: What would you like?

            CHOICE:
              1. Tea
              2. Coffee

            OPTION: Tea
            CHARACTER: Meera
            DIALOGUE: One masala chai, please!
            GOTO: END_CHOICE

            OPTION: Coffee
            CHARACTER: Meera
            DIALOGUE: One filter coffee, please!

            END_CHOICE

            CHARACTER: Shopkeeper
            DIALOGUE: Coming right up, fresh and hot!
        """.trimIndent()

        val SAMPLE_SCENE_SCRIPT = """
            SCENE: 01

            TITLE: A Rainy Evening

            LOCATION: EXT. Chennai - Tea Shop

            TIME: Evening

            WEATHER: Rain

            CHARACTERS:
              - Krishna
              - Meera

            DESCRIPTION:
              Warm tungsten lights glow beneath the tin awning as monsoon rain patters on the street.

            ACTION:
              Krishna stands near the tea shop holding two notebooks.
              Meera walks briskly towards him under a bright umbrella.

            DIALOGUE:
              Krishna: Hey, you're late!
              Meera: Sorry, the rain delayed me.

            CAMERA:
              Wide shot of the tea shop in the rain.
              Close-up on steaming clay cups of chai.

            AUDIO:
              Rain ambience
              Soft acoustic background music

            TRANSITION: Fade Out

            SCENE: 02

            TITLE: Storyboard Breakthrough

            LOCATION: INT. Tea Shop Corner Table

            TIME: Night

            WEATHER: Heavy Rain

            CHARACTERS:
              - Krishna
              - Meera
              - Shopkeeper

            ACTION:
              Krishna sketches a branching flowchart while Meera reviews the scene cards.

            DIALOGUE:
              Meera: If the player chooses the secret key, Scene 03 unlocks immediately.
              Krishna: Perfect, let's link both branches now.

            CAMERA:
              Over-the-shoulder shot of the notebook diagrams.

            AUDIO:
              Clinking glasses and distant thunder

            TRANSITION: Cut To
        """.trimIndent()

        val SAMPLE_GAME_FLOWCHART = """
            Start
            Input: Enter Crime Scene
            Clue found on terminal?
            yes: Decrypt memory shard
            no: Interrogate night guard
            Verify suspect alibi
            Alibi holds?
            yes: Release suspect
            no: Issue warrant
            End
        """.trimIndent()

        val SAMPLE_GAME_DIALOGUE = """
            SCENE: Sector 7 Interrogation

            CHARACTER: Inspector Rao
            DIALOGUE: Where were you when the relay went dark?

            CHOICE:
              1. Press with evidence
              2. Offer protection deal

            OPTION: Press with evidence
            CHARACTER: Inspector Rao
            DIALOGUE: Your badge key was logged at the terminal at 02:14.
            GOTO: END_CHOICE

            OPTION: Offer protection deal
            CHARACTER: Inspector Rao
            DIALOGUE: Help us trace the syndicate and you walk free tonight.

            END_CHOICE
        """.trimIndent()

        val SAMPLE_GAME_SCENE = """
            SCENE: 01

            TITLE: Neon Alleyway

            LOCATION: EXT. Sector 7 Plaza

            TIME: Midnight

            WEATHER: Fog & Neon Drizzle

            CHARACTERS:
              - Inspector Rao

            ACTION:
              Holographic billboards flicker across wet cobblestones.

            CAMERA:
              Low-angle dolly shot following Rao.

            AUDIO:
              Synth drone and distant siren

            TRANSITION: Dissolve To
        """.trimIndent()
    }
}
