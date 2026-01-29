package com.varista.mapper;

import com.varista.entity.Company;
import com.varista.entity.Invoice;
import com.varista.model.InvoiceData;
import com.varista.model.InvoiceRequest;
import com.varista.utils.InvoiceUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;


@Component
public class InvoiceMapper {


    public InvoiceData mapInvoiceRequestToInvoiceData(InvoiceRequest invoiceRequest, Company buyerInfo) {
        InvoiceData invoiceData = new InvoiceData();

        invoiceData.setInvoiceNo("TI/2005/08");
        invoiceData.setDated(LocalDateTime.now().toString());

        // Map seller and buyer details
        getBuyerInfo(buyerInfo, invoiceData);

        getSellerInfo(invoiceData);

        invoiceData.setBuyerOrderNo("238-4234");

        // Map items
        List<InvoiceData.Item> items = invoiceRequest.getItems().stream().map(itemRequest -> {
            InvoiceData.Item item = new InvoiceData.Item();
            item.setDescription(itemRequest.getDescription());
            item.setHsnSac(itemRequest.getHsnSac());
            item.setQuantity(itemRequest.getQuantity());
            item.setRate(itemRequest.getRate());
            item.setUnit(itemRequest.getUnit());
            item.setAmount(itemRequest.getAmount());
            return item;
        }).toList();

        invoiceData.setItems(items);
        invoiceData.setHsnSac(invoiceRequest.getHsnSac());
        invoiceData.setTotalTaxableValue(BigDecimal.valueOf(invoiceData.getItems().stream().mapToDouble(InvoiceData.Item::getAmount).sum()));
        invoiceData.setCentralTaxRate(BigDecimal.valueOf(invoiceRequest.getCentralTaxRate()));
        invoiceData.setCentralTaxAmount(InvoiceUtils.calculateTaxAmount(invoiceData.getTotalTaxableValue(), invoiceData.getCentralTaxRate()));
        invoiceData.setStateTaxRate(BigDecimal.valueOf(invoiceRequest.getStateTaxRate()));
        invoiceData.setStateTaxAmount(InvoiceUtils.calculateTaxAmount(invoiceData.getTotalTaxableValue(), invoiceData.getStateTaxRate()));
        invoiceData.setIgstAmount(invoiceData.getStateTaxAmount().add(invoiceData.getCentralTaxAmount()));
        invoiceData.setTotalAmount(invoiceData.getIgstAmount().add(invoiceData.getTotalTaxableValue()));

        // Map bank details
        InvoiceData.BankDetails bankDetails = new InvoiceData.BankDetails();
        bankDetails.setBankName(invoiceRequest.getBankName());
        bankDetails.setAccNo(invoiceRequest.getAccountNumber());
        bankDetails.setIfsc(invoiceRequest.getIfscCode());
        invoiceData.setBankDetails(bankDetails);

        return invoiceData;
    }

    private void getBuyerInfo(Company invoiceRequest, InvoiceData invoiceData) {
        Company buyer = new Company();
        buyer.setName(invoiceRequest.getName());
        buyer.setEmail(invoiceRequest.getEmail());
        buyer.setPhone(invoiceRequest.getPhone());
        buyer.setAddress(invoiceRequest.getAddress());
        buyer.setGstin(invoiceRequest.getGstin());
        buyer.setId(invoiceRequest.getId());
        invoiceData.setBuyer(buyer);
    }

    private static void getSellerInfo(InvoiceData invoiceData) {
        Company buyer = new Company();
        buyer.setName("ABC");
        buyer.setEmail("abc@gmail.com");
        buyer.setPhone(BigDecimal.valueOf( 6461591415L));
        buyer.setAddress("2394 23-4 23i8 ");
        buyer.setGstin("2398yu48");
        buyer.setId(23983L);
        invoiceData.setBuyer(buyer);
    }

    public Invoice mapInvoiceRequestToInvoiceEntity(InvoiceRequest invoiceRequest, String invoiceId) {
        Invoice invoice = new Invoice();
        invoice.setId(Long.valueOf(invoiceId));
        invoice.setCompany_Id(invoiceRequest.getBuyerId());
        invoice.setCreated_at(LocalDateTime.now());
        invoice.setInvoice_number(invoiceId);
        invoice.setIssue_date(LocalDateTime.now());
        invoice.setStatus("Pending"); // Assuming 1 indicates 'Created'
        invoice.setTotal_amount(BigDecimal.valueOf(invoiceRequest.getTotalAmount()));
        invoice.setUpdated_at(LocalDateTime.now());
        return invoice;
    }
}
