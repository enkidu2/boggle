package jgc;

import lombok.Getter;
import lombok.Setter;

import static jgc.Trie.FIRST_CHAR;
import static jgc.Trie.LAST_CHAR;

/**
 * These are now consumed at all levels of Boggle to aid in performance.  Efficient solution finding
 * can't be obtained w/o traversing tries manually and decorating the tree (get/setUsed) along the
 * way.
 */
public class TrieNode {
    public TrieNode[] map;  // assumes small range

    @Setter // creates isEnd()
    @Getter
    private boolean end;    // is leaf node

    @Setter // creates isUsed()
    @Getter
    private boolean used;    // has been used

    @Setter
    @Getter
    private String word;    // optimization for constructing words from TrieNode

    public TrieNode(){
        // Simple array[26]
        map = new TrieNode[LAST_CHAR - FIRST_CHAR + 1];
    }
}
