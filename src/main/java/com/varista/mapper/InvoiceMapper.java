package com.varista.mapper;

import com.varista.entity.Company;
import com.varista.entity.Invoice;
import com.varista.model.CompanyInfo;
import com.varista.model.InvoiceData;
import com.varista.model.InvoiceRequest;
import com.varista.utils.InvoiceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Collections;
import java.util.stream.IntStream;


@Component
public class InvoiceMapper {

    private static final Logger log = LoggerFactory.getLogger(InvoiceMapper.class);

    @Value("${varista.bankName}")
    private String bankName;
    @Value("${varista.accountNumber}")
    private String accountNumber;
    @Value("${varista.ifsc}") private String ifsc;


    public InvoiceData mapInvoiceRequestToInvoiceData(InvoiceRequest invoiceRequest, Company buyerInfo) {
        log.debug("mapInvoiceRequestToInvoiceData - start: buyerId={}, itemsCount={}",
                buyerInfo != null ? buyerInfo.getId() : null,
                invoiceRequest != null && invoiceRequest.getItems() != null ? invoiceRequest.getItems().size() : 0);

        InvoiceData invoiceData = new InvoiceData();
        Long id=InvoiceUtils.generate5DigitId();

        String invoiceNo = String.format("INV-%d",  id);
        invoiceData.setInvoiceId(id);
        invoiceData.setInvoiceNo(invoiceNo);
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");
        invoiceData.setDated(now.format(formatter));
        // Map seller and buyer details
        getBuyerInfo(buyerInfo, invoiceData);

        invoiceData.setBuyerOrderNo("238-4234");

        // Map items
        List<InvoiceData.Item> items;
        if (invoiceRequest == null || invoiceRequest.getItems() == null || invoiceRequest.getItems().isEmpty()) {
            log.warn("InvoiceRequest items are null or empty for buyerId={}", buyerInfo != null ? buyerInfo.getId() : null);
            items = Collections.emptyList();
        } else {
            items = IntStream.range(0, invoiceRequest.getItems().size())
                    .mapToObj(i -> {
                        InvoiceRequest.ItemRequest itemRequest = invoiceRequest.getItems().get(i);
                        InvoiceData.Item item = new InvoiceData.Item();
                        item.setSrNo(i + 1); // Serial number starts from 1
                        item.setDescription(itemRequest.getDescription());
                        item.setHsnSac(itemRequest.getHsnSac().toString());
                        item.setQuantity(itemRequest.getQuantity());
                        item.setRate(itemRequest.getRate());
                        item.setUnit(itemRequest.getUnit());
                        item.setAmount(itemRequest.getAmount());
                        return item;
                    })
                    .toList();
        }

        invoiceData.setItems(items);

        if (!items.isEmpty()) {
            invoiceData.setHsnSac(invoiceRequest.getItems().get(0).getHsnSac());
        } else {
            invoiceData.setHsnSac(null);
        }

        BigDecimal totalTaxable = BigDecimal.valueOf(invoiceData.getItems().stream().mapToDouble(InvoiceData.Item::getAmount).sum());
        invoiceData.setTotalTaxableValue(totalTaxable);

        BigDecimal taxRate = invoiceRequest != null ? BigDecimal.valueOf(invoiceRequest.getTaxRate()) : BigDecimal.ZERO;
        invoiceData.setTaxRate(taxRate);

        BigDecimal taxAmount = invoiceRequest != null && invoiceRequest.getTaxAmt() != null ? invoiceRequest.getTaxAmt() : BigDecimal.ZERO;
        if (invoiceRequest == null || invoiceRequest.getTaxAmt() == null) {
            log.warn("Tax amount missing in InvoiceRequest for buyerId={}", buyerInfo != null ? buyerInfo.getId() : null);
        }
        invoiceData.setTaxAmount(taxAmount);

        invoiceData.setTotalAmount(invoiceData.getTaxAmount().add(invoiceData.getTotalTaxableValue()));

        // Map bank details
        InvoiceData.BankDetails bankDetails = new InvoiceData.BankDetails();
        bankDetails.setBankName(bankName);
        bankDetails.setAccNo(accountNumber);
        bankDetails.setIfsc(ifsc);
        invoiceData.setBankDetails(bankDetails);

        String maskedAcc = accountNumber == null ? "null" : accountNumber.length() > 4 ? "****" + accountNumber.substring(accountNumber.length() - 4) : "****";
        log.info("Created InvoiceData: invoiceNo={}, buyerId={}, items={}, totalTaxable={}, taxRate={}, taxAmount={}, totalAmount={}, bank={}/{}",
                invoiceData.getInvoiceNo(),
                buyerInfo != null ? buyerInfo.getId() : null,
                items.size(),
                invoiceData.getTotalTaxableValue(),
                invoiceData.getTaxRate(),
                invoiceData.getTaxAmount(),
                invoiceData.getTotalAmount(),
                bankName,
                maskedAcc
        );

        log.debug("mapInvoiceRequestToInvoiceData - end: invoiceNo={}", invoiceData.getInvoiceNo());
        return invoiceData;
    }

