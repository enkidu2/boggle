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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * TODO:
 *  make UI pretty
 *  clean/reindent/org code
 *  check licenses
 *  try a swing/awt UI?
 *  too many errors are swallowed
 *  foreign dictionaries - inject dictionaries
 *      -- don't allow foreign letters as they're too hard to type on standard keyboards...
 *  bugs:
 *    hitting return multiple times scrolls down
 *    hitting return multiple times gets stuck and doesn't go to next col
 *    make top of third column higher
 *    make bottom of third column higher
 *    clean up FIX THIS comments
 *    clean up access protections - why use protected?
 */

@Command(name = "boggle", mixinStandardHelpOptions = true, version = "boggle 1.0",
        description = "Game of Boggle (c) 1972 Parker Brothers",
        footer="")
public class Boggle implements Callable<Integer> {

    private static final Logger log = LogManager.getLogger(Boggle.class);

    @Spec
    Model.CommandSpec spec; // command line options injected by picocli

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

    @Option(names = {"-p", "--processes"}, description = "Size of thread pool.", defaultValue = "3")
    protected int numThreads;

    @Option(names = {"-bj", "--boardJson"}, description = "Preset board in JSON format")
    protected String boardJson;

    @Option(names = {"-bs", "--boardString"}, description = "Preset board in compact string format")
    protected String boardString;

    @Option(names = {"-X"}, description = "Enable experimental assist features", defaultValue="false")
    protected boolean assist;

    @Option(names = {"-XX"}, description = "Enable extra assist", defaultValue="false")
    protected boolean extraAssist;

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
    char[][] _board;             // current board
    Dictionary dict;            // dictionary, including word trie
    TermServices ts;            // display routines
    Set<String> solutionSet;    // set of all answers
    List<String> solutionList;  // sorted list of answers - TreeSets too expensive to create a sorted set
    Dictionary solutionDictionary;  // dictionary of just solution words
    String solutionMax;         // longest string in current solution
    int solutionScore;          // the scoring of the entire solution

