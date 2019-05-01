import java.time.format.DateTimeFormatter
import java.time.ZonedDateTime
import java.util.regex.Pattern

public class Kersplat {

    //////////////////////////////////
    // MEGATON OF STATIC VARIABLES: //
    //////////////////////////////////

    final static DateTimeFormatter dateParser=
        DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss XXXX")
    final static DateTimeFormatter dateFormatter=
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXXX")

    final static char SPACE=' ', DBLQ='"', RBRACK=']', LBRACK='['

    // These are created in their order of appearance, based on the
    // nginx default configuration
    final static List metaCols=[
        new MetaCol("Host", SPACE, 3),
        new MetaCol("Auth", LBRACK),
        new MetaCol([
            title: "Date", checkChar: RBRACK, skipafter:3,
            parser: {ZonedDateTime.parse(it, dateParser).format(dateFormatter)}
        ]),
        new MetaCol("Method", SPACE),
        new MetaCol("URL", SPACE),
        new MetaCol("Version", DBLQ, 2),
        new MetaCol("Status", SPACE),
        new MetaCol("Bytes-sent", SPACE, 2),
        new MetaCol("Referer", DBLQ, 3),
        new MetaCol([title: "User-agent", checkChar: DBLQ, skipafter: 3, printQuote: true]),
        new MetaCol("Gzip-ratio", DBLQ)
    ]
    final static Map metaColsByName=metaCols.collectEntries{[(it.title.toLowerCase()): it]}
    final static Set metaColSet=metaColsByName.keySet()
    final static MetaCol metaColDate=metaCols.find{it.title=="Date"}


    /** This contains necessary information for parsing a given value type out of the line. */
    private static class MetaCol {
        private static int __counter=-1

        final int index
        final String title
        final char checkChar
        final int skipafter
        final boolean printQuote
        final Closure parser
        Pattern pattern
        boolean patternMatchNot
        public MetaCol(String title, char checkChar) {
            this(title, checkChar, 1, false, null)
        }
        public MetaCol(String title, char checkChar, int skipafter) {
            this(title, checkChar, skipafter, false, null)
        }
        public MetaCol(Map map) {
            this(map.title, map.checkChar, map.skipafter, map.printQuote, map.parser)
        }

        public MetaCol(String title, char checkChar, int skipafter, boolean printQuote, Closure parser) {
            this.index=++__counter
            this.title=title
            this.checkChar=checkChar
            this.skipafter=skipafter
            this.printQuote=printQuote
            this.parser=parser
        }
    }


    ///////////////////////////////
    // COMMAND-LINE ENTRY POINT: //
    ///////////////////////////////

    public static void main(String[] args) {
        AppConfig config=new AppConfig(args)
        if (config.help)
            help()
        else
        if (config.file!=null)
            handle(config, new FileInputStream(new File(config.file)))
        else
            handle(config, System.in)
    }

    private static class AppConfig extends ArgChecker{
        public String file;
        public boolean help=false
        public final List columns
        public final Map regexes=[:]
        public String countBy=false
        public boolean originalFormat=false


        public AppConfig(String[] args) {
            super(args)
            List colNames=null
            final Set countBys=["day","hour","minute","second"].toSet()
            while (next()) {
                if (checkArg("help"))
                    help = true
                else if (checkArg("cols")){
                    colNames=[]
                    while (isNextValue())
                        colNames+=grabNext()
                }
                else if (checkArg("file"))
                    file=grabNext()
                else if (checkArg("original"))
                    originalFormat=true
                else if (checkArg("count")) {
                    String byWhat=isNextValue() ?grabNext() :"second"
                    countBy=countBys.find{it==byWhat}
                    if (countBy==null) throw new Exception("Invalid: $byWhat")
                }
                else if (checkArg("grep")) {
                    boolean matchNot=(checkNextArg("not") || checkNextArg("v")) && next()
                    while (isNextValue())
                        regexes+=[
                            (grabNext()): [
                                matchNot: matchNot, regex: grabNext()
                            ]
                        ]
                }
                else if (file==null && isValue())
                    file=grab()
                else
                    throw new Exception("unexpected: ${args[i]}")
            }
            columns=colNames==null
                ?null
                :colNames.collect{
                    def c=metaColsByName[it.toLowerCase()]
                    if (c==null)
                        throw new Exception("Invalid column name: $it")
                    c
                }
            regexes.each{key, val->
                def c=metaColsByName[key.toLowerCase()]
                if (c==null)
                    throw new Exception("Invalid column name: $key")
                c.pattern=Pattern.compile(val.regex)
                c.patternMatchNot=val.matchNot
            }
        }
    }


    private static void handle(AppConfig config, InputStream instr) {
        handle(config, new BufferedReader(new InputStreamReader(instr)))
    }

    /////////////////////
    // THE BIG DRIVER: //
    /////////////////////

