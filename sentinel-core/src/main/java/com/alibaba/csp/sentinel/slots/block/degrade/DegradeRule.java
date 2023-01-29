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
package com.alibaba.csp.sentinel.slots.block.degrade;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import com.alibaba.csp.sentinel.concurrent.NamedThreadFactory;
import com.alibaba.csp.sentinel.context.Context;
import com.alibaba.csp.sentinel.node.ClusterNode;
import com.alibaba.csp.sentinel.node.DefaultNode;
import com.alibaba.csp.sentinel.slots.block.AbstractRule;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.clusterbuilder.ClusterBuilderSlot;

/**
 * <p>
 * Degrade is used when the resources are in an unstable state, these resources
 * will be degraded within the next defined time window. There are two ways to
 * measure whether a resource is stable or not:
 * </p>
 * <ul>
 * <li>
 * Average response time ({@code DEGRADE_GRADE_RT}): When
 * the average RT exceeds the threshold ('count' in 'DegradeRule', in milliseconds), the
 * resource enters a quasi-degraded state. If the RT of next coming 5
 * requests still exceed this threshold, this resource will be downgraded, which
 * means that in the next time window (defined in 'timeWindow', in seconds) all the
 * access to this resource will be blocked.
 * </li>
 * <li>
 * Exception ratio: When the ratio of exception count per second and the
 * success qps exceeds the threshold, access to the resource will be blocked in
 * the coming window.
 * </li>
 * </ul>
 *
 * @author jialiang.linjl
 */
public class DegradeRule extends AbstractRule {

    private static final int RT_MAX_EXCEED_N = 5;

    @SuppressWarnings("PMD.ThreadPoolCreationRule")
    private static ScheduledExecutorService pool = Executors.newScheduledThreadPool(
        Runtime.getRuntime().availableProcessors(), new NamedThreadFactory("sentinel-degrade-reset-task", true));

    public DegradeRule() {}

    public DegradeRule(String resourceName) {
        setResource(resourceName);
    }

    /**
     * RT threshold or exception ratio threshold count.
     * 降级响应时间的阈值
     */
    private double count;

    /**
     * Degrade recover timeout (in seconds) when degradation occurs.
     */
    private int timeWindow;

    /**降级策略 0 响应时间  1 异常比例
     * Degrade strategy (0: average RT, 1: exception ratio).
     */
    private int grade = RuleConstant.DEGRADE_GRADE_RT;
    /**
     * 断路器的标记位 默认未断开
     */
    private final AtomicBoolean cut = new AtomicBoolean(false);

    public int getGrade() {
        return grade;
    }

    public DegradeRule setGrade(int grade) {
        this.grade = grade;
        return this;
    }

    private AtomicLong passCount = new AtomicLong(0);

    public double getCount() {
        return count;
    }

    public DegradeRule setCount(double count) {
        this.count = count;
        return this;
    }

    private boolean isCut() {
        return cut.get();
    }

    private void setCut(boolean cut) {
        this.cut.set(cut);
    }

    public AtomicLong getPassCount() {
        return passCount;
    }

    public int getTimeWindow() {
        return timeWindow;
    }

    public DegradeRule setTimeWindow(int timeWindow) {
        this.timeWindow = timeWindow;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DegradeRule)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        DegradeRule that = (DegradeRule)o;

        if (count != that.count) {
            return false;
        }
        if (timeWindow != that.timeWindow) {
            return false;
        }
        if (grade != that.grade) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + new Double(count).hashCode();
        result = 31 * result + timeWindow;
        result = 31 * result + grade;
        return result;
    }

    /**
     * 降级检测
     * @param context current {@link Context}
     * @param node    current {@link com.alibaba.csp.sentinel.node.Node}
     * @param acquireCount
     * @param args    arguments of the original invocation.
     * @return
     */
    @Override
    public boolean passCheck(Context context, DefaultNode node, int acquireCount, Object... args) {
        if (cut.get()) {
            //如果断路器已经断开 则直接返回不通过
            return false;
        }

        ClusterNode clusterNode = ClusterBuilderSlot.getClusterNode(this.getResource());
        if (clusterNode == null) {
            return true;
        }

        if (grade == RuleConstant.DEGRADE_GRADE_RT) {
            //降级规则为 响应时间
            //获取统计到的平均响应时间
            double rt = clusterNode.avgRt();
            if (rt < this.count) {
                //平均响应时间小于阈值
                passCount.set(0);
                return true;
            }

            // Sentinel will degrade the service only if count exceeds.
            if (passCount.incrementAndGet() < RT_MAX_EXCEED_N) {
                return true;
            }
        } else if (grade == RuleConstant.DEGRADE_GRADE_EXCEPTION_RATIO) {
            //降级规则为异常比例
            double exception = clusterNode.exceptionQps();
            double success = clusterNode.successQps();
            double total = clusterNode.totalQps();
            // if total qps less than RT_MAX_EXCEED_N, pass.
            if (total < RT_MAX_EXCEED_N) {
                //总的请求数小于5 不计算比例 直接通过
                return true;
            }

            double realSuccess = success - exception;
            if (realSuccess <= 0 && exception < RT_MAX_EXCEED_N) {
                //成功数小于0 且异常数小于5 也直接通过检测
                return true;
            }

            if (exception / success < count) {
                //异常数/成功数 小于异常比例的阈值 也通过检测
                return true;
            }
        } else if (grade == RuleConstant.DEGRADE_GRADE_EXCEPTION_COUNT) {
            //降级规则为异常数
            double exception = clusterNode.totalException();
            if (exception < count) {
                //异常数小于阈值 通过检测
                return true;
            }
        }

        if (cut.compareAndSet(false, true)) {
            ResetTask resetTask = new ResetTask(this);
            pool.schedule(resetTask, timeWindow, TimeUnit.SECONDS);
        }

        return false;
    }

    @Override
    public String toString() {
        return "DegradeRule{" +
            "resource=" + getResource() +
            ", grade=" + grade +
            ", count=" + count +
            ", limitApp=" + getLimitApp() +
            ", timeWindow=" + timeWindow +
            "}";
    }

    private static final class ResetTask implements Runnable {

        private DegradeRule rule;

        ResetTask(DegradeRule rule) {
            this.rule = rule;
        }

        @Override
        public void run() {
            rule.getPassCount().set(0);
            rule.cut.set(false);
        }
    }
}

