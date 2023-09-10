package org.apache.kafka.coordinator.group.consumer;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.common.config.ConfigDef;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import static org.apache.kafka.clients.CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG;

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
public class ConsumerGroupConfig extends AbstractConfig {

    private static final ConfigDef CONFIG;

//    private Map<String, >

    static {
        CONFIG = new ConfigDef().define(BOOTSTRAP_SERVERS_CONFIG,
                ConfigDef.Type.LIST,
                Collections.emptyList(),
                new ConfigDef.NonNullValidator(),
                ConfigDef.Importance.HIGH,
                CommonClientConfigs.BOOTSTRAP_SERVERS_DOC);
    }

    public static Optional<ConfigDef.Type> configType(String configName) {
        return Optional.ofNullable(CONFIG.configKeys().get(configName)).map(c -> c.type);
    }



    public ConsumerGroupConfig(Properties props) {
        super(CONFIG, props);
    }

    public ConsumerGroupConfig(Map<String, Object> props) {
        super(CONFIG, props);
    }

    protected ConsumerGroupConfig(Map<?, ?> props, boolean doLog) {
        super(CONFIG, props, doLog);
    }

}
