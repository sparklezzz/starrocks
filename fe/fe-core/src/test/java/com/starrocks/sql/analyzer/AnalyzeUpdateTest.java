// This file is licensed under the Elastic License 2.0. Copyright 2021-present, StarRocks Inc.
package com.starrocks.sql.analyzer;

import com.starrocks.utframe.UtFrameUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.util.UUID;

import static com.starrocks.sql.analyzer.AnalyzeTestUtil.analyzeFail;
import static com.starrocks.sql.analyzer.AnalyzeTestUtil.analyzeSuccess;

public class AnalyzeUpdateTest {
    // may also start a Mocked Frontend
    private static String runningDir = "fe/mocked/AnalyzeUpdate/" + UUID.randomUUID().toString() + "/";

    @BeforeClass
    public static void beforeClass() throws Exception {
        UtFrameUtils.createMinStarRocksCluster();
        AnalyzeTestUtil.init();
    }

    @AfterClass
    public static void tearDown() {
        File file = new File(runningDir);
        file.delete();
    }

    @Test
    public void testSingle() {
        analyzeFail("update tjson set v_json = '' where v_int = 1",
                "only support updating primary key table");

        analyzeFail("update tprimary set pk = 2 where pk = 1",
                "primary key column cannot be updated:");

        analyzeFail("update tprimary set v1 = 'aaa'",
                "must specify where clause to prevent full table update");

        analyzeSuccess("update tprimary set v1 = 'aaa' where pk = 1");
        analyzeSuccess("update tprimary set v2 = v2 + 1 where pk = 1");
        analyzeSuccess("update tprimary set v1 = 'aaa', v2 = v2 + 1 where pk = 1");
        analyzeSuccess("update tprimary set v2 = v2 + 1 where v1 = 'aaa'");

        analyzeSuccess("update tprimary set v3 = [231,4321,42] where pk = 1");
    }

    @Test
    public void testMulti() {
        analyzeSuccess("update tprimary set v2 = tp2.v2 from tprimary2 tp2 where tprimary.pk = tp2.pk");

        analyzeSuccess(
                "update tprimary set v2 = tp2.v2 from tprimary2 tp2 join t0 where tprimary.pk = tp2.pk " +
                        "and tp2.pk = t0.v1 and t0.v2 > 0");
    }

    @Test
    public void testCTE() {
        analyzeSuccess(
                "with tp2cte as (select * from tprimary2 where v2 < 10) update tprimary set v2 = tp2cte.v2 " +
                        "from tp2cte where tprimary.pk = tp2cte.pk");
    }
}
