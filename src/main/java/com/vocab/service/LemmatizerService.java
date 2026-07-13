package com.vocab.service;

import opennlp.tools.lemmatizer.LemmatizerME;
import opennlp.tools.lemmatizer.LemmatizerModel;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Lemmatization service using Apache OpenNLP.
 * Loads POS tagger and lemmatizer models from data/opennlp/.
 */
public class LemmatizerService {

    private static final Path POS_MODEL_PATH = Path.of("data", "opennlp", "en-pos-maxent.bin");
    private static final Path LEMMA_MODEL_PATH = Path.of("data", "opennlp", "en-lemmatizer.bin");

    private POSTaggerME posTagger;
    private LemmatizerME lemmatizer;
    private boolean available;

    public LemmatizerService() {
        available = false;
        loadModels();
    }

    private void loadModels() {
        if (!Files.exists(POS_MODEL_PATH)) {
            printSetupInstructions("POS model not found at " + POS_MODEL_PATH);
            return;
        }
        if (!Files.exists(LEMMA_MODEL_PATH)) {
            printSetupInstructions("Lemmatizer model not found at " + LEMMA_MODEL_PATH);
            return;
        }

        try {
            try (InputStream posIn = new FileInputStream(POS_MODEL_PATH.toFile())) {
                POSModel posModel = new POSModel(posIn);
                posTagger = new POSTaggerME(posModel);
            }
            try (InputStream lemmaIn = new FileInputStream(LEMMA_MODEL_PATH.toFile())) {
                LemmatizerModel lemmaModel = new LemmatizerModel(lemmaIn);
                lemmatizer = new LemmatizerME(lemmaModel);
            }
            available = true;
        } catch (IOException e) {
            printSetupInstructions("Failed to load OpenNLP models: " + e.getMessage());
        }
    }

    /**
     * Lemmatize a single word. Returns the lemma, or the word itself if
     * models are unavailable.
     */
    public String lemmatize(String word) {
        if (!available) {
            return word.toLowerCase();
        }

        String[] tokens = { word.toLowerCase() };
        String[] tags = posTagger.tag(tokens);
        String[] lemmas = lemmatizer.lemmatize(tokens, tags);

        // OpenNLP returns "O" when it can't determine the lemma
        String result = lemmas[0];
        if (result == null || result.equals("O")) {
            return word.toLowerCase();
        }
        return result.toLowerCase();
    }

    public boolean isAvailable() {
        return available;
    }

    private void printSetupInstructions(String reason) {
        System.out.println();
        System.out.println("  [!] OpenNLP Setup Required");
        System.out.println("  ---------------------------------------------");
        System.out.println("  " + reason);
        System.out.println();
        System.out.println("  Please download the following models:");
        System.out.println("    1. en-pos-maxent.bin   -> place in data/opennlp/");
        System.out.println("    2. en-lemmatizer.bin   -> place in data/opennlp/");
        System.out.println();
        System.out.println("  Download from:");
        System.out.println("    https://opennlp.apache.org/models.html");
        System.out.println();
        System.out.println("  [!] Running in fallback mode (no lemmatization).");
        System.out.println();
    }
}
