package com.ecren.billing.mapper;

import com.ecren.billing.domain.Plan;
import com.ecren.billing.domain.PlanMetricLimit;
import com.ecren.billing.dto.response.PlanMetricLimitResponse;
import com.ecren.billing.dto.response.PlanResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PlanMapper {

    @Mapping(target = "planId", source = "id")
    @Mapping(target = "status", expression = "java(plan.getStatus().name())")
    PlanResponse toResponse(Plan plan);

    @Mapping(target = "limitId", source = "id")
    @Mapping(target = "metric", expression = "java(limit.getMetric().name())")
    PlanMetricLimitResponse toResponse(PlanMetricLimit limit);
}
