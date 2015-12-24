package com.itextpdf.basics.font.otf;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Lookup Type 2:
 * Pair Adjustment Positioning Subtable
 */
public class GposLookupType2 extends OpenTableLookup {

    private List<OpenTableLookup> listRules = new ArrayList<>();

    public GposLookupType2(OpenTypeFontTableReader openReader, int lookupFlag, int[] subTableLocations) throws IOException {
        super(openReader, lookupFlag, subTableLocations);
        readSubTables();
    }

    @Override
    public boolean transformOne(GlyphLine line) {
        if (line.idx >= line.end)
            return false;
        if (openReader.isSkip(line.glyphs.get(line.idx).index, lookupFlag)) {
            line.idx++;
            return false;
        }
        for (OpenTableLookup lookup : listRules) {
            if (lookup.transformOne(line))
                return true;
        }
        ++line.idx;
        return false;
    }

    @Override
    protected void readSubTable(int subTableLocation) throws IOException {
        openReader.rf.seek(subTableLocation);
        int gposFormat = openReader.rf.readShort();
        switch (gposFormat) {
            case 1: {
                PairPosAdjustmentFormat1 format1 = new PairPosAdjustmentFormat1(openReader, lookupFlag, subTableLocation);
                listRules.add(format1);
                break;
            }
            case 2: {
                PairPosAdjustmentFormat2 format2 = new PairPosAdjustmentFormat2(openReader, lookupFlag, subTableLocation);
                listRules.add(format2);
                break;
            }
        }
    }

    private static class PairPosAdjustmentFormat1 extends OpenTableLookup {
        private Map<Integer,Map<Integer,PairValueFormat>> gposMap = new HashMap<>();

        public PairPosAdjustmentFormat1(OpenTypeFontTableReader openReader, int lookupFlag, int subtableLocation) throws IOException {
            super(openReader, lookupFlag, null);
            readFormat(subtableLocation);
        }

        public boolean transformOne(GlyphLine line) {
            if (line.idx >= line.end || line.idx < line.start)
                return false;
            boolean changed = false;
            Glyph g1 = line.glyphs.get(line.idx);
            Map<Integer,PairValueFormat> m = gposMap.get(g1.index);
            if (m != null) {
                GlyphIndexer gi = new GlyphIndexer();
                gi.line = line;
                gi.idx = line.idx;
                gi.nextGlyph(openReader, lookupFlag);
                if (gi.glyph != null) {
                    PairValueFormat pv = m.get(gi.glyph.index);
                    if (pv != null) {
                        Glyph g2 = gi.glyph;
                        // TODO
                        //line.glyphs.set(line.idx, new Glyph(g1, pv.first.XPlacement, pv.first.YPlacement, pv.first.XAdvance, pv.first.YAdvance, 0));
                        //line.glyphs.set(gi.idx, new Glyph(g2, pv.second.XPlacement, pv.second.YPlacement, pv.second.XAdvance, pv.second.YAdvance, 0));
                        line.idx = gi.idx;
                        changed = true;
                    }
                }
            }
            return changed;
        }

        protected void readFormat(int subTableLocation) throws IOException {
            int coverage = openReader.rf.readUnsignedShort() + subTableLocation;
            int valueFormat1 = openReader.rf.readUnsignedShort();
            int valueFormat2 = openReader.rf.readUnsignedShort();
            int pairSetCount = openReader.rf.readUnsignedShort();
            int[] locationRule = openReader.readUShortArray(pairSetCount, subTableLocation);
            List<Integer> coverageList = openReader.readCoverageFormat(coverage);
            for (int k = 0; k < pairSetCount; ++k) {
                openReader.rf.seek(locationRule[k]);
                HashMap<Integer,PairValueFormat> pairs = new HashMap<>();
                gposMap.put(coverageList.get(k), pairs);
                int pairValueCount = openReader.rf.readUnsignedShort();
                for (int j = 0; j < pairValueCount; ++j) {
                    int glyph2 = openReader.rf.readUnsignedShort();
                    PairValueFormat pair = new PairValueFormat();
                    pair.first = openReader.ReadGposValueRecord(valueFormat1);
                    pair.second = openReader.ReadGposValueRecord(valueFormat2);
                    pairs.put(glyph2, pair);
                }
            }
        }

        @Override
        protected void readSubTable(int subTableLocation) throws IOException {
            //never called here
        }
    }

    private static class PairPosAdjustmentFormat2 extends OpenTableLookup {
        private OtfClass classDef1;
        private OtfClass classDef2;
        private HashSet<Integer> coverageSet;
        private Map<Integer,PairValueFormat[]> posSubs = new HashMap<>();

        public PairPosAdjustmentFormat2(OpenTypeFontTableReader openReader, int lookupFlag, int subtableLocation) throws IOException {
            super(openReader, lookupFlag, null);
            readFormat(subtableLocation);
        }

        public boolean transformOne(GlyphLine line) {
            if (line.idx >= line.end || line.idx < line.start)
                return false;
            Glyph g1 = line.glyphs.get(line.idx);
            if (!coverageSet.contains(g1.index))
                return false;
            int c1 = classDef1.getOtfClass(g1.index);
            PairValueFormat[] pvs = posSubs.get(c1);
            if (pvs == null)
                return false;
            GlyphIndexer gi = new GlyphIndexer();
            gi.line = line;
            gi.idx = line.idx;
            gi.nextGlyph(openReader, lookupFlag);
            if (gi.glyph == null)
                return false;
            Glyph g2 = gi.glyph;
            int c2 = classDef2.getOtfClass(g2.index);
            if (c2 >= pvs.length)
                return false;
            PairValueFormat pv = pvs[c2];
            // TODO
            //line.glyphs.set(line.idx, new Glyph(g1, pv.first.XPlacement, pv.first.YPlacement, pv.first.XAdvance, pv.first.YAdvance, 0));
            //line.glyphs.set(gi.idx, new Glyph(g2, pv.second.XPlacement, pv.second.YPlacement, pv.second.XAdvance, pv.second.YAdvance, 0));
            line.idx = gi.idx;
            return true;
        }

        protected void readFormat(int subTableLocation) throws IOException {
            int coverage = openReader.rf.readUnsignedShort()+ subTableLocation;
            int valueFormat1 = openReader.rf.readUnsignedShort();
            int valueFormat2 = openReader.rf.readUnsignedShort();
            int locationClass1 = openReader.rf.readUnsignedShort() + subTableLocation;
            int locationClass2 = openReader.rf.readUnsignedShort() + subTableLocation;
            int class1Count = openReader.rf.readUnsignedShort();
            int class2Count = openReader.rf.readUnsignedShort();

            for (int k = 0; k < class1Count; ++k) {
                PairValueFormat[] pairs = new PairValueFormat[class2Count];
                posSubs.put(k, pairs);
                for (int j = 0; j < class2Count; ++j) {
                    PairValueFormat pair = new PairValueFormat();
                    pair.first = openReader.ReadGposValueRecord(valueFormat1);
                    pair.second = openReader.ReadGposValueRecord(valueFormat2);
                    pairs[j] = pair;
                }
            }

            coverageSet = new HashSet<>(openReader.readCoverageFormat(coverage));
            classDef1 = openReader.readClassDefinition(locationClass1);
            classDef2 = openReader.readClassDefinition(locationClass2);
        }

        @Override
        protected void readSubTable(int subTableLocation) throws IOException {
            //never called here
        }
    }

    private static class PairValueFormat {
        public GposValueRecord first;
        public GposValueRecord second;
    }
}
