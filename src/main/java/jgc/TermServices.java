package jgc;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;
import com.googlecode.lanterna.terminal.ansi.UnixTerminal;
import com.googlecode.lanterna.terminal.swing.SwingTerminalFrame;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.CharBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Text based UI using lanterna library.  Runs in either current terminal window, or will pop
 * up a new terminal.  See -s option.
 *
 * Most of the effort is in keeping the cursor in the correct position.  Lanterna will automatically
 * position the cursor after the last displayed character.  Boggle will try to always move the cursor
 * to where it's anticipated the next character will be typed by the user.  For example, updating the
 * clock each second will necessitate the cursor being repositioned.  Or, entering a valid word should
 * move the cursor to the next slot in the displayed solution list, which may be in a new column.
 *
 * cursor
 */
public class TermServices {

    private static final Logger log = LogManager.getLogger(TermServices.class);

    static final int BOARD_X = 22;
    static final int BOARD_Y = 1;

    static final int WORD_X = 0;        // start of word lists
    static final int WORD_Y = 6;
    static final int WORD_WIDTH = 11;

    static final int STATUS_X = 0;
    static final int STATUS_Y = 0;

    Terminal terminal;
    TextGraphics tGraphics;
    int tWidth;         // screen width
    int tHeight;        // screen height
    int boardHeight;    // number of rows in a displayed board
    int boardWidth;     // number of columns in a displayed board
    int boardRight;     // X coord of right side of board

    int cursorColumn;   // cursor X coord - need to reset after displaying elsewhere
    int cursorRow;      // cursor Y coord

    // types of read responses from readWord()
    enum ReadType {
        STRING, PREVIOUS, HELP, PAUSE, CONTINUE
    }

    // returned by prompt() and readWord()
    @AllArgsConstructor
    @Getter
    class ReadValue {
        ReadType type;
        String value;
        public ReadValue(ReadType type) {
            this.type = type;
            value = null;
        }
    }

    String[] helpText = {
            "A Standard Boggle game, with the same dice and scoring as the Parker Brothers",
            "4x4 board game.  Play is extended to allow for different sized boards, time",
            "limits, real time corrections and pausing.",
            "",
            "After play starts, you should type words which you find anywhere on on the ",
            "board, horizontally, vertically or diagonally, followed by 'enter.'  The",
            "current score and the list of correctly guessed words will be continuously",
            "displayed.  If a word already been guessed, the original guess will be ",
            "highlighted in place in the list.  If a word being entered does not spell a",
            "word from the dictionary, or can not be formed from the board, it will be",
            "highlighted in place while typing.  The current word can be discarded by using ",
            "'enter' or 'backspace' over the incorrect letters.",
            "",
            "The letter 'q' will assume to be followed by an optional 'u' which does not",
            "need to be displayed on the board.   For example, if a row is 'q', 'i', 't', ",
            "the word \"quit\", may be played, as long as the 'u' is typed.  Foreign loan",
            "words, such as \"qat,\" where the 'u' is not present, are allowed as well.",
            "",
            "Play lasts for 3 minutes and the current time remaining will be displayed in",
            "the center, above the board.  Play can be paused using ^s and resumed using ^q.",
            "",
            "After play is finished, the entire solution list will be displayed.  Hit return",
            "to paginate, or ^p to return to a previous page.",
            "",
            "Boggle.log will contain info on the previously played game, including the board,",
            "solutions and scoring, when log level INFO or higher is used.",
            "",
            "Pre-defined boards can be loaded using -bj or -bs.  Previously played games can",
            "be replayed by using this option with the board strings dumped into boggle.log.",
            "",
            "Example:",
            "   java -cp \"./target/lib/*\" -jar \"./target/boggle.jar\" -l INFO -dXXL -bs tslneiaentrtbeso",
            "",
            "   This will produce a board and dictionary which will have a max score of 3258.",
            "",
};

    public TermServices(boolean useSwing, String commandHelpMsg) {
        try {
            if (useSwing) {
                terminal = new DefaultTerminalFactory().createSwingTerminal();
                SwingTerminalFrame sterm = (SwingTerminalFrame) terminal;
                sterm.setVisible(true);
            }
            else {
                // will create a swing frame if term not accessible, such as when running in the background
                terminal = new DefaultTerminalFactory().createTerminal();
            }

            if (terminal instanceof UnixTerminal) {
                stty("-ixon");  // enable ^s/^q scroll lock
            }
            tGraphics = terminal.newTextGraphics();
            TerminalSize size = terminal.getTerminalSize();
            log.debug("terminal size: " + size);
            tWidth = size.getColumns();
            tHeight = size.getRows();
            terminal.clearScreen();
            helpText = ArrayUtils.addAll(helpText, commandHelpMsg.split("\\r?\\n"));
            setCursorPos(0, WORD_Y);        // start position
            terminal.setCursorVisible(true);   // always visible

            Runtime.getRuntime().addShutdownHook(new Thread() {
                public void run() {
                    try {
                        // useful if user uses ctrl-c to escape
                        TermServices.this.stop();
                    } catch (Exception e) {
                        e.printStackTrace();
                        Thread.currentThread().interrupt();
                    }
                }
            });

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * display the given board in the middle of the terminal
     * @param board
     */
    void displayBoard(List<String> board) {
        boardHeight = board.size();
        boardWidth = board.get(0).length();
        boardRight = BOARD_X + boardWidth;
        try {
            int row = BOARD_Y;
            for (String str : board) {
                tGraphics.putString(BOARD_X, row++, str + "    ");
            }
            terminal.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Overwrite board with spaces.
     */
    void clearBoard() {
        String format = spaces(boardWidth);
        try {
            for (int i = 0; i < boardHeight; i++) {
                tGraphics.putString(BOARD_X, BOARD_Y+i, format);
            }
            terminal.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Update displayed status
     * @param wordCount
     * @param maxScore
     * @param curCount
     * @param curScore
     * @param maxWord
     * @param maxWordScore
     */
    void displayStatus(int wordCount, int maxScore, int curCount, int curScore, String maxWord, int maxWordScore) {
        tGraphics.putString(STATUS_X, STATUS_Y, "score: "  + curScore + "    ");
        tGraphics.putString(STATUS_X, STATUS_Y + 1, "count: "  + curCount + "    ");
        tGraphics.putString(STATUS_X, STATUS_Y + 3, "total score: "  + maxScore + "    ");
        tGraphics.putString(STATUS_X, STATUS_Y + 4, "word count: "  + wordCount + "    ");
        if (maxWord != null) {
            tGraphics.putString(boardRight + 4, STATUS_Y + 3, "max word: " + maxWord + "    ");
            tGraphics.putString(boardRight + 4, STATUS_Y + 4, "max word score: " + maxWordScore + "    ");
        }
        try {
            restoreCursorPos();
            terminal.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void displayTime(int min, int sec) {
        String s = String.format("%02d:%02d    ", min, sec);
        int timeX = BOARD_X + (boardWidth / 2) - 2;
        int timeY = BOARD_Y-1;
        tGraphics.putString(timeX, timeY, s);
        try {
            restoreCursorPos();
            terminal.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void displayHelp() {
        int rows = 0;
        for (int i = 0; i < helpText.length; i++) {
            if (++rows >= tHeight - 2) {
                try {
                    terminal.flush();
                    ReadValue p = prompt("Hit return to continue:");
                    if (p.getType() == ReadType.PREVIOUS) {
                        i = 0;
                    }
                    rows = 0;
                    clear();
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
            tGraphics.putString(0, rows, helpText[i]);
        }
        try {
            terminal.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void alert(String msg) {
        clearBoard();
        tGraphics.enableModifiers(SGR.REVERSE);
        tGraphics.putString(BOARD_X, BOARD_Y + 2, msg);
        try {
            terminal.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
        tGraphics.disableModifiers(SGR.REVERSE);
    }

    void message(String msg) {
        message(msg, false);
    }

    protected void message(String msg, boolean reverse) {
        if (reverse) {
            tGraphics.enableModifiers(SGR.REVERSE);
        }
        tGraphics.putString(boardRight + 4,0, msg + "     ");
        try {
            terminal.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (reverse) {
            tGraphics.disableModifiers(SGR.REVERSE);
        }
    }

    void setCursorPos(int col, int row) throws IOException {
        cursorColumn = col;
        cursorRow = row;
        terminal.setCursorPosition(col, row);
    }

    void restoreCursorPos() throws IOException {
        terminal.setCursorPosition(cursorColumn, cursorRow);
    }

    /**
     * Display prompt string and read a response.
     * @param p
     * @return
     * @throws InterruptedException
     */
    ReadValue prompt(String p) throws InterruptedException {
        ReadValue retval = null;
        tGraphics.putString(0, tHeight-1, p + "  ");
        try {
            int oldCursorX = cursorColumn;
            int oldCursorY = cursorRow;
            setCursorPos(p.length() + 1, tHeight-1);
            terminal.flush();
            retval = readWord(0);
            tGraphics.putString(0, tHeight-1, spaces(p.length() + 1));
            setCursorPos(oldCursorX, oldCursorY);
            terminal.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return retval;
    }

    void clear() {
        try {
            terminal.clearScreen();
            terminal.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Reads a word from the terminal.
     *     Allows echoing at a specific position in the guess list.
     *     Allows for ^s/^q pause/resume.
     *     Displays help on ?
     *     Support delete/backspace for word editing
     *     Highlights words, or partial words, which will not be correct
     * blocks until newline, ESC/interrupt
     * FIX THIS: IO doesn't respond well to all interrupts, so looks for done flag as well
     *
     * @return
     * @throws InterruptedException
     */

    ReadValue readWord(int guessCount) throws InterruptedException, IOException {
        return readWord(guessCount, false, null);
    }

    ReadValue readWord(int guessCount, boolean doEcho, Boggle b) throws InterruptedException, IOException {
        StringBuilder buf = new StringBuilder();
        while (true) {
            try {
                restoreCursorPos();
                KeyStroke key = null;
                while (key == null) {
                    key = terminal.pollInput(); // non-blocking IO
                    if (key == null) {
                        Thread.sleep(10);
                    }
                }
                if (key.isCtrlDown()) {
                    switch (key.getKeyType()) {
                        case Character:
                            char c = key.getCharacter();
                            switch (c) {
                                case 's':
                                    return new ReadValue(ReadType.PAUSE);
                                case 'q':
                                    return new ReadValue(ReadType.CONTINUE);
                                case 'p':
                                    return new ReadValue(ReadType.PREVIOUS);
                                case 'd':
                                    b.logData();
                                    return new ReadValue(ReadType.CONTINUE);
                                default:
                                    // fall through to interrupt
                            }
                    }
                    throw new InterruptedException();
                }
                else if (key.isAltDown()) {
                    throw new InterruptedException();
                }
                switch (key.getKeyType()) {
                    case Character:
                        char c = key.getCharacter();
                        if (Character.isAlphabetic(c)) {
                            buf.append(Character.toLowerCase(c));
                        }
                        else if (c == '?') {
                            return new ReadValue(ReadType.HELP);
                        }
                        break;
                    case Delete:
                    case Backspace:
                        if (buf.length() > 0) {
                            if (doEcho) {
                                showSingleWord(guessCount, " ", false);
                            }
                            buf.deleteCharAt(buf.length() - 1);
                        }
                        break;
                    case Enter:
                        setCursorPos(0, cursorRow+1);
                        return new ReadValue(ReadType.STRING, buf.toString());
                    case Escape:
                    case EOF:
                        throw new InterruptedException();
                    default:
                        log.info("unknown keystroke: " + key);
                }
                String word = buf.toString();
                boolean useReverse = b != null && b.isBadPartial(word);
                if (doEcho) {
                    showSingleWord(guessCount, word, useReverse);
                }
            } catch (RuntimeException r) {
                if (r.getMessage() != null && r.getMessage().contains("interrupt")) {
                    throw new InterruptedException();
                }
                r.printStackTrace();
                throw r;
            }
        }
    }

    /**
     * shutdown display and reset to defaults
     */
    void stop() {
        // terminal.st
        try {
            tGraphics.disableModifiers(SGR.REVERSE);
            setCursorPos(0, tHeight);
            terminal.setBackgroundColor(TextColor.ANSI.DEFAULT);
            terminal.setForegroundColor(TextColor.ANSI.DEFAULT);
            terminal.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (terminal instanceof Window) {
            ((Window) terminal).dispose();
        }
    }

    public void showGuessList(List<String> guesses) {
        showWordList(null, guesses, 0, 0, false, false);
    }

    public void showSolutionList(List<String> solution, List<String> guesses) {
        ReadValue p;
        int count = 0;
        try {
            while (count >= 0) {
                Pair<Integer,Integer> pair = showWordList(solution, guesses, count, 0, false, false);
                count = pair.getLeft();
                if (count > 0) {
                    p = prompt("hit return to continue:");
                } else {
                    clearWordList(pair.getRight());
                    p = prompt("hit return to exit:");
                }
                if (p.getType() == TermServices.ReadType.PREVIOUS) {
                    count = 0;
                }
            }
        } catch (InterruptedException io) {
            // do nothing, we're done
        }
    }

    public void showSingleWord(int start, String word, boolean useReverse) {
        showWordList(Arrays.asList(word), null, 0, start, true, useReverse);
    }

    protected void clearWordList(int startPos) {
        String spaces = spaces(WORD_WIDTH);
        while (true) {
            Pair<Integer, Integer> coords = findSpot(startPos++);
            if (coords == null) {
                return;
            }
            tGraphics.putString(coords.getLeft(), coords.getRight(), spaces);
        }
    }

    /**
     * Display a list of words, such as guesses or the full solution.  These go in multiple columns, which may have
     * different start points to work around the centrally displayed board.  If the list doesn't fit on the screen it
     * will be paginated.  ^P can be used to return to a previous page.  Solution words which are also in the guess
     * list will be highlighted.
     *
     * Also used to display a single word
     *
     * @param solution  All words in the solution list
     * @param guesses   Words which have been guessed at, so far
     * @param startWordIx   The word number to start with
     * @param startPos      Where to display on screen (used for single word only)
     * @param displaySingle Show only a single word
     * @param useReverse    Display all words in reverse
     * @return Pair of: last word index from list displayed, screen index of last word
     */
    private Pair<Integer,Integer> showWordList(List<String> solution, List<String> guesses, int startWordIx,
                                               int startPos, boolean displaySingle, boolean useReverse) {
        List<String> words = (solution != null) ? solution : guesses;
        int count = 0;
        Pair<Integer,Integer> prevCoords = null;
        int prevCount = 0;
        try {
            for (int i = startWordIx; i < words.size(); i++) {
                String word = words.get(i);
                Pair<Integer, Integer> coords = findSpot( displaySingle ? startPos : i - startWordIx);

                if (coords == null) {
                    // no room to display new word
                    if (prevCoords != null) {
                        tGraphics.putString(prevCoords.getLeft(), prevCoords.getRight(), "....    ");
                    }
                    return Pair.of(count + startWordIx - 1, i - startWordIx);
                }
                prevCoords = coords;
                prevCount = i - startWordIx;
                // highlight guessed words when showing entire solution list
                if (solution != null && guesses != null && guesses.contains(word)) {
                    tGraphics.enableModifiers(SGR.REVERSE);
                }
                if (useReverse) {
                    tGraphics.enableModifiers(SGR.REVERSE);
                }
                try {
                    if (displaySingle) {
                        setCursorPos(coords.getLeft() + word.length(), coords.getRight());
                    } else {
                        Pair<Integer, Integer> nextCoords = findSpot( i - startWordIx + 1);
                        if (nextCoords != null) {
                            setCursorPos(nextCoords.getLeft(), nextCoords.getRight());
                        }
                        word += spaces(WORD_WIDTH - word.length());
                    }
                } catch (IOException io) {
                    io.printStackTrace();
                }
                tGraphics.putString(coords.getLeft(), coords.getRight(), word);
                count++;
                tGraphics.disableModifiers(SGR.REVERSE);
            }
        } finally {
            try {
                tGraphics.disableModifiers(SGR.REVERSE);
                terminal.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return Pair.of(-1,prevCount);
    }

    /**
     * Where to place the next word in the display list.  Tries to fit on the existing screen and make room for the
     * board.
     *
     * @param num
     * @return (x,y) of where to display Nth (num) word,  null if it doesn't fit
     */
    private Pair<Integer, Integer> findSpot(int num) {
        int x = WORD_X;
        int y = WORD_Y;
        int y2 = BOARD_Y + boardHeight + 2;
        for (int i = 0; i < num; i++) {
            y++;
            if (y >= tHeight-2) {
                x += WORD_WIDTH;
                if (x + WORD_WIDTH >= tWidth) {
                    return null;
                }
                if (x >= BOARD_X && x <= BOARD_X + boardWidth + 2) {
                    y = y2;
                }
                else {
                    y = WORD_Y;
                }
            }
        }
        return Pair.of(x, y);
    }

    public String spaces( int spaces ) {
        return CharBuffer.allocate( spaces ).toString().replace( '\0', ' ' );
    }

    /**
     * ^s/^q flow control is a holdout from teletype days and 300 baud modems, when characters were displayed far
     * more slowly than we could type.  Boggle uses it to halt and resume the clock.  As we can now display at
     * 30M+ characters/second, it's functionality has been disabled and buried into stty.
     *
     * @param args
     * @throws IOException
     * @throws InterruptedException
     */
    protected void stty(String args) throws IOException, InterruptedException {
        exec(new String[] { "sh", "-c", "stty " + args + " < /dev/tty"});
    }

    /**
     * Runs a command.  Only returns output upon an error.
     * @param cmd
     * @throws IOException
     * @throws InterruptedException
     */
    private void exec(String[] cmd) throws IOException, InterruptedException {
        Process p = Runtime.getRuntime().exec(cmd);

        try (InputStream in = p.getInputStream(); InputStream err = p.getErrorStream(); ByteArrayOutputStream bout = new ByteArrayOutputStream()) {
            int c;

            while ((c = in.read()) != -1) {
                bout.write(c);
            }

            while ((c = err.read()) != -1) {
                bout.write(c);
            }

            p.waitFor(5, TimeUnit.SECONDS);
            if (p.exitValue() != 0) {
                throw new RuntimeException("exit code: " + p.exitValue() + ", " + new String(bout.toByteArray()));
            }
        }
    }
}
