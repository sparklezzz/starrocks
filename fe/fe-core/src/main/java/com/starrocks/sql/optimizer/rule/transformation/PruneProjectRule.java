// This file is licensed under the Elastic License 2.0. Copyright 2021-present, StarRocks Inc.

package com.starrocks.sql.optimizer.rule.transformation;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.starrocks.sql.optimizer.ExpressionContext;
import com.starrocks.sql.optimizer.OptExpression;
import com.starrocks.sql.optimizer.OptimizerContext;
import com.starrocks.sql.optimizer.base.ColumnRefSet;
import com.starrocks.sql.optimizer.operator.OperatorType;
import com.starrocks.sql.optimizer.operator.logical.LogicalOperator;
import com.starrocks.sql.optimizer.operator.logical.LogicalProjectOperator;
import com.starrocks.sql.optimizer.operator.pattern.Pattern;
import com.starrocks.sql.optimizer.operator.scalar.ColumnRefOperator;
import com.starrocks.sql.optimizer.operator.scalar.ScalarOperator;
import com.starrocks.sql.optimizer.rule.RuleType;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class PruneProjectRule extends TransformationRule {
    public PruneProjectRule() {
        super(RuleType.TF_PRUNE_PROJECT, Pattern.create(OperatorType.LOGICAL_PROJECT)
                .addChildren(Pattern.create(OperatorType.PATTERN_LEAF, OperatorType.PATTERN_MULTI_LEAF)));
    }

    @Override
    public boolean check(OptExpression input, OptimizerContext context) {
        Map<ColumnRefOperator, ScalarOperator> projections = ((LogicalProjectOperator) input.getOp()).getColumnRefMap();

        // avoid prune expression
        for (Map.Entry<ColumnRefOperator, ScalarOperator> entry : projections.entrySet()) {
            if (!entry.getKey().equals(entry.getValue())) {
                return false;
            }
        }

        // For count(*), the child output columns maybe empty, we needn't apply this rule
        LogicalOperator logicalOperator = (LogicalOperator) input.inputAt(0).getOp();
        ColumnRefSet outputColumn = logicalOperator.getOutputColumns(new ExpressionContext(input.inputAt(0)));
        return outputColumn.cardinality() > 0;
    }

    @Override
    public List<OptExpression> transform(OptExpression input, OptimizerContext context) {
        if (((LogicalProjectOperator) input.getOp()).getColumnRefMap().isEmpty()) {
            Map<ColumnRefOperator, ScalarOperator> projectMap = Maps.newHashMap();

            OptExpression child = input.inputAt(0);
            LogicalOperator logicalOperator = (LogicalOperator) child.getOp();

            ColumnRefOperator smallestColumn =
                    logicalOperator.getSmallestColumn(context.getColumnRefFactory(), child);
            if (smallestColumn != null) {
                projectMap.put(smallestColumn, smallestColumn);
            }
            return Lists.newArrayList(OptExpression
                    .create(new LogicalProjectOperator(projectMap, logicalOperator.getLimit()), input.getInputs()));
        }

        return Collections.emptyList();
    }

}