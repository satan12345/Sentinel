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
package com.alibaba.csp.sentinel.slots.block.flow.controller;

import java.util.concurrent.atomic.AtomicLong;

import com.alibaba.csp.sentinel.slots.block.flow.TrafficShapingController;

import com.alibaba.csp.sentinel.util.TimeUtil;
import com.alibaba.csp.sentinel.node.Node;

/** 漏洞算法：请求匀速执行
 *
 * @author jialiang.linjl
 */
public class RateLimiterController implements TrafficShapingController {
    /**
     * 最大排队超时时间
     */
    private final int maxQueueingTimeMs;
    /**
     * 单机阈值
     */
    private final double count;

    private final AtomicLong latestPassedTime = new AtomicLong(-1);

    public RateLimiterController(int timeOut, double count) {
        this.maxQueueingTimeMs = timeOut;
        //单机阈值
        this.count = count;
    }

    @Override
    public boolean canPass(Node node, int acquireCount) {
        return canPass(node, acquireCount, false);
    }

    @Override
    public boolean canPass(Node node, int acquireCount, boolean prioritized) {
        // Pass when acquire count is less or equal than 0.
        if (acquireCount <= 0) {
            return true;
        }
        // Reject when count is less or equal than 0.
        // Otherwise,the costTime will be max of long and waitTime will overflow in some cases.
        if (count <= 0) {
            return false;
        }
        //获取当前请求的调用时间
        long currentTime = TimeUtil.currentTimeMillis();
        /**
         * 计算两个请求的时间间隔
         * 比如设置的单机阈值为10 即该接口1s通过10个请求 即每个请求的时间间隔应为100ms
         */
        // Calculate the interval between every two requests.
        long costTime = Math.round(1.0 * (acquireCount) / count * 1000);
        /**
         * 用上一个请求的结束时间+两个请求之间的间隔 算出本次请求的结束时间 作为期望时间
         */
        // Expected pass time of this request.
        long expectedTime = costTime + latestPassedTime.get();

        if (expectedTime <= currentTime) {
            // Contention may exist here, but it's okay.
            /**
             * 当前请求来的时间 大于 期望时间 则允许通过
             * 并将当前请求的时间设置为上一次请求通过的时间
             */
            latestPassedTime.set(currentTime);

            return true;
        } else {

            /**
             * 代码走到这里 说明当前请求到达的时候 比预期的时间来的要早 即当前时间的时间戳比expectedTime小
         *期望的时间-当前时间
             * expectedTime - 当前时间戳=算出 该次请求应该等待的时间
             */

            // Calculate the time to wait.
            long waitTime = expectedTime - TimeUtil.currentTimeMillis();

            if (waitTime > maxQueueingTimeMs) {
                //如果要等待的时间超过设置的限制了 即放弃
                return false;
            } else {
                //这里算出的oldTime 就等于 expectedTime
                long oldTime = latestPassedTime.addAndGet(costTime);
                try {
                    //重新算一次waitTime
                    waitTime = oldTime - TimeUtil.currentTimeMillis();
                    if (waitTime > maxQueueingTimeMs) {
                        //等待时间大于最大等待时间  数据还原 不允许通过
                        latestPassedTime.addAndGet(-costTime);
                        return false;
                    }
                    // in race condition waitTime may <= 0
                    if (waitTime > 0) {
                        //线程暂时睡眠这么长时间
                        Thread.sleep(waitTime);
                    }
                    return true;
                } catch (InterruptedException e) {
                }
            }
        }
        return false;
    }

}
