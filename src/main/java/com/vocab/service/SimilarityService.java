package com.vocab.service;

import net.didion.jwnl.JWNL;
import net.didion.jwnl.JWNLException;
import net.didion.jwnl.data.*;
import net.didion.jwnl.dictionary.Dictionary;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Synonym lookup service using JWNL (Java WordNet Library).
 * Loads WordNet dictionary data from data/wordnet/.
 */
public class SimilarityService {

    private static final Path WORDNET_DIR = Path.of("data", "wordnet");
    private static final int MAX_SYNONYMS = 8;

    private Dictionary dictionary;
    private boolean available;

    public SimilarityService() {
        available = false;
        loadWordNet();
    }

    private void loadWordNet() {
        if (!Files.isDirectory(WORDNET_DIR)) {
            printWarning("WordNet directory not found at " + WORDNET_DIR);
            return;
        }

        // Generate JWNL properties XML pointing to our data dir
        try {
            // Suppress JWNL's internal java.util.logging output
            java.util.logging.Logger.getLogger("net.didion.jwnl").setLevel(java.util.logging.Level.OFF);

            String propsXml = buildPropertiesXml(WORDNET_DIR.toAbsolutePath().toString());
            JWNL.initialize(new ByteArrayInputStream(propsXml.getBytes()));
            dictionary = Dictionary.getInstance();
            available = true;
        } catch (Exception e) {
            // Print the full cause chain to diagnose
            StringBuilder msg = new StringBuilder("Failed to initialize WordNet: " + e.getMessage());
            Throwable cause = e.getCause();
            while (cause != null) {
                msg.append("\n  Caused by: ").append(cause.getClass().getSimpleName())
                   .append(": ").append(cause.getMessage());
                cause = cause.getCause();
            }
            printWarning(msg.toString());
        }
    }

    /**
     * Look up synonyms for a lemma across NOUN, VERB, and ADJECTIVE POS.
     * Returns up to 8 unique synonyms (excluding the input word).
     */
    public Set<String> getSynonyms(String lemma) {
        Set<String> synonyms = new LinkedHashSet<>();
        if (!available || dictionary == null) {
            return synonyms;
        }

        POS[] partsOfSpeech = { POS.NOUN, POS.VERB, POS.ADJECTIVE };

        try {
            for (POS pos : partsOfSpeech) {
                IndexWord indexWord = dictionary.lookupIndexWord(pos, lemma);
                if (indexWord == null) continue;

                for (Synset synset : indexWord.getSenses()) {
                    for (Word word : synset.getWords()) {
                        String synonym = word.getLemma().replace('_', ' ').toLowerCase();
                        if (!synonym.equalsIgnoreCase(lemma) && !synonyms.contains(synonym)) {
                            synonyms.add(synonym);
                            if (synonyms.size() >= MAX_SYNONYMS) {
                                return synonyms;
                            }
                        }
                    }
                }
            }
        } catch (JWNLException e) {
            System.err.println("  [!] Synonym lookup error: " + e.getMessage());
        }
        return synonyms;
    }

    public boolean isAvailable() {
        return available;
    }

    private String buildPropertiesXml(String dictPath) {
        // Normalize Windows backslashes to forward slashes for XML
        String normalizedPath = dictPath.replace('\\', '/');
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
             + "<jwnl_properties language=\"en\">"
             + "  <version publisher=\"Princeton\" number=\"3.0\"/>"
             + "  <dictionary class=\"net.didion.jwnl.dictionary.FileBackedDictionary\">"
             + "    <param name=\"morphological_processor\""
             + "           value=\"net.didion.jwnl.dictionary.morph.DefaultMorphologicalProcessor\">"
             + "      <param name=\"suffix_map\""
             + "             value=\"net.didion.jwnl.dictionary.morph.SuffixMap\"/>"
             + "      <param name=\"operations\">"
             + "        <param value=\"net.didion.jwnl.dictionary.morph.LookupExceptionsOperation\"/>"
             + "        <param value=\"net.didion.jwnl.dictionary.morph.DetachSuffixesOperation\">"
             + "          <param name=\"noun\" value=\"|s=|ses=s|xes=x|zes=z|ches=ch|shes=sh|men=man|ies=y|\"/>"
             + "          <param name=\"verb\" value=\"|s=|ies=y|es=e|es=|ed=e|ed=|ing=e|ing=|\"/>"
             + "          <param name=\"adjective\" value=\"|er=|est=|er=e|est=e|\"/>"
             + "        </param>"
             + "        <param value=\"net.didion.jwnl.dictionary.morph.LookupIndexWordOperation\"/>"
             + "      </param>"
             + "    </param>"
             + "    <param name=\"dictionary_element_factory\""
             + "           value=\"net.didion.jwnl.princeton.data.PrincetonWN17FileDictionaryElementFactory\"/>"
             + "    <param name=\"file_manager\""
             + "           value=\"net.didion.jwnl.dictionary.file_manager.FileManagerImpl\">"
             + "      <param name=\"file_type\""
             + "             value=\"net.didion.jwnl.princeton.file.PrincetonRandomAccessDictionaryFile\"/>"
             + "      <param name=\"dictionary_path\" value=\"" + normalizedPath + "\"/>"
             + "    </param>"
             + "  </dictionary>"
             + "  <resource class=\"net.didion.jwnl.princeton.PrincetonResource\"/>"
             + "</jwnl_properties>";
    }

    private void printWarning(String reason) {
        System.out.println();
        System.out.println("  [!] WordNet Setup Required");
        System.out.println("  ---------------------------------------------");
        System.out.println("  " + reason);
        System.out.println();
        System.out.println("  Download WordNet 3.1 and place the dict/ contents in data/wordnet/");
        System.out.println("  https://wordnet.princeton.edu/download/current-version");
        System.out.println();
        System.out.println("  [!] Running without synonym support.");
        System.out.println();
    }
}
