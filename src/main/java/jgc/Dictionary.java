package jgc;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Reads a list of words, filters or converts non-ASCII characters and places most of them into a Trie.
 * Ignores proper nouns, or any word with a capital letter.  Most accented letters are converted into
 * their non-accented equivalents, such as éclair becomes eclair.  Any word with an accented consonants
 * such as cedillas (ç,) are dropped (e.g. français.)  With the exception of eñe, which is converted to
 * 'n.'  (Thank you El Niño...)
 * */

public class Dictionary {

    static Logger log = LogManager.getLogger(Dictionary.class);

    private static Dictionary dictionary;   // usually a singleton

    public enum DictSize { S, M, L, XL, XXL }

    private Trie trie;
    private DictSize dictSize;
    private Pattern nonASCIIPattern;

    public static synchronized Dictionary getDictionary(DictSize dictSize) {
        if (dictionary != null && dictionary.getSize() == dictSize) {
            return dictionary;
        }
        dictionary = new Dictionary(dictSize);
        return dictionary;
    }

    // constructor used only for solution dictionary
    public static synchronized Dictionary getDictionary(List<String> words) {
        return new Dictionary(words);
    }

    private Dictionary (List<String> all) {
        init(null, all);
    }

    private Dictionary (DictSize dictSize) {
        init(dictSize, null);
    }

    public DictSize getSize() {
        return dictSize;
    }

    // TEST ONLY
    protected int getTrieCount() {
        return trie.getCount();
    }

    /**
     * using locally installed dictionaries from wamerican-{small, large, huge, insane} packages.
     * @param size
     * @return
     */
    public static String getDictPath(DictSize size) {
        switch (size) {
            case S:
                return "/usr/share/dict/american-english-small";
            case L:
                return "/usr/share/dict/american-english-large";
            case XL:
                return "/usr/share/dict/american-english-huge";
            case XXL:
                return "/usr/share/dict/american-english-insane";
            case M:
            default:
                return "/usr/share/dict/american-english";
        }
    }

    /**
     * Reads the dictionary, discarding irregular words, and converting the rest into a single Trie.
     * For a 500,000 word dictionary (XXL), this takes about 200ms on an old laptop.
     * @param dictSize
     * @param all
     */
    private void init(DictSize dictSize, List<String> all) {
        long start = System.currentTimeMillis();
        this.dictSize = dictSize;
        this.nonASCIIPattern = Pattern.compile(".*[^0-9A-Za-z]+.*");
        if (all == null) {
            String dictPath = getDictPath(dictSize);
            all = readDictionary(dictPath);
            long end = System.currentTimeMillis();
            log.debug("time to read dictionary file: " + dictPath + ": " + (end - start) + " ms.");
        }
        else {
            log.debug("dictionary init with word list");
        }

        this.trie = new Trie();
        for (String s : all) {
            this.trie.insert(s);
        }

        long end = System.currentTimeMillis();
        log.debug("time to init dict: " + (end - start) + " ms.");
        log.debug("dict size: " + this.trie.getCount());
    }

