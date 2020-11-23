package jgc;

import java.util.Set;
import java.util.TreeSet;

/**
 * Trie for words in a simple 'a' -> 'z' lowercase ASCII character set only.
 * Any other characters will fail.  No error checking - this is coded for performance.
 */
public class Trie {

    public static char FIRST_CHAR = 'a';
    public static char LAST_CHAR = 'z';

    private TrieNode root = new TrieNode();
    private int count = 0;  // number of words (leaf nodes)

    public void insert(String str) {
        insert(str.toCharArray());
    }

    private void insert(char[] a) {
        TrieNode[] map = root.map;

        TrieNode t = null;
        for(int i = 0; i < a.length; i++) {
            char c = a[i];
            t = map[c-FIRST_CHAR];
            if (t == null) {        // new word
                t = new TrieNode();
                map[c - FIRST_CHAR] = t;
            }
            map = t.map;
        }
        t.setEnd(true);   // mark last node as a whole word
        t.setWord(new String(a));
        this.count++;
    }

    public int getCount() {
        return this.count;
    }

    public TrieNode findNode(String str){
        return findNode(root, str.toCharArray(), (char) 0);
    }

    /**
     *
     * @param start root node
     * @param a string to look for
     * @param b optimization to eliminate too many objects being created
     * @return
     */
    public TrieNode findNode(TrieNode start, char[] a, char b) {
        if (start == null) {
            start = root;
        }
        TrieNode t = start;
        if (a == null && t != null) {
            TrieNode[] map = t.map;
            t = map[b-FIRST_CHAR];
        }
        else {
            for (int i = 0; i < a.length && t != null; i++) {
                TrieNode[] map = t.map;
                char c = a[i];
                t = map[c - FIRST_CHAR];
            }
        }
        return t;       // doesn't have to be a leaf node
    }

    // TEST ONLY - expensive way to get all words
    // note that this produces an alphabetically sorted list
    protected String[] extractWords() {
        Set<String> set = new TreeSet<>();
        extractWords(set, root, new char[100], 0);
        return set.toArray(new String[set.size()]);
    }

    // TEST ONLY
    private void extractWords(Set<String> set, TrieNode node, char[] word, int k) {
        if (node.isEnd()) {
            set.add(new String(word, 0, k));
        }
        for (char c  = FIRST_CHAR; c <= LAST_CHAR; c++) {
            if (node.map[c-FIRST_CHAR] != null) {
                word[k] = c;
                extractWords(set, node.map[c-FIRST_CHAR], word, k + 1);
                word[k] = 0;
            }
        }
    }
}