    private static void handle(AppConfig config, BufferedReader br) {

        final List forOutput=config.columns==null
            ?["Date", "Status", "Method", "Auth", "URL", "Version", "Host", "Bytes-sent", "User-agent"]
                .collect{metaColsByName[it.toLowerCase()]}
            :config.columns
        final MetaCol lastForOutput=forOutput.isEmpty() ?null :forOutput.last()

        final StringBuilder output=new StringBuilder()
        final LineBuff buff=new LineBuff()
        final boolean grepping=!config.regexes.isEmpty()

        String countingBy=null;
        int countBy=-1
        if ("second"==config.countBy) countBy="2019-04-26T11:07:32".length()
        else
        if ("minute"==config.countBy) countBy="2019-04-26T11:07".length()
        else
        if ("hour"==config.countBy) countBy="2019-04-26T11".length()
        else
        if ("day"==config.countBy) countBy="2019-04-26".length()

        long lineNumber=0;
        long count;
        String line
        while ((line=br.readLine())!=null) {
            try {
                // Initialize:
                boolean found=grepping
                lineNumber++
                buff.reset(line)

                // Parse and check regex while we're at it:
                final List parsed=metaCols.collect{
                    String s=buff.next(it)
                    if (it.pattern!=null)
                        found &= (it.pattern.matcher(s).find() ^ it.patternMatchNot)
                    s
                }
                buff.complete()

                // Print output as necessary:
                if (found || !grepping) {
                    if (countBy!=-1) {
                        String s=parsed[metaColDate.index]
                        s=s.substring(0, countBy)
                        if (countingBy!=s) {
                            if (count>0) println("$countingBy $count")
                            count=1
                            countingBy=s
                        } else
                            count++
                    } else {
                        for (x in forOutput) {
                            output.append(x.title)
                            output.append(": ")
                            if (x.printQuote)
                                output.append("\"")
                            output.append(parsed[x.index])
                            if (x.printQuote)
                                output.append("\"")
                            if (x!=lastForOutput)
                                output.append(" ")
                        }
                        if (config.originalFormat) {
                            if (lastForOutput!=null)
                                output.append(" Original: ")
                            output.append(line)
                        }
                        println(output)
                        output.setLength(0)
                    }
                }
            } catch (Exception e) {
                System.err.println("ERROR at line $lineNumber");
                System.err.println("CAUSED BY: $line");
                e.printStackTrace();
                return
            }
        }
        if (countBy!=-1 && count>0)
            println("$countingBy $count")
    }



    /** Knows how to walk the line to the next MetaCol of info */
    private static class LineBuff {
        String buff
        int currIndex
        public void reset(String buff) {
            this.buff=buff;
            this.currIndex=0
        }
        public String next(char marker, int skipafter) {
            int lineIndex=buff.indexOf((int)marker, currIndex);
            String value=buff.substring(currIndex, lineIndex);
            currIndex=lineIndex + skipafter
            value
        }
        public void complete() {
            if (currIndex < buff.length()-1) throw new Exception("You missed things");
        }
        public Object next(MetaCol cfg) {
            String s=next(cfg.checkChar, cfg.skipafter);
            cfg.parser==null ?s :cfg.parser.call(s)
        }
    }


    /** General purpose fancy argument checker. */
    private static class ArgChecker {
        private final String[] args;
        private int i=-1;

        public ArgChecker(String[] args) {
            this.args=args;
        }
        public boolean next() {
            ++i<args.length
        }

        public boolean checkNextArg(String lookFor) {
            pCheckArg(lookFor, i+1)
        }
        public boolean checkArg(String lookFor) {
            pCheckArg(lookFor, i)
        }
        private boolean pCheckArg(String lookFor, int index) {
            if (index >= args.length)
                return false
            String have=args[index]
            boolean result = have != null && (
                have.startsWith("-${lookFor}") || have.startsWith("--${lookFor}")
            )
            result
        }

        public boolean isNextValue() {
            pIsValue(i+1)
        }
        public boolean isValue() {
            pIsValue(i)
        }
        private boolean pIsValue(int index) {
            index<args.length && !args[index].startsWith("-")
        }
        public String grabNext() {
            i++
            grab()
        }
        public String grab() {
            if (i>=args.length)
                throw new Exception("Expected more arguments")
            if (args[i]==null)
                throw new Exception("Internal error: Null argument")
            String r=args[i];
            args[i]=null;
            r
        }
    }


    private static void help() {
        String colOptions=metaCols.collect{it.title}.join(" ")
        println("""
            Usage: [-file <filename> ] [-cols <names>] [-grep [-not] <name> <expression>] [-count <day|hour|minute|second>] [-original] [-help]

                Parses the default log format for nginx.

                -file <name>: Name of the log file. You can leave "-file" off in most cases and just give the file name.
                    When a file isn't specified, we read from stdin.

                -cols <names>: Print only the specified column values
                    <names>: use any of (case-insensitive):
                        $colOptions

                -grep [-not] <name> <expression>: Show rows matching expression(s)
                    Allows multiple name-expression pairs, but -grep can also be used more than once. When multiple expressions
                    are used, they are "anded", which is to say all expressions must be matched or the row will not be
                    displayed.

                    [-not]: Show rows that do *not* match the expression(s); also, you can substitute "-v" for "-not".
                    <name>: Same names that can be used with -cols
                    <expression>: A regex. Allows partial match, so "foo" matches "blahfoobar"; use "^foo\$" for
                        an exact match.

                -count <day|hour|minute|second>: Measure hits per time interval
                    Aggregates rows by day/hour/minute/second depending on input, and prints the rowcount for each interval.
                    Note 1: Assumes that records are already sorted by date.
                    Note 2: This will not show a "0" for missing intervals; e.g. if 9:08pm has hits, and 9:10pm has hits,
                        but there are no entries for 9:09pm, then you won't see a count (of 0) for 9:09.

                -original: Prints original log line
                    This prints the original log line at the end of every output line.
                    If you also provide "-cols" with no parameters, then the output will be purely the original log data.

            """.stripIndent()
        )
    }
}
