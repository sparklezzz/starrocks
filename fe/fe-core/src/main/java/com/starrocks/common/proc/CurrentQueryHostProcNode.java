// This file is licensed under the Elastic License 2.0. Copyright 2021-present, StarRocks Inc.

package com.starrocks.common.proc;

import com.clearspring.analytics.util.Lists;
import com.google.common.collect.ImmutableList;
import com.starrocks.common.AnalysisException;
import com.starrocks.common.util.QueryStatisticsFormatter;
import com.starrocks.qe.QueryStatisticsItem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class CurrentQueryHostProcNode implements ProcNodeInterface {
    private static final Logger LOG = LogManager.getLogger(CurrentQueryHostProcNode.class);
    private static final ImmutableList<String> TITILE_NAMES = new ImmutableList.Builder<String>()
            .add("Host").add("ScanBytes").add("ScanRows").add("CpuCostSeconds").build();

    private QueryStatisticsItem item;

    public CurrentQueryHostProcNode(QueryStatisticsItem item) {
        this.item = item;
    }

    @Override
    public ProcResult fetchResult() throws AnalysisException {
        final CurrentQueryInfoProvider provider = new CurrentQueryInfoProvider();
        final Map<String, CurrentQueryInfoProvider.QueryStatistics> statisticsMap
                = provider.getQueryStatisticsByHost(item);
        // collect statistics by host
        final List<List<String>> sortedRowDatas = Lists.newArrayList();
        for (Map.Entry<String, CurrentQueryInfoProvider.QueryStatistics> entry : statisticsMap.entrySet()) {
            String host = entry.getKey();
            CurrentQueryInfoProvider.QueryStatistics statistics = entry.getValue();
            final List<String> rowData = Lists.newArrayList();
            rowData.add(host);
            rowData.add(QueryStatisticsFormatter.getScanBytes(statistics.scanBytes));
            rowData.add(QueryStatisticsFormatter.getRowsReturned(statistics.scanRows));
            rowData.add(QueryStatisticsFormatter.getCPUCostSeconds(statistics.cpuCostNs));
            sortedRowDatas.add(rowData);
        }
        sortedRowDatas.sort(new Comparator<List<String>>() {
            @Override
            public int compare(List<String> o1, List<String> o2) {
                return o1.get(0).compareTo(o2.get(0));
            }
        });
        final BaseProcResult result = new BaseProcResult();
        result.setNames(TITILE_NAMES.asList());
        result.setRows(sortedRowDatas);
        return result;
    }
}
