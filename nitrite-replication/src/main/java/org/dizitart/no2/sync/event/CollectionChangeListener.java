/*
 * Copyright (c) 2017-2021 Nitrite author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.dizitart.no2.sync.event;

import lombok.extern.slf4j.Slf4j;
import org.dizitart.no2.collection.Document;
import org.dizitart.no2.collection.NitriteId;
import org.dizitart.no2.collection.events.CollectionEventInfo;
import org.dizitart.no2.collection.events.CollectionEventListener;
import org.dizitart.no2.sync.crdt.ConflictFreeReplicatedDataType;

import static org.dizitart.no2.common.Constants.REPLICATOR;

/**
 * @author Anindya Chatterjee
 */
@Slf4j
public class CollectionChangeListener implements CollectionEventListener {
    private final ConflictFreeReplicatedDataType replicatedDataType;

    public CollectionChangeListener(ConflictFreeReplicatedDataType replicatedDataType) {
        this.replicatedDataType = replicatedDataType;
    }

    @Override
    public void onEvent(CollectionEventInfo<?> eventInfo) {
        if (eventInfo != null) {
            if (!REPLICATOR.equals(eventInfo.getOriginator())) {
                // discard the removes coming from replicator crdt
                switch (eventInfo.getEventType()) {
                    case Remove:
                        Document document = (Document) eventInfo.getItem();
                        handleRemoveEvent(document);
                        break;
                    case Insert:
                    case Update:
                    case IndexStart:
                    case IndexEnd:
                        break;
                }
            }
        }
    }

    private void handleRemoveEvent(Document document) {
        NitriteId nitriteId = document.getId();
        Long deleteTime = document.getLastModifiedSinceEpoch();

        if (replicatedDataType != null) {
            replicatedDataType.createTombstone(nitriteId, deleteTime);
        }
    }
}
