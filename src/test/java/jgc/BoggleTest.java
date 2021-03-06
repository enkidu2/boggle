package jgc;

import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BoggleTest {

    private Boggle getBoggle() {
        return getBoggle(Dictionary.DictSize.S);
    }

    private Boggle getBoggle(Dictionary.DictSize ds) {
        Boggle b = new Boggle();
        CommandLine.ParseResult pr = new CommandLine(b).parseArgs("-h");    // hack to init boggle commandspec
        b.N = 4;
        b.logLevel = Level.DEBUG.name();
        b.dictSize = ds;
        b.init(false);
        return b;
    }

    @Test
    void buildDice() {
        Boggle b = getBoggle();
        char[][] dice = b.buildDice(4);
        assertNotNull(dice);
        assertEquals(16, dice.length);  // 4x4
        assertEquals(6, dice[0].length);    // each die is a cube

        dice = b.buildDice(3);
        assertNotNull(dice);
        assertEquals(9, dice.length);  // 3x3
        assertEquals(6, dice[0].length);    // each die is a cube

        try {
            dice = b.buildDice(2);
        } catch (Exception ex) {
            System.out.println("expected error: " + ex.getMessage());
            assertTrue(ex.getMessage().contains("must be >= 3"));
        }

        try {
            dice = b.buildDice(8);
        } catch (Exception ex) {
            System.out.println("expected error: " + ex.getMessage());
            assertTrue(ex.getMessage().contains("and <= 7"));
        }

        b.close();
    }

    @Test
    void fillBoard() {
        Boggle b = getBoggle();
        assertNull(b._board);
        b.fillBoard();
        assertNotNull(b._board);
        b.close();
    }

    @Test
    void boardToString() {
        Boggle b = getBoggle();
        String x = "tslneiaentrtbeso";
        b.boardString = x;
        b.fillBoard();
        assertEquals(4, b._board.length);
        assertEquals(4, b._board[0].length);
        assertEquals('t', b._board[0][0]);
        assertEquals('o', b._board[3][3]);
        int i = 0;
        for (char bs : b.boardToString(b._board).toCharArray()) {
            assertEquals(x.charAt(i++), bs);
        }
        b.close();
    }

    /**
     * The performance of solve() is incredibly uneven.  Sure, it's fast - but fluctuates 20% each time it's called,
     * despite having no I/O.  It's likely due to object proliferation (it creates many millions of
     * strings - and perhaps locking contention w/gc?)  Swapping solve() to use a Trie w/no new Strings made it run
     * slower.
     *
     * On my 4 year old laptop (2.8GHz) it runs at 5700->6800 solutions/sec with the standard dictionary.
     */
    // @Test
    void solvePerformance() {
        solvePerformance2(true);
        solvePerformance2(false);
    }

    void solvePerformance2(boolean trialRun) {
        int LOOPMAX = 5;
        final int SOLVEMAX = 2000;

        int[] expected = {83, 102, 113, 171, 229 };
        long[] perfs = {0, 0, 0, 0, 0};
        long[][] allRates = new long[LOOPMAX][5];
        int[] size = {0, 0, 0, 0, 0};
        int[] ssize = {0, 0, 0, 0, 0};

        String title = "";
        if (trialRun) {
            LOOPMAX = 2;
            title = "trial run";
        }
        Map<Dictionary.DictSize, Boggle> dicts = new HashMap<>();
        for (Dictionary.DictSize ds : Dictionary.DictSize.values()) {
            Boggle b = getBoggle(ds);
            dicts.put(ds, b);
        }

        for (int loop = 1; loop <= LOOPMAX; loop++) {
            int j = 0;
            System.out.println("---------------- " + title + " loop #" + loop);
            for (Dictionary.DictSize ds : Dictionary.DictSize.values()) {
                Boggle b = dicts.get(ds);
                String x = "tleowsaitezpnsyi";      // typical: 102 words using M
                b.boardString = x;
                b.fillBoard();
                Set<String> sset = null;
                if (!trialRun) {
                    System.gc();
                    try {
                        Thread.sleep(500);
                    } catch (Exception e) {
                    }
                }
                long start2 = System.nanoTime();
                for (int i = 0; i < SOLVEMAX; i++) {
                    b.solve();
                    sset = b.solutionSet;
                }
                long end2 = System.nanoTime();
                long time = end2 - start2;
                long rate = (long)(((double)SOLVEMAX * 1000000000.0) / (double)time);
                allRates[j][loop-1] = rate;
                if (rate > perfs[j]) {
                    perfs[j] = rate;
                }
                size[j] = b.dict.getTrieCount();
                ssize[j] = sset.size();
                assertEquals(expected[j], sset.size());
                j++;
                b.close();
            }
        }
        if (!trialRun) {
            int j = 0;
            long vscore = 0;
            long best = 0;
            for (Dictionary.DictSize ds : Dictionary.DictSize.values()) {
                long avg = 0;
                for (long r : allRates[j]) {
                    avg += Math.abs(perfs[j] - r);
                }
                System.out.println("dictionary:    " + ds + " (" + size[j] + "), \tsolution size: " + ssize[j] +
                        ", \tbest rate: " + perfs[j] + "/sec\tavg variance: " + (avg / LOOPMAX));
                vscore += (avg / LOOPMAX);
                best += perfs[j];
                j++;
            }
            System.out.println("total avg variance: " + vscore + "\tavg rate: " + (best / j));
        }
    }

    @Test
    void isValid() {
        Boggle b = getBoggle();
        assertTrue(b.isValid("abc"));
        assertFalse(b.isValid("a"));
        assertFalse(b.isValid(""));
        assertFalse(b.isValid("a_b"));
        assertFalse(b.isValid("a b"));
    }


    @Test
    void score() {
        Boggle b = getBoggle();
        for (String s : Arrays.asList("help", "helps", "helping", "helpings", "123456789")) {
            System.out.println("score for: " + s + " = " + b.score(s));
        }
        b.close();
    }

    @Test
    void testSolve() {
        Boggle b = getBoggle(Dictionary.DictSize.XXL);
        String x = "tslneiaentrtbeso";
        b.boardString = x;
        b.fillBoard();
        b.solve();
        assertEquals(1142, b.solutionList.size());
        assertEquals("aer", b.solutionList.get(0));
        assertEquals(b.solutionList.size(), b.solutionSet.size());
        assertEquals("tst", b.solutionList.get(b.solutionSet.size() - 1));
    }

    @Test
    void testReach() {
        Boggle b = getBoggle(Dictionary.DictSize.XXL);
        String x = "tslneiaentrtbeso";
        b.boardString = x;
        b.fillBoard();
        b.solve();
        b.assist = true;
        b.extraAssist = false;
        int count = showReach(b, "sta");
        assertEquals(4, count);
        count = showReach(b, "start");
        assertEquals(5, count);
        count = showReach(b, "a");
        assertEquals(1, count);
        count = showReach(b, "x");
        assertEquals(0, count);

        Boggle.Reach[][] reach = b.boardReach("");
        assertEquals(null, reach);
    }


    @Test
    void testExtraReach() {
        Boggle b = getBoggle(Dictionary.DictSize.L);
        String x = "tslneiaentrtbeso";
        b.boardString = x;
        b.fillBoard();
        b.solve();
        b.assist = true;
        b.extraAssist = false;
        int count = showReach(b, "ben");
        assertEquals(3, count);

        b.extraAssist = true;
        count = showReach(b, "ben");
        assertEquals(4, count);

        Boggle.Reach[][] reach = b.boardReach("");
        assertEquals(null, reach);
    }


    int showReach(Boggle b, String word) {
        Boggle.Reach[][] reach = b.boardReach(word);
        int count = 0;
        System.out.println("------- " + word);
        for (int i = 0; i < reach.length; i++) {
            System.out.println();
            for (int j = 0; j < reach[0].length; j++) {
                System.out.print(reach[i][j] + "\t");
                if (reach[i][j] != Boggle.Reach.NONE) {
                    count++;
                }
            }
        }
        System.out.println();
        return count;
    }


    @Test
    void isBadPartial() {
        Boggle b = getBoggle();
        String x = "tleowsaitezpnsyi";      // typical: 102 words using M
        b.boardString = x;
        b.fillBoard();
        b.solve();
        assertTrue(b.isBadPartial("abc"));
        assertTrue(b.isBadPartial("kjsdfkasfhd"));
        assertFalse(b.isBadPartial("a"));
        assertTrue(b.isBadPartial("sitar"));
        assertFalse(b.isBadPartial("yeast"));
    }
}