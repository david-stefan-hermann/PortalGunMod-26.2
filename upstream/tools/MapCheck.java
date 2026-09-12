import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.lang.classfile.*;

public class MapCheck {
    static Map<String,String> int2obfClass = new HashMap<>();
    static Map<String,List<String[]>> intMethod = new HashMap<>();
    static Map<String,List<String[]>> intField = new HashMap<>();
    static Map<String,String> obf2named = new HashMap<>();
    static Map<String,String> named2obf = new HashMap<>();
    static Map<String,String> pgMethod = new HashMap<>();
    static Map<String,String> pgField = new HashMap<>();
    static Map<String,ClassModel> mc262 = new HashMap<>();

    static String jvmType(String t) {
        int dims = 0; while (t.endsWith("[]")) { dims++; t = t.substring(0, t.length()-2); }
        String b = switch (t) { case "void"->"V"; case "int"->"I"; case "long"->"J"; case "boolean"->"Z"; case "byte"->"B";
            case "short"->"S"; case "char"->"C"; case "float"->"F"; case "double"->"D";
            default -> { String s = t.replace('.', '/'); String o = named2obf.get(s); yield "L" + (o != null ? o : s) + ";"; } };
        return "[".repeat(dims) + b;
    }

    public static void main(String[] a) throws Exception {
        String cur = null;
        for (String line : Files.readAllLines(Path.of("inter/mappings/mappings.tiny"))) {
            String[] p = line.split("\t");
            if (p[0].equals("c")) { cur = p[1]; int2obfClass.put(p[2], p[1]); int d = Math.max(p[2].lastIndexOf('$'), p[2].lastIndexOf('/')); int2obfClass.putIfAbsent("simple:" + p[2].substring(d + 1), p[1]); }
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
        try (JarFile jf = new JarFile(System.getProperty("user.home") + "/.gradle/caches/fabric-loom/26.2/minecraft-merged.jar")) {
            for (Enumeration<JarEntry> e = jf.entries(); e.hasMoreElements();) {
                JarEntry je = e.nextElement();
                if (je.getName().endsWith(".class") && (je.getName().startsWith("net/minecraft") || je.getName().startsWith("com/mojang"))) {
                    mc262.put(je.getName().substring(0, je.getName().length() - 6), ClassFile.of().parse(jf.getInputStream(je).readAllBytes()));
                }
            }
        }
        System.out.println("26.2 classes loaded: " + mc262.size());
        Map<String,String> simple262 = new HashMap<>();
        for (String k : mc262.keySet()) simple262.merge(k.substring(k.lastIndexOf('/') + 1), k, (x, y) -> x + "," + y);

        try (PrintWriter cw = new PrintWriter("report-classes.tsv")) {
            cw.println("intermediary\tnamed_1.21.11\tin_26.2\thint");
            for (String ic : Files.readAllLines(Path.of("../classes.txt"))) {
                String obf = int2obfClass.get("net/minecraft/" + ic); if (obf == null) obf = int2obfClass.get("simple:" + ic); String named = obf == null ? "?" : obf2named.getOrDefault(obf, "?" + obf);
                boolean ok = mc262.containsKey(named);
                String hint = ""; if (!ok) { String sn = named.substring(named.lastIndexOf('/') + 1); String c = simple262.get(sn); hint = c == null ? "" : "same simple name at: " + c; }
                cw.println(ic + "\t" + named + "\t" + (ok ? "yes" : "NO") + "\t" + hint);
            }
        }
        try (PrintWriter mw = new PrintWriter("report-methods.tsv")) {
            mw.println("intermediary\towner_named\tmethod_named\towner_in_26.2\tmethod_in_26.2");
            for (String im : Files.readAllLines(Path.of("../methods.txt"))) {
                List<String[]> owners = intMethod.get(im);
                if (owners == null) { mw.println(im + "\t?\t?\t?\t?"); continue; }
                String out = null;
                for (String[] o : owners) {
                    String namedOwner = obf2named.get(o[0]); if (namedOwner == null) continue;
                    String namedM = pgMethod.get(o[0] + "|" + o[1] + "|" + o[2]); if (namedM == null) continue;
                    boolean ownerOk = mc262.containsKey(namedOwner);
                    boolean mOk = ownerOk && hasMethod(namedOwner, namedM, new HashSet<>());
                    out = im + "\t" + namedOwner + "\t" + namedM + "\t" + (ownerOk ? "yes" : "NO") + "\t" + (mOk ? "yes" : "NO");
                    if (mOk) break;
                }
                mw.println(out == null ? im + "\t" + owners.get(0)[0] + "/" + owners.get(0)[1] + "\tunresolved\t?\t?" : out);
            }
        }
        try (PrintWriter fw = new PrintWriter("report-fields.tsv")) {
            fw.println("intermediary\towner_named\tfield_named\towner_in_26.2\tfield_in_26.2");
            for (String f : Files.readAllLines(Path.of("../fields.txt"))) {
                List<String[]> owners = intField.get(f);
                if (owners == null) { fw.println(f + "\t?\t?\t?\t?"); continue; }
                String out = null;
                for (String[] o : owners) {
                    String namedOwner = obf2named.get(o[0]); if (namedOwner == null) continue;
                    String namedF = pgField.get(o[0] + "|" + o[1]); if (namedF == null) continue;
                    boolean ownerOk = mc262.containsKey(namedOwner);
                    boolean fOk = ownerOk && hasField(namedOwner, namedF, new HashSet<>());
                    out = f + "\t" + namedOwner + "\t" + namedF + "\t" + (ownerOk ? "yes" : "NO") + "\t" + (fOk ? "yes" : "NO");
                    if (fOk) break;
                }
                fw.println(out == null ? f + "\tunresolved\t?\t?\t?" : out);
            }
        }
        System.out.println("done");
    }
    static boolean hasMethod(String cls, String name, Set<String> seen) {
        ClassModel cm = mc262.get(cls); if (cm == null || !seen.add(cls)) return false;
        for (MethodModel m : cm.methods()) if (m.methodName().stringValue().equals(name)) return true;
        if (cm.superclass().isPresent() && hasMethod(cm.superclass().get().asInternalName(), name, seen)) return true;
        for (var i : cm.interfaces()) if (hasMethod(i.asInternalName(), name, seen)) return true;
        return false;
    }
    static boolean hasField(String cls, String name, Set<String> seen) {
        ClassModel cm = mc262.get(cls); if (cm == null || !seen.add(cls)) return false;
        for (FieldModel f : cm.fields()) if (f.fieldName().stringValue().equals(name)) return true;
        if (cm.superclass().isPresent() && hasField(cm.superclass().get().asInternalName(), name, seen)) return true;
        for (var i : cm.interfaces()) if (hasField(i.asInternalName(), name, seen)) return true;
        return false;
    }
}
