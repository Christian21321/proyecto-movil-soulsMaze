package com.cardclash.data.local

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.cardclash.data.local.entity.MatchHistoryEntity
import com.cardclash.data.local.entity.OwnedCardEntity
import com.cardclash.data.local.entity.PlayerProfileEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests de INTEGRACION con Room real sobre Robolectric (Grupo A, P0).
 *
 * Construye una base [AppDatabase] in-memory con el runtime de Room (SQL
 * generado por KSP validado contra SQLite real) y ejercita los DAO directos:
 * UPSERT de la coleccion, decremento con piso en 0, fila unica de perfil,
 * historial ordenado y las restricciones de integridad referencial (FK y
 * borrado en cascada).
 *
 * Robolectric se fija a SDK 35 porque targetSdk (37) supera el maxSdkVersion
 * estable de Robolectric 4.16.1. Cada test usa una base aislada (in-memory)
 * que se cierra en [tearDown].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppDatabaseIntegrationTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ------------------------------------------------------------------
    // OwnedCardDao (coleccion de cartas)
    // ------------------------------------------------------------------

    @Test
    fun ownedCardDao_increment_insertaNuevaCon1() = runBlocking {
        db.ownedCardDao().increment("attack-0")
        assertEquals(1, db.ownedCardDao().countOf("attack-0"))
        val all = db.ownedCardDao().getAll()
        assertEquals(1, all.size)
        assertEquals(OwnedCardEntity("attack-0", 1), all.single())
    }

    @Test
    fun ownedCardDao_increment_acumulaConOnConflict() = runBlocking {
        repeat(3) { db.ownedCardDao().increment("attack-0") }
        assertEquals(3, db.ownedCardDao().countOf("attack-0"))
        // Una fila por carta, sin duplicados.
        assertEquals(1, db.ownedCardDao().getAll().size)
    }

    @Test
    fun ownedCardDao_decrement_respetaMayorQueCero_yDevuelveFilasAfectadas() = runBlocking {
        // Sin fila: 0 filas afectadas (no lanza, no crea).
        assertEquals(0, db.ownedCardDao().decrement("attack-0"))

        db.ownedCardDao().increment("attack-0")
        db.ownedCardDao().increment("attack-0")
        assertEquals(1, db.ownedCardDao().decrement("attack-0"))
        assertEquals(1, db.ownedCardDao().countOf("attack-0"))

        assertEquals(1, db.ownedCardDao().decrement("attack-0"))
        // En 0 la fila persiste pero el decremento ya no baja (WHERE owned_count > 0).
        assertEquals(0, db.ownedCardDao().countOf("attack-0"))
        assertEquals(0, db.ownedCardDao().decrement("attack-0"))
        assertEquals(0, db.ownedCardDao().countOf("attack-0"))
    }

    @Test
    fun ownedCardDao_delete_eliminaFila_hasta0NoPersiste() = runBlocking {
        db.ownedCardDao().increment("attack-0")
        db.ownedCardDao().increment("attack-0")
        db.ownedCardDao().delete("attack-0")
        assertNull(db.ownedCardDao().countOf("attack-0"))
        assertTrue(db.ownedCardDao().getAll().isEmpty())
    }

    @Test
    fun ownedCardDao_clear_vaciaLaTablaCompleta() = runBlocking {
        db.ownedCardDao().increment("attack-0")
        db.ownedCardDao().increment("attack-1")
        db.ownedCardDao().clear()
        assertTrue(db.ownedCardDao().getAll().isEmpty())
    }

    // ------------------------------------------------------------------
    // PlayerProfileDao (fila unica de perfil)
    // ------------------------------------------------------------------

    @Test
    fun playerProfileDao_upsert_reemplazaFilaUnica() = runBlocking {
        val dao = db.playerProfileDao()
        val pid = PlayerProfileEntity.DEFAULT_PLAYER_ID
        dao.upsert(PlayerProfileEntity(pid, 10, 111L))
        dao.upsert(PlayerProfileEntity(pid, 25, 222L))
        val row = dao.get(pid)
        assertEquals(25, row!!.xpTotal)
        assertEquals(222L, row.updatedAt)
    }

    @Test
    fun playerProfileDao_get_getXpTotal_nullCuandoNoExiste() = runBlocking {
        val pid = PlayerProfileEntity.DEFAULT_PLAYER_ID
        assertNull(db.playerProfileDao().get(pid))
        assertNull(db.playerProfileDao().getXpTotal(pid))
    }

    @Test
    fun playerProfileDao_get_devuelveXpTotalPersistido() = runBlocking {
        val pid = PlayerProfileEntity.DEFAULT_PLAYER_ID
        db.playerProfileDao().upsert(PlayerProfileEntity(pid, 42, 999L))
        assertEquals(42, db.playerProfileDao().getXpTotal(pid))
    }

    // ------------------------------------------------------------------
    // MatchHistoryDao (historial + integridad referencial)
    // ------------------------------------------------------------------

    @Test
    fun matchHistoryDao_recent_ordenaDescendentePorFechaYLimita() = runBlocking {
        val pid = PlayerProfileEntity.DEFAULT_PLAYER_ID
        db.playerProfileDao().upsert(PlayerProfileEntity(pid, 0, 1L))
        val dao = db.matchHistoryDao()
        dao.insert(MatchHistoryEntity("m1", pid, true, 5, 100L, "a"))
        dao.insert(MatchHistoryEntity("m2", pid, false, 2, 300L, "b"))
        dao.insert(MatchHistoryEntity("m3", pid, true, 5, 200L, "c"))

        // Mas nueva primero; limite 2.
        assertEquals(listOf("m2", "m3"), dao.recent(pid, 2).map { it.matchId })
        // Sin limite efectivo: las tres, ordenadas por played_at DESC.
        assertEquals(listOf("m2", "m3", "m1"), dao.recent(pid, 10).map { it.matchId })
    }

    @Test
    fun matchHistoryDao_insert_conProfileInexistente_violaFK() {
        // El player_id referencia player_profile (FK): insertar sin perfil
        // existente debe violar la restriccion (SQLiteConstraintException).
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking {
                db.matchHistoryDao().insert(
                    MatchHistoryEntity("m-fk", "no-existe", true, 5, 1L, null),
                )
            }
        }
    }

    @Test
    fun matchHistory_borrarProfile_cascadaBorrada() = runBlocking {
        val pid = PlayerProfileEntity.DEFAULT_PLAYER_ID
        db.playerProfileDao().upsert(PlayerProfileEntity(pid, 0, 1L))
        db.matchHistoryDao().insert(MatchHistoryEntity("m1", pid, true, 5, 100L, "a"))
        assertEquals(1, db.matchHistoryDao().recent(pid, 10).size)

        // Borrar el perfil elimina su historial en cascada (FK onDelete CASCADE).
        db.playerProfileDao().clear()
        assertTrue(db.matchHistoryDao().recent(pid, 10).isEmpty())
    }

    @Test
    fun matchHistoryDao_insert_repeticionReemplaza() = runBlocking {
        val pid = PlayerProfileEntity.DEFAULT_PLAYER_ID
        db.playerProfileDao().upsert(PlayerProfileEntity(pid, 0, 1L))
        db.matchHistoryDao().insert(MatchHistoryEntity("m1", pid, true, 5, 100L, "a"))
        // La misma match_id (PK) se reemplaza con el ultimo estado.
        db.matchHistoryDao().insert(MatchHistoryEntity("m1", pid, false, 2, 200L, "b"))
        val all = db.matchHistoryDao().recent(pid, 10)
        assertEquals(1, all.size)
        assertEquals(false, all.single().victory)
        assertEquals(2, all.single().xpEarned)
    }
}