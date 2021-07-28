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

package org.dizitart.no2.sync.handlers;

import lombok.Getter;
import okhttp3.WebSocket;
import org.dizitart.no2.sync.ReplicatedCollection;
import org.dizitart.no2.sync.message.DataGateFeedAck;
import org.dizitart.no2.sync.message.Receipt;

/**
 * @author Anindya Chatterjee
 */
public class DataGateFeedAckHandler implements MessageHandler<DataGateFeedAck> {
    @Getter private final ReplicatedCollection replicatedCollection;

    public DataGateFeedAckHandler(ReplicatedCollection replicatedCollection) {
        this.replicatedCollection = replicatedCollection;
    }

    @Override
    public void handleMessage(WebSocket webSocket, DataGateFeedAck message) {
        Receipt receipt = message.getReceipt();
        replicatedCollection.getFeedLedger().writeOff(receipt);
    }
}
