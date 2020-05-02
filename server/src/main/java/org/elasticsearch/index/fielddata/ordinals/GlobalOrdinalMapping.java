/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.index.fielddata.ordinals;

import org.apache.lucene.index.OrdinalMap;
import org.apache.lucene.index.SortedSetDocValues;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.LongValues;

import java.io.IOException;

/**
 * A {@link SortedSetDocValues} implementation that returns ordinals that are global.
 */
final class GlobalOrdinalMapping extends SortedSetDocValues {

    private final SortedSetDocValues values;
    private final OrdinalMap ordinalMap;
    private final LongValues mapping;
    private final TermsEnum[] lookups;

    /**
     * 注意values来自于segmentIndex对应的LeafReaderContext (即Lucene的segment)。
     */
    GlobalOrdinalMapping(OrdinalMap ordinalMap, SortedSetDocValues values, TermsEnum[] lookups, int segmentIndex) {
        super();
        this.values = values;
        this.lookups = lookups;
        this.ordinalMap = ordinalMap;

        // 获取segment中局部ordinal到全局ordinal的映射
        this.mapping = ordinalMap.getGlobalOrds(segmentIndex);
    }

    @Override
    public long getValueCount() {
        return ordinalMap.getValueCount();
    }

    public long getGlobalOrd(long segmentOrd) {
        return mapping.get(segmentOrd);
    }

    @Override
    public boolean advanceExact(int target) throws IOException {
        return values.advanceExact(target);
    }

    @Override
    public long nextOrd() throws IOException {
        long segmentOrd = values.nextOrd();
        if (segmentOrd == SortedSetDocValues.NO_MORE_ORDS) {
            return SortedSetDocValues.NO_MORE_ORDS;
        } else {
            return getGlobalOrd(segmentOrd);
        }
    }

    @Override
    public BytesRef lookupOrd(long globalOrd) throws IOException {
        /**
         * 注意这里的readerIndex并不一定和segmentIndex相同，而下面的segmentOrd是readerIndex对应的
         * LeafReaderContext (Lucene segment)下ordinal。这也是GlobalOrdinalMapping为何需要依赖
         * 所有LeafReaderContext的TermsEnum，而不仅仅是segmentIndex对应的LeafReaderContext相关的
         * TermsEnum。这个主要和OrdinalMap的实现有关， 因为OrdinalMap本身没有实现lookup global
         * ordinal的功能。
         */
        final long segmentOrd = ordinalMap.getFirstSegmentOrd(globalOrd);
        int readerIndex = ordinalMap.getFirstSegmentNumber(globalOrd);

        lookups[readerIndex].seekExact(segmentOrd);
        return lookups[readerIndex].term();
    }

    @Override
    public int docID() {
        return values.docID();
    }

    @Override
    public int nextDoc() throws IOException {
        return values.nextDoc();
    }

    @Override
    public int advance(int target) throws IOException {
        return values.advance(target);
    }

    @Override
    public long cost() {
        return values.cost();
    }

}
