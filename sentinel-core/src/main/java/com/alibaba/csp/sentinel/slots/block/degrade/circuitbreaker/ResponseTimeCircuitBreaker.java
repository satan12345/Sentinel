/*
 * Copyright 1999-2019 Alibaba Group Holding Ltd.
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
package com.alibaba.csp.sentinel.slots.block.degrade.circuitbreaker;

import java.util.List;
import java.util.concurrent.atomic.LongAdder;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.context.Context;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.statistic.base.LeapArray;
import com.alibaba.csp.sentinel.slots.statistic.base.WindowWrap;
import com.alibaba.csp.sentinel.util.AssertUtil;
import com.alibaba.csp.sentinel.util.TimeUtil;

/** 响应时间的熔断器
 * @author Eric Zhao
 * @since 1.8.0
 */
public class ResponseTimeCircuitBreaker extends AbstractCircuitBreaker {

    private static final double SLOW_REQUEST_RATIO_MAX_VALUE = 1.0d;
    /**
     * 配置的最大响应时间RT
     */
    private final long maxAllowedRt;
    /**
     * 最大慢请求比率
     */
    private final double maxSlowRequestRatio;
    /**
     * 最小请求数
     */
    private final int minRequestAmount;

    private final LeapArray<SlowRequestCounter> slidingCounter;

    public ResponseTimeCircuitBreaker(DegradeRule rule) {
        this(rule, new SlowRequestLeapArray(1, rule.getStatIntervalMs()));
    }

    ResponseTimeCircuitBreaker(DegradeRule rule, LeapArray<SlowRequestCounter> stat) {
        super(rule);
        AssertUtil.isTrue(rule.getGrade() == RuleConstant.DEGRADE_GRADE_RT, "rule metric type should be RT");
        AssertUtil.notNull(stat, "stat cannot be null");
        this.maxAllowedRt = Math.round(rule.getCount());
        this.maxSlowRequestRatio = rule.getSlowRatioThreshold();
        this.minRequestAmount = rule.getMinRequestAmount();
        this.slidingCounter = stat;
    }

    @Override
    public void resetStat() {
        // Reset current bucket (bucket count = 1).
        slidingCounter.currentWindow().value().reset();
    }

    @Override
    public void onRequestComplete(Context context) {
        //滑动时间窗
        SlowRequestCounter counter = slidingCounter.currentWindow().value();
        Entry entry = context.getCurEntry();
        if (entry == null) {
            return;
        }
        //获取相应时间
        long completeTime = entry.getCompleteTimestamp();
        if (completeTime <= 0) {
            completeTime = TimeUtil.currentTimeMillis();
        }
        //相应时间=结束时间-创建时间
        long rt = completeTime - entry.getCreateTimestamp();
        if (rt > maxAllowedRt) {
            //慢请求数量+1
            counter.slowCount.add(1);
        }
        //总请求数量+1
        counter.totalCount.add(1);

        handleStateChangeWhenThresholdExceeded(rt);
    }

    /**
     * 根据响应时间 改变熔断器状态
     * @param rt
     */
    private void handleStateChangeWhenThresholdExceeded(long rt) {
        if (currentState.get() == State.OPEN) {
            //打开的状态 说明又在熔断 直接返回
            return;
        }
        
        if (currentState.get() == State.HALF_OPEN) {
            //
            // In detecting request
            // TODO: improve logic for half-open recovery
            if (rt > maxAllowedRt) {
                /**
                 * 当响应时间大于最大允许时间 修改断路器状态为OPEN
                 */
                fromHalfOpenToOpen(1.0d);
            } else {
                //修改断路器状态为CLOSE
                fromHalfOpenToClose();
            }
            return;
        }
        //熔断器处于CLOSE状态
        List<SlowRequestCounter> counters = slidingCounter.values();
        //统计慢调用 与总调用次数
        long slowCount = 0;
        long totalCount = 0;
        for (SlowRequestCounter counter : counters) {
            slowCount += counter.slowCount.sum();
            totalCount += counter.totalCount.sum();
        }
        //总调用小于最小请求数 直接返回
        if (totalCount < minRequestAmount) {
            return;
        }
        //计算慢请求比率
        double currentRatio = slowCount * 1.0d / totalCount;
        if (currentRatio > maxSlowRequestRatio) {
            //慢请求比率大于设置的最大慢请求比率
            transformToOpen(currentRatio);
        }
        if (Double.compare(currentRatio, maxSlowRequestRatio) == 0 &&
                Double.compare(maxSlowRequestRatio, SLOW_REQUEST_RATIO_MAX_VALUE) == 0) {
            //慢请求比率等于设置的最大请求比率或者等于1
            transformToOpen(currentRatio);
        }
    }

    static class SlowRequestCounter {
        private LongAdder slowCount;
        private LongAdder totalCount;

        public SlowRequestCounter() {
            this.slowCount = new LongAdder();
            this.totalCount = new LongAdder();
        }

        public LongAdder getSlowCount() {
            return slowCount;
        }

        public LongAdder getTotalCount() {
            return totalCount;
        }

        public SlowRequestCounter reset() {
            slowCount.reset();
            totalCount.reset();
            return this;
        }

        @Override
        public String toString() {
            return "SlowRequestCounter{" +
                "slowCount=" + slowCount +
                ", totalCount=" + totalCount +
                '}';
        }
    }

    static class SlowRequestLeapArray extends LeapArray<SlowRequestCounter> {

        public SlowRequestLeapArray(int sampleCount, int intervalInMs) {
            super(sampleCount, intervalInMs);
        }

        @Override
        public SlowRequestCounter newEmptyBucket(long timeMillis) {
            return new SlowRequestCounter();
        }

        @Override
        protected WindowWrap<SlowRequestCounter> resetWindowTo(WindowWrap<SlowRequestCounter> w, long startTime) {
            w.resetTo(startTime);
            w.value().reset();
            return w;
        }
    }
}
