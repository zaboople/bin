import java.util.regex.Pattern



def parseTemplate(String templateStr) {
    List template=[]
    def yell={s->
        template.add({__->print(s)})
    }
    def yellItem={i->
        i= i-1
        template.add({items->
            if (i<items.length)
                print(items[i])
        })
    }
    def grabIndex={endIndex, afterIndex->
        int index=Integer.parseInt(templateStr.substring(0, endIndex))
        yellItem(index)
        templateStr=templateStr.substring(afterIndex)
    }
    def addRemainder={i->
        String remainder=templateStr.substring(0, i)
        if (remainder!="")
            yell(remainder)
    }
    def lteq={a, b->
        a > -1 && (a <= b || b==-1)
    }

    Pattern patternNoDigits=Pattern.compile("\\D")

    while (true) {
        int i1=templateStr.indexOf("\${")
        int i2=templateStr.indexOf("\$")
        int i3=templateStr.indexOf("\\\$")
        int i4=templateStr.indexOf("\\\\\$")
        println("$i1 $i2 $i3 $i4 >$templateStr")
        if (i1+i2==-2) {
            break
        }
        if (lteq(i1, i3) && lteq(i1, i2)) {
            addRemainder(i1)
            templateStr=templateStr.substring(i1+2)
            int endBrack=templateStr.indexOf("}")
            if (endBrack==-1)
                throw new Exception("Could not find ending bracket: $templateStr")
            grabIndex(endBrack, endBrack+1)
        } else if (lteq(i2, i3)) {
            addRemainder(i2)
            templateStr=templateStr.substring(i2+1)
            def matcher=patternNoDigits.matcher(templateStr)
            int endIndex=matcher.find()
                ?matcher.end() -1
                :templateStr.length()
            if (endIndex>0) {
                grabIndex(endIndex, endIndex)
            }
        } else if (lteq(i3, i4)) {
            addRemainder(i3)
            yell("\$")
            templateStr=templateStr.substring(i3+2)
        } else {
            addRemainder(i4)
            yell("\\")
            templateStr=templateStr.substring(i4+2)
        }
    }
    if (templateStr!="")
        yell(templateStr)
    template
}

def doRunRun(String[] args) {
    List template=parseTemplate(args[0])
    BufferedReader br=new BufferedReader(new InputStreamReader(System.in))
    String line
    while ((line=br.readLine())!=null) {
        String[] items = line.split(" ").findAll{"" != it}
        if (items.length!=0)
            for (x in template)
                x.call(items)
        println()
    }
}

if (args.length==0 || args[0].startsWith("-help") || args[0].startsWith("--help")) {
    println("""
        Usage: gook <template>

        Gook is a gawk/awk replacement that allows you to just use a template with
        \$# syntax for indexed variables. For example:

           cat myfile | gook 'First value is \$1, Second value is \$2'

        And of course you can do the "stricter" way of saying variable names:

           cat myfile | gook 'First value is \${1}, Second value is \${2}'

        But how to escape the \$ symbol? With \\ of course:

           cat myfile | gook 'Gimme all your \\\$\\\$\\\$. Also: First value is \$1, Second value is \$2'

        (You can escape the \\ itself with \\\\ if you actually want to put a
         backslash in front of your variable because you're recursively making templates
         or god knows what. But normally you can just leave backslashes as backslashes)

        But of course if you double-quote instead of single-quote, you'll need
        a \\ to escape your shell's parser:

           cat myfile | gook "Gimme all your \\\$\\\$\\\$. Also: First value is \\\$1, Second value is \\\$2"

    """.stripIndent())
    return
}
doRunRun(args)
