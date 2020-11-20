import java.io.*
if (args.length!=3 || args[0].startsWith("-h")) {
    println("""
        Usage sidebyside <width> <file1> <file2>
            width: This is the left-column width
    """)
    return
}
final int askWidth=Integer.parseInt(args[0])
final File f1=new File(args[1]), f2=new File(args[2])

def getReader={f->
    if (!f.exists()) {
        println("Not a file $f")
        System.exit(1)
    }
    return new BufferedReader(new InputStreamReader(new FileInputStream(f)))
}
def r1=getReader(f1), r2=getReader(f2)
def has1=true, has2=true
while (has1 || has2) {
    String line1, line2
    if (has1)
        has1=(line1=r1.readLine())!=null
    if (has2)
        has2=(line2=r2.readLine())!=null
    int remains=askWidth
    if (has1) {
        print(line1)
        remains-=line1.length()
    }
    if (has2) {
        if (remains>0)
            print(" "*remains)
        print(line2)
    }
    println()
}