// This file is licensed under the Elastic License 2.0. Copyright 2021-present, StarRocks Inc.

#pragma once

#include <memory>
#include <utility>
#include <vector>

#include "exec/pipeline/scan/morsel.h"

namespace starrocks::pipeline {
class DriverSource;
using DriverSourcePtr = std::unique_ptr<DriverSource>;
using DriverSources = std::vector<DriverSourcePtr>;
class DriverSource {
public:
    DriverSource(Morsels morsels, int32_t source_id) : _morsels(std::move(morsels)), _source_id(source_id) {}

    const Morsels& get_morsels() const { return _morsels; }

    int32_t get_source_id() const { return _source_id; }

private:
    Morsels _morsels;
    int32_t _source_id;
};
} // namespace starrocks::pipeline
