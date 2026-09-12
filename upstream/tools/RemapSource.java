import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/** Rewrites Vineflower output (intermediary names) to Mojang names using intermediary tiny v2 + proguard client.txt (1.21.11). */
public class RemapSource {
    static Map<String,String> int2obfClass = new HashMap<>();      // net/minecraft/class_1 or a$class_2 -> obf
    static Map<String,List<String[]>> intMethod = new HashMap<>(); // method_x -> [obfOwner, obfName, obfDesc]
    static Map<String,List<String[]>> intField = new HashMap<>();
    static Map<String,String> obf2named = new HashMap<>();
    static Map<String,String> named2obf = new HashMap<>();
    static Map<String,String> pgMethod = new HashMap<>();
    static Map<String,String> pgField = new HashMap<>();

    static String jvmType(String t) {
        int dims = 0; while (t.endsWith("[]")) { dims++; t = t.substring(0, t.length()-2); }
        String b = switch (t) { case "void"->"V"; case "int"->"I"; case "long"->"J"; case "boolean"->"Z"; case "byte"->"B";
            case "short"->"S"; case "char"->"C"; case "float"->"F"; case "double"->"D";
            default -> { String s = t.replace('.', '/'); String o = named2obf.get(s); yield "L" + (o != null ? o : s) + ";"; } };
        return "[".repeat(dims) + b;
    }

    public static void main(String[] a) throws Exception {
        Path srcDir = Path.of(a[0]), outDir = Path.of(a[1]);
        String cur = null;
        for (String line : Files.readAllLines(Path.of("inter/mappings/mappings.tiny"))) {
            String[] p = line.split("\t");
            if (p[0].equals("c")) { cur = p[1]; int2obfClass.put(p[2], p[1]); }
            else if (p.length >= 5 && p[1].equals("m")) intMethod.computeIfAbsent(p[4], k -> new ArrayList<>()).add(new String[]{cur, p[3], p[2]});
            else if (p.length >= 5 && p[1].equals("f")) intField.computeIfAbsent(p[4], k -> new ArrayList<>()).add(new String[]{cur, p[3], p[2]});
        }
        List<String> pg = Files.readAllLines(Path.of("client.txt"));
        for (String line : pg) {
            if (line.startsWith("#") || line.startsWith(" ")) continue;
            String[] p = line.split(" -> "); String named = p[0].replace('.', '/'); String obf = p[1].replace(":", "").replace('.', '/');
            obf2named.put(obf, named); named2obf.put(named, obf);
        }
        String curObf = null;
        for (String line : pg) {
            if (line.startsWith("#")) continue;
            if (!line.startsWith(" ")) { curObf = line.split(" -> ")[1].replace(":", "").replace('.', '/'); continue; }
            String s = line.trim(); String[] lr = s.split(" -> "); String obfName = lr[1];
            String left = lr[0].replaceAll("^\\d+:\\d+:", "");
            String[] tn = left.split(" ", 2); String type = tn[0]; String nameArgs = tn[1];
            int par = nameArgs.indexOf('(');
            if (par < 0) { pgField.put(curObf + "|" + obfName, nameArgs); continue; }
            String name = nameArgs.substring(0, par); String args = nameArgs.substring(par + 1, nameArgs.length() - 1);
            StringBuilder d = new StringBuilder("(");
            if (!args.isEmpty()) for (String arg : args.split(",")) d.append(jvmType(arg));
            d.append(")").append(jvmType(type));
            pgMethod.put(curObf + "|" + obfName + "|" + d, name);
        }
        // intermediary full class name (with $) -> named full name (with $)
        Map<String,String> classFull = new HashMap<>();
        for (var e : int2obfClass.entrySet()) { String n = obf2named.get(e.getValue()); if (n != null) classFull.put(e.getKey(), n); }
        Map<String,String> simpleClass = new HashMap<>(); // class_123 -> Named simple
        for (var e : classFull.entrySet()) { String k = e.getKey(); String v = e.getValue();
            String ks = k.substring(Math.max(k.lastIndexOf('$'), k.lastIndexOf('/')) + 1);
            String vs = v.substring(Math.max(v.lastIndexOf('$'), v.lastIndexOf('/')) + 1);
            simpleClass.putIfAbsent(ks, vs); }
        Map<String,String> methodName = new HashMap<>();
        for (var e : intMethod.entrySet()) for (String[] o : e.getValue()) { String n = pgMethod.get(o[0] + "|" + o[1] + "|" + o[2]); if (n != null) { methodName.putIfAbsent(e.getKey(), n); break; } }
        Map<String,String> fieldName = new HashMap<>();
        for (var e : intField.entrySet()) for (String[] o : e.getValue()) { String n = pgField.get(o[0] + "|" + o[1]); if (n != null) { fieldName.putIfAbsent(e.getKey(), n); break; } }

        Pattern fq = Pattern.compile("net\\.minecraft\\.class_\\d+(\\.class_\\d+)*");
        Pattern tok = Pattern.compile("\\b(class|method|field)_(\\d+)\\b");
        Set<String> unresolved = new TreeSet<>();
        try (var walk = Files.walk(srcDir)) {
            for (Path p : (Iterable<Path>) walk.filter(f -> f.toString().endsWith(".java"))::iterator) {
                String text = Files.readString(p);
                // 1. fully qualified intermediary class chains -> named (imports and inline)
                Matcher m = fq.matcher(text); StringBuilder sb = new StringBuilder();
                while (m.find()) {
                    String key = m.group().replace("net.minecraft.", "net/minecraft/").replace('.', '$').replace("net/minecraft/", "net/minecraft/");
                    // key like net/minecraft/class_304$class_11900
                    String named = classFull.get(key);
                    if (named == null) { unresolved.add(m.group()); m.appendReplacement(sb, Matcher.quoteReplacement(m.group())); }
                    else m.appendReplacement(sb, Matcher.quoteReplacement(named.replace('/', '.').replace('$', '.')));
                }
                m.appendTail(sb); text = sb.toString();
                // 2. simple tokens
                m = tok.matcher(text); sb = new StringBuilder();
                while (m.find()) {
                    String whole = m.group(); String rep = switch (m.group(1)) {
                        case "class" -> simpleClass.get(whole);
                        case "method" -> methodName.get(whole);
                        default -> fieldName.get(whole); };
                    if (rep == null) { unresolved.add(whole); rep = whole; }
                    m.appendReplacement(sb, Matcher.quoteReplacement(rep));
                }
                m.appendTail(sb); text = sb.toString();
                Path out = outDir.resolve(srcDir.relativize(p));
                Files.createDirectories(out.getParent());
                Files.writeString(out, text.replace("\r\n", "\n"));
            }
        }
        System.out.println("unresolved: " + unresolved);
        // also dump the used-symbol table for reference
        try (PrintWriter w = new PrintWriter(outDir.resolve("MAPPING-TABLE.tsv").toFile())) {
            w.println("intermediary\tnamed_1.21.11");
            for (String c : Files.readAllLines(Path.of("../classes.txt"))) { String full = null;
                for (var e : classFull.entrySet()) if (e.getKey().endsWith("/" + c) || e.getKey().endsWith("$" + c)) { full = e.getValue(); break; }
                w.println(c + "\t" + (full == null ? "?" : full.replace('/', '.'))); }
            for (String x : Files.readAllLines(Path.of("../methods.txt"))) w.println(x + "\t" + methodName.getOrDefault(x, "?"));
            for (String x : Files.readAllLines(Path.of("../fields.txt"))) w.println(x + "\t" + fieldName.getOrDefault(x, "?"));
        }
    }
}
