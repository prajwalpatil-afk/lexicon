package com.vocab.cli;

import com.vocab.db.WordEntry;
import com.vocab.service.VocabularyService;
import com.vocab.service.VocabularyService.AddResult;

import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;

/**
 * Interactive command-line parser. Reads from stdin in a loop.
 */
public class CommandParser {

    private final VocabularyService vocab;
    private final Scanner scanner;

    public CommandParser(VocabularyService vocab) {
        this.vocab = vocab;
        this.scanner = new Scanner(System.in);
    }

    public void run() {
        while (true) {
            System.out.print("\n  vocab> ");
            if (!scanner.hasNextLine()) break;

            String line = scanner.nextLine().trim();
            if (line.isEmpty()) continue;

            String[] parts = line.split("\\s+", 2);
            String command = parts[0].toLowerCase();
            String arg = parts.length > 1 ? parts[1].trim() : "";

            switch (command) {
                case "add"    -> handleAdd(arg);
                case "show"   -> handleShow(arg);
                case "search" -> handleSearch(arg);
                case "remove" -> handleRemove(arg);
                case "top"    -> handleTop(arg);
                case "stats"  -> handleStats();
                case "help"   -> handleHelp();
                case "exit", "quit" -> {
                    System.out.println("\n  Goodbye! Happy learning.\n");
                    return;
                }
                default -> System.out.println("\n  Unknown command. Type 'help' for options.");
            }
        }
    }

    // -- Command Handlers ---------------------------------------------------

    private void handleAdd(String arg) {
        if (arg.isEmpty()) {
            System.out.println("\n  Usage: add <word>");
            return;
        }
        AddResult result = vocab.add(arg);

        switch (result) {
            case AddResult.Added added -> {
                WordEntry e = added.entry();
                System.out.println();
                System.out.println("  Word    : " + e.getWord());
                System.out.println("  Lemma   : " + e.getLemma());
                printSimilar(e.getSimilarWords());
                System.out.println("\n  [OK] Saved.");
            }
            case AddResult.AlreadyExists exists -> {
                WordEntry e = exists.entry();
                System.out.println();
                System.out.println("  Word  : " + arg);
                System.out.println("  Lemma : " + e.getLemma());
                System.out.printf("\n  [!] Already exists (%s, seen %dx). Count updated -> %dx.%n",
                        e.getLemma(), exists.newCount() - 1, exists.newCount());
            }
        }
    }

    private void handleShow(String arg) {
        if (arg.isEmpty()) {
            System.out.println("\n  Usage: show <word>");
            return;
        }
        Optional<WordEntry> opt = vocab.show(arg);
        if (opt.isEmpty()) {
            System.out.println("\n  Word not found.");
            return;
        }
        WordEntry e = opt.get();
        String simStr = e.getSimilarWords().isEmpty() ? "(none)"
                : String.join(", ", e.getSimilarWords());

        // Build box
        int boxWidth = 35;
        String border  = "-".repeat(boxWidth);
        String padded  = padRight(e.getLemma(), boxWidth - 2);

        System.out.println();
        System.out.println("  +" + border + "+");
        System.out.println("  | " + padded + " |");
        System.out.println("  +" + border + "+");
        System.out.printf("  | Encountered : %-" + (boxWidth - 17) + "s |%n",
                e.getEncounterCount() + "x");
        // Similar words - may wrap
        printBoxField("Similar", simStr, boxWidth);
        System.out.println("  +" + border + "+");
    }

    private void handleSearch(String arg) {
        if (arg.isEmpty()) {
            System.out.println("\n  Usage: search <prefix>");
            return;
        }
        List<WordEntry> results = vocab.search(arg);
        if (results.isEmpty()) {
            System.out.println("\n  No matches found.");
            return;
        }
        System.out.println("\n  Matches:");
        for (WordEntry e : results) {
            System.out.printf("    - %-20s x%d%n", e.getLemma(), e.getEncounterCount());
        }
    }

    private void handleRemove(String arg) {
        if (arg.isEmpty()) {
            System.out.println("\n  Usage: remove <word>");
            return;
        }
        vocab.remove(arg);
        System.out.println("\n  [OK] Removed.");
    }

    private void handleTop(String arg) {
        int n = 10;
        if (!arg.isEmpty()) {
            try { n = Integer.parseInt(arg); }
            catch (NumberFormatException e) {
                System.out.println("\n  Usage: top [n]  (n must be a number)");
                return;
            }
        }
        List<WordEntry> top = vocab.top(n);
        if (top.isEmpty()) {
            System.out.println("\n  No words stored yet.");
            return;
        }
        System.out.println("\n  Most encountered words:");
        for (int i = 0; i < top.size(); i++) {
            WordEntry e = top.get(i);
            System.out.printf("  %2d.  %-18s x%d%n", i + 1, e.getLemma(), e.getEncounterCount());
        }
    }

    private void handleStats() {
        VocabularyService.Stats stats = vocab.getStats();
        System.out.println();
        System.out.printf("  Total entries   : %d%n", stats.totalEntries());
        if (stats.totalEntries() > 0) {
            System.out.printf("  Top word        : %s (x%d)%n", stats.topWord(), stats.topCount());
        }
        System.out.println("  DB location     : ./vocab.db");
    }

    private void handleHelp() {
        System.out.println("""
        
          Commands:
          -------------------------------------
          add <word>      Lemmatize and store a word
          show <word>     Display lemma, synonyms, count
          search <prefix> List matching lemmas
          remove <word>   Delete by lemma
          top [n]         Top n words by encounter count
          stats           Total entries, top word
          help            Show this message
          exit / quit     Exit the program
        """);
    }

    // -- Formatting Helpers -------------------------------------------------

    private void printSimilar(Set<String> words) {
        if (words == null || words.isEmpty()) {
            System.out.println("  Similar : (none)");
        } else {
            System.out.println("  Similar : " + String.join(", ", words));
        }
    }

    private void printBoxField(String label, String value, int boxWidth) {
        String prefix = label + "     : ";
        int contentWidth = boxWidth - 4 - prefix.length();

        if (value.length() <= contentWidth) {
            System.out.printf("  | %s%-" + contentWidth + "s |%n", prefix, value);
        } else {
            // Wrap
            String[] words = value.split(", ");
            StringBuilder line = new StringBuilder();
            boolean first = true;
            for (String w : words) {
                String candidate = line.isEmpty() ? w : line + ", " + w;
                if (candidate.length() > contentWidth && !line.isEmpty()) {
                    if (first) {
                        System.out.printf("  | %s%-" + contentWidth + "s |%n", prefix, line);
                        first = false;
                    } else {
                        System.out.printf("  | %s%-" + contentWidth + "s |%n",
                                " ".repeat(prefix.length()), line);
                    }
                    line = new StringBuilder(w);
                } else {
                    line = new StringBuilder(candidate);
                }
            }
            if (!line.isEmpty()) {
                if (first) {
                    System.out.printf("  | %s%-" + contentWidth + "s |%n", prefix, line);
                } else {
                    System.out.printf("  | %s%-" + contentWidth + "s |%n",
                            " ".repeat(prefix.length()), line);
                }
            }
        }
    }

    private String padRight(String s, int width) {
        if (s.length() >= width) return s.substring(0, width);
        return s + " ".repeat(width - s.length());
    }
}
