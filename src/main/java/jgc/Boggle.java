package jgc;

import com.google.gson.Gson;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import picocli.CommandLine;
import picocli.CommandLine.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * TODO:
 *  show cursor where typing
 *  make UI pretty
 *  add unit/func tests
 *  clean/reindent/org code
 *  repackage code to make it shippable
 *  check licenses
 *  put into github
 *  try a swing/awt UI?
 *  check competition
 *  too many errors are swallowed
 *  foreign dictionaries
 *  doc dependent packages
 *  inject dictionaries?
 *  bugs:
 *    hitting return multiple times scrolls down
 *    hitting return multiple times gets stuck and doesn't go to next col
 *    upper case board strings crash
 *    make top of third column higher
 *    make bottom of third column higher
 *    FIXED: unable to guess 'sletreat' using -bs tslneiaentrtbeso
 *    clean up FIX THIS comments
 *    clean up access protections
 *    enable pom.xml to run tests
 *    khz & kHz are both added to dictionary - how to eliminate this efficiently? - strip words with upper cased letters...
 */

@Command(name = "boggle", mixinStandardHelpOptions = true, version = "boggle 1.0",
        description = "Game of Boggle (c) 1972 Parker Brothers",
        footer="")
public class Boggle implements Callable<Integer> {

    private static final Logger log = LogManager.getLogger(Boggle.class);

    @Spec
    Model.CommandSpec spec; // injected by picocli

    @Option(names = {"-n", "--num"}, description = "Board size, an integer value between 3 and 7", defaultValue = "4")
    protected Integer N;

    @Option(names = {"-l", "--logLevel"}, description = "Logging level, one of: error, warn, info, debug, trace", defaultValue="warn")
    protected String logLevel;

    @Option(names = {"-s", "--swing"}, description = "Create Swing UI.  The default (false) is to use a curses text terminal.", defaultValue = "false")
    protected boolean swing = false;

    @Option(names = {"-w", "--wordLen"}, description = "Minimum word length.  The default is 3.", defaultValue = "3")
    protected int wordLen;

    @Option(names = {"-t", "--time"}, description = "Time limit in seconds.  The default is 180s.", defaultValue = "180")
    protected int time;

    @Option(names = {"-d", "--dict"}, description = "Dictionary size, one of: ${COMPLETION-CANDIDATES}.", defaultValue = "M")
    protected Dictionary.DictSize dictSize;

    @Option(names = {"-bj", "--boardJson"}, description = "Preset board in JSON format")
    protected String boardJson;

    @Option(names = {"-bs", "--boardString"}, description = "Preset board in compact string format")
    protected String boardString;

    static final int DIE_SIZE = 6;        // size of each die
    static final int TIMER_PERIOD = 1000; // clock tic, in millis

    AtomicInteger timeLeft = new AtomicInteger(0);
    Timer timer;

    Random rand = new Random();

    // standard old boggle dice for 4x4 board - not used
    String[] oldDice = {
            "aaciot", "ahmors", "egkluy", "abilty",
            "acdemp", "egintv", "gilruw", "elpstu",
            "denosw", "acelrs", "abjmoq", "eefhiy",
            "ehinps", "dknotu", "adenvz", "biforx"
    };

    // standard modern boggle dice for 4x4 board
    String[] fourDice = {
            "aaeegn", "abbjoo", "achops", "affkps",
            "aoottw", "cimotu", "deilrx", "delrvy",
            "distty", "eeghnw", "eeinsu", "ehrtvw",
            "eiosst", "elrtty", "himnuq", "hlnnrz"
    };

    // standard boggle dice for 5x5 board
    String[] fiveDice = {
            "hiprry", "ceipst", "aafirs", "adennn", "ddlonr",
            "ooottu", "aaafrs", "ceiilt", "ccnstw", "fiprsy",
            "aeegmu", "dhlnor", "gorrvw", "dhhlor", "aaeeee",
            "ensssu", "ceilpt", "emottt", "aeeeem", "eiiitt",
            "afirsy", "dhhnot", "aegmnn", "nootuw", "bjkqxz"
    };

