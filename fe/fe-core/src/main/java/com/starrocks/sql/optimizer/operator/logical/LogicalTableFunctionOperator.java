// This file is licensed under the Elastic License 2.0. Copyright 2021-present, StarRocks Inc.
package com.starrocks.sql.optimizer.operator.logical;

import com.starrocks.catalog.TableFunction;
import com.starrocks.common.Pair;
import com.starrocks.sql.optimizer.ExpressionContext;
import com.starrocks.sql.optimizer.OptExpression;
import com.starrocks.sql.optimizer.OptExpressionVisitor;
import com.starrocks.sql.optimizer.base.ColumnRefSet;
import com.starrocks.sql.optimizer.operator.OperatorType;
import com.starrocks.sql.optimizer.operator.OperatorVisitor;
import com.starrocks.sql.optimizer.operator.scalar.ColumnRefOperator;
import com.starrocks.sql.optimizer.operator.scalar.ScalarOperator;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class LogicalTableFunctionOperator extends LogicalOperator {
    private final TableFunction fn;
    //ColumnRefSet represent output by table function
    private final ColumnRefSet fnResultColumnRefSet;
    //External column ref of the join logic generated by the table function
    private ColumnRefSet outerColumnRefSet;
    //table function input parameters
    private final List<Pair<ColumnRefOperator, ScalarOperator>> fnParamColumnProject;

    public LogicalTableFunctionOperator(ColumnRefSet fnResultColumnRefSet, TableFunction fn,
                                        List<Pair<ColumnRefOperator, ScalarOperator>> fnParamColumnProject,
                                        ColumnRefSet outerColumnRefSet) {
        super(OperatorType.LOGICAL_TABLE_FUNCTION);
        this.fnResultColumnRefSet = fnResultColumnRefSet;
        this.fn = fn;
        this.fnParamColumnProject = fnParamColumnProject;
        this.outerColumnRefSet = outerColumnRefSet;
    }

    public LogicalTableFunctionOperator(ColumnRefSet fnResultColumnRefSet, TableFunction fn,
                                        List<Pair<ColumnRefOperator, ScalarOperator>> fnParamColumnProject) {
        this(fnResultColumnRefSet, fn, fnParamColumnProject, new ColumnRefSet());
    }

    private LogicalTableFunctionOperator(Builder builder) {
        super(OperatorType.LOGICAL_TABLE_FUNCTION, builder.getLimit(), builder.getPredicate(), builder.getProjection());
        this.fnResultColumnRefSet = builder.fnResultColumnRefSet;
        this.fn = builder.fn;
        this.fnParamColumnProject = builder.fnParamColumnProject;
        this.outerColumnRefSet = builder.outerColumnRefSet;
    }

    public ColumnRefSet getFnResultColumnRefSet() {
        return fnResultColumnRefSet;
    }

    public TableFunction getFn() {
        return fn;
    }

    public List<Pair<ColumnRefOperator, ScalarOperator>> getFnParamColumnProject() {
        return fnParamColumnProject;
    }

    public ColumnRefSet getOuterColumnRefSet() {
        return outerColumnRefSet;
    }

    public void setOuterColumnRefSet(ColumnRefSet outerColumnRefSet) {
        this.outerColumnRefSet = outerColumnRefSet;
    }

    @Override
    public ColumnRefSet getOutputColumns(ExpressionContext expressionContext) {
        if (projection != null) {
            return new ColumnRefSet(new ArrayList<>(projection.getColumnRefMap().keySet()));
        } else {
            ColumnRefSet outputColumns = (ColumnRefSet) outerColumnRefSet.clone();
            outputColumns.union(fnResultColumnRefSet);
            return outputColumns;
        }
    }

    @Override
    public <R, C> R accept(OperatorVisitor<R, C> visitor, C context) {
        return visitor.visitLogicalTableFunction(this, context);
    }

    @Override
    public <R, C> R accept(OptExpressionVisitor<R, C> visitor, OptExpression optExpression, C context) {
        return visitor.visitLogicalTableFunction(optExpression, context);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        LogicalTableFunctionOperator that = (LogicalTableFunctionOperator) o;
        return Objects.equals(fn, that.fn) && Objects.equals(fnResultColumnRefSet, that.fnResultColumnRefSet)
                && Objects.equals(outerColumnRefSet, that.outerColumnRefSet)
                && Objects.equals(fnParamColumnProject, that.fnParamColumnProject);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), fn, fnResultColumnRefSet, outerColumnRefSet, fnParamColumnProject);
    }

    public static class Builder
            extends LogicalOperator.Builder<LogicalTableFunctionOperator, LogicalTableFunctionOperator.Builder> {
        private TableFunction fn;
        private ColumnRefSet fnResultColumnRefSet;
        private ColumnRefSet outerColumnRefSet;
        private List<Pair<ColumnRefOperator, ScalarOperator>> fnParamColumnProject;

        @Override
        public LogicalTableFunctionOperator build() {
            return new LogicalTableFunctionOperator(this);
        }

        @Override
        public LogicalTableFunctionOperator.Builder withOperator(LogicalTableFunctionOperator tableFunctionOperator) {
            super.withOperator(tableFunctionOperator);

            this.fnResultColumnRefSet = tableFunctionOperator.fnResultColumnRefSet;
            this.fn = tableFunctionOperator.fn;
            this.fnParamColumnProject = tableFunctionOperator.fnParamColumnProject;
            this.outerColumnRefSet = tableFunctionOperator.outerColumnRefSet;
            return this;
        }
    }
}