package com.varista.service;

import com.varista.entity.Company;
import com.varista.entity.Invoice;
import com.varista.model.InvoiceData;
import com.varista.model.InvoiceRequest;
import com.varista.model.InvoiceResponse;
import com.varista.repository.InvoiceRepository;
import com.varista.utils.PdfGenerator;
import lombok.extern.slf4j.Slf4j;
import com.varista.mapper.InvoiceMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import com.varista.utils.IndianCurrencyConverter;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
@Slf4j
public class InvoiceService {

    @Autowired
    InvoiceMapper invoiceMapper;

    @Autowired
    CompanyService companyService;

    @Autowired
    InvoiceRepository invoiceRepository;

    public InvoiceResponse createInvoicePdfBase64(InvoiceRequest invoiceRequest) throws IOException {
        // Convert InvoiceRequest to InvoiceData (mocked for this example)
        InvoiceResponse invoiceResponse = new InvoiceResponse();
        try {
            String htmlTemplate;
            InvoiceData invoiceData = new InvoiceData();

            Company buyerInfo = companyService.getCompanyById(Long.valueOf(invoiceRequest.getBuyerId()));

            invoiceData = invoiceMapper.mapInvoiceRequestToInvoiceData(invoiceRequest, buyerInfo);

            ClassPathResource resource = new ClassPathResource("invoice_template.html");
            try (InputStream inputStream = resource.getInputStream()) {
                htmlTemplate = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }

            // Populate the HTML template (simplified example)
            String populatedHtml = htmlTemplate
                    .replace("{{invoice_no}}", invoiceData.getInvoiceNo())
                    .replace("{{dated}}", invoiceData.getDated())
                    .replace("{{seller_name}}", invoiceData.getSeller().getName())
                    .replace("{{seller_address}}", invoiceData.getSeller().getAddress())
                    .replace("{{seller_gstin}}", invoiceData.getSeller().getGstin())
                    .replace("{{seller_email}}", invoiceData.getSeller().getEmail())
                    .replace("{{buyer_name}}", invoiceData.getBuyer().getName())
                    .replace("{{buyer_address}}", invoiceData.getBuyer().getAddress())
                    .replace("{{buyer_gstin}}", invoiceData.getBuyer().getGstin())
                    .replace("{{buyer_email}}", invoiceData.getBuyer().getEmail())
                    .replace("{{buyer_phone}}", invoiceData.getBuyer().getPhone().toString())
                    .replace("{{buyer_order_no}}", invoiceData.getBuyerOrderNo())
                    .replace("{{item_rows}}", generateItemRowsHtml(invoiceData.getItems()))
                    .replace("{{hsn_sac}}", invoiceData.getHsnSac().toString())
                    .replace("{{total_quantity}}", String.valueOf(
                            invoiceData.getItems().stream().mapToDouble(InvoiceData.Item::getQuantity).sum()))
                    .replace("{{total_amount_in_words}}", IndianCurrencyConverter.convertRupeesToWords(BigDecimal.valueOf(
                            invoiceData.getItems().stream().mapToDouble(InvoiceData.Item::getAmount).sum())))
                    .replace("{{total_taxable_value}}", String.valueOf(invoiceData.getTotalTaxableValue()))
                    .replace("{{central_tax_rate}}", String.valueOf(invoiceData.getCentralTaxRate()))
                    .replace("{{central_tax_amount}}", String.valueOf(invoiceData.getCentralTaxAmount()))
                    .replace("{{state_tax_rate}}", String.valueOf(invoiceData.getStateTaxRate()))
                    .replace("{{state_tax_amount}}", String.valueOf(invoiceData.getStateTaxAmount()))
                    .replace("{{igst_amount}}", String.valueOf(invoiceData.getIgstAmount()))
                    .replace("{{total_amount}}", String.valueOf(invoiceData.getTotalAmount()))
                    .replace("{{bank_name}}", invoiceData.getBankDetails().getBankName())
                    .replace("{{account_number}}", invoiceData.getBankDetails().getAccNo())
                    .replace("{{ifsc_code}}", invoiceData.getBankDetails().getIfsc());

            System.out.println("HTML template populated. Ready for PDF conversion.");

            // Converthtml to pdf and save to cloud storage or return the invoice id


            PdfGenerator.generateInvoicePdfToFile(populatedHtml, "generated_invoice.pdf");



            invoiceResponse.setInvoiceId("TI/2005/08");
            invoiceResponse.setCreatedAt("2024-08-15");
            invoiceResponse.setAmount(invoiceData.getTotalAmount().toString());
            invoiceResponse.setStatus("SUCCESS");
            invoiceResponse.setMessage("Invoice PDF generated successfully");

            // save the invoiceinfo to database if needed
            Invoice invoice = invoiceMapper.mapInvoiceRequestToInvoiceEntity(invoiceRequest, invoiceResponse.getInvoiceId());

            invoiceRepository.save(invoice);

            return invoiceResponse;
        } catch (Exception e) {
            log.error("Error generating invoice PDF: ", e);

            throw e;
        }
    }

    private String generateItemRowsHtml(List<InvoiceData.Item> items) {
        StringBuilder rowsHtml = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            rowsHtml.append("<tr style=\"height:20pt\">");
            rowsHtml.append("<td style=\"width:37pt;border-left-style:solid;border-left-width:2pt;border-left-color:#606060;\">\n" +
                    "            <p class=\"s3\" style=\"padding-top: 5pt;text-align: center;\">").append(i + 1).append("</p></td>");  //pass serial number here
            rowsHtml.append("<td style=\"width:37pt;border-left-style:solid;border-left-width:2pt;border-left-color:#606060;\">\n" +
                    "            <p class=\"s3\" style=\"padding-top: 5pt;text-align: center;\">").append(items.get(i).getDescription()).append("</p></td>");
            rowsHtml.append("<td style=\"width:37pt;border-left-style:solid;border-left-width:2pt;border-left-color:#606060;\">\n" +
                    "            <p class=\"s3\" style=\"padding-top: 5pt;text-align: center;\">").append(items.get(i).getHsnSac()).append("</p></td>");
            rowsHtml.append("<td style=\"width:37pt;border-left-style:solid;border-left-width:2pt;border-left-color:#606060;\">\n" +
                    "            <p class=\"s3\" style=\"padding-top: 5pt;text-align: center;\">").append(items.get(i).getQuantity()).append("</p></td>");
            rowsHtml.append("<td style=\"width:37pt;border-left-style:solid;border-left-width:2pt;border-left-color:#606060;\">\n" +
                    "            <p class=\"s3\" style=\"padding-top: 5pt;text-align: center;\">").append(items.get(i).getRate()).append("</p></td>");
            rowsHtml.append("<td style=\"width:37pt;border-left-style:solid;border-left-width:2pt;border-left-color:#606060;\">\n" +
                    "            <p class=\"s3\" style=\"padding-top: 5pt;text-align: center;\">").append(items.get(i).getUnit()).append("</p></td>");
            rowsHtml.append("<td style=\"width:37pt;border-left-style:solid;border-left-width:2pt;border-left-color:#606060;border-right-style:solid;border-right-width:2pt;border-right-color:#606060;\">\n" +
                    "            <p class=\"s3\" style=\"padding-top: 5pt;text-align: center;\">").append(items.get(i).getAmount()).append("</p></td>");
            rowsHtml.append("</tr>");
        }
        return rowsHtml.toString();
    }
}
