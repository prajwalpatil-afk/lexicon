package com.vocab;

import com.vocab.cli.CommandParser;
import com.vocab.db.DatabaseService;
import com.vocab.service.LemmatizerService;
import com.vocab.service.SimilarityService;
import com.vocab.service.VocabularyService;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Entry point for the Vocab CLI application.
 */
public class Main {

    public static void main(String[] args) {
        // Force UTF-8 output for Windows console
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));

        printBanner();

        // 1. Database
        DatabaseService db = new DatabaseService();

        // 2. NLP services
        LemmatizerService lemmatizer = new LemmatizerService();
        SimilarityService similarity = new SimilarityService();

        // 3. Business logic
        VocabularyService vocab = new VocabularyService(db, lemmatizer, similarity);

        // 4. Start interactive loop
        CommandParser parser = new CommandParser(vocab);
        parser.run();
    }

    private static void printBanner() {
        System.out.println();
        System.out.println("  +-----------------------------+");
        System.out.println("  |   Vocab CLI  -  ready       |");
        System.out.println("  +-----------------------------+");
        System.out.println("  Type 'help' for commands.");
    }
}
