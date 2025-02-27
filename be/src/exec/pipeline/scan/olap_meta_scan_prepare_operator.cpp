// This file is licensed under the Elastic License 2.0. Copyright 2021-present, StarRocks Inc.

#include "exec/pipeline/scan/olap_meta_scan_prepare_operator.h"

#include "exec/pipeline/scan/meta_scan_context.h"
#include "exec/pipeline/scan/meta_scan_operator.h"
#include "gen_cpp/Types_types.h"
#include "storage/olap_common.h"

namespace starrocks::pipeline {

OlapMetaScanPrepareOperator::OlapMetaScanPrepareOperator(OperatorFactory* factory, int32_t id, int32_t plan_node_id,
                                                         int32_t driver_sequence,
                                                         vectorized::OlapMetaScanNode* const scan_node,
                                                         MetaScanContextPtr scan_ctx)
        : MetaScanPrepareOperator(factory, id, plan_node_id, driver_sequence, std::string("olap_meta_scan_prepare"),
                                  scan_ctx),
          _scan_node(scan_node) {}

Status OlapMetaScanPrepareOperator::_prepare_scan_context(RuntimeState* state) {
    auto meta_scan_ranges = _morsel_queue->olap_scan_ranges();
    for (auto& scan_range : meta_scan_ranges) {
        vectorized::MetaScannerParams params;
        params.scan_range = scan_range;
        auto scanner = std::make_shared<vectorized::OlapMetaScanner>(_scan_node);
        RETURN_IF_ERROR(scanner->init(state, params));
        TTabletId tablet_id = scan_range->tablet_id;
        _scan_ctx->add_scanner(tablet_id, scanner);
    }
    return Status::OK();
}

OlapMetaScanPrepareOperatorFactory::OlapMetaScanPrepareOperatorFactory(
        int32_t id, int32_t plan_node_id, vectorized::OlapMetaScanNode* const scan_node,
        std::shared_ptr<MetaScanContextFactory> scan_ctx_factory)
        : MetaScanPrepareOperatorFactory(id, plan_node_id, "olap_meta_scan_prepare", scan_ctx_factory),
          _scan_node(scan_node) {}

OperatorPtr OlapMetaScanPrepareOperatorFactory::create(int32_t degree_of_parallelism, int32_t driver_sequence) {
    return std::make_shared<OlapMetaScanPrepareOperator>(this, _id, _plan_node_id, driver_sequence, _scan_node,
                                                         _scan_ctx_factory->get_or_create(driver_sequence));
}

} // namespace starrocks::pipeline