    Set<String> guessSet;       // set of guesses
    List<String> guessList;     // duplicate ordered list of guesses
    ExecutorService threadPool; // thread pool

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
        // boardString = "tslneiaentrtbeso";
        dice = buildDice(N);
        dict = Dictionary.getDictionary(dictSize);
        if (numThreads <= 0 || numThreads > 128) {
            throw new ParameterException(spec.commandLine(), "--processes must be >= 1 and <= 128 ");
        }
        threadPool = Executors.newFixedThreadPool(numThreads);

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
            _board = jsonToBoard(boardJson);
            N = _board.length;
        }
        else if (StringUtils.isNotEmpty(boardString)) {
            _board = stringToBoard(boardString);
            N = _board.length;
        }
        else {
            _board = new char[N][N];
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
                    _board[i][j] = die[rand.nextInt(DIE_SIZE)];
                }
            }
        }
    }

    /**
     * Convert the board to a list of strings.
     * @return
     */
    protected List<String> getBoardDisplayString(char[][] board) {
        List<String> list = new ArrayList<>();
        String line = "+---+";
        for (int i = 1; i < N; i++) {
            line += "---+";
        }

        list.add(line);
        for (char[] row : board) {
            StringBuilder buf = new StringBuilder();
            for (char c : row) {
                buf.append("| ");
                buf.append(Character.toUpperCase(c));
                buf.append(" ");
            }
            buf.append("|");
            list.add(buf.toString());
            list.add(line);
        }
        return list;
    }

    /**
     * Data logged after each game
     */
    protected void logData() {
        log.info("---------- game summary ----------");
        log.info("number of words: " + solutionSet.size());
        log.info("total possible score: " + score(solutionSet));
        log.info("number of guesses: " + guessSet.size());
        log.info("current score: " + score(guessSet));
        log.info("max word: " + solutionMax);
        log.info("max word score: " + score(solutionMax));
        log.info("board string: " + boardToString(_board));
        log.info("board json: " + boardToJson(_board));
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
    static final int[] moves = {0,-1,  1,-1,  1,0,  1,1,  0,1,  -1,1,  -1,0,  -1,-1};

    // are the coords in range and is that board location empty?
    private boolean isValid(char[][] board, int i, int j) {
        if (i < 0 || i >= N || j < 0 || j >= N) return false;
        return board[i][j] != 0;    // == 0 if already visited
    }

    /**
     * Finds all words which can be found on the board.  Assumes board is NxN, minimum word size is wordLen,
     * and the dictionary in Trie format is "dict."
     *
     * 'Q' is not assumed to be followed by a 'u'.  Instead, an optional 'u' will always be placed after
     * any q's on the board.  For example, the board { qi,at }, can form quit or qat.
     *
     * Sets this.solutionSet, this.solutionList and this.solutionDictionary
     *
     * @return
     */
    protected void solve() {
        final Set<TrieNode> solNodes = ConcurrentHashMap.newKeySet();   // expensive synchronized & partitioned map
        List<Callable<Void>> calls = new ArrayList<>(N*N+1);
        for (int i = 0; i < N; i++) {
            final int ii = i;
            for (int j = 0; j < N; j++) {
                final int jj = j;
                calls.add(() -> solvePosition(solNodes, ii, jj));
            }
        }

        try {
            threadPool.invokeAll(calls);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        Set<String> solutionSet = new HashSet<>();
        List<String> solutionList = new ArrayList<>();
        for (TrieNode tnode : solNodes) {
            tnode.setUsed(false);
            solutionSet.add(tnode.getWord());
            solutionList.add(tnode.getWord());
        }
        this.solutionSet = solutionSet;
        this.solutionList = solutionList;
        Collections.sort(this.solutionList);
        this.solutionDictionary = Dictionary.getDictionary(this.solutionList);
    }

    // thread safe solve - uses local copy of board & buffer
    protected Void solvePosition(Set<TrieNode> set, int i, int j) {
        char[][] boardCopy = Arrays.stream(_board).map(char[]::clone).toArray(char[][]::new);
        char[] buf = new char[N*N+1];
        solve(boardCopy, buf, i, j, 0, null, set);
        return null;
    }

    /**
     * Find all solutions for a single board position
     * @param soFar The current word we're building
     * @param oldi  Previous i coord
     * @param oldj  Previous j coord
     * @param k     Size of the current word, so far
     * @param root  Current dictionary node
     * @param set   A set of all the words found thus far
     */
    protected void solve(char[][] board, char[] soFar, int oldi, int oldj, int k, TrieNode root, Set<TrieNode> set) {
        if (root != null && root.isEnd() && k >= wordLen && !root.isUsed()) {
            root.setUsed(true);
            set.add(root);
        } // keep going, as the word may continue to grow

        // if we're at 'q', try adding an optional 'u' and continue - ad also continue w/o the 'u'
        if (k > 0 && soFar[k-1] == 'q') {
            TrieNode q = dict.findWordTree(root, 'u');
            if (q != null) {
                soFar[k] = 'u';
                solve(board, soFar, oldi, oldj, k + 1, q, set);
            }
        }

        for (int ix = 0; ix < moves.length; ix += 2) {
            int i = oldi + moves[ix];
            int j = oldj + moves[ix + 1];
            if (!isValid(board, i, j)) {
                continue;
            }
            TrieNode fragment = dict.findWordTree(root, board[i][j], null);
            if (fragment != null) {
                soFar[k] = board[i][j];
                board[i][j] = 0;    // no going back onto a square
                solve(board, soFar, i, j, k + 1, fragment, set);
                board[i][j] = soFar[k];
            }
        }
    }

    enum Reach { NONE, REACHED, MORE }

    /**
     * Mark all board positions which the given word fragment may occupy.  Used to highlight word in
     * progress.
     * @param word
     * @return A boolean mirror of the board, where each true may have been reached by the word.
     */
    protected Reach[][] boardReach(String word) {
        if (StringUtils.isEmpty(word)) {
            return null;
        }
        Reach[][] matchBoard = new Reach[N][N];
        char[] wchars = word.toCharArray();
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                matchBoard[i][j] = Reach.NONE;
            }
        }
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                if (_board[i][j] == wchars[0] && reachCrawl(matchBoard, i, j, wchars, 0)) {
                    matchBoard[i][j] = Reach.REACHED;
                }
            }
        }
        return matchBoard;
    }

    /**
     * Recursive helper function for boardReach().
     * @param matchBoard Where to record matches
     * @param oldi Last matched row
     * @param oldj Last matched column
     * @return True if the current path matches word entirely
     */
    boolean reachCrawl(Reach[][] matchBoard, int oldi, int oldj, char[] word, int k) {
        if (k >= word.length-1) {
            if (extraAssist) {
                TrieNode tnode = solutionDictionary.findWordTree(null, word);
                if (tnode != null) {
                    solutionCrawl(matchBoard, oldi, oldj, tnode);
                }
            }
            return true;
        }

        boolean found = false;
        for (int ix = 0; ix < moves.length; ix += 2) {
            int newi = oldi + moves[ix];
            int newj = oldj + moves[ix + 1];
            if (!isValid(_board, newi, newj)) {
                continue;
            }
            if (_board[newi][newj] == word[k+1] && reachCrawl( matchBoard, newi, newj, word, k+1)) {
                matchBoard[newi][newj] = Reach.REACHED;
                found = true;
            }
        }
        return found;
    }

    /**
     * After a board reach has found the word typed thus far, this optionally takes the crawl to
     * the next level: which adjacent letters can be used to find a valid word?  This is shameless
     * cheating.
     * @param matchBoard Where to record new paths to investigate
     * @param oldi Row of last letter of word
     * @param oldj Col of last letter of word
     * @param tnode The trie node of the word reached thus far
     */
    void solutionCrawl(Reach[][] matchBoard, int oldi, int oldj, TrieNode tnode) {
        for (int ix = 0; ix < moves.length; ix += 2) {
            int newi = oldi + moves[ix];
            int newj = oldj + moves[ix + 1];
            if (!isValid(_board, newi, newj)) {
                continue;
            }
            if (solutionDictionary.findWordTree(tnode, _board[newi][newj]) != null) {
                matchBoard[newi][newj] = Reach.MORE;
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

    /**
     * Cancels timer after displaying a ^Q alert.
     */
    private void pauseTimer() {
        ts.alert("^q to continue");
        timer.cancel();
    }

    /**
     * Resumes timer from previous timeLeft.
     */
    private void restartTimer() {
        ts.message("Type '?' for help");
        timer.cancel();
        ts.displayBoard(_board, null);
        displayScore(false);
        timer = newTimer();
    }

    /**
     * The timer merely sends the caller a simple interrupt after the time is up, while updating the
     * displayed clock every TIMER_PERIOD milliseconds.
     *
     * @return
     */
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

    /**
     * True if the word is of the right length and entirely alphabetic.  ts.readWord probably takes care of
     * non-alphabetic characters.
     *
     * @param word
     * @return
     */
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

    /**
     * True if the word is not a word in the dictionary, or is a prefix of a possible word.
     * @param word
     * @return
     */
    protected boolean isBadPartial(String word) {
        return StringUtils.isNotBlank(word) && solutionDictionary.findWordTree(word) == null;
    }

    /**
     * Stop timer, shut down UI.
     */
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
     * meanwhile, the timer loop separately displays top and center and interrupts play upon timeout
     * show all solutions, best word, score, words found and missed
     * reset screen, exit with full solutions displayed.
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

        ts.displayBoard(_board, null);
        ts.message("Type '?' for help");
        startTimer();
        String redo = null;     // word to rewrite, such as a previous error
        int redoNum = -1;
        String w = null;

        try {
            while (true) {
                TermServices.ReadValue p = ts.readWord(guessSet.size(), true, this);
                ts.displayBoard(_board, null);
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
                        log.error("unknown response: " + p);
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
            ts.displayBoard(_board, null);
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

    public boolean needsAssist() {
        return assist || extraAssist;
    }

    /**
     * picocli entry point to handle command line processing.
     * @return
     * @throws Exception
     */
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
