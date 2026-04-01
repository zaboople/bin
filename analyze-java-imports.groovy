import java.io.*;

public class AnalyzeJavaImports {

    public static void main(String[] args) {
        List files = [];
        for (int i=0; i<args.length; i++) {
            files.add(args[i]);
        }
        if (files.isEmpty())
            analyze(System.in);
        else
            for (file in files)
                analyze(new FileInputStream(new File(file)));
    }
    private static void analyze(InputStream is) {
        String text = is.getText();
        List lines = text.split("(\r|\n)+");
        List toJoin = [];
        List toCheck = [];
        for (String line: lines)
            if (line =~ /^ *import/) {
                int endImport = line.indexOf("import") + "import".length();
                String packagePart = line.substring(endImport).replace(";", "").trim();
                List packageNodes = packagePart.split("\\.");
                String lastNode = packageNodes.last().trim();
                if (lastNode == "*")
                    continue;
                toCheck.add([line: line, lastNode: lastNode]);
            } else {
                toJoin.add(line);
            }
        final String toJoinText = toJoin.join("\n");
        List results = [];
        for (Map stuff: toCheck) {
            boolean matches = toJoinText =~ /( |	|[^A-Z^a-z^0-9])+${stuff.lastNode} *[^A-Z^a-z^0-9]+/;
            if (!matches) {
                results.add("Questionable ${stuff.line}");
            }
        }
        if (results.isEmpty())
            println("GOOD");
        else
            for (String s: results) {
                println("Unused: $s");
            }
    }
}
