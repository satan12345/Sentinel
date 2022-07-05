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
package com.alibaba.csp.sentinel.slotchain;

import com.alibaba.csp.sentinel.context.Context;

/**
 * @author qinan.qn
 * @author jialiang.linjl
 */
public class DefaultProcessorSlotChain extends ProcessorSlotChain {
    //创建一个链条的头结点
    AbstractLinkedProcessorSlot<?> first = new AbstractLinkedProcessorSlot<Object>() {

        @Override
        public void entry(Context context, ResourceWrapper resourceWrapper, Object t, int count, boolean prioritized, Object... args)
            throws Throwable {
            super.fireEntry(context, resourceWrapper, t, count, prioritized, args);
        }

        @Override
        public void exit(Context context, ResourceWrapper resourceWrapper, int count, Object... args) {
            super.fireExit(context, resourceWrapper, count, args);
        }

    };
    //尾结点
    AbstractLinkedProcessorSlot<?> end = first;

    @Override
    public void addFirst(AbstractLinkedProcessorSlot<?> protocolProcessor) {
        /**
         * 这里先获取头结点的下一个节点作为新增加节点的下一个节点
         * 将头结点的下一个节点指向新增的节点
         * 虽然方法名显示的是增加头节点，但是其实是增加到二个位置,头结点作为一个虚拟节点
         * 一直在这里
         */
        protocolProcessor.setNext(first.getNext());

        first.setNext(protocolProcessor);
        if (end == first) {
            end = protocolProcessor;
        }
    }

    /**
     * 向链条的尾结点添加原始
     * @param protocolProcessor processor to be added.
     */
    @Override
    public void addLast(AbstractLinkedProcessorSlot<?> protocolProcessor) {
        /**
         * 原来尾结点的下一个节点指向新增的节点
         * 即将新增的节点设置为尾结点
         */
        end.setNext(protocolProcessor);
        //尾结点参数指向新增的节点
        end = protocolProcessor;
    }

    /**
     * Same as {@link #addLast(AbstractLinkedProcessorSlot)}.
     *
     * @param next processor to be added.
     */
    @Override
    public void setNext(AbstractLinkedProcessorSlot<?> next) {
        addLast(next);
    }

    @Override
    public AbstractLinkedProcessorSlot<?> getNext() {
        return first.getNext();
    }

    @Override
    public void entry(Context context, ResourceWrapper resourceWrapper, Object t, int count, boolean prioritized, Object... args)
        throws Throwable {

        first.transformEntry(context, resourceWrapper, t, count, prioritized, args);
    }

    @Override
    public void exit(Context context, ResourceWrapper resourceWrapper, int count, Object... args) {
        first.exit(context, resourceWrapper, count, args);
    }

}
