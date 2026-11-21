package dev.chill.patchwork.nbtstrings;

import java.io.DataInput;
import java.io.IOException;
import java.io.UTFDataFormatException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * For the nbtstrings module: reads an NBT string (key or value) the way DataInput.readUTF does, but hands back one
 * shared String for each short string it has seen recently. Loaded chunks repeat a few dozen key names ("Name",
 * "Properties", "Palette", "data") and their palette values ("minecraft:stone") hundreds of times each, and readUTF made
 * a new String and byte[] for every one. Strings are immutable, so a shared one is the same value.
 * The table is fixed-size and shared by every thread: a slot holds the last string whose bytes hashed there and is
 * replaced on a miss, so it never grows. Entries are immutable records, so a thread racing another just sees an older
 * or newer entry, both correct.
 */
public final class NbtStrings {
    private static final int SLOTS = 4096;
    /** Longer strings (sign text, item names, JSON) rarely repeat; they are read as before, just not kept. */
    private static final int MAX_CACHED = 64;
    private static final Entry[] TABLE = new Entry[SLOTS];
    private static final ThreadLocal<byte[][]> SCRATCH = ThreadLocal.withInitial(() -> new byte[][]{new byte[256]});

    private record Entry(byte[] utf, int hash, String value) {}

    private NbtStrings() {}

    public static String readUTF(DataInput in) throws IOException {
        int len = in.readUnsignedShort();
        byte[][] holder = SCRATCH.get();
        byte[] buf = holder[0];
        if (buf.length < len) {
            buf = holder[0] = new byte[Math.max(len, buf.length * 2)];
        }
        in.readFully(buf, 0, len);
        if (len > MAX_CACHED) {
            return decode(buf, len);
        }
        int hash = hash(buf, len);
        int slot = hash & (SLOTS - 1);
        Entry entry = TABLE[slot];
        if (entry != null && entry.hash == hash && Arrays.equals(entry.utf, 0, entry.utf.length, buf, 0, len)) {
            return entry.value;
        }
        String value = decode(buf, len);
        TABLE[slot] = new Entry(Arrays.copyOf(buf, len), hash, value);
        return value;
    }

    private static int hash(byte[] buf, int len) {
        int h = len;
        for (int i = 0; i < len; i++) {
            h = 31 * h + buf[i];
        }
        return h ^ (h >>> 16);
    }

    /** Modified UTF-8, as DataInputStream.readUTF decodes it, with the same errors on malformed input. */
    private static String decode(byte[] buf, int len) throws UTFDataFormatException {
        int i = 0;
        while (i < len && buf[i] >= 0) {
            i++;
        }
        if (i == len) {
            return new String(buf, 0, len, StandardCharsets.ISO_8859_1);
        }
        char[] chars = new char[len];
        for (int k = 0; k < i; k++) {
            chars[k] = (char) buf[k];
        }
        int n = i;
        while (i < len) {
            int c = buf[i] & 0xFF;
            switch (c >> 4) {
                case 0, 1, 2, 3, 4, 5, 6, 7 -> {
                    i++;
                    chars[n++] = (char) c;
                }
                case 12, 13 -> {
                    i += 2;
                    if (i > len) {
                        throw new UTFDataFormatException("malformed input: partial character at end");
                    }
                    int c2 = buf[i - 1];
                    if ((c2 & 0xC0) != 0x80) {
                        throw new UTFDataFormatException("malformed input around byte " + i);
                    }
                    chars[n++] = (char) (((c & 0x1F) << 6) | (c2 & 0x3F));
                }
                case 14 -> {
                    i += 3;
                    if (i > len) {
                        throw new UTFDataFormatException("malformed input: partial character at end");
                    }
                    int c2 = buf[i - 2];
                    int c3 = buf[i - 1];
                    if ((c2 & 0xC0) != 0x80 || (c3 & 0xC0) != 0x80) {
                        throw new UTFDataFormatException("malformed input around byte " + (i - 1));
                    }
                    chars[n++] = (char) (((c & 0x0F) << 12) | ((c2 & 0x3F) << 6) | (c3 & 0x3F));
                }
                default -> throw new UTFDataFormatException("malformed input around byte " + i);
            }
        }
        return new String(chars, 0, n);
    }
}
