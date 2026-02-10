import groovy.json.JsonSlurper
import groovy.yaml.YamlSlurper;
import groovy.yaml.YamlBuilder;

/**
 * A JSON parser that makes the json easier to read. Use -help to learn more.
 */
public class MommyYaml {
    static int squashLimit=3
    static final YamlSlurper yamlParser = new YamlSlurper();

    public static void main(String[] args) {
        String datafile=null;
        for (int i=0; i<args.length; i++)
            if (args[i].startsWith("-f") || args[i].startsWith("--f"))
                datafile = args[++i];
            else
            if (args[i].startsWith("-h") || args[i].startsWith("--h")) {
                println helpBlob;
                return;
            }
            else
            if (datafile == null) {
                datafile = args[i];
            }
            else {
                println("ERROR: Unrecognized parameter: "+args[i]);
                println helpBlob;
                System.exit(1);
            }

        Object datablob = datafile != null ?parseYamlFile(datafile) :parseYaml(System.in);
        crawl2(datablob, "");
    }

    private static Object parseYamlFile(String filename) {
        InputStream istr;
        try {
            istr=new FileInputStream(filename);
        } catch (Exception e) {
            println("Couldn't open: \""+filename+"\"\nbecause: "+e);
            System.exit(1);
        }
        return parseYaml(istr);
    }

    private static Object parseYaml(InputStream istr) {
        try {
            return yamlParser.parse(istr);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    /** Does the main crawl & print. */
    private static void crawl2(Object json, String mommies) {
        if (json instanceof java.util.Map) {
            // Sorting is 1. simple values before maps/lists 2. alphabetic by key
            json.each{key, value-> crawl2(value, mommies +"."+key)};
        } else if (json instanceof java.util.List) {
            if (mommies.equals(""))
                mommies="<root>";
            json.indexed().each{index, value->
                crawl2(value, mommies+"["+index+"]")
            };
        } else  {
            println(mommies + "=" + json);
        }
    }


    private final static String helpBlob = """
        ---------------------------------------------------------------------------------------------------

        MommyYaml is a YAML pretty-printer/hand-holder for people with giant yaml files that are impossible
        to understand because of ridiculously deep nesting as well as massive arrays and maps. As the name
        implies, the parentage of a node is displayed to its left as a sort of breadcrumb, so that you know
        where the bleep you are, roughly analagous to a javascript assignment statement.

        Arguments: [[-f] <filename>]  "
            -f <filename>:
                A json file we can parse. Otherwise we use stdin by default.
                    Technically the -f is optional

        ---------------------------------------------------------------------------------------------------
    """.stripIndent()
}
