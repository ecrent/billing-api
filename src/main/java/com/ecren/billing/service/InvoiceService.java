package com.ecren.billing.service;

import com.ecren.billing.common.TenantContext;
import com.ecren.billing.domain.Invoice;
import com.ecren.billing.domain.enums.InvoiceStatus;
import com.ecren.billing.dto.response.InvoiceResponse;
import com.ecren.billing.dto.response.PageResponse;
import com.ecren.billing.exception.ConflictException;
import com.ecren.billing.exception.ResourceNotFoundException;
import com.ecren.billing.mapper.InvoiceMapper;
import com.ecren.billing.repository.InvoiceRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class InvoiceService {

    private final InvoiceRepository repository;
    private final InvoiceMapper mapper;

    public InvoiceService(InvoiceRepository repository, InvoiceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public PageResponse<InvoiceResponse> getAll(int page, int size, String status) {
        UUID tenantId = TenantContext.get();
        Pageable pageable = PageRequest.of(page, size);

        Page<Invoice> result;
        if (status == null || status.isBlank()) {
            result = repository.findByTenantId(tenantId, pageable);
        } else {
            InvoiceStatus invoiceStatus;
            try {
                invoiceStatus = InvoiceStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Unknown invoice status: " + status);
            }
            result = repository.findByTenantIdAndStatus(tenantId, invoiceStatus, pageable);
        }

        return new PageResponse<>(
                result.getContent().stream().map(mapper::toResponse).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    public InvoiceResponse getById(UUID id) {
        UUID tenantId = TenantContext.get();
        Invoice invoice = repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + id));
        return mapper.toResponse(invoice);
    }

    @Transactional
    public InvoiceResponse void_(UUID id) {
        UUID tenantId = TenantContext.get();
        Invoice invoice = repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + id));

        switch (invoice.getStatus()) {
            case PAID -> throw new ConflictException("Cannot void a PAID invoice");
            case VOID -> throw new ConflictException("Invoice is already VOID");
            default -> invoice.setStatus(InvoiceStatus.VOID);
        }

        return mapper.toResponse(repository.save(invoice));
    }
}
