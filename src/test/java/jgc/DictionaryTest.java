package jgc;

import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DictionaryTest {

    static Dictionary dictXXL;

    @BeforeAll
    static void setUp() {
        long start = System.currentTimeMillis();
        dictXXL = Dictionary.getDictionary(Dictionary.DictSize.XXL);
        long end = System.currentTimeMillis();
        System.out.println("time to setup: " + (end - start));
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void getDictionary() {
        String[] wordsM = Dictionary.getDictionary(Dictionary.DictSize.M).getWords();
        System.out.println("num medium words: " + wordsM.length);
        String[] wordsXXL = dictXXL.getWords();
        System.out.println("num XXL words: " + wordsXXL.length);
        System.out.println("count XXL words: " + dictXXL.getTrieCount());
        assertTrue(wordsM.length > 1000);
        assertTrue(wordsXXL.length > wordsM.length);
        assertEquals(wordsXXL.length, dictXXL.getTrieCount());
    }

    @Test
    void timeDictionaryInits() {
        long[] best = {99999, 99999, 99999, 99999, 99999};
        long[] len = {0, 0, 0, 0, 0};
        for (int loop = 1; loop <= 5; loop++) {
            System.out.println("---------------- loop #" + loop);
            System.gc();
            try { Thread.sleep(1000); } catch (Exception e) { }
            int count = 0;
            for (Dictionary.DictSize ds : Dictionary.DictSize.values()) {
                long start = System.currentTimeMillis();
                Dictionary dict = Dictionary.getDictionary(ds);
                long end = System.currentTimeMillis();
                if ((end - start) < best[count]) {
                    best[count] = (end - start);
                }
                len[count] = dict.getWords().length;
                count++;
            }
        }
        int count = 0;
        for (Dictionary.DictSize ds : Dictionary.DictSize.values()) {
            System.out.println("best time for dictionary: " + ds + "(" + len[count] + "): " + best[count]);
            count++;
        }
    }

    @Test
    void testGetDictionary() {
        Dictionary dict1 = Dictionary.getDictionary(Dictionary.DictSize.M);
        String[] words = dict1.getWords();
        Set<String> set = new HashSet(Arrays.asList(words));
        assertEquals(words.length, set.size());
        Dictionary dict2 = Dictionary.getDictionary(Arrays.asList(words));
        assertEquals(Dictionary.DictSize.valueOf("M"), dict1.getSize());
        assertNull(dict2.getSize());
        assertEquals(dict1.getWords().length, dict2.getWords().length);
        System.out.println("num words: " + words.length);

        Dictionary dict3 = Dictionary.getDictionary(Dictionary.DictSize.XXL);
        String[] words3 = dict3.getWords();
        Set<String> set3 = new HashSet(Arrays.asList(words3));

        // Duplicates can arise when converting accents.  For example, if éclair becomes eclair and
        // the dictionary already has eclair.  Missing words shouldn't be possible.
        Set<String> set2 = new HashSet();
        for (String s : words3) {
            if (!set3.contains(s)) {
                fail("missing word: " + s);
            }
            if (set2.contains(s)) {
                fail("duplicate word:" + s);
            }
            set2.add(s);
        }

        assertEquals(words3.length, set3.size());
        Dictionary dict4 = Dictionary.getDictionary(Arrays.asList(words3));
        assertEquals(Dictionary.DictSize.valueOf("XXL"), dict3.getSize());
        assertNull(dict4.getSize());
        assertEquals(dict3.getWords().length, dict4.getWords().length);
        System.out.println("num words: " + words3.length);
    }

    @Test
    void getSize() {
        assertTrue(dictXXL.getSize() == Dictionary.DictSize.XXL);
    }

    @Test
    void getDictPath() {
        assertEquals("/usr/share/dict/american-english", Dictionary.getDictPath(Dictionary.DictSize.M));
        assertEquals("/usr/share/dict/american-english", dictXXL.getDictPath(Dictionary.DictSize.M));
    }

    @Test
    void findWordTree() {
        TrieNode node = dictXXL.findWordTree("lutfisk");
        assertEquals(true, node.isEnd());
    }

    @Test
    void testFindWordTree() {
        TrieNode node = dictXXL.findWordTree("yg");
        assertEquals(false, node.isEnd());
        node = dictXXL.findWordTree(node,  'g');
        assertEquals(false, node.isEnd());
        node = dictXXL.findWordTree(node,  'x');
        assertNull(node);
    }
}