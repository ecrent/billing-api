package com.ecren.billing.mapper;

import com.ecren.billing.domain.Invoice;
import com.ecren.billing.domain.InvoiceLineItem;
import com.ecren.billing.dto.response.InvoiceResponse;
import com.ecren.billing.dto.response.LineItemResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface InvoiceMapper {

    @Mapping(target = "status", expression = "java(invoice.getStatus().name())")
    InvoiceResponse toResponse(Invoice invoice);

    @Mapping(target = "type", expression = "java(item.getType().name())")
    LineItemResponse toResponse(InvoiceLineItem item);
}