    // using old 4x4 Boggle, plus new 4x4, plus a few extra 5x5
    String[] sixDice = {
            "aaciot", "abjmoq", "acdemp", "acelrs", "adenvz", "ahmors",
            "biforx", "denosw", "dknotu", "eefhiy", "egkluy", "egintv",
            "ehinps", "elpstu", "gilruw", "aaeegn", "abbjoo", "achops",
            "affkps", "aoottw", "cimotu", "deilrx", "delrvy", "distty",
            "eeghnw", "eeinsu", "ehrtvw", "eiosst", "elrtty", "himnuq",
            "hlnnrz", "ceipst", "aafirs", "adennn", "ddlonr", "ensssu"
    };

    // using new 4x4, plus 5x5 and a few old die
    String[] sevenDice = {
            "aaeegn", "abbjoo", "achops", "affkps", "aoottw", "cimotu", "deilrx",
            "delrvy", "distty", "eeghnw", "eeinsu", "ehrtvw", "eiosst", "elrtty",
            "himnuq", "hlnnrz", "hiprry", "ceipst", "aafirs", "adennn", "ddlonr",
            "ooottu", "aaafrs", "ceiilt", "ccnstw", "fiprsy", "aeegmu", "dhlnor",
            "gorrvw", "dhhlor", "aaeeee", "ensssu", "ceilpt", "emottt", "aeeeem",
            "eiiitt", "afirsy", "dhhnot", "aegmnn", "nootuw", "bjkqxz", "aaciot",
            "abjmoq", "acdemp", "acelrs", "adenvz", "ahmors", "denosw", "dknotu"
    };

    // a bad idea
    String[] threeDice = {
            "aaeegn", "achops", "distty",
            "eeghnw", "eeinsu", "ehrtvw",
            "eiosst", "elrtty", "hlnnre"
    };

    String[][] allDice = { null, null, null, threeDice, fourDice, fiveDice, sixDice, sevenDice };

    char[][] dice;              // dice for NxN board
    char[][] board;             // current board
    Dictionary dict;            // dictionary, including word trie
    TermServices ts;            // display routines
    Set<String> solutionSet;    // set of all answers
    List<String> solutionList;  // sorted list of answers - TreeSets too expensive to create a sorted set
    Dictionary solutionDictionary;  // dictionary of just solution words
    String solutionMax;         // longest string in current solution
    int solutionScore;          // the scoring of the entire solution

    Set<String> guessSet;       // set of guesses
    List<String> guessList;     // duplicate ordered list of guesses

    public Boggle() {
    }

    void init() {
        init(true);
    }

    /**
     *
     * @param useTermServices TEST only, for headless performance tests
     */
    void init(boolean useTermServices) {
        setLogLevel(logLevel);
        dice = buildDice(N);
        dict = Dictionary.getDictionary(dictSize);

        // Place guesses in two locations: a set and an ordered list.  A LinkedHashMap can't do this
        // more efficiently, assuming that we'll never have more than a few dozen guesses.
        guessSet = new HashSet<>();
        guessList = new ArrayList<>();

        if (useTermServices) {
            ts = new TermServices(swing, getHelpMessage());
        }
    }

    /**
     * Logging level can be set via a command line option.
     *
     * throws IllegalArgumentException for a bad logLevel
     * @param logLevel
     */
    private void setLogLevel(String logLevel) {
        Level level = Level.valueOf(logLevel);
        Configurator.setLevel(System.getProperty("log4j2.logger"), level);
        log.info("logging level is: " + level);
    }

    /**
     * Create an array of "dice", which is a list of six character strings.
     *
     * @param N - size of board
     * @return
     */
    protected char[][] buildDice(int N) {
        if (N < 3 || N > 7) {
            throw new ParameterException(spec.commandLine(), "--num must be >= 3 and <= 7");
        }
        String[] diceStrings = allDice[N];
        char[][] newDice = new char[diceStrings.length][DIE_SIZE];
        int i = 0;
        for (String s : diceStrings) {
            newDice[i++] = s.toCharArray();
        }
        return newDice;
    }

