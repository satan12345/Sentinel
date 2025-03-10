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
package com.alibaba.csp.sentinel.slots.statistic;

/**
 * @author Eric Zhao
 */
public enum MetricEvent {

    /**
     * Normal pass.
     * 正常的通过数 没有被限流的数量
     */
    PASS,
    /**
     * Normal block.
     * 被限流的数据
     */
    BLOCK,
    /**
     * 发生异常的次数
     */
    EXCEPTION,
    /**
     * 调用的请求数据
     */
    SUCCESS,
    /**
     * 总的响应时间
     */
    RT,

    /**
     * Passed in future quota (pre-occupied, since 1.5.0).
     */
    OCCUPIED_PASS
}
