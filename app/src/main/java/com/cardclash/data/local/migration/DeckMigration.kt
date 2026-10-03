package com.cardclash.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migracion Room v1 -> v2: anade tablas de mazos.
 *
 * Crea:
 * - `decks`: tabla de mazos con FK a player_profile, indice unico (name, player_id).
 * - `deck_cards`: tabla de cartas de mazo con PK compuesta (deck_id, slot), FK a decks con ON DELETE CASCADE.
 *
 * ## Estrategia de migracion
 * En debug: `fallbackToDestructiveMigration()` (reinicia esquema, datos de prueba).
 * En release: esta migracion manual preservando datos existentes de player_profile, owned_cards, match_history.
 */
object DeckMigration {

    /** Migracion v1 -> v2: anade tablas de mazos sin tocar datos existentes. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Tabla decks
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS decks (
                    deck_id TEXT NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    is_active INTEGER NOT NULL DEFAULT 0,
                    player_id TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    FOREIGN KEY (player_id) REFERENCES player_profile (player_id) ON DELETE CASCADE ON UPDATE CASCADE
                )
            """.trimIndent())

            // Indice unico: un nombre de mazo por jugador
            database.execSQL("""
                CREATE UNIQUE INDEX IF NOT EXISTS idx_decks_name_player
                ON decks (name, player_id)
            """.trimIndent())

            // Indice para consultas por jugador
            database.execSQL("""
                CREATE INDEX IF NOT EXISTS idx_decks_player_id
                ON decks (player_id)
            """.trimIndent())

            // Tabla deck_cards
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS deck_cards (
                    deck_id TEXT NOT NULL,
                    slot INTEGER NOT NULL,
                    card_id TEXT NOT NULL,
                    PRIMARY KEY (deck_id, slot),
                    FOREIGN KEY (deck_id) REFERENCES decks (deck_id) ON DELETE CASCADE ON UPDATE CASCADE
                )
            """.trimIndent())

            // Indice para consultas por deck_id (aunque PK ya lo cubre, explicito para claridad)
            database.execSQL("""
                CREATE INDEX IF NOT EXISTS idx_deck_cards_deck_id
                ON deck_cards (deck_id)
            """.trimIndent())
        }
    }
}