    /**
     * Initialize board by rolling each available die for each NxN place on the board.
     */
    protected void fillBoard() {
        if (StringUtils.isNotEmpty(boardJson)) {
            board = jsonToBoard(boardJson);
            N = board.length;
        }
        else if (StringUtils.isNotEmpty(boardString)) {
            board = stringToBoard(boardString);
            N = board.length;
        }
        else {
            board = new char[N][N];
            List<char[]> deck = new ArrayList<char[]>();
            for (char[] die : dice) {
                deck.add(die);
            }
            List<char[]> shuffled = new ArrayList<char[]>();
            while (!deck.isEmpty()) {
                shuffled.add(deck.remove(rand.nextInt(deck.size())));
            }
            int k = 0;
            for (int i = 0; i < N; i++) {
                for (int j = 0; j < N; j++) {
                    char[] die = shuffled.get(k++);
                    board[i][j] = die[rand.nextInt(DIE_SIZE)];
                }
            }
        }
    }

    /**
     * Convert the board to a list of strings.
     * @return
     */
    protected List<String> getBoardDisplayString() {
        List<String> list = new ArrayList<>();
        String line = "+---+";
        for (int i = 1; i < N; i++) {
            line += "---+";
        }

        list.add(line);
        for (char[] row : board) {
            StringBuilder buf = new StringBuilder();
            for (char c : row) {
                buf.append("| " + Character.toUpperCase(c) + " ");
            }
            buf.append("|");
            list.add(buf.toString());
            list.add(line);
        }
        return list;
    }

    protected void logData() {

        log.info("---------- game summary ----------");
        log.info("number of words: " + solutionSet.size());
        log.info("total possible score: " + score(solutionSet));
        log.info("number of guesses: " + guessSet.size());
        log.info("current score: " + score(guessSet));
        log.info("max word: " + solutionMax);
        log.info("max word score: " + score(solutionMax));
        log.info("board string: " + boardToString(board));
        log.info("board json: " + boardToJson(board));
        log.info("solution list: " + new Gson().toJson(solutionList));
        log.info("guess set: " + new Gson().toJson(guessSet));
        log.info("---------- end summary ----------");
    }

    /*
     *   8 moves from any location.
     *      701
     *      6-2
     *      543
     */
    static int[] moves = {0,-1,  1,-1,  1,0,  1,1,  0,1,  -1,1,  -1,0,  -1,-1};

    // are the coords in range and is that board location empty?
    private boolean isValid(int i, int j) {
        if (i < 0 || i >= N || j < 0 || j >= N) return false;
        return board[i][j] != 0;
    }

