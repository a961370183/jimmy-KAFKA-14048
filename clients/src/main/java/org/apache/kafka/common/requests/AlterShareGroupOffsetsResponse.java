/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.kafka.common.requests;

import org.apache.kafka.common.message.AlterShareGroupOffsetsResponseData;
import org.apache.kafka.common.message.AlterShareGroupOffsetsResponseData.AlterShareGroupOffsetsResponseTopic;
import org.apache.kafka.common.protocol.ApiKeys;
import org.apache.kafka.common.protocol.ByteBufferAccessor;
import org.apache.kafka.common.protocol.Errors;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class AlterShareGroupOffsetsResponse extends AbstractResponse {

    private final AlterShareGroupOffsetsResponseData data;

    public AlterShareGroupOffsetsResponse(AlterShareGroupOffsetsResponseData data) {
        super(ApiKeys.ALTER_SHARE_GROUP_OFFSETS);
        this.data = data;
    }

    @Override
    public Map<Errors, Integer> errorCounts() {
        Map<Errors, Integer> counts = new HashMap<>();
        data.responses().forEach(topic -> topic.partitions().forEach(partitionResponse ->
            updateErrorCounts(counts, Errors.forCode(partitionResponse.errorCode()))
        ));
        return counts;
    }

    @Override
    public int throttleTimeMs() {
        return data.throttleTimeMs();
    }

    @Override
    public void maybeSetThrottleTimeMs(int throttleTimeMs) {
        data.setThrottleTimeMs(throttleTimeMs);
    }

    @Override
    public AlterShareGroupOffsetsResponseData data() {
        return data;
    }

    public static AlterShareGroupOffsetsResponse parse(ByteBuffer buffer, short version) {
        return new AlterShareGroupOffsetsResponse(
            new AlterShareGroupOffsetsResponseData(new ByteBufferAccessor(buffer), version)
        );
    }

    public static class Builder {
        AlterShareGroupOffsetsResponseData data = new AlterShareGroupOffsetsResponseData();
        HashMap<String, AlterShareGroupOffsetsResponseTopic> topics = new HashMap<>();

        private AlterShareGroupOffsetsResponseTopic getOrCreateTopic(String topic) {
            AlterShareGroupOffsetsResponseData.AlterShareGroupOffsetsResponseTopic topicData = topics.get(topic);
            if (topicData == null) {
                topicData = new AlterShareGroupOffsetsResponseData.AlterShareGroupOffsetsResponseTopic()
                    .setTopicName(topic);
                topics.put(topic, topicData);
            }
            return topicData;
        }

        public Builder addPartition(String topic, int partition, Errors error) {
            AlterShareGroupOffsetsResponseTopic topicData = getOrCreateTopic(topic);
            topicData.partitions().add(new AlterShareGroupOffsetsResponseData.AlterShareGroupOffsetsResponsePartition()
                .setPartitionIndex(partition)
                .setErrorCode(error.code()));
            return this;
        }

        public AlterShareGroupOffsetsResponse build() {
            data.setResponses(new ArrayList<>(topics.values()));
            return new AlterShareGroupOffsetsResponse(data);
        }

        public Builder merge(AlterShareGroupOffsetsResponseData data) {
            data.responses().forEach(topic -> {
                AlterShareGroupOffsetsResponseTopic newTopic = getOrCreateTopic(topic.topicName());
                topic.partitions().forEach(partition -> {
                    newTopic.partitions().add(new AlterShareGroupOffsetsResponseData.AlterShareGroupOffsetsResponsePartition()
                        .setPartitionIndex(partition.partitionIndex())
                        .setErrorCode(partition.errorCode()));
                });
            });
            return this;

        }
    }
}
