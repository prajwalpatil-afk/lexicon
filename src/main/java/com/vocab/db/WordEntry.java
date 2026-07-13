package com.vocab.db;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Plain Java class representing a vocabulary word entry.
 * No ORM – mapped manually from JDBC ResultSet.
 */
public class WordEntry {

    private Long id;
    private String word;            // original word as entered
    private String lemma;           // lemmatized form
    private int encounterCount;
    private Set<String> similarWords;

    public WordEntry() {
        this.similarWords = new LinkedHashSet<>();
        this.encounterCount = 1;
    }

    public WordEntry(String word, String lemma) {
        this();
        this.word = word;
        this.lemma = lemma;
    }

    // ── Getters & Setters ──────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getWord() { return word; }
    public void setWord(String word) { this.word = word; }

    public String getLemma() { return lemma; }
    public void setLemma(String lemma) { this.lemma = lemma; }

    public int getEncounterCount() { return encounterCount; }
    public void setEncounterCount(int encounterCount) { this.encounterCount = encounterCount; }

    public Set<String> getSimilarWords() { return similarWords; }
    public void setSimilarWords(Set<String> similarWords) { this.similarWords = similarWords; }

    @Override
    public String toString() {
        return "WordEntry{lemma='" + lemma + "', count=" + encounterCount + "}";
    }
}
