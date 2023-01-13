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
package com.alibaba.csp.sentinel.slots;

import com.alibaba.csp.sentinel.log.RecordLog;
import com.alibaba.csp.sentinel.slotchain.AbstractLinkedProcessorSlot;
import com.alibaba.csp.sentinel.slotchain.DefaultProcessorSlotChain;
import com.alibaba.csp.sentinel.slotchain.ProcessorSlot;
import com.alibaba.csp.sentinel.slotchain.ProcessorSlotChain;
import com.alibaba.csp.sentinel.slotchain.SlotChainBuilder;
import com.alibaba.csp.sentinel.spi.Spi;
import com.alibaba.csp.sentinel.spi.SpiLoader;

import java.util.List;

/**
 * Builder for a default {@link ProcessorSlotChain}.
 *
 * @author qinan.qn
 * @author leyou
 */
@Spi(isDefault = true)
public class DefaultSlotChainBuilder implements SlotChainBuilder {

    /**
     * # Sentinel default ProcessorSlots
     * com.alibaba.csp.sentinel.slots.nodeselector.NodeSelectorSlot
     * com.alibaba.csp.sentinel.slots.clusterbuilder.ClusterBuilderSlot
     * com.alibaba.csp.sentinel.slots.logger.LogSlot
     * com.alibaba.csp.sentinel.slots.statistic.StatisticSlot
     * com.alibaba.csp.sentinel.slots.block.authority.AuthoritySlot
     * com.alibaba.csp.sentinel.slots.system.SystemSlot
     * com.alibaba.csp.sentinel.slots.block.flow.FlowSlot
     * com.alibaba.csp.sentinel.slots.block.degrade.DegradeSlot
     * @return
     */
    @Override
    public ProcessorSlotChain build() {
        ProcessorSlotChain chain = new DefaultProcessorSlotChain();
//        AbstractLinkedProcessorSlot<?> first = ((DefaultProcessorSlotChain) chain).getFirst();
        //获取SPI加载器
        SpiLoader<ProcessorSlot> spiLoader = SpiLoader.of(ProcessorSlot.class);
        //加载指定Slot并排序 slot类上含有@Spi 注解 利用order属性进行排序
        List<ProcessorSlot> sortedSlotList = spiLoader.loadInstanceListSorted();
        for (ProcessorSlot slot : sortedSlotList) {

            if (!(slot instanceof AbstractLinkedProcessorSlot)) {
                RecordLog.warn("The ProcessorSlot(" + slot.getClass().getCanonicalName() + ") is not an instance of AbstractLinkedProcessorSlot, can't be added into ProcessorSlotChain");
                continue;
            }
            //执行链中加载相应的Slot
            chain.addLast((AbstractLinkedProcessorSlot<?>) slot);
        }

        return chain;
    }
}
