package com.ecren.billing.mapper;

import com.ecren.billing.domain.LedgerEntry;
import com.ecren.billing.dto.response.LedgerEntryResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface LedgerMapper {

    @Mapping(target = "ledgerEntryId", source = "id")
    @Mapping(target = "type", expression = "java(entry.getType().name())")
    LedgerEntryResponse toResponse(LedgerEntry entry);
}
