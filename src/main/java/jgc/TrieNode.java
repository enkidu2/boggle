package jgc;

import lombok.Getter;
import lombok.Setter;

import static jgc.Trie.FIRST_CHAR;
import static jgc.Trie.LAST_CHAR;

public class TrieNode {
    public TrieNode[] map;  // assumes small range

    @Setter // creates isEnd()
    @Getter
    private boolean end;    // is leaf node

    @Setter // creates isEnd()
    @Getter
    private boolean used;    // is leaf node

    @Setter
    @Getter
    private String word;    // optimization for constructing words from TrieNode

    public TrieNode(){
        // Simple array[26]
        map = new TrieNode[LAST_CHAR - FIRST_CHAR + 1];
    }
}
