package com.ecren.billing.mapper;

import com.ecren.billing.domain.UsageRecord;
import com.ecren.billing.dto.response.UsageRecordResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UsageMapper {

    @Mapping(target = "metric", expression = "java(record.getMetric().name())")
    UsageRecordResponse toResponse(UsageRecord record);
}
