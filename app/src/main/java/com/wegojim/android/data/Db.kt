package com.wegojim.android.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.builtins.ListSerializer

@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey val uuid: String,
    val name: String,
    val category: String,
    val equipment: String,
    val instructions: String,
    /** Comma-separated muscle names. */
    val primaryMuscles: String,
    val secondaryMuscles: String,
    val aliases: String,
    val cardioProfile: String?,
    val isCustom: Boolean,
) {
    val muscleSummary: String
        get() = (primaryList + secondaryList).distinct().joinToString(", ")
    val primaryList: List<String> get() = primaryMuscles.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    val secondaryList: List<String> get() = secondaryMuscles.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    val isCardio: Boolean get() = cardioProfile != null
}

@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey val id: String = newId(),
    val name: String = "",
    val notes: String = "",
    val folderId: String? = null,
    val sortOrder: Int = 0,
    val exercises: List<ExerciseEntry> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "[]") val cardio: List<CardioBlock> = emptyList(),
)

@Entity(tableName = "template_folders")
data class TemplateFolderEntity(
    @PrimaryKey val id: String = newId(),
    val name: String,
    val sortOrder: Int = 0,
)

@Entity(tableName = "workouts", indices = [Index("startedAt")])
data class WorkoutEntity(
    @PrimaryKey val id: String,
    val templateId: String?,
    val name: String,
    val startedAt: Long,
    val endedAt: Long,
    val durationSeconds: Int,
    val totalVolumeKg: Double,
    val prCount: Int,
    val notes: String,
    val exercises: List<ExerciseEntry>,
    @ColumnInfo(defaultValue = "[]") val cardio: List<CardioBlock> = emptyList(),
) {
    val cardioSeconds: Int get() = cardio.filter { it.completed }.sumOf { it.durationSeconds ?: 0 }
    val completedSetCount: Int get() = exercises.sumOf { e -> e.sets.count { it.completed } }
}

/** Flattened completed set, used for history lookups, PRs and progress. */
@Entity(tableName = "set_facts", indices = [Index("exerciseUuid"), Index("workoutId")])
data class SetFactEntity(
    @PrimaryKey val setId: String,
    val workoutId: String,
    val exerciseUuid: String,
    val exerciseName: String,
    val completedAt: Long,
    val setIndex: Int,
    val isWarmup: Boolean,
    val reps: Int,
    val weight: Double?,
    val unit: LoadUnit,
    val weightKg: Double?,
    val e1rmKg: Double?,
    val volumeKg: Double,
)

data class ExerciseBest(
    val exerciseUuid: String,
    val exerciseName: String,
    val bestE1rmKg: Double?,
    val maxWeightKg: Double?,
    val maxReps: Int,
    val sessions: Int,
    val lastAt: Long,
)

// Top-level so Room's KSP pass doesn't need to resolve the plugin-generated serializer type.
private val exerciseListSerializer by lazy { ListSerializer(ExerciseEntry.serializer()) }
private val cardioListSerializer by lazy { ListSerializer(CardioBlock.serializer()) }

class Converters {
    @TypeConverter
    fun fromExercises(value: List<ExerciseEntry>): String = AppJson.encodeToString(exerciseListSerializer, value)

    @TypeConverter
    fun toExercises(value: String): List<ExerciseEntry> = AppJson.decodeFromString(exerciseListSerializer, value)

    @TypeConverter
    fun fromCardio(value: List<CardioBlock>): String = AppJson.encodeToString(cardioListSerializer, value)

    @TypeConverter
    fun toCardio(value: String): List<CardioBlock> = AppJson.decodeFromString(cardioListSerializer, value)

    @TypeConverter
    fun fromUnit(value: LoadUnit): String = value.name

    @TypeConverter
    fun toUnit(value: String): LoadUnit = LoadUnit.valueOf(value)
}

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises ORDER BY name COLLATE NOCASE")
    fun all(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE uuid = :uuid")
    fun byUuid(uuid: String): Flow<ExerciseEntity?>

    @Query("SELECT * FROM exercises WHERE uuid = :uuid")
    suspend fun get(uuid: String): ExerciseEntity?

    @Upsert
    suspend fun upsert(items: List<ExerciseEntity>)

    @Query("DELETE FROM exercises WHERE uuid = :uuid AND isCustom = 1")
    suspend fun deleteCustom(uuid: String)
}

