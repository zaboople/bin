import java.util.regex.Pattern;
class Chopper {
    /* A nice enhancement would be to match multiple lines with a series of regexes */
    public static void main(String[] args) {
        int flags = 0;
        String expression=null;
        boolean nocase=false;
        if (args.length==0) {
            help();
            return;
        }
        for (int i=0; i<args.length; i++) {
            if (args[i].startsWith("--h")) {
                help();
                return;
            }
            else if (args[i].startsWith("--i") || args[i].startsWith("-i"))
                flags |= Pattern.CASE_INSENSITIVE;
            /* else if (args[i].startsWith("--m") || args[i].startsWith("-m"))
                flags |= Pattern.MULTILINE; */
            else if (expression==null)
                expression=args[i];
            else {
                println("ERROR: Unexpected argument: "+ args[i]);
                help();
                System.exit(1);
            }
        }
        final BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        final Pattern pattern = Pattern.compile(expression, flags);

        BufferedWriter bw = null;
        String line;
        int index = 0;
        Set already = new HashSet();
        while ((line=br.readLine())!=null) {
            if (pattern.matcher(line).matches()) {
                if (bw != null)
                    bw.close();
                String name = purge(line);
                if (name.length() > 127)
                    name=name.substring(0, 127);
                if (name.length()==0)
                    name="empty";
                if (already.contains(name)) {
                    name += (++index);
                }
                already.add(name);
                bw = getWriter(name);
            }
            if (bw==null) {
                bw = getWriter(purge("0000" + expression));
            }
            bw.writeLine(line);
        }
        if (bw != null)
            bw.close();
    }
    private static String purge(String name) {
        return name.replaceAll("[^A-Z,a-z,0-9,\\.]", "");
    }
    private static BufferedWriter getWriter(String name) {
        println(name);
        File file = new File(name);
        return new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file)));
    }
    private static void help() {
        println("""
            Usage: chopper <regular expression> [-i]

            Uses expression to chop blob of text up into files. Prints name of each
            file at command line. Files will be named using the line matched, with an index
            added if needed to guarantee uniqueness.

            Parameters:
                -i Case-insensitive
        """);
    }
}
