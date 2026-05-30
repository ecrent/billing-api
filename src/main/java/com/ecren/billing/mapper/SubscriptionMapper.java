package com.ecren.billing.mapper;

import com.ecren.billing.domain.Subscription;
import com.ecren.billing.dto.response.SubscriptionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SubscriptionMapper {

    @Mapping(target = "status", expression = "java(subscription.getStatus().name())")
    SubscriptionResponse toResponse(Subscription subscription);
}
