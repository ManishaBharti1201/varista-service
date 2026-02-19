package com.varista.service;

import com.varista.entity.Company;
import com.varista.entity.Invoice;
import com.varista.entity.Quotation;
import com.varista.mapper.QuotationMapper;
import com.varista.model.InvoiceData;
import com.varista.model.QuotationRequest;
import com.varista.repository.QuotationRepository;
import com.varista.utils.IndianCurrencyConverter;
import com.varista.utils.InvoiceUtils;
import com.varista.utils.PdfGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

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
public class QuotationService {

    @Autowired
    QuotationMapper quotationMapper;

    @Autowired
    CompanyService companyService;

    @Autowired
    QuotationRepository quotationRepository;

    @Value("${quotation.directory}")
    public String QUOTATION_DIRECTORY;


    public Quotation createQuotation(QuotationRequest quotationRequest) throws IOException {

        quotationRequest.setId(InvoiceUtils.generate5DigitId());
        String quotationId = "EST-" + InvoiceUtils.generate5DigitId();
        Quotation quotationResponse = new Quotation();
        try {
            log.info("Creating quotation for customer: {}", quotationRequest.getCustomerName());
            String htmlTemplate;
            ClassPathResource resource = new ClassPathResource("quotation_template.html");
            try (InputStream inputStream = resource.getInputStream()) {
                htmlTemplate = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }

            htmlTemplate = generateQuoteItemRowsHtml(quotationRequest.getItems(), htmlTemplate);

            //convert image into base64 and update the html template
            String logoBase64 = InvoiceUtils.convertImageToBase64();
            htmlTemplate = htmlTemplate.replace("{{company_logo}}", "data:image/png;base64," + logoBase64);

            // Populate the HTML template (simplified example)
            String populatedHtml = htmlTemplate
                    .replace("{{quotationNbr}}", quotationId)
                    .replace("{{createDate}}", quotationRequest.getCreationDate().toString())
                    .replace("{{customer_name}}", quotationRequest.getCustomerName().replace("&", "&amp;"))
                    .replace("{{customer_email}}", quotationRequest.getCustomerEmail())
                    .replace("{{tax_amount}}", String.valueOf(quotationRequest.getTaxAmount()))
                    .replace("{{total_amt}}", String.valueOf(quotationRequest.getTotalAmount()));

            log.info("Populated HTML for quotation:\n{}", populatedHtml);

            // Path: /app/quotations/{companyId}/INV-{id}.pdf
            String directory = QUOTATION_DIRECTORY + quotationRequest.getCustomerName().replace("/", "_");
            // Ensure directory exists
            Path dirPath = Paths.get(directory);
            if (!dirPath.toFile().exists()) {
                log.info("Creating directory for quotations at path: {}", dirPath.toAbsolutePath());
                dirPath.toFile().mkdirs();
            }
            String fileName = quotationId + ".pdf";
            String fullPath = directory + "/" + fileName;

            PdfGenerator.generatePdfFromHtml(populatedHtml, fullPath);

            quotationResponse.setId(quotationRequest.getId());
            quotationResponse.setQuotationNumber(quotationId);
            quotationResponse.setCreationDate(quotationRequest.getCreationDate());
            quotationResponse.setTotalAmount(quotationRequest.getTotalAmount());
            quotationResponse.setCustomerName(quotationRequest.getCustomerName());
            quotationResponse.setCustomerEmail(quotationRequest.getCustomerEmail());

            // save the invoiceinfo to database if needed
            quotationRepository.save(quotationResponse);
            log.info("Quotation created successfully with ID: {} and saved to database", quotationResponse.getId());

            return quotationResponse;

        } catch (Exception e) {
            log.error("Error creating quotation: {}", e.getMessage());
            throw e;
        }
    }

    public void deleteQuotation(Long id) throws IOException {

        String getDirectoryName = quotationRepository.findById(id)
                .map(Quotation::getCustomerName)
                .orElse("unknown_customer")
                .replace("/", "_");
        String filePathPattern = QUOTATION_DIRECTORY + "/" + getDirectoryName + "/EST-" + id + ".pdf";
        //delete the directory if no other quotation files are present for the same customer, otherwise delete only the file
        try {
            log.info("Attempting to delete quotation file for ID: {} at path: {}", id, filePathPattern);
            Path dirPath = Paths.get(QUOTATION_DIRECTORY + "/" + getDirectoryName);
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


        quotationRepository.deleteById(id);
    }

    public List<Quotation> getAll() {
        List<Quotation> quotations = quotationRepository.findAll();

        return quotations;
    }

    private String generateQuoteItemRowsHtml(List<InvoiceData.Item> items, String itemTable) {
        for (int i = 0; i < 15; i++) {
            int row = i + 1; // because placeholders start from 1

            if (i < items.size()) {
                InvoiceData.Item item = items.get(i);

                itemTable = itemTable
                        .replace("{{item_serial_number_" + row + "}}", String.valueOf(item.getSrNo()))
                        .replace("{{item_description_" + row + "}}", item.getDescription())
                        .replace("{{item_hsn_" + row + "}}", item.getHsnSac())
                        .replace("{{item_qty_" + row + "}}", String.valueOf(item.getQuantity()))
                        .replace("{{item_rate_" + row + "}}", String.valueOf(item.getRate()))
                        .replace("{{item_unit_" + row + "}}", item.getUnit())
                        .replace("{{item_total_" + row + "}}", String.valueOf(item.getAmount()));
            } else {
                // No item for this row → replace with empty values
                itemTable = itemTable
                        .replace("{{item_serial_number_" + row + "}}", "")
                        .replace("{{item_description_" + row + "}}", "")
                        .replace("{{item_hsn_" + row + "}}", "")
                        .replace("{{item_qty_" + row + "}}", "")
                        .replace("{{item_rate_" + row + "}}", "")
                        .replace("{{item_unit_" + row + "}}", "")
                        .replace("{{item_total_" + row + "}}", "");
            }
        }

        return itemTable;

    }

    public Resource downloadQuotation(long id) {
        try {
            log.info("Attempting to download quotation for ID: {}", id);
            String getDirectoryName = quotationRepository.findById(id)
                    .map(Quotation::getCustomerName)
                    .orElse("unknown_customer")
                    .replace("/", "_");
            String filePathPattern = QUOTATION_DIRECTORY + getDirectoryName + "/EST-" + id + ".pdf";

            Path filePath = Paths.get(filePathPattern);
            if (!Files.exists(filePath)) {
                throw new IOException("Quotation file not found on disk for ID: " + id);
            }
            ByteArrayResource resource = new ByteArrayResource(Files.readAllBytes(filePath));
            log.info("Streaming PDF for invoice ID: {} (Size: {} bytes)", id, resource.contentLength());
            return resource;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
