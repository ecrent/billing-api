package com.ecren.billing.mapper;

import com.ecren.billing.domain.Tenant;
import com.ecren.billing.dto.response.TenantResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TenantMapper {

    @Mapping(target = "tenantId", source = "id")
    @Mapping(target = "status", expression = "java(tenant.getStatus().name())")
    TenantResponse toResponse(Tenant tenant);
}