@Dao
interface TemplateDao {
    @Query("SELECT * FROM templates ORDER BY sortOrder, name COLLATE NOCASE")
    fun all(): Flow<List<TemplateEntity>>

    @Query("SELECT * FROM templates WHERE id = :id")
    suspend fun get(id: String): TemplateEntity?

    @Upsert
    suspend fun upsert(template: TemplateEntity)

    @Query("DELETE FROM templates WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM templates")
    suspend fun maxSortOrder(): Int

    @Query("SELECT * FROM template_folders ORDER BY sortOrder, name COLLATE NOCASE")
    fun folders(): Flow<List<TemplateFolderEntity>>

    @Upsert
    suspend fun upsertFolder(folder: TemplateFolderEntity)

    @Query("DELETE FROM template_folders WHERE id = :id")
    suspend fun deleteFolder(id: String)

    @Query("UPDATE templates SET folderId = NULL WHERE folderId = :folderId")
    suspend fun clearFolder(folderId: String)

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM template_folders")
    suspend fun maxFolderSortOrder(): Int
}

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workouts ORDER BY startedAt DESC")
    fun all(): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workouts WHERE id = :id")
    fun byId(id: String): Flow<WorkoutEntity?>

    @Upsert
    suspend fun upsert(workout: WorkoutEntity)

    @Query("DELETE FROM workouts WHERE id = :id")
    suspend fun delete(id: String)

    @Upsert
    suspend fun upsertFacts(facts: List<SetFactEntity>)

    @Query("DELETE FROM set_facts WHERE workoutId = :workoutId")
    suspend fun deleteFacts(workoutId: String)

    @Query("SELECT MAX(e1rmKg) FROM set_facts WHERE exerciseUuid = :exerciseUuid AND isWarmup = 0 AND workoutId != :excludeWorkoutId")
    suspend fun bestE1rm(exerciseUuid: String, excludeWorkoutId: String): Double?

    @Query(
        """SELECT * FROM set_facts WHERE exerciseUuid = :exerciseUuid AND workoutId = (
               SELECT workoutId FROM set_facts WHERE exerciseUuid = :exerciseUuid
               ORDER BY completedAt DESC LIMIT 1)
           ORDER BY setIndex"""
    )
    suspend fun lastSessionSets(exerciseUuid: String): List<SetFactEntity>

    @Query("SELECT * FROM set_facts ORDER BY completedAt")
    fun allFacts(): Flow<List<SetFactEntity>>

    @Query("SELECT MAX(completedAt) FROM set_facts WHERE exerciseUuid = :exerciseUuid")
    suspend fun lastCompletedAt(exerciseUuid: String): Long?

    @Query("SELECT * FROM set_facts WHERE exerciseUuid = :exerciseUuid ORDER BY completedAt DESC, setIndex")
    fun factsForExercise(exerciseUuid: String): Flow<List<SetFactEntity>>

    @Query(
        """SELECT exerciseUuid, exerciseName,
                  MAX(CASE WHEN isWarmup = 0 THEN e1rmKg END) AS bestE1rmKg,
                  MAX(CASE WHEN isWarmup = 0 THEN weightKg END) AS maxWeightKg,
                  MAX(CASE WHEN isWarmup = 0 THEN reps ELSE 0 END) AS maxReps,
                  COUNT(DISTINCT workoutId) AS sessions,
                  MAX(completedAt) AS lastAt
           FROM set_facts GROUP BY exerciseUuid ORDER BY lastAt DESC"""
    )
    fun exerciseBests(): Flow<List<ExerciseBest>>
}

@Database(
    entities = [ExerciseEntity::class, TemplateEntity::class, TemplateFolderEntity::class, WorkoutEntity::class, SetFactEntity::class],
    version = 2,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exercises(): ExerciseDao
    abstract fun templates(): TemplateDao
    abstract fun workouts(): WorkoutDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "wgj.db")
                .addMigrations(MIGRATION_1_2)
                .build()

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE templates ADD COLUMN cardio TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE workouts ADD COLUMN cardio TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("CREATE TABLE IF NOT EXISTS template_folders (id TEXT NOT NULL, name TEXT NOT NULL, sortOrder INTEGER NOT NULL, PRIMARY KEY(id))")
            }
        }
    }
}
