/*
 * Copyright (c) 2017-2020. Nitrite author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.dizitart.no2.filters;

import lombok.ToString;
import org.dizitart.no2.collection.Document;
import org.dizitart.no2.collection.NitriteId;
import org.dizitart.no2.common.tuples.Pair;
import org.dizitart.no2.exceptions.FilterException;
import org.dizitart.no2.index.ComparableIndexer;
import org.dizitart.no2.index.TextIndexer;
import org.dizitart.no2.store.NitriteMap;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.dizitart.no2.common.util.ObjectUtils.deepEquals;

/**
 * @author Anindya Chatterjee.
 */
@ToString
class EqualsFilter extends IndexAwareFilter {
    EqualsFilter(String field, Object value) {
        super(field, value);
    }

    @Override
    @SuppressWarnings("rawtypes")
    protected Set<NitriteId> findIndexedIdSet() {
        Set<NitriteId> idSet = new LinkedHashSet<>();
        if (getIsFieldIndexed()) {
            if (getValue() == null || getValue() instanceof Comparable) {
                if (getNitriteIndexer() instanceof ComparableIndexer) {
                    ComparableIndexer comparableIndexer = (ComparableIndexer) getNitriteIndexer();
                    idSet = comparableIndexer.findEqual(getCollectionName(), getField(), (Comparable) getValue());
                } else if (getNitriteIndexer() instanceof TextIndexer && getValue() instanceof String) {
                    // eq filter is not compatible with TextIndexer
                    setIsFieldIndexed(false);
                } else {
                    throw new FilterException("eq filter is not supported on indexed field "
                        + getField());
                }
            } else {
                throw new FilterException(getValue() + " is not comparable");
            }
        }
        return idSet;
    }

    @Override
    protected Set<NitriteId> findIdSet(NitriteMap<NitriteId, Document> collection) {
        Set<NitriteId> idSet = new LinkedHashSet<>();
        if (getOnIdField() && getValue() instanceof String) {
            NitriteId nitriteId = NitriteId.createId((String) getValue());
            if (collection.containsKey(nitriteId)) {
                idSet.add(nitriteId);
            }
        }
        return idSet;
    }

    @Override
    public boolean apply(Pair<NitriteId, Document> element) {
        Document document = element.getSecond();
        Object fieldValue = document.get(getField());
        return deepEquals(fieldValue, getValue());
    }

    @Override
    public void setIsFieldIndexed(Boolean isFieldIndexed) {
        if (!(getNitriteIndexer() instanceof TextIndexer && getValue() instanceof String)) {
            super.setIsFieldIndexed(isFieldIndexed);
        }
    }
}
