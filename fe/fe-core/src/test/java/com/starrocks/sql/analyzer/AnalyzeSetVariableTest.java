// This file is licensed under the Elastic License 2.0. Copyright 2021-present, StarRocks Inc.
package com.starrocks.sql.analyzer;

import com.starrocks.analysis.Subquery;
import com.starrocks.sql.ast.SetPassVar;
import com.starrocks.sql.ast.SetStmt;
import com.starrocks.sql.ast.UserVariable;
import com.starrocks.utframe.UtFrameUtils;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import static com.starrocks.sql.analyzer.AnalyzeTestUtil.analyzeFail;
import static com.starrocks.sql.analyzer.AnalyzeTestUtil.analyzeSuccess;

public class AnalyzeSetVariableTest {
    @BeforeClass
    public static void beforeClass() throws Exception {
        UtFrameUtils.createMinStarRocksCluster();
        AnalyzeTestUtil.init();
    }

    @Test
    public void testSetVariable() {
        String sql = "set query_timeout = 10";
        analyzeSuccess(sql);
        sql = "set `query_timeout` = 10";
        analyzeSuccess(sql);
        sql = "set \"query_timeout\" = 10";
        analyzeFail(sql);
        sql = "set GLOBAL query_timeout = 10";
        analyzeSuccess(sql);
        sql = "set SESSION query_timeout = 10";
        analyzeSuccess(sql);
        sql = "set LOCAL query_timeout = 10";
        analyzeSuccess(sql);
        sql = "set tablet_internal_parallel_mode = auto";
        analyzeSuccess(sql);
        sql = "set tablet_internal_parallel_mode = force_split";
        analyzeSuccess(sql);
        sql = "set tablet_internal_parallel_mode = force";
        analyzeFail(sql);
    }

    @Test
    public void testUserVariable() {
        String sql = "set @var1 = 1";
        analyzeSuccess(sql);
        sql = "set @`var1` = 1";
        analyzeSuccess(sql);
        sql = "set @'var1' = 1";
        analyzeSuccess(sql);
        sql = "set @\"var1\" = 1";
        analyzeSuccess(sql);

        sql = "set @varvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv123 = 1";
        analyzeFail(sql, "User variable name 'varvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv123' is illegal");

        sql = "set @var = NULL";
        analyzeSuccess(sql);

        sql = "set @var = 1 + 2";
        SetStmt setStmt = (SetStmt) analyzeSuccess(sql);
        UserVariable userVariable = (UserVariable) setStmt.getSetVars().get(0);
        Assert.assertNotNull(userVariable.getResolvedExpression());
        Assert.assertEquals("3", userVariable.getResolvedExpression().getStringValue());

        sql = "set @var = abs(1.2)";
        setStmt = (SetStmt) analyzeSuccess(sql);
        userVariable = (UserVariable) setStmt.getSetVars().get(0);
        Assert.assertTrue(userVariable.getExpression() instanceof Subquery);

        sql = "set @var = (select 1)";
        analyzeSuccess(sql);

        sql = "set @var = (select v1 from test.t0)";
        analyzeSuccess(sql);

        sql = "set @var = (select sum(v1) from test.t0)";
        analyzeSuccess(sql);

        sql = "set @var = (select sum(v1) from test.t0 group by v2)";
        setStmt = (SetStmt) analyzeSuccess(sql);
        Assert.assertTrue(setStmt.getSetVars().get(0).getExpression().getType().isIntegerType());

        sql = "set @var1 = 1, @var2 = 2";
        setStmt = (SetStmt) analyzeSuccess(sql);
        Assert.assertEquals(2, setStmt.getSetVars().size());

        sql = "set @var = [1,2,3]";
        analyzeFail(sql, "Can't set variable with type ARRAY");

        sql = "set @var = bitmap_empty()";
        analyzeFail(sql, "Can't set variable with type BITMAP");

        sql = "set @var = (select bitmap_empty())";
        analyzeFail(sql, "Can't set variable with type BITMAP");

        sql = "set @var = hll_empty()";
        analyzeFail(sql, "Can't set variable with type HLL");

        sql = "set @var = percentile_empty()";
        analyzeFail(sql, "Can't set variable with type PERCENTILE");

        sql = "set @var=foo";
        analyzeFail(sql, "Column 'foo' cannot be resolved");
    }

    @Test
    public void testSystemVariable() {
        String sql = "set @@query_timeout = 1";
        analyzeSuccess(sql);
        sql = "set @@GLOBAL.query_timeout = 1";
        analyzeSuccess(sql);
        sql = "set @@SESSION.query_timeout = 1";
        analyzeSuccess(sql);
        sql = "set @@LOCAL.query_timeout = 1";
        analyzeSuccess(sql);
        sql = "set @@event_scheduler = ON";
        analyzeSuccess(sql);
    }

    @Test
    public void testSetNames() {
        String sql = "SET NAMES 'utf8mb4' COLLATE 'bogus'";
        analyzeSuccess(sql);
        sql = "SET NAMES 'utf8mb4'";
        analyzeSuccess(sql);
        sql = "SET NAMES default";
        analyzeSuccess(sql);
        sql = "SET CHARSET 'utf8mb4'";
        analyzeSuccess(sql);
        sql = "SET CHAR SET 'utf8mb4'";
        analyzeSuccess(sql);
    }

    @Test
    public void testSetPassword() {
        String sql = "SET PASSWORD FOR 'testUser' = PASSWORD('testPass')";
        SetStmt setStmt = (SetStmt) analyzeSuccess(sql);
        SetPassVar setPassVar = (SetPassVar) setStmt.getSetVars().get(0);
        String password = new String(setPassVar.getPassword());
        Assert.assertEquals("*88EEBA7D913688E7278E2AD071FDB5E76D76D34B", password);

        sql = "SET PASSWORD = PASSWORD('testPass')";
        setStmt = (SetStmt) analyzeSuccess(sql);
        setPassVar = (SetPassVar) setStmt.getSetVars().get(0);
        password = new String(setPassVar.getPassword());
        Assert.assertEquals("*88EEBA7D913688E7278E2AD071FDB5E76D76D34B", password);

        sql = "SET PASSWORD = '*88EEBA7D913688E7278E2AD071FDB5E76D76D34B'";
        setStmt = (SetStmt) analyzeSuccess(sql);
        setPassVar = (SetPassVar) setStmt.getSetVars().get(0);
        password = new String(setPassVar.getPassword());
        Assert.assertEquals("*88EEBA7D913688E7278E2AD071FDB5E76D76D34B", password);
    }
}