    /**
     * Read all words from the given file and add them to the dictionary.
     * - accents are converted to plain ASCII, e.g. ångström becomes angstrom
     * - proper nouns are removed, e.g. Google is disallowed, but googol is ok
     * - ignore contractions, or any word with apostrophes, e.g. can't
     * - anything with a cedilla (ç), because what are they anyway?  Drop 'em.
     *
     * @param fname
     * @return
     */
    private List<String> readDictionary(String fname) {
        ArrayList<String> all = new ArrayList<String>();
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(fname))) {
            String word;
            while ((word = reader.readLine()) != null) {
                word = stripAccents(word);
                if (isUpperCased(word)) {
                    continue;   // skip proper nouns and mixed cased words, such as mHz
                }
                if (nonASCIIPattern.matcher(word).matches()) {
                    // useful to see how much of dictionary is being ignored (mostly apostrophes)
                    // log.debug("elided: " + word);
                    continue;   // skip contractions, foreign letters
                }
                if (word.contains("khz")) {
                    int ii = 0;
                }
                all.add(word);
            }
        } catch(IOException e){
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return all;
    }

    /**
     *
     * @param word
     * @return True if any of the characters are upper cased
     */
    private boolean isUpperCased(String word) {
        return !word.chars().allMatch(Character::isLowerCase);
    }

    /**
     * Strip accents from vowels, where possible.
     *
     * Many "American" words are loan words from foreign alphabets using accents characters.
     * These may sometimes be preserved when writing them in English.  For example, "éclair"
     * is usually written as "eclair" in English.  English may have its own accents, such as
     * diaereses, in the word coöp, or reëlect, to signify that the second vowel belongs to
     * a separate syllable.  Most accents are used to modify the vowel into something
     * language specific.  American English has about 12 basic vowels and Danish has 30+?
     * vowels.  There are a few languages which have only 2 (!)  This is one reason Turkey
     * abandoned the Arabic alphabet in favor of the Roman alphabet in 1928, in order to
     * better express vowels which don't exist in Arabic.  Turkish uses 3 modified consonants
     * (Ç, Ş, Ğ) and any words which have non-ASCII consonants will not be allowed.  For
     * example, "garçon" will not be allowed, if it is present in the dictionary file,
     * English or not.  An exception is is made for 'ñ' -> 'n' as it is so common in written
     * English.
     *
     * For Boggle, we just strip all accents for simplicity's sake.  Boggle also allows for
     * foreign dictionaries, but again, only ANSI a->z characters will be used.
     *
     * Without stripping, the Trie node would increase from 26 characters to perhaps 250, to
     * support much of lower-cased Unicode, which would add perhaps 400MB to memory
     * requirements.
     *
     * @param word
     * @return
     */
    private String stripAccents(String word) {
        char[] buf = word.toCharArray();
        boolean changed = false;
        for (int i = 0; i < buf.length; i++) {
            char c = buf[i];
            switch (c) {
                case '\'':
                    continue;   // skip contractions
                case 'å':
                case 'ä':
                case 'à':
                case 'á':
                case 'â':
                case 'ã':
                    buf[i] = 'a';
                    changed = true;
                    break;
                case 'ë':
                case 'è':
                case 'é':
                case 'ê':
                    buf[i] = 'e';
                    changed = true;
                    break;
                case 'ï':
                case 'ì':
                case 'í':
                case 'î':
                    buf[i] = 'i';
                    changed = true;
                    break;
                case 'ñ':
                    buf[i] = 'n';
                    changed = true;
                    break;
                case 'ö':
                case 'ø':
                case 'ò':
                case 'ó':
                case 'ô':
                case 'õ':
                    buf[i] = 'o';
                    changed = true;
                    break;
                case 'ü':
                case 'ú':
                case 'ù':
                case 'û':
                    buf[i] = 'u';
                    changed = true;
                    break;
            }
        }
        if (changed) {
            word = new String(buf);
        }
        return word;
    }

    /**
     * Returns a Trie node matching the given string.  This is used to determine whether the string matches a
     * partial word, or is a valid word unto itself.
     *
     * @param word
     * @return
     */
    public TrieNode findWordTree(String word) {
        return findWordTree(null, (char)0, word);
    }
    public TrieNode findWordTree(TrieNode tnode, char c) {
        return findWordTree(tnode, c, null);
    }

    /**
     *
     * Returns a Trie node which branches off the given Trie with a given character.  Used to traverse a Trie with a
     * set of restricted characters, such as when searching for solutions on a Boggle board.  For example, valid
     * characters following 'cat' are: [abcefghijklmnorstw].  Hence findWordTree({cat}, 'd') will return null.
     *
     * @param tnode - current trie node
     * @param c - character to traverse
     * @param word - optional word to traverse
     * @return
     */
    private TrieNode findWordTree(TrieNode tnode, char c, String word) {
        char[] a = (word != null) ? word.toCharArray() : null;
        tnode = trie.findNode(tnode, a, c);
        if (tnode == null) {
            return null;
        }
        return tnode;
    }

    protected String[] getWords() {
        return trie.extractWords();
    }
}
