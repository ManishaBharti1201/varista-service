package com.varista.service;

import com.varista.constant.Constant;
import com.varista.entity.Company;
import com.varista.entity.Invoice;
import com.varista.entity.Quotation;
import com.varista.model.InvoiceData;
import com.varista.model.InvoiceRequest;
import com.varista.model.InvoiceResponse;
import com.varista.repository.InvoiceRepository;
import com.varista.utils.PdfGenerator;
import lombok.extern.slf4j.Slf4j;
import com.varista.mapper.InvoiceMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import com.varista.utils.IndianCurrencyConverter;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
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

    @Value("${invoice.directory}")
    public String INVOICE_DIRECTORY;


    public InvoiceResponse createInvoice(InvoiceRequest invoiceRequest) throws IOException {
        // Convert InvoiceRequest to InvoiceData (mocked for this example)
        InvoiceResponse invoiceResponse = new InvoiceResponse();
        try {
            String htmlTemplate;
            InvoiceData invoiceData = new InvoiceData();

            Company buyerInfo = companyService.getCompanyById(invoiceRequest.getBuyerId());

            invoiceData = invoiceMapper.mapInvoiceRequestToInvoiceData(invoiceRequest, buyerInfo);

            ClassPathResource resource = new ClassPathResource("new_invoice_template.html");
            try (InputStream inputStream = resource.getInputStream()) {
                htmlTemplate = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }

            htmlTemplate = generateItemRowsHtml(invoiceData.getItems(), htmlTemplate);

            // Populate the HTML template (simplified example)
            String populatedHtml = htmlTemplate
                    .replace("{{invoice_number}}", invoiceData.getInvoiceNo())
                    .replace("{{invoice_date}}", invoiceData.getDated())
                    .replace("{{buyer_name}}", invoiceData.getBuyer().getName().replace("&", "&amp;"))
                    .replace("{{buyer_address_line1}}", invoiceData.getBuyer().getAddress().getAddressLine1())
                    .replace("{{buyer_address_line2}}", invoiceData.getBuyer().getAddress().getAddressLine2())
                    .replace("{{buyer_address_line3}}", invoiceData.getBuyer().getAddress().getCityStateZip())
                    .replace("{{buyer_gst}}", invoiceData.getBuyer().getGstin())
                    .replace("{{buyer_email}}", invoiceData.getBuyer().getEmail())
                    .replace("{{buyer_phone}}", invoiceData.getBuyer().getPhone())
                    .replace("{{buyer_order_number}}", invoiceData.getBuyerOrderNo())
                    .replace("{{hsn_sac}}", invoiceData.getHsnSac().toString())
                    .replace("{{total_quantity}}", String.valueOf(
                            invoiceData.getItems().stream().mapToDouble(InvoiceData.Item::getQuantity).sum()))
                    .replace("{{amount_in_words}}", IndianCurrencyConverter.convertRupeesToWords(BigDecimal.valueOf(
                            invoiceData.getItems().stream().mapToDouble(InvoiceData.Item::getAmount).sum())))
                    .replace("{{taxable_value}}", String.valueOf(invoiceData.getTotalTaxableValue()))
                    .replace("{{tax_rate}}", String.valueOf(invoiceData.getTaxRate()))
                    .replace("{{tax_amount}}", String.valueOf(invoiceData.getTaxAmount()))
                    .replace("{{igst_roundoff}}", String.valueOf(invoiceData.getTaxAmount()))
                    .replace("{{total_amount}}", String.valueOf(invoiceData.getTotalAmount()))
                    .replace("{{bank_name}}", invoiceData.getBankDetails().getBankName())
                    .replace("{{account_number}}", invoiceData.getBankDetails().getAccNo())
                    .replace("{{ifsc_code}}", invoiceData.getBankDetails().getIfsc())
                    .replace("{{company_name}}", "Varista Industrial Solutions");

            System.out.println("HTML template populated. Ready for PDF conversion.");

            // Converthtml to pdf and save to cloud storage or return the invoice id

            // Path: /app/invoices/{companyId}/INV-{id}.pdf
            String directory = INVOICE_DIRECTORY + invoiceData.getBuyer().getName().replace("/", "_");
            // Ensure directory exists
            Path dirPath = Paths.get(directory);
            if (!dirPath.toFile().exists()) {
                log.info("Creating directory for quotations at path: {}", dirPath.toAbsolutePath());
                dirPath.toFile().mkdirs();
            }
            String fileName = invoiceData.getInvoiceNo() + ".pdf";
            String fullPath = directory + "/" + fileName;
            //PdfGenerator.generateInvoicePdfToFile(populatedHtml, fullPath);
            PdfGenerator.generatePdfFromHtml(populatedHtml, fullPath);

            invoiceResponse.setInvoiceId(invoiceData.getInvoiceId());
            invoiceResponse.setInvoiceNumber(invoiceData.getInvoiceNo());
            invoiceResponse.setCreatedAt(LocalDateTime.now().toString());
            invoiceResponse.setAmount(invoiceData.getTotalAmount().toString());
            invoiceResponse.setStatus("SUCCESS");
            invoiceResponse.setMessage("Invoice PDF generated successfully");

            // save the invoiceinfo to database if needed
            Invoice invoice = invoiceMapper.mapInvoiceRequestToInvoiceEntity(invoiceRequest);
            invoice.setId(invoiceData.getInvoiceId());
            invoice.setInvoiceNumber(invoiceData.getInvoiceNo());

            invoiceRepository.save(invoice);

            return invoiceResponse;
        } catch (Exception e) {
            log.error("Error creating invoice: {}", e.getMessage());

            throw e;
        }
    }

    private String generateItemRowsHtml(List<InvoiceData.Item> items, String itemTable) {
        for (int i = 0; i < 15; i++) {
            int row = i + 1; // because placeholders start from 1

            if (i < items.size()) {
                InvoiceData.Item item = items.get(i);

                itemTable = itemTable
                        .replace("{{item_serial_number_" + row + "}}", String.valueOf(item.getSrNo()))
                        .replace("{{item_description_" + row + "}}", item.getDescription())
                        .replace("{{item_hsn_" + row + "}}", item.getHsnSac())
                        .replace("{{item_quantity_" + row + "}}", String.valueOf(item.getQuantity()))
                        .replace("{{item_rate_" + row + "}}", String.valueOf(item.getRate()))
                        .replace("{{item_per_" + row + "}}", item.getUnit())
                        .replace("{{item_amount_" + row + "}}", String.valueOf(item.getAmount()));
            } else {
                // No item for this row → replace with empty values
                itemTable = itemTable
                        .replace("{{item_serial_number_" + row + "}}", "")
                        .replace("{{item_description_" + row + "}}", "")
                        .replace("{{item_hsn_" + row + "}}", "")
                        .replace("{{item_quantity_" + row + "}}", "")
                        .replace("{{item_rate_" + row + "}}", "")
                        .replace("{{item_per_" + row + "}}", "")
                        .replace("{{item_amount_" + row + "}}", "");
            }
        }

        return itemTable;

    }

    public void deleteInvoice(Long id) throws IOException {

        Long companyId = invoiceRepository.findById(id).map(Invoice::getCompanyId)
                .orElseThrow(() -> new RuntimeException("Invoice not found with ID: " + id));
        String companyName = companyService.getCompanyById(companyId).getName().replace("/", "_");


        String filePathPattern = INVOICE_DIRECTORY + companyName + "/INV-" + id + ".pdf";
        //delete the directory if no other quotation files are present for the same customer, otherwise delete only the file
        try {
            Path dirPath = Paths.get(INVOICE_DIRECTORY + "/" + companyName);
            if (Files.exists(dirPath)) {
                long fileCount = Files.list(dirPath).filter(Files::isRegularFile).count();
                if (fileCount == 1) {
                    Files.deleteIfExists(Path.of(filePathPattern));
                    Files.deleteIfExists(dirPath);
                    log.info("Deleted directory: {}", dirPath.toAbsolutePath());
                } else {
                    Path filePath = Paths.get(filePathPattern);
                    Files.deleteIfExists(filePath);
                    log.info("Deleted file: {}", filePath.toAbsolutePath());
                }
            }
        } catch (IOException e) {
            log.error("Error deleting quotation file: {}", e.getMessage());
            throw e;
        }
        invoiceRepository.deleteById(id);
    }

    public List<InvoiceData> getAll() {
        List<Invoice> invoices = invoiceRepository.findAll();

        return invoiceMapper.mapToInvoiceData(invoices);
    }

}