    /**
     * Finds all words which can be found on the board.  Assumes board is NxN, minimum word size is wordLen,
     * and the dictionary in Trie format is "dict."
     *
     * Sets this.solutionSet, this.solutionList and this.solutionDictionary
     *
     * @return
     */
    // uses board, dict.trie
    protected void solve() {
        // using TreeSet is twice as slow as using a HashSet/ArrayList combo
        Set<String> set = new HashSet<>(1000);
        List<String> list = new ArrayList(1000);
        char[] buf = new char[N*N+1];
        long start = System.currentTimeMillis();
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                solve(buf, i, j, 0, null, set, list);
            }
        }
        long end = System.currentTimeMillis();
        this.solutionSet = set;
        this.solutionList = list;
        this.solutionDictionary = Dictionary.getDictionary(this.solutionList);
    }

    /**
     * Find all solutions on a board for all dictionary matches.
     * @param soFar The current word we're building
     * @param oldi  Previous i coord
     * @param oldj  Previous j coord
     * @param k     Size of the current word, so far
     * @param root  Current dictionary node
     * @param set   A set of all the words found thus far
     */
    protected void solve(char[] soFar, int oldi, int oldj, int k, TrieNode root, Set<String> set, List<String> list) {
        if (root != null && root.isEnd() && k >= wordLen) {
            String word = new String(soFar, 0, k);  // a lot of object proliferation
            if (!set.contains(word)) {
                set.add(word);
                list.add(word); // faster to keep a separate list
            }
        } // keep going, as the word may continue to grow

        // if we're at 'q', try adding an optional 'u' and continue - ad also continue w/o the 'u'
        if (k > 0 && soFar[k-1] == 'q') {
            TrieNode q = dict.findWordTree(root, 'u');
            if (q != null) {
                soFar[k] = 'u';
                solve(soFar, oldi, oldj, k + 1, q, set, list);
            }
        }

        for (int ix = 0; ix < moves.length; ix += 2) {
            int i = oldi + moves[ix];
            int j = oldj + moves[ix + 1];
            if (!isValid(i, j)) {
                continue;
            }
            TrieNode fragment = dict.findWordTree(root, board[i][j]);
            if (fragment != null) {
                soFar[k] = board[i][j];
                board[i][j] = 0;    // no going back onto a square
                solve(soFar, i, j, k + 1, fragment, set, list);
                board[i][j] = soFar[k];
                soFar[k] = 0;
            }
        }
    }

    /**
     * Standard Boggle scoring of words.  It's a shame that the word 'onomatopoeia' scores the same as the simpler
     * and much shorter: 'swimming'
     *
     * 3, 4     1
     * 5        2
     * 6        3
     * 7        5
     * 8+       11
     * @param words
     * @return
     */
    int[] scores = {0, 0, 0, 1, 1, 2, 3, 5, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11};
    protected int score(String word) {
        return scores[word.length()];
    }

    protected int score(Set<String> words) {
        return words.stream().mapToInt(w -> score(w)).sum();
    }

    /**
     * Return longest word in the list of words, which will presumably be the highest scoring word.
     * @param words
     * @return
     */
    protected String maxWord(Set<String> words) {
        return words.stream().max(Comparator.comparingInt(String::length)).get();
    }

    /**
     * Need: init screen, display board, display guesses, display score, input words, time left, words found, words missed
     */

    private void displayBoard() {
        ts.displayBoard(getBoardDisplayString());
    }

    private String boardToJson(char[][] b) {
        return new Gson().toJson(b);
    }

    private char[][] jsonToBoard(String json) {
        char[][] newBoard = new Gson().fromJson(json, char[][].class);
        int len = newBoard.length;
        if (len < 3 || len > 7) {
            throw new ParameterException(spec.commandLine(), "--boardJson must imply a board size >= 3 and <= 7");
        }
        return newBoard;
    }

    protected String boardToString(char[][] b) {
        return Arrays.stream(b).map(String::valueOf).collect(Collectors.joining());
    }

    protected char[][] stringToBoard(String str) {
        double d = Math.sqrt(str.length());
        if (d != Math.rint(d)) {
            throw new ParameterException(spec.commandLine(), "--boardString length must be a square number");
        }
        int len = (int)d;
        if (len < 3 || len > 7) {
            throw new ParameterException(spec.commandLine(), "--boardString must imply a board size >= 3 and <= 7");
        }
        char[][] newBoard = new char[len][len];
        int i = 0;
        int k = 0;
        for (int j = len; j <= str.length(); j += len) {
            String b = str.substring(i, j);
            newBoard[k++] = b.toCharArray();
            i = j;
        }

        return newBoard;
    }

    private void displayScore(boolean showMax) {
        if (showMax) {
            ts.displayStatus(solutionSet.size(), solutionScore, guessSet.size(), score(guessSet), solutionMax, score(solutionMax));
        }
        else {
            ts.displayStatus(solutionSet.size(), solutionScore, guessSet.size(), score(guessSet), null, 0);
        }
    }

    private void displaySolutionList() {
        ts.showSolutionList(solutionList, guessList);
    }

    private void displayHelp() {
        timer.cancel();
        ts.clear();
        ts.displayHelp();
        try {
            TermServices.ReadValue p = ts.prompt("hit return to start:");
            if (p.getType() == TermServices.ReadType.PREVIOUS) {
                displayHelp();
            }
            ts.clear();
            ts.showGuessList(guessList);
        } catch (InterruptedException e) {
            // do nothing
        }
        restartTimer();
    }

    private void startTimer() {
        timeLeft.set(time);
        timer = newTimer();
    }

    private void pauseTimer() {
        ts.alert("^q to continue");
        timer.cancel();
    }

    private void restartTimer() {
        ts.message("Type '?' for help");
        timer.cancel();
        displayBoard();
        displayScore(false);
        timer = newTimer();
    }

    private Timer newTimer() {
        Thread parent = Thread.currentThread();
        Timer timer = new Timer();

        timer.schedule(new TimerTask() {
            final long endTime = System.currentTimeMillis() + timeLeft.get() * 1000;

            @Override
            public void run() {
                long curTime = System.currentTimeMillis();
                int left = (int)((endTime - curTime) / 1000);
                timeLeft.set(left);
                ts.displayTime(left / 60, left % 60);
                if (left <= 0) {
                    parent.interrupt();
                    log.debug("out of time: " + left);
                    return;
                }
            }
        }, 0, TIMER_PERIOD);

        return timer;
    }

    protected boolean isValid(String word) {
        if (word == null || word.length() < wordLen) {
            return false;
        }
        for (char c : word.toCharArray()) {
            if (!Character.isAlphabetic(c)) {
                return false;
            }
        }
        return true;
    }

    protected boolean isBadPartial(String word) {
        return StringUtils.isNotBlank(word) && solutionDictionary.findWordTree(word) == null;
    }

    public void close() {
        if (timer != null) {
            timer.cancel();
        }
        if (ts != null) {
            ts.stop();
        }
    }

    /**
     * To play:
     * init board, dictionary, find solutions, start timer
     * loop {
     *     display board, scores
     *     read word and score it - else show it as invalid
     *     allow for pause, continue, quit, help
     * } until timeout
     * show all solutions, best word
     * reset screen
     */
    protected void play() {
        fillBoard();
        solve();
        this.solutionScore = score(this.solutionSet);
        this.solutionMax = maxWord(this.solutionSet);
        displayScore(false);
        String blank = ts.spaces(TermServices.WORD_WIDTH);

        try {
            ts.prompt("hit return to start:");
        } catch (InterruptedException e) {
            // do nothing
        }

        displayBoard();
        ts.message("Type '?' for help");
        startTimer();
        String redo = null;     // word to rewrite, such as a previous error
        int redoNum = -1;
        String w = null;

        try {
            while (true) {
                TermServices.ReadValue p = ts.readWord(guessSet.size(), true, this);
                switch(p.getType()) {
                    case STRING:
                        w = p.getValue();
                        break;
                    case HELP:
                        displayHelp();
                        continue;
                    case PAUSE:
                        pauseTimer();
                        continue;
                    case CONTINUE:
                        restartTimer();
                        continue;
                    case PREVIOUS:
                        // do nothing
                        continue;
                    default:
                        log.debug("FIX THIS, unknown response: " + p);
                }
                if (redo != null) {
                    ts.showSingleWord(redoNum, redo, false);
                    redo = null;
                }

                if (StringUtils.isEmpty(w)) {
                    continue;
                }
                if (!isValid(w)) {
                    ts.showSingleWord(guessSet.size(), w, true);
                    redoNum = guessSet.size() -1;
                    redo = w;
                }
                else if (guessSet.contains(w)) {
                    redoNum = guessList.indexOf(w); // redraw this later in non-reverse
                    redo = w;
                    ts.showSingleWord(redoNum, redo, true);
                    ts.showSingleWord(guessSet.size(), blank, false);
                    ts.showSingleWord(guessSet.size(), "", false);
                }
                else if (!solutionSet.contains(w)) {
                    ts.showSingleWord(guessSet.size(), blank, false);
                    ts.showSingleWord(guessSet.size(), "", false);
                }
                else {
                    guessSet.add(w);
                    guessList.add(w);
                    ts.showGuessList(guessList);
                }
                displayScore(false);
            }
        } catch (InterruptedException e) {
            // all good
            log.debug("interrupted");
        } catch (IOException io) {
            /// FIX THIS
            io.printStackTrace();
        }

        try {
            timer.cancel();
            ts.clear();
            displayBoard();
            displayScore(true);
            displaySolutionList();
            logData();
        } catch (Throwable t) {
            t.printStackTrace();
        }

        close();
    }

    private String getHelpMessage() {
        // strips non-printable formatting characters from help
        return spec.commandLine().getUsageMessage(Help.Ansi.OFF);
    }

    @Override
    public Integer call() throws Exception {
        init();
        play();
        return 0;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Boggle()).execute(args);
        System.exit(exitCode);
    }
}
