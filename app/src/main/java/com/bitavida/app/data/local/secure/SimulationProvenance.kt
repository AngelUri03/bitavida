package com.bitavida.app.data.local.secure

import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.migration.Migration
import androidx.room3.withWriteTransaction
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Staged infrastructure: deliberately NOT registered in the production database or provider.
 * It cannot seed data or run a migration until explicitly integrated and tested.
 */
@Entity(tableName = "simulation_scenarios")
internal data class SimulationScenarioEntity(
    @PrimaryKey val scenarioId: String,
    val manifestSha256: String,
    val completedAtEpochMillis: Long,
)

@Entity(
    tableName = "simulation_provenance",
    primaryKeys = ["scenarioId", "namespace", "fixtureKey"],
    foreignKeys = [
        ForeignKey(
            entity = SimulationScenarioEntity::class,
            parentColumns = ["scenarioId"],
            childColumns = ["scenarioId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["namespace", "entityId"], unique = true),
    ],
)
internal data class SimulationProvenanceEntity(
    val scenarioId: String,
    /** Physical entity/table name, never a mutable display name. */
    val namespace: String,
    /** Stable identity within this versioned, independently specified fixture. */
    val fixtureKey: String,
    /** Actual persisted identity in the named entity namespace. */
    val entityId: String,
)

internal val SimulationProvenanceSchemaStatements = listOf(
    """CREATE TABLE IF NOT EXISTS `simulation_scenarios` (`scenarioId` TEXT NOT NULL, `manifestSha256` TEXT NOT NULL, `completedAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`scenarioId`))""",
    """CREATE TABLE IF NOT EXISTS `simulation_provenance` (`scenarioId` TEXT NOT NULL, `namespace` TEXT NOT NULL, `fixtureKey` TEXT NOT NULL, `entityId` TEXT NOT NULL, PRIMARY KEY(`scenarioId`, `namespace`, `fixtureKey`), FOREIGN KEY(`scenarioId`) REFERENCES `simulation_scenarios`(`scenarioId`) ON UPDATE NO ACTION ON DELETE RESTRICT )""",
    """CREATE UNIQUE INDEX IF NOT EXISTS `index_simulation_provenance_namespace_entityId` ON `simulation_provenance` (`namespace`, `entityId`)""",
)

/** Additive only; does not classify, alter or merge pre-existing patient data. */
internal val SimulationProvenanceMigration33To34 = object : Migration(33, 34) {
    override suspend fun migrate(connection: SQLiteConnection) {
        SimulationProvenanceSchemaStatements.forEach(connection::execSQL)
    }
}

@Dao
internal interface SimulationProvenanceDao {
    @Query("SELECT * FROM simulation_scenarios WHERE scenarioId = :scenarioId")
    suspend fun scenario(scenarioId: String): SimulationScenarioEntity?

    @Query("SELECT * FROM simulation_provenance WHERE scenarioId = :scenarioId ORDER BY namespace, fixtureKey")
    suspend fun identities(scenarioId: String): List<SimulationProvenanceEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertScenario(scenario: SimulationScenarioEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertIdentities(identities: List<SimulationProvenanceEntity>)
}

internal data class SimulationIdentity(
    val namespace: String,
    val fixtureKey: String,
    val entityId: String,
)

internal enum class SimulationLoadResult { CREATED, ALREADY_PRESENT }

/**
 * One Room write transaction encompasses scenario marker, domain writes and provenance.
 *
 * The caller must use repositories backed by this same database and defer every non-SQL
 * effect (notifications, files and alarms) until after commit. Callback must return every
 * newly persisted, qualified identity, including revisions and scheduling entities.
 * This coordinator does not provide a loader, a reset operation, or proof that a callback
 * created its claimed entities; the future loader must verify those facts independently.
 */
internal class SimulationProvenanceRepository(
    private val database: SecureSpikeDatabase,
    private val provenance: SimulationProvenanceDao,
) {
    suspend fun createOnce(
        scenarioId: String,
        manifestSha256: String,
        completedAtEpochMillis: Long,
        createRecords: suspend () -> List<SimulationIdentity>,
    ): SimulationLoadResult {
        require(scenarioId.matches(Regex("[a-z0-9][a-z0-9._-]{0,127}")))
        require(manifestSha256.matches(Regex("[a-f0-9]{64}")))
        require(completedAtEpochMillis >= 0)

        return database.withWriteTransaction {
            val existing = provenance.scenario(scenarioId)
            if (existing != null) {
                check(existing.manifestSha256 == manifestSha256) {
                    "A scenario with a different manifest already exists"
                }
                return@withWriteTransaction SimulationLoadResult.ALREADY_PRESENT
            }

            // Insert first so that all associated identities satisfy the foreign key.
            // A failure or cancellation in the callback rolls this marker back as well.
            provenance.insertScenario(
                SimulationScenarioEntity(scenarioId, manifestSha256, completedAtEpochMillis),
            )
            val identities = createRecords()
            require(identities.isNotEmpty()) { "A scenario must identify its records" }
            require(identities.all {
                it.namespace.matches(Regex("[a-z][a-z0-9_]{0,127}")) &&
                    it.fixtureKey.isNotBlank() && it.fixtureKey.length <= 256 &&
                    it.entityId.isNotBlank() && it.entityId.length <= 512
            })
            require(identities.map { it.namespace to it.fixtureKey }.toSet().size == identities.size)
            require(identities.map { it.namespace to it.entityId }.toSet().size == identities.size)
            provenance.insertIdentities(identities.map {
                SimulationProvenanceEntity(scenarioId, it.namespace, it.fixtureKey, it.entityId)
            })
            SimulationLoadResult.CREATED
        }
    }
}
