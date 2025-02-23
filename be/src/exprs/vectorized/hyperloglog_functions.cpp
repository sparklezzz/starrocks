// This file is licensed under the Elastic License 2.0. Copyright 2021-present, StarRocks Inc.

#include "exprs/vectorized/hyperloglog_functions.h"

#include "column/column_builder.h"
#include "column/column_viewer.h"
#include "column/object_column.h"
#include "exprs/vectorized/unary_function.h"
#include "types/hll.h"
#include "udf/udf.h"
#include "util/phmap/phmap.h"

namespace starrocks::vectorized {

// hll_cardinality_from_string
DEFINE_UNARY_FN_WITH_IMPL(hllCardinalityFromStringImpl, str) {
    HyperLogLog hll(str);
    return hll.estimate_cardinality();
}

StatusOr<ColumnPtr> HyperloglogFunctions::hll_cardinality_from_string(FunctionContext* context,
                                                                      const starrocks::vectorized::Columns& columns) {
    return VectorizedStrictUnaryFunction<hllCardinalityFromStringImpl>::evaluate<TYPE_VARCHAR, TYPE_BIGINT>(columns[0]);
}

// hll_cardinality
DEFINE_UNARY_FN_WITH_IMPL(hllCardinalityImpl, hll_ptr) {
    return hll_ptr->estimate_cardinality();
}

StatusOr<ColumnPtr> HyperloglogFunctions::hll_cardinality(FunctionContext* context,
                                                          const starrocks::vectorized::Columns& columns) {
    return VectorizedStrictUnaryFunction<hllCardinalityImpl>::evaluate<TYPE_HLL, TYPE_BIGINT>(columns[0]);
}

// hll_hash
StatusOr<ColumnPtr> HyperloglogFunctions::hll_hash(FunctionContext* context, const Columns& columns) {
    ColumnViewer<TYPE_VARCHAR> str_viewer(columns[0]);

    auto hll_column = HyperLogLogColumn::create();

    size_t size = columns[0]->size();
    for (int row = 0; row < size; ++row) {
        HyperLogLog hll;
        if (!str_viewer.is_null(row)) {
            Slice s = str_viewer.value(row);
            uint64_t hash = HashUtil::murmur_hash64A(s.data, s.size, HashUtil::MURMUR_SEED);
            hll.update(hash);
        }

        hll_column->append(&hll);
    }

    if (ColumnHelper::is_all_const(columns)) {
        return ConstColumn::create(hll_column, columns[0]->size());
    } else {
        return hll_column;
    }
}

// hll_empty
StatusOr<ColumnPtr> HyperloglogFunctions::hll_empty(FunctionContext* context, const Columns& columns) {
    auto p = HyperLogLogColumn::create();

    p->append_default();
    return ConstColumn::create(p, 1);
}

} // namespace starrocks::vectorized
