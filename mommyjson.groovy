import groovy.json.JsonSlurper

/**
 * MommyJson is a JSON pretty-printer/hand-holder for people with giant json files that are impossible to understand
 * because of ridiculously deep nesting as well as massive arrays and maps. As the name implies, the parentage
 * of a node is displayed to its left as a sort of breadcrumb, so that you know where the bleep you are.
 *
 * Note that MommyJson produces JSON-complaint output, not JSON-compliant output.
 */
public class MommyJson {

    static int squashLimit=3

    public static void main(String[] args) {
        def json=null
        for (int i=0; i<args.length; i++)
            if (args[i].startsWith("-f") || args[i].startsWith("--f"))
                try {
                    json=new JsonSlurper().parse(new FileInputStream(args[++i]))
                } catch (Exception e) {
                    println("Couldn't open: "+args[i]+"\nbecause: "+e)
                    System.exit(1)
                }
            else
            if (args[i].startsWith("-s") || args[i].startsWith("--s"))
                try {
                    squashLimit=Integer.parseInt(args[++i])
                } catch (Exception e) {
                    println("Couldn't parse squash size: "+args[i]+"\nbecause: "+e)
                    System.exit(1)
                }
            else
            if (args[i].startsWith("-h") || args[i].startsWith("--h")) {
                println """
                    Arguments: [-f <filename>] [-s <squash-size>] "
                        -f <filename>:
                            A json file we can parse. If not given a file, we'll just look for
                            data in stdin.
                        -s <squash-size>:
                            A numeric "depth" that tells us whether to "squash" the current node
                            onto one line, or break it out into multiple lines. The larger the
                            squash size, the more likely we will try to squash on one line. A
                            value of "0" will of course disable squashing entirely.
                """
                return
            }
            else {
                println("Unrecognized parameter: "+args[i])
                System.exit(1)
            }

        if (json==null)
            json=new JsonSlurper().parse(System.in)
        crawl(json, "")
    }

    /** Does the main crawl & print. */
    private static void crawl(Object json, String mommies) {
        if (json instanceof java.util.Map) {
            if (getSize(json) <= squashLimit) {
                print(mommies)
                miniPrint(json)
                println()
                return
            }
            json=json.toSorted(
                [compare: { e1, e2 ->
                    if ((e2.value instanceof Collection) && !(e1.value instanceof Collection))
                        -1
                    else
                    if ((e1.value instanceof Collection) && !(e2.value instanceof Collection))
                        1
                    else
                        e1.key.compareTo(e2.key)
                }] as Comparator
            )
            print(mommies)
            println("{")
            json.each{key, value-> crawl(value, mommies +"  "+key +": ")}
            print(mommies)
            println("}")
        } else if (json instanceof java.util.List) {
            if (getSize(json) <= squashLimit) {
                print(mommies)
                miniPrint(json)
                println()
                return
            }
            print(mommies)
            println("[")
            final int sz=json.size()
            json.indexed().each{index, value->
                crawl(value, mommies+"  ["+index+"] ")
                //if (index < sz-1) {print(mommies); println("  ,")}
            }
            print(mommies)
            println("]")
        } else  {
            print(mommies)
            println(json)
        }
    }

    /** This squashes a map/list into one-line output */
    private static void miniPrint(Object json) {
        if (json instanceof Map) {
            print("{")
            int sz=json.size()
            json.eachWithIndex{key, value, index->
                print(key)
                print(": ")
                miniPrint(value)
                if (index < sz-1)
                    print(", ")
            }
            print("}")
        } else if (json instanceof List) {
            print("[")
            int sz=json.size()
            json.indexed().each{index, value->
                miniPrint(value)
                if (index<sz-1)
                    print(", ")
            }
            print("]")
        } else
            print(json)
    }

    /** This gets a size estimate on the json's nesting; we decide whether to squash or not using that value. */
    private static int getSize(Object json) {
        int mysize=1
        if (json instanceof Map)
            json.each{__, value->
                mysize+=getSize(value)
                if (mysize>squashLimit)
                    return
            }
        else if (json instanceof List)
            json.each{value->
                mysize+=getSize(value)
                if (mysize>squashLimit)
                    return
            }
        mysize
    }
}
