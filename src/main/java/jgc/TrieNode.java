package jgc;

import lombok.Getter;
import lombok.Setter;

import static jgc.Trie.FIRST_CHAR;
import static jgc.Trie.LAST_CHAR;

public class TrieNode {
    public TrieNode[] map;  // assumes small range

    @Setter // creates isEnd() not getEnd()
    @Getter
    private boolean end;    // is leaf node

    @Setter
    @Getter
    private boolean data;   // user flag

    public TrieNode(){
        // Simple array[25] plus a flag.  Note that each node doesn't know what word it represents.
        map = new TrieNode[LAST_CHAR - FIRST_CHAR + 1];
    }
}