    private void getBuyerInfo(Company invoiceRequest, InvoiceData invoiceData) {
        if (invoiceRequest == null) {
            log.warn("getBuyerInfo called with null Company");
            return;
        }
        CompanyInfo buyer = new CompanyInfo();
        buyer.setName(invoiceRequest.getName());
        buyer.setEmail(invoiceRequest.getEmail());
        buyer.setPhone(invoiceRequest.getPhone());
        buyer.setAddress(invoiceRequest.getAddress());
        buyer.setGstin(invoiceRequest.getGstin());
        buyer.setId(invoiceRequest.getId());
        invoiceData.setBuyer(buyer);

        log.debug("getBuyerInfo - mapped buyerId={}, name={}", invoiceRequest.getId(), invoiceRequest.getName());
    }

    public Invoice mapInvoiceRequestToInvoiceEntity(InvoiceRequest invoiceRequest) {
        log.debug("mapInvoiceRequestToInvoiceEntity - start: buyerId={}, totalAmt={}",
                invoiceRequest != null ? invoiceRequest.getBuyerId() : null,
                invoiceRequest != null ? invoiceRequest.getTotalAmt() : null);

        Invoice invoice = new Invoice();
        if (invoiceRequest != null) {
            invoice.setCompanyId(invoiceRequest.getBuyerId());
            invoice.setTotalAmount(invoiceRequest.getTotalAmt());
        } else {
            log.warn("mapInvoiceRequestToInvoiceEntity called with null InvoiceRequest");
        }
        invoice.setCreatedAt(LocalDateTime.now());
        invoice.setStatus("Pending"); // Assuming 1 indicates 'Created'
        invoice.setUpdatedAt(LocalDateTime.now());

        log.debug("mapInvoiceRequestToInvoiceEntity - end: companyId={}, totalAmount={}",
                invoice.getCompanyId(), invoice.getTotalAmount());
        return invoice;
    }

    public List<InvoiceData> mapToInvoiceData(List<Invoice> invoices) {
        if (invoices == null || invoices.isEmpty()) {
            log.debug("mapToInvoiceData - received empty or null invoice list");
            return Collections.emptyList();
        }

        log.debug("mapToInvoiceData - start: invoicesCount={}", invoices.size());

        List<InvoiceData> result = invoices.stream()
                .map(invoice -> {
                    InvoiceData dto = new InvoiceData();

                    CompanyInfo company = new CompanyInfo();
                    company.setId(invoice.getCompanyId());
                    dto.setBuyer(company);

                    dto.setTotalAmount(invoice.getTotalAmount());
                    dto.setDated(invoice.getCreatedAt() != null
                            ? invoice.getCreatedAt().toString()
                            : null);
                    dto.setInvoiceNo(invoice.getInvoiceNumber());
                    dto.setInvoiceId(invoice.getId());
                    dto.setStatus(invoice.getStatus());

                    log.debug("mapToInvoiceData - mapped invoiceId={}, companyId={}, totalAmount={}",
                            invoice.getId(), invoice.getCompanyId(), invoice.getTotalAmount());
                    return dto;
                })
                .toList();

        log.info("mapToInvoiceData - completed: mappedCount={}", result.size());
        return result;
    }


}