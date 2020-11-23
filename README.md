# boggle
Classic Boggle game with ASCII/terminal graphics written in Java.

Created as an exercise in Java, maven, git, etc.  

Requires the following dictionary packages to be installed:

* sudo apt install wamerican wamerican-huge wamerican-insane wamerican-large wamerican-small

Easily adaptable to any set of dictionaries with a config file.

Implemented with a standard Trie.  Fairly fast at solving about 6,000 standard boards/second on a 2.8GHz cpu.  Uses dictionary directed editing which is perhaps too helpful, but it hasn't personally helped my scores overly much.

* Current command line options:
```
Usage: boggle [-hsV] [-bj=<boardJson>] [-bs=<boardString>] [-d=<dictSize>]
              [-l=<logLevel>] [-n=<N>] [-t=<time>] [-w=<wordLen>]
Game of Boggle (c) 1972 Parker Brothers
      -bj, --boardJson=<boardJson>
                            Preset board in JSON format
      -bs, --boardString=<boardString>
                            Preset board in compact string format
  -d, --dict=<dictSize>     Dictionary size, one of: S, M, L, XL, XXL.
  -h, --help                Show this help message and exit.
  -l, --logLevel=<logLevel> Logging level, one of: error, warn, info, debug,
                              trace
  -n, --num=<N>             Board size, an integer value between 3 and 7
  -s, --swing               Create Swing UI.  The default (false) is to use a
                              curses text terminal.
  -t, --time=<time>         Time limit in seconds.  The default is 180s.
  -V, --version             Print version information and exit.
  -w, --wordLen=<wordLen>   Minimum word length.  The default is 3.
```
