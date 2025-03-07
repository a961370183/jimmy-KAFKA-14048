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
package org.apache.kafka.streams.kstream.internals.foreignkeyjoin;

import org.apache.kafka.common.errors.UnsupportedVersionException;
import org.apache.kafka.streams.kstream.internals.Change;
import org.apache.kafka.streams.kstream.internals.KTableValueGetter;
import org.apache.kafka.streams.kstream.internals.KTableValueGetterSupplier;
import org.apache.kafka.streams.processor.api.ContextualProcessor;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.ProcessorSupplier;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.ValueAndTimestamp;

import java.util.Objects;

/**
 * Receives {@code SubscriptionWrapper<KLeft>} events and processes them according to their Instruction.
 * Depending on the results, {@code SubscriptionResponseWrapper}s are created, which will be propagated to
 * the {@code ResponseJoinProcessorSupplier} instance.
 *
 * @param <KLeft> Type of primary keys
 * @param <KRight> Type of foreign key
 * @param <VRight> Type of foreign value
 */
public class SubscriptionJoinProcessorSupplier<KLeft, KRight, VRight>
    implements ProcessorSupplier<CombinedKey<KRight, KLeft>, Change<ValueAndTimestamp<SubscriptionWrapper<KLeft>>>, KLeft, SubscriptionResponseWrapper<VRight>> {

    private final KTableValueGetterSupplier<KRight, VRight> foreignValueGetterSupplier;

    public SubscriptionJoinProcessorSupplier(final KTableValueGetterSupplier<KRight, VRight> foreignValueGetterSupplier) {
        this.foreignValueGetterSupplier = foreignValueGetterSupplier;
    }

    @Override
    public Processor<CombinedKey<KRight, KLeft>, Change<ValueAndTimestamp<SubscriptionWrapper<KLeft>>>, KLeft, SubscriptionResponseWrapper<VRight>> get() {
        return new ContextualProcessor<>() {
            private KTableValueGetter<KRight, VRight> foreignValues;

            @Override
            public void init(final ProcessorContext<KLeft, SubscriptionResponseWrapper<VRight>> context) {
                super.init(context);
                foreignValues = foreignValueGetterSupplier.get();
                foreignValues.init(context);
            }

            @Override
            public void process(final Record<CombinedKey<KRight, KLeft>, Change<ValueAndTimestamp<SubscriptionWrapper<KLeft>>>> record) {
                Objects.requireNonNull(record.key(), "This processor should never see a null key.");
                Objects.requireNonNull(record.value(), "This processor should never see a null value.");
                final ValueAndTimestamp<SubscriptionWrapper<KLeft>> valueAndTimestamp = record.value().newValue;
                Objects.requireNonNull(valueAndTimestamp, "This processor should never see a null newValue.");
                final SubscriptionWrapper<KLeft> value = subscriptionWrapper(valueAndTimestamp);

                final ValueAndTimestamp<VRight> foreignValueAndTime =
                    record.key().foreignKey() == null ?
                        null :
                        foreignValues.get(record.key().foreignKey());

                final long resultTimestamp =
                    foreignValueAndTime == null ?
                        valueAndTimestamp.timestamp() :
                        Math.max(valueAndTimestamp.timestamp(), foreignValueAndTime.timestamp());

                switch (value.instruction()) {
                    case DELETE_KEY_AND_PROPAGATE:
                        context().forward(
                            record.withKey(record.key().primaryKey())
                                .withValue(new SubscriptionResponseWrapper<VRight>(
                                    value.hash(),
                                    null,
                                    value.primaryPartition()
                                ))
                                .withTimestamp(resultTimestamp)
                        );
                        break;
                    case PROPAGATE_NULL_IF_NO_FK_VAL_AVAILABLE:
                        //This one needs to go through regardless of LEFT or INNER join, since the extracted FK was
                        //changed and there is no match for it. We must propagate the (key, null) to ensure that the
                        //downstream consumers are alerted to this fact.
                        final VRight valueToSend = foreignValueAndTime == null ? null : foreignValueAndTime.value();

                        context().forward(
                            record.withKey(record.key().primaryKey())
                                .withValue(new SubscriptionResponseWrapper<>(
                                        value.hash(),
                                        valueToSend,
                                        value.primaryPartition()
                                ))
                                .withTimestamp(resultTimestamp)
                        );
                        break;
                    case PROPAGATE_ONLY_IF_FK_VAL_AVAILABLE:
                        if (foreignValueAndTime != null) {
                            context().forward(
                                record.withKey(record.key().primaryKey())
                                   .withValue(new SubscriptionResponseWrapper<>(
                                       value.hash(),
                                       foreignValueAndTime.value(),
                                       value.primaryPartition()
                                   ))
                                   .withTimestamp(resultTimestamp)
                            );
                        }
                        break;
                    case DELETE_KEY_NO_PROPAGATE:
                        break;
                    default:
                        throw new IllegalStateException("Unhandled instruction: " + value.instruction());
                }
            }

            private SubscriptionWrapper<KLeft> subscriptionWrapper(final ValueAndTimestamp<SubscriptionWrapper<KLeft>> valueAndTimestamp) {
                final SubscriptionWrapper<KLeft> value = valueAndTimestamp.value();

                if (value.version() > SubscriptionWrapper.CURRENT_VERSION) {
                    //Guard against modifications to SubscriptionWrapper. Need to ensure that there is compatibility
                    //with previous versions to enable rolling upgrades. Must develop a strategy for upgrading
                    //from older SubscriptionWrapper versions to newer versions.
                    throw new UnsupportedVersionException("SubscriptionWrapper is of an incompatible version.");
                }

                return value;
            }
        };
    }
}