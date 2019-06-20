import java.time.format.DateTimeFormatter
import java.time.ZonedDateTime
import java.time.ZoneId
import java.util.regex.Pattern
import java.util.ArrayList

/** This takes a log file template and parses using that. Pretty cool then. */
public class LogParser {
    final static DateTimeFormatter dateFormatter=
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXXX")

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
        public final List regexes=[]
        public String countBy=false
        public boolean originalFormat=false
        public String template
        public String dateFormat

        public AppConfig(String[] args) {
            super(args)
            final Set countBys=["day","hour","minute","second"].toSet()
            while (next()) {
                if (checkArg("help"))
                    help = true
                else if (checkArg("cols")){
                    columns=[]
                    while (isNextValue())
                        columns+=grabNext()
                }
                else if (checkArg("template"))
                    template=grabNext()
                else if (checkArg("date-format"))
                    dateFormat=grabNext()
                else if (checkArg("log-file"))
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
                        regexes+=new MetaPattern(grabNext(), grabNext(), matchNot)
                }
                else if (file==null && isValue())
                    file=grab()
                else
                    throw new Exception("unexpected: ${args[i]}")
            }
        }
    }

    private static void handle(AppConfig config, InputStream instr) {
        handle(config, new BufferedReader(new InputStreamReader(instr)))
    }

    private static void handle(AppConfig config, BufferedReader br) {

        // MetaColumn parsing:
        List metaCols=parseTemplate(config.template)
        Map metaColsByName=metaCols.collectEntries{[(it.title.toLowerCase()): it]}

        MetaCol metaColDate=metaCols.find{it.title=="Date"}
        final DateTimeFormatter dateParser=
            metaColDate != null && config.dateFormat !=null
                ?DateTimeFormatter.ofPattern(config.dateFormat).withZone(ZoneId.systemDefault())
                :null

        final List forOutput=config.columns==null
            ? metaCols
            : config.columns.collect{
                def c=metaColsByName[it.toLowerCase()]
                if (c==null)
                    throw new Exception("Invalid column name: $it")
                c
            }
        final MetaCol lastForOutput=forOutput.isEmpty() ?null :forOutput.last()

        config.regexes.each{MetaPattern mp->
            def c=metaColsByName[mp.colName.toLowerCase()]
            if (c==null)
                throw new Exception("Invalid column name: ${mp.colName}")
            c.patterns= (c.patterns ?: []) + mp
        }

        final StringBuilder output=new StringBuilder()

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
        int colCount=metaCols.size()
        while ((line=br.readLine())!=null)
            try {
                // Initialize:
                boolean found=grepping
                lineNumber++

                // Parse and check regex while we're at it:
                int currIndex=0

                final List parsed=new ArrayList(metaCols.size())
                for (int i=0; i<colCount; i++) {
                    MetaCol mc=metaCols[i]
                    if (mc.before!=null)
                        currIndex=line.indexOf(mc.before, currIndex) + mc.before.length()
                    String value
                    if (mc.after!=null) {
                        int endindex=line.indexOf(mc.after, currIndex)
                        if (endindex==-1)
                            throw new Exception("Failed to find ${mc.after} for column ${mc.title}")
                        value=line.substring(currIndex, endindex)
                        currIndex=endindex+mc.after.length()
                    } else if (i==colCount-1) {
                        value=line.substring(currIndex)
                    } else
                        throw new Exception("Wut")
                    if (mc==metaColDate && dateParser!=null)
                        value=ZonedDateTime.parse(value, dateParser).format(dateFormatter)
                    if (mc.patterns!=null && found)
                        for (MetaPattern mp: mc.patterns)
                            found &= mp.pattern.matcher(value).find() ^ mp.matchNot
                    parsed.add(value)
                }


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
        if (countBy!=-1 && count>0)
            println("$countingBy $count")

    }

    private static List parseTemplate(String template) {
        List list=[]
        Pattern pattern=Pattern.compile("[a-zA-Z]+")
        def matcher=pattern.matcher(template)
        int currIndex=0
        while (matcher.find()) {
            int start=matcher.start();
            int end=matcher.end();
            String before=start!=currIndex
                ?template.substring(currIndex, start)
                :null
            String title=template.substring(start, end)
            list.add(new MetaCol(title, before, null))
            currIndex=end
        }
        if (currIndex<template.length())
            list[list.size()-1].after=template.substring(currIndex)
        for (int i=1; i<list.size(); i++) {
            list[i-1].after=list[i].before
            list[i].before=null
        }
        list
    }

    /** This contains necessary information for parsing a given value type out of the line. */
    private static class MetaCol {
        private static int __counter=-1

        final int index
        String title, before, after
        List patterns
        boolean printQuote

        public MetaCol(String title, String before, String after) {
            this.index=++__counter
            this.before=before
            this.after=after
            this.title=title
        }
        public String toString() {
            "Title: $title - Before: $before - After: $after"
        }
    }
    private static class MetaPattern {
        public final String colName
        public final Pattern pattern
        public final boolean matchNot
        public MetaPattern(String name, String rawPattern, boolean matchNot) {
            this.colName=name
            this.pattern=Pattern.compile(rawPattern)
            this.matchNot=matchNot
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
        println("""
            Usage: -template <text> [-log-file <filename> ] [-cols <names>] [-grep [-not] <name> <expression>]
                        [-count <day|hour|minute|second>] [-original] [-help]

                Parses a given log using a template

                -template <text>:
                    Provides names surrounded by special characters that identify boundaries between the values.
                    Special characters can include spaces, dashes, quotes, tabs, etc. Anything that is not a letter
                    is treated as a special character.
                    Example:
                        [ Date ]  - Referer - Status "UserAgent"

                -date-format <format>: A format for parsing the date, if there is a column named "Date".
                    This is based on the specification for Java's DateTimeFormatter class
                    Examples:
                        yyyy-MM-dd HH:mm:ss.SSS
                        dd/MMM/yyyy:HH:mm:ss XXXX

                -log-file <name>: Name of the log file. You can leave "-file" off in most cases and just give the file name.
                    When a file isn't specified, we read from stdin.

                -cols <names>: Print only the specified column values.
                    These are the names from the -template argument

                -grep [-not] <name> <expression>: Show rows matching expression(s)
                    Allows multiple name-expression pairs, but -grep can also be used more than once. When multiple expressions
                    are used, they are "anded", which is to say all expressions must be matched or the row will not be
                    displayed.

                    [-not]: Show rows that do *not* match the expression(s); also, you can substitute "-v" for "-not".
                    <name>: Same names that can be used with -cols
                    <expression>: A regex. Allows partial match, so "foo" matches "blahfoobar"; use "^foo\$" for
                        an exact match.

                -original: Prints original log line
                    This prints the original log line at the end of every output line.
                    If you also provide "-cols" with no parameters, then the output will be purely the original log data.

                -count <day|hour|minute|second>: Measure hits per time interval
                    Aggregates rows by day/hour/minute/second depending on input, and prints the rowcount for each interval.
                    Note 1: In case it isn't obvious, this won't work without a value for the "-date-format" parameter
                    Note 2: Assumes that records are already sorted by date. If they aren't, you can always do a first pass
                        with "-date-format xxxxxx -cols Date -original", run it through the system sort command, then
                        run it back through on a second pass. Yeah we know that's stupid but it works.
                    Note 2: This will not show a "0" for missing intervals; e.g. if 9:08pm has hits, and 9:10pm has hits,
                        but there are no entries for 9:09pm, then you won't see a count (of 0) for 9:09.

            """.stripIndent()
        )
    }

}
