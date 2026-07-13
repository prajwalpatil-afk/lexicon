package com.vocab.db;

import java.sql.*;
import java.util.*;

/**
 * Raw JDBC database service for SQLite.
 * Creates and manages the vocab.db file.
 */
public class DatabaseService {

    private static final String DB_URL = "jdbc:sqlite:vocab.db";

    public DatabaseService() {
        initSchema();
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    // ── Schema ─────────────────────────────────────────────────

    public void initSchema() {
        String wordsTable = """
            CREATE TABLE IF NOT EXISTS words (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                word TEXT NOT NULL,
                lemma TEXT NOT NULL UNIQUE,
                encounter_count INTEGER DEFAULT 1
            )
            """;
        String similarTable = """
            CREATE TABLE IF NOT EXISTS similar_words (
                word_id INTEGER REFERENCES words(id),
                similar_word TEXT NOT NULL
            )
            """;
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            stmt.execute(wordsTable);
            stmt.execute(similarTable);
        } catch (SQLException e) {
            System.err.println("  ✗ Failed to initialize database: " + e.getMessage());
        }
    }

    // ── Find ───────────────────────────────────────────────────

    public Optional<WordEntry> findByLemma(String lemma) {
        String sql = "SELECT id, word, lemma, encounter_count FROM words WHERE lemma = ?";
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, lemma);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                WordEntry entry = mapRow(rs);
                entry.setSimilarWords(loadSimilarWords(conn, entry.getId()));
                return Optional.of(entry);
            }
        } catch (SQLException e) {
            System.err.println("  ✗ DB lookup error: " + e.getMessage());
        }
        return Optional.empty();
    }

    // ── Save ───────────────────────────────────────────────────

    public void save(WordEntry entry) {
        String insertWord = "INSERT INTO words (word, lemma, encounter_count) VALUES (?, ?, ?)";
        String insertSimilar = "INSERT INTO similar_words (word_id, similar_word) VALUES (?, ?)";

        try (Connection conn = connect()) {
            conn.setAutoCommit(false);

            try (PreparedStatement ps = conn.prepareStatement(insertWord, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, entry.getWord());
                ps.setString(2, entry.getLemma());
                ps.setInt(3, entry.getEncounterCount());
                ps.executeUpdate();

                ResultSet keys = ps.getGeneratedKeys();
                if (keys.next()) {
                    long id = keys.getLong(1);
                    entry.setId(id);

                    for (String sim : entry.getSimilarWords()) {
                        try (PreparedStatement ps2 = conn.prepareStatement(insertSimilar)) {
                            ps2.setLong(1, id);
                            ps2.setString(2, sim);
                            ps2.executeUpdate();
                        }
                    }
                }
            }
            conn.commit();
        } catch (SQLException e) {
            System.err.println("  ✗ Failed to save word: " + e.getMessage());
        }
    }

    // ── Increment ──────────────────────────────────────────────

    public void incrementCount(String lemma) {
        String sql = "UPDATE words SET encounter_count = encounter_count + 1 WHERE lemma = ?";
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, lemma);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("  ✗ Failed to update count: " + e.getMessage());
        }
    }

    // ── Get All ────────────────────────────────────────────────

    public List<WordEntry> getAll() {
        String sql = "SELECT id, word, lemma, encounter_count FROM words ORDER BY lemma";
        List<WordEntry> entries = new ArrayList<>();
        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                WordEntry entry = mapRow(rs);
                entry.setSimilarWords(loadSimilarWords(conn, entry.getId()));
                entries.add(entry);
            }
        } catch (SQLException e) {
            System.err.println("  ✗ Failed to fetch words: " + e.getMessage());
        }
        return entries;
    }

    // ── Top N ──────────────────────────────────────────────────

    public List<WordEntry> getTopN(int n) {
        String sql = "SELECT id, word, lemma, encounter_count FROM words ORDER BY encounter_count DESC LIMIT ?";
        List<WordEntry> entries = new ArrayList<>();
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, n);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                WordEntry entry = mapRow(rs);
                entry.setSimilarWords(loadSimilarWords(conn, entry.getId()));
                entries.add(entry);
            }
        } catch (SQLException e) {
            System.err.println("  ✗ Failed to fetch top words: " + e.getMessage());
        }
        return entries;
    }

    // ── Search by prefix ───────────────────────────────────────

    public List<WordEntry> searchByPrefix(String prefix) {
        String sql = "SELECT id, word, lemma, encounter_count FROM words WHERE lemma LIKE ? ORDER BY lemma";
        List<WordEntry> entries = new ArrayList<>();
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, prefix + "%");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                WordEntry entry = mapRow(rs);
                entry.setSimilarWords(loadSimilarWords(conn, entry.getId()));
                entries.add(entry);
            }
        } catch (SQLException e) {
            System.err.println("  ✗ Search failed: " + e.getMessage());
        }
        return entries;
    }

    // ── Delete ─────────────────────────────────────────────────

    public void delete(String lemma) {
        String findId = "SELECT id FROM words WHERE lemma = ?";
        String delSimilar = "DELETE FROM similar_words WHERE word_id = ?";
        String delWord = "DELETE FROM words WHERE id = ?";

        try (Connection conn = connect()) {
            conn.setAutoCommit(false);

            long id = -1;
            try (PreparedStatement ps = conn.prepareStatement(findId)) {
                ps.setString(1, lemma);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    id = rs.getLong("id");
                }
            }

            if (id > 0) {
                try (PreparedStatement ps = conn.prepareStatement(delSimilar)) {
                    ps.setLong(1, id);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(delWord)) {
                    ps.setLong(1, id);
                    ps.executeUpdate();
                }
            }
            conn.commit();
        } catch (SQLException e) {
            System.err.println("  ✗ Failed to delete word: " + e.getMessage());
        }
    }

    // ── Stats helpers ──────────────────────────────────────────

    public int getTotalCount() {
        String sql = "SELECT COUNT(*) FROM words";
        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            System.err.println("  ✗ Count query failed: " + e.getMessage());
        }
        return 0;
    }

    // ── Private helpers ────────────────────────────────────────

    private WordEntry mapRow(ResultSet rs) throws SQLException {
        WordEntry entry = new WordEntry();
        entry.setId(rs.getLong("id"));
        entry.setWord(rs.getString("word"));
        entry.setLemma(rs.getString("lemma"));
        entry.setEncounterCount(rs.getInt("encounter_count"));
        return entry;
    }

    private Set<String> loadSimilarWords(Connection conn, long wordId) throws SQLException {
        String sql = "SELECT similar_word FROM similar_words WHERE word_id = ?";
        Set<String> words = new LinkedHashSet<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, wordId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                words.add(rs.getString("similar_word"));
            }
        }
        return words;
    }
}
