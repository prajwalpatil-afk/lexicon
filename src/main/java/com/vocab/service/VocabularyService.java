package com.vocab.service;

import com.vocab.db.DatabaseService;
import com.vocab.db.WordEntry;

import java.util.*;

/**
 * Orchestrates lemmatization, synonym lookup, and persistence.
 */
public class VocabularyService {

    private final DatabaseService db;
    private final LemmatizerService lemmatizer;
    private final SimilarityService similarity;

    public VocabularyService(DatabaseService db, LemmatizerService lemmatizer, SimilarityService similarity) {
        this.db = db;
        this.lemmatizer = lemmatizer;
        this.similarity = similarity;
    }

    public sealed interface AddResult permits AddResult.Added, AddResult.AlreadyExists {
        record Added(WordEntry entry) implements AddResult {}
        record AlreadyExists(WordEntry entry, int newCount) implements AddResult {}
    }

    public AddResult add(String rawWord) {
        String lemma = lemmatizer.lemmatize(rawWord);
        Optional<WordEntry> existing = db.findByLemma(lemma);
        if (existing.isPresent()) {
            db.incrementCount(lemma);
            WordEntry entry = existing.get();
            int newCount = entry.getEncounterCount() + 1;
            entry.setEncounterCount(newCount);
            return new AddResult.AlreadyExists(entry, newCount);
        }
        Set<String> synonyms = similarity.getSynonyms(lemma);
        WordEntry entry = new WordEntry(rawWord, lemma);
        entry.setSimilarWords(synonyms);
        db.save(entry);
        return new AddResult.Added(entry);
    }

    public Optional<WordEntry> show(String word) {
        String lemma = lemmatizer.lemmatize(word);
        return db.findByLemma(lemma);
    }

    public List<WordEntry> search(String prefix) {
        return db.searchByPrefix(prefix.toLowerCase());
    }

    public List<WordEntry> top(int n) {
        return db.getTopN(n);
    }

    public void remove(String word) {
        String lemma = lemmatizer.lemmatize(word);
        db.delete(lemma);
    }

    public Stats getStats() {
        int total = db.getTotalCount();
        List<WordEntry> topList = db.getTopN(1);
        String topWord = topList.isEmpty() ? "—" : topList.get(0).getLemma();
        int topCount = topList.isEmpty() ? 0 : topList.get(0).getEncounterCount();
        return new Stats(total, topWord, topCount);
    }

    public record Stats(int totalEntries, String topWord, int topCount) {}
}
