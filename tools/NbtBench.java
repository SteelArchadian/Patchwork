import it.unimi.dsi.fastutil.objects.*;
import java.io.*;
import java.lang.management.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Offline alloc check of Patchwork's NBT load changes on real chunk data: strings (JDK readUTF vs NbtStrings) and compound maps (vanilla HashMap, fastutil default, Patchwork scratch+exact). */
public class NbtBench {
    static int strMode, mapMode;
    static final ArrayDeque<Object2ObjectOpenHashMap<String,Object>> SCRATCH = new ArrayDeque<>();
    static String str(DataInput in) throws IOException {
        return strMode == 0 ? in.readUTF() : dev.chill.patchwork.nbtstrings.NbtStrings.readUTF(in);
    }
    static Object payload(DataInput in, int type) throws IOException {
        switch (type) {
            case 1: return in.readByte();
            case 2: return in.readShort();
            case 3: return in.readInt();
            case 4: return in.readLong();
            case 5: return in.readFloat();
            case 6: return in.readDouble();
            case 7: { byte[] b = new byte[in.readInt()]; in.readFully(b); return b; }
            case 8: return str(in);
            case 9: { int t = in.readByte(); int n = in.readInt(); ArrayList<Object> l = new ArrayList<>(n); for (int i = 0; i < n; i++) l.add(payload(in, t)); return l; }
            case 10: return compound(in);
            case 11: { int[] a = new int[in.readInt()]; for (int i = 0; i < a.length; i++) a[i] = in.readInt(); return a; }
            case 12: { long[] a = new long[in.readInt()]; for (int i = 0; i < a.length; i++) a[i] = in.readLong(); return a; }
        }
        throw new IOException("bad type " + type);
    }
    static Map<String,Object> compound(DataInput in) throws IOException {
        Map<String,Object> m;
        if (mapMode == 0) m = new HashMap<>();
        else if (mapMode == 1) m = new Object2ObjectOpenHashMap<>();
        else if (mapMode == 2) m = new Object2ObjectOpenHashMap<>(4);
        else { var s = SCRATCH.pollLast(); m = s != null ? s : new Object2ObjectOpenHashMap<>(16); }
        int t;
        while ((t = in.readByte()) != 0) { String k = str(in); m.put(k, payload(in, t)); }
        if (mapMode == 3) {
            var scratch = (Object2ObjectOpenHashMap<String,Object>) m; int size = scratch.size(); Map<String,Object> exact;
            if (size <= 8) { Object[] ks = new Object[size], vs = new Object[size]; int i = 0;
                for (var it = scratch.object2ObjectEntrySet().fastIterator(); it.hasNext();) { var e = it.next(); ks[i] = e.getKey(); vs[i++] = e.getValue(); }
                exact = size == 0 ? new Object2ObjectArrayMap<>() : new Object2ObjectArrayMap<>(ks, vs, size);
            } else { var sized = new Object2ObjectOpenHashMap<String,Object>(size);
                for (var it = scratch.object2ObjectEntrySet().fastIterator(); it.hasNext();) { var e = it.next(); sized.put(e.getKey(), e.getValue()); } exact = sized; }
            scratch.clear(); if (size > 64) scratch.trim(16); SCRATCH.addLast(scratch); m = exact;
        }
        return m;
    }
    public static void main(String[] a) throws Exception {
        List<byte[]> chunks = new ArrayList<>();
        for (int f = 0; f < a.length && chunks.size() < 3000; f++) {
            byte[] r = Files.readAllBytes(Path.of(a[f]));
            for (int i = 0; i < 1024 && r.length >= 8192; i++) {
                int off = ((r[i*4]&255)<<16|(r[i*4+1]&255)<<8|(r[i*4+2]&255)) * 4096; if (off == 0) continue;
                int len = (r[off]&255)<<24|(r[off+1]&255)<<16|(r[off+2]&255)<<8|(r[off+3]&255); int comp = r[off+4];
                if (comp != 2) continue;
                chunks.add(new InflaterInputStream(new ByteArrayInputStream(r, off+5, len-1)).readAllBytes());
            }
        }
        System.out.println("chunks " + chunks.size());
        var mx = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean(); long tid = Thread.currentThread().getId();
        String[] names = {"strings JDK  + HashMap(vanilla)", "strings JDK  + O2O default(lithium)", "strings JDK  + O2O(4)", "strings JDK  + scratch/exact (PW maps)", "NbtStrings   + scratch/exact (PW 1.0.25)", "NbtStrings   + HashMap"};
        int[][] modes = {{0,0},{0,1},{0,2},{0,3},{1,3},{1,0}};
        for (int round = 0; round < 3; round++) for (int k = 0; k < modes.length; k++) {
            strMode = modes[k][0]; mapMode = modes[k][1];
            long b0 = mx.getThreadAllocatedBytes(tid);
            for (byte[] c : chunks) { var in = new DataInputStream(new ByteArrayInputStream(c)); in.readByte(); in.readUTF(); compound(in); }
            long b1 = mx.getThreadAllocatedBytes(tid);
            if (round == 2) System.out.printf("%-42s %8.1f MB  (%.0f KB/chunk)%n", names[k], (b1-b0)/1e6, (b1-b0)/1024.0/chunks.size());
        }
    }
}
