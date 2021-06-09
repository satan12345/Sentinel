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

import java.util.concurrent.atomic.AtomicReference;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.context.Context;
import com.alibaba.csp.sentinel.slotchain.ResourceWrapper;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.util.AssertUtil;
import com.alibaba.csp.sentinel.util.TimeUtil;
import com.alibaba.csp.sentinel.util.function.BiConsumer;

/**
 * @author Eric Zhao
 * @since 1.8.0
 */
public abstract class AbstractCircuitBreaker implements CircuitBreaker {

    protected final DegradeRule rule;
    /**
     * 熔断时长
     */
    protected final int recoveryTimeoutMs;

    private final EventObserverRegistry observerRegistry;
    /**
     * 断路器状态
     */
    protected final AtomicReference<State> currentState = new AtomicReference<>(State.CLOSED);
    protected volatile long nextRetryTimestamp;

    public AbstractCircuitBreaker(DegradeRule rule) {
        this(rule, EventObserverRegistry.getInstance());
    }

    AbstractCircuitBreaker(DegradeRule rule, EventObserverRegistry observerRegistry) {
        AssertUtil.notNull(observerRegistry, "observerRegistry cannot be null");
        if (!DegradeRuleManager.isValidRule(rule)) {
            throw new IllegalArgumentException("Invalid DegradeRule: " + rule);
        }
        this.observerRegistry = observerRegistry;
        this.rule = rule;
        this.recoveryTimeoutMs = rule.getTimeWindow() * 1000;
    }

    @Override
    public DegradeRule getRule() {
        return rule;
    }

    @Override
    public State currentState() {
        return currentState.get();
    }

    @Override
    public boolean tryPass(Context context) {
        // Template implementation.
        if (currentState.get() == State.CLOSED) {
            //断路器是关闭状态 直接返回true 请求可以通过
            return true;
        }
        if (currentState.get() == State.OPEN) {
            // For half-open state we allow a request for probing.
            /**
             * 判断时间点是否达到下一次尝试的时间点
             * 到了 则执行后面方法
             * 没到 直接返回false 请求不允许通过
             */
            return retryTimeoutArrived() && fromOpenToHalfOpen(context);
        }
        return false;
    }

    /**
     * Reset the statistic data.
     */
    abstract void resetStat();

    protected boolean retryTimeoutArrived() {
        return TimeUtil.currentTimeMillis() >= nextRetryTimestamp;
    }

    /**
     * 更新允许下一次尝试的时间戳
     */
    protected void updateNextRetryTimestamp() {
        this.nextRetryTimestamp = TimeUtil.currentTimeMillis() + recoveryTimeoutMs;
    }

    /**
     * 从close--》open
     * @param snapshotValue
     * @return
     */
    protected boolean fromCloseToOpen(double snapshotValue) {
        State prev = State.CLOSED;

        if (currentState.compareAndSet(prev, State.OPEN)) {
            //将熔断器由关闭状态变为打开状态
            //更新下一次尝试的时间戳
            updateNextRetryTimestamp();

            notifyObservers(prev, State.OPEN, snapshotValue);
            return true;
        }
        return false;
    }

    /**
     * 将断路器状态由打开状态变为半开状态 然后返回true 允许本次通过
     * @param context
     * @return
     */
    protected boolean fromOpenToHalfOpen(Context context) {
        if (currentState.compareAndSet(State.OPEN, State.HALF_OPEN)) {
            //由open状态 变为half_open
            notifyObservers(State.OPEN, State.HALF_OPEN, null);
            Entry entry = context.getCurEntry();
            entry.whenTerminate(new BiConsumer<Context, Entry>() {
                @Override
                public void accept(Context context, Entry entry) {
                    // Note: This works as a temporary workaround for https://github.com/alibaba/Sentinel/issues/1638
                    // Without the hook, the circuit breaker won't recover from half-open state in some circumstances
                    // when the request is actually blocked by upcoming rules (not only degrade rules).
                    if (entry.getBlockError() != null) {
                        // Fallback to OPEN due to detecting request is blocked
                        currentState.compareAndSet(State.HALF_OPEN, State.OPEN);
                        notifyObservers(State.HALF_OPEN, State.OPEN, 1.0d);
                    }
                }
            });
            return true;
        }
        return false;
    }
    
    private void notifyObservers(CircuitBreaker.State prevState, CircuitBreaker.State newState, Double snapshotValue) {
        for (CircuitBreakerStateChangeObserver observer : observerRegistry.getStateChangeObservers()) {
            observer.onStateChange(prevState, newState, rule, snapshotValue);
        }
    }

    protected boolean fromHalfOpenToOpen(double snapshotValue) {
        if (currentState.compareAndSet(State.HALF_OPEN, State.OPEN)) {
            //更新下一次重试的时间
            updateNextRetryTimestamp();
            notifyObservers(State.HALF_OPEN, State.OPEN, snapshotValue);
            return true;
        }
        return false;
    }

    protected boolean fromHalfOpenToClose() {
        if (currentState.compareAndSet(State.HALF_OPEN, State.CLOSED)) {
            resetStat();
            notifyObservers(State.HALF_OPEN, State.CLOSED, null);
            return true;
        }
        return false;
    }

    /**
     * 修改断路器的状态为打开
     * @param triggerValue
     */
    protected void transformToOpen(double triggerValue) {
        State cs = currentState.get();
        switch (cs) {
            case CLOSED:
                fromCloseToOpen(triggerValue);
                break;
            case HALF_OPEN:
                fromHalfOpenToOpen(triggerValue);
                break;
            default:
                break;
        }
    }
}
