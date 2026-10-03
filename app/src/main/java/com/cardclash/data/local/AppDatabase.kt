package com.cardclash.data.local

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.cardclash.data.local.dao.DeckDao
import com.cardclash.data.local.dao.MatchHistoryDao
import com.cardclash.data.local.dao.OwnedCardDao
import com.cardclash.data.local.dao.PlayerProfileDao
import com.cardclash.data.local.entity.DeckCardEntity
import com.cardclash.data.local.entity.DeckEntity
import com.cardclash.data.local.entity.MatchHistoryEntity
import com.cardclash.data.local.entity.OwnedCardEntity
import com.cardclash.data.local.entity.PlayerProfileEntity
import com.cardclash.data.local.migration.DeckMigration

/**
 * Base de datos Room de CardClash (persistencia local, Etapa 5).
 *
 * ## Esquema (version 2)
 * - [OwnedCardEntity] (`owned_cards`): coleccion de cartas (CardId + copias).
 * - [PlayerProfileEntity] (`player_profile`): XP total acumulado, fila unica.
 * - [MatchHistoryEntity] (`match_history`): historial de partidas (con FK a
 *   player_profile y indices en player_id / played_at).
 * - [DeckEntity] (`decks`): mazos del jugador (nombre, activo, timestamps).
 * - [DeckCardEntity] (`deck_cards`): cartas en cada mazo (slot 0-8, FK a decks).
 *
 * ## Migraciones
 * v1 -> v2: [DeckMigration.MIGRATION_1_2] anade tablas de mazos preservando
 * datos existentes (player_profile, owned_cards, match_history).
 *
 * ## Configuracion
 * Singleton thread-safe ([getInstance]); el esquema se exporta a
 * `app/schemas/com.cardclash.data.local.AppDatabase/2.json` (validacion de
 * migraciones futuras).
 *
 * En builds de DEBUG se habilita fallbackToDestructiveMigration() para
 * desarrollo rapido; en RELEASE se usa la migracion manual.
 */
@Database(
    entities = [
        OwnedCardEntity::class,
        PlayerProfileEntity::class,
        MatchHistoryEntity::class,
        DeckEntity::class,
        DeckCardEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun ownedCardDao(): OwnedCardDao

    abstract fun playerProfileDao(): PlayerProfileDao

    abstract fun matchHistoryDao(): MatchHistoryDao

    abstract fun deckDao(): DeckDao

    companion object {
        /** Nombre del archivo SQLite local. */
        const val DATABASE_NAME = "cardclash.db"

        @Volatile
        private var instance: AppDatabase? = null

        /**
         * Singleton thread-safe (double-checked locking) con contexto de
         * aplicacion (evita leaks de Activity/Context).
         *
         * En DEBUG: fallbackToDestructiveMigration() para iteracion rapida.
         * En RELEASE: migracion manual v1->v2 preservando datos.
         */
        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME,
                ).apply {
                    val isDebug = (context.applicationContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
                    if (isDebug) {
                        fallbackToDestructiveMigration()
                    } else {
                        addMigrations(DeckMigration.MIGRATION_1_2)
                    }
                }.build().also { instance = it }
            }

        /**
         * Cierra y libera la instancia. Reservado para testing/instrumentados o
         * limpieza manual; NO llamar en flujos normales de la aplicacion.
         */
        fun closeInstance() {
            synchronized(this) {
                instance?.close()
                instance = null
            }
        }
    }
}