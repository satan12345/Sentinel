/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.csp.sentinel.dashboard.rule.nacos;

import com.alibaba.csp.sentinel.dashboard.datasource.entity.rule.FlowRuleEntity;
import com.alibaba.csp.sentinel.datasource.Converter;
import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigFactory;
import com.alibaba.nacos.api.config.ConfigService;
import java.util.Properties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * @author Eric Zhao
 * @since 1.4.0
 */
@Configuration
public class NacosConfig {

    @Value("${nacos.host}")
    private String nacosHost;
    @Value("${namespace}")
    private String namespace;

    /**
     * 流控规则编码 流控规则--》String
     * @return
     */
    @Bean
    public Converter<List<FlowRuleEntity>, String> flowRuleEntityEncoder() {
        return JSON::toJSONString;
    }

    /**
     * 流控规则解码 String--》流控规则
     * @return
     */
    @Bean
    public Converter<String, List<FlowRuleEntity>> flowRuleEntityDecoder() {
        return s -> JSON.parseArray(s, FlowRuleEntity.class);
    }

    /**
     * 创建Nacos配置服务
     * @return
     * @throws Exception
     */
    @Bean
    public ConfigService nacosConfigService() throws Exception {
        Properties properties=new Properties();
        //设置Nacos地址
        properties.put(PropertyKeyConst.SERVER_ADDR, nacosHost);
        //设置命名空间
        properties.put(PropertyKeyConst.NAMESPACE,namespace);
        return ConfigFactory.createConfigService(properties);
//        return ConfigFactory.createConfigService(nacosHost);
        //return ConfigFactory.createConfigService("192.168.159.8");
    }
}
