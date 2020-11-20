package jgc;

import com.sun.javaws.jnl.RContentDesc;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TrieTest {

    private Trie buildSimple() {
        Trie trie = new Trie();
        trie.insert("abc");
        return trie;
    }

    @Test
    void testInsert() {
        Trie trie = buildSimple();
        trie.insert("ab");
        assertEquals(false, trie.findNode("a").isEnd());
        assertTrue(trie.findNode("ab").isEnd());
        assertTrue(trie.findNode("abc").isEnd());
    }

    @Test
    void testFindNode() {
        Trie trie = buildSimple();

        assertFalse(trie.findNode("a").isEnd());
        assertFalse(trie.findNode("ab").isEnd());
        assertTrue(trie.findNode("abc").isEnd());
        trie.insert("ab");
        assertTrue(trie.findNode("ab").isEnd());
        assertNull(trie.findNode("x"));
    }

    @Test
    void insert() {
        Trie trie = buildSimple();
        try {
            trie.insert("CRASH");   // must be lowercased
        } catch (Exception e) {
            assertTrue((""+e).contains("java.lang.ArrayIndexOutOfBoundsException"));
        }
        try {
            trie.insert("");
        } catch (Exception e) {
            assertTrue((""+e).contains("java.lang.NullPointerException"));
        }
    }

    @Test
    void findNode() {
        Trie trie = buildSimple();
        TrieNode node = trie.findNode("a");
        assertNotNull(node);
        TrieNode node2 = trie.findNode(node, new char[]{'a'}, (char)0);
        assertNull(node2);
        node2 = trie.findNode(node, new char[]{'b'}, (char)0);
        assertNotNull(node2);
        assertEquals(false, node.isEnd());
        assertEquals(false, node2.isEnd());
        node2 = trie.findNode(node2, new char[]{'c'}, (char)0);
        assertNotNull(node2);
        assertEquals(true, node2.isEnd());
        node2 = trie.findNode(node2, new char[]{'c'}, (char)0);
        assertNull(node2);
    }

    @Test
    void getCount() {
        Trie trie = buildSimple();
        assertEquals(1, trie.getCount());
        trie.insert("ab");
        trie.insert("a");
        assertEquals(3, trie.getCount());
    }
}