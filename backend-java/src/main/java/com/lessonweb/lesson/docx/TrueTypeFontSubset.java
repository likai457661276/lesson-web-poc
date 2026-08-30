package com.lessonweb.lesson.docx;

import org.apache.fontbox.ttf.TTFSubsetter;
import org.apache.fontbox.ttf.TrueTypeFont;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** FontBox remaps outlines; a format-12 cmap preserves full Unicode in Word. */
final class TrueTypeFontSubset {
    private TrueTypeFontSubset() { }

    static byte[] create(TrueTypeFont font, String characters) throws IOException {
        // FontBox 3's cmap writer only supports BMP. Keep its format-4 map
        // and add a full Unicode map from the same old/new glyph mapping.
        // Never copy GSUB/GPOS with references to the original glyph indices.
        TTFSubsetter subsetter = new TTFSubsetter(font, List.of(
                "cmap", "head", "hhea", "hmtx", "maxp", "loca", "glyf",
                "name", "OS/2", "post", "cvt ", "fpgm", "prep", "gasp"));
        int[] codePoints = characters.codePoints().distinct().sorted().toArray();
        java.util.Set<Integer> supplementaryGlyphs = new java.util.HashSet<>();
        for (int codePoint : codePoints) {
            if (codePoint <= 0xffff) subsetter.add(codePoint);
            else supplementaryGlyphs.add(font.getUnicodeCmapLookup().getGlyphId(codePoint));
        }
        subsetter.addGlyphIds(supplementaryGlyphs);
        Map<Integer, Integer> oldToNew = new HashMap<>();
        subsetter.getGIDMap().forEach((newId, oldId) -> oldToNew.put(oldId, newId));
        TreeMap<Integer, Integer> mappings = new TreeMap<>();
        for (int codePoint : codePoints) {
            int oldId = font.getUnicodeCmapLookup().getGlyphId(codePoint);
            if (oldId != 0) mappings.put(codePoint, oldToNew.get(oldId));
        }
        if (mappings.isEmpty()) throw new IOException("Font subset has no supported characters");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        subsetter.writeToStream(output);
        Map<String, byte[]> tables = readTables(output.toByteArray());
        tables.put("cmap", cmap(mappings, tables.get("cmap")));
        // FontBox emits only the 78-byte OS/2 v0 fields while retaining the
        // source version. Preserve the complete metrics/license table so Office
        // and LibreOffice do not reject a truncated v4/v5 font.
        tables.put("OS/2", font.getTableBytes(font.getOS2Windows()));
        ByteBuffer os2 = ByteBuffer.wrap(tables.get("OS/2"));
        os2.putShort(64, (short) Math.min(0xffff, mappings.firstKey()));
        os2.putShort(66, (short) Math.min(0xffff, mappings.lastKey()));
        return writeTables(tables);
    }

    private static byte[] cmap(Map<Integer, Integer> mappings, byte[] bmpCmap) {
        int bmpOffset = ByteBuffer.wrap(bmpCmap).getInt(8);
        byte[] bmp = java.util.Arrays.copyOfRange(bmpCmap, bmpOffset, bmpCmap.length);
        int length = 16 + 12 * mappings.size();
        ByteBuffer table = ByteBuffer.allocate(28 + bmp.length + length);
        table.putShort((short) 0).putShort((short) 3);
        table.putShort((short) 0).putShort((short) 4).putInt(28 + bmp.length);
        table.putShort((short) 3).putShort((short) 1).putInt(28);
        table.putShort((short) 3).putShort((short) 10).putInt(28 + bmp.length);
        table.put(bmp);
        table.putShort((short) 12).putShort((short) 0).putInt(length).putInt(0).putInt(mappings.size());
        mappings.forEach((codePoint, glyph) -> table.putInt(codePoint).putInt(codePoint).putInt(glyph));
        return table.array();
    }

    private static Map<String, byte[]> readTables(byte[] font) {
        ByteBuffer buffer = ByteBuffer.wrap(font);
        int count = Short.toUnsignedInt(buffer.getShort(4));
        Map<String, byte[]> tables = new TreeMap<>();
        for (int index = 0; index < count; index++) {
            int record = 12 + index * 16;
            String tag = new String(font, record, 4, StandardCharsets.US_ASCII);
            int offset = buffer.getInt(record + 8);
            int length = buffer.getInt(record + 12);
            tables.put(tag, java.util.Arrays.copyOfRange(font, offset, offset + length));
        }
        return tables;
    }

    private static byte[] writeTables(Map<String, byte[]> tables) {
        // Both table and whole-font checksums use a zero checkSumAdjustment.
        ByteBuffer.wrap(tables.get("head")).putInt(8, 0);
        int directorySize = 12 + 16 * tables.size();
        int size = directorySize + tables.values().stream().mapToInt(value -> aligned(value.length)).sum();
        ByteBuffer font = ByteBuffer.allocate(size);
        int power = Integer.highestOneBit(tables.size());
        font.putInt(0x00010000).putShort((short) tables.size()).putShort((short) (power * 16));
        font.putShort((short) Integer.numberOfTrailingZeros(power)).putShort((short) ((tables.size() - power) * 16));
        int offset = directorySize;
        int headOffset = 0;
        for (var entry : tables.entrySet()) {
            byte[] table = entry.getValue();
            font.put(entry.getKey().getBytes(StandardCharsets.US_ASCII));
            font.putInt(checksum(table)).putInt(offset).putInt(table.length);
            System.arraycopy(table, 0, font.array(), offset, table.length);
            if (entry.getKey().equals("head")) headOffset = offset;
            offset += aligned(table.length);
        }
        font.putInt(headOffset + 8, 0xB1B0AFBA - checksum(font.array()));
        return font.array();
    }

    private static int aligned(int length) {
        return (length + 3) & ~3;
    }

    private static int checksum(byte[] bytes) {
        int sum = 0;
        for (int index = 0; index < bytes.length; index++) {
            sum += (bytes[index] & 0xff) << (24 - 8 * (index % 4));
        }
        return sum;
    }
}
