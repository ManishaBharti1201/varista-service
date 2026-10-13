package com.varista.service;

import com.varista.constant.ApplicationConstants;
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


    public Quotation createQuotation(QuotationRequest quotationRequest) throws IOException {
        log.info("Initiating quotation creation for customer: {}", quotationRequest.getCustomerName());
        quotationRequest.setId(InvoiceUtils.generate5DigitId());
        String quotationId = "EST-" + InvoiceUtils.generate5DigitId();
        Quotation quotationResponse = new Quotation();
        try {
            String htmlTemplate;
            ClassPathResource resource = new ClassPathResource("quotation_template.html");
            try (InputStream inputStream = resource.getInputStream()) {
                htmlTemplate = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
            log.debug("Loaded HTML template for quotation.");

            htmlTemplate = generateQuoteItemRowsHtml(quotationRequest.getItems(), htmlTemplate);
            log.debug("Generated item rows HTML for quotation.");

            //convert image into base64 and update the html template
            String logoBase64 = InvoiceUtils.convertImageToBase64();
            htmlTemplate = htmlTemplate.replace("{{company_logo}}", "data:image/png;base64," + logoBase64);
            log.debug("Company logo converted to base64 and embedded in HTML.");

            // Populate the HTML template (simplified example)
            String populatedHtml = htmlTemplate
                    .replace("{{quotationNbr}}", quotationId)
                    .replace("{{createDate}}", quotationRequest.getCreationDate().toString())
                    .replace("{{customer_name}}", quotationRequest.getCustomerName().replace("&", "&amp;"))
                    .replace("{{customer_email}}", quotationRequest.getCustomerEmail())
                    .replace("{{tax_amount}}", String.valueOf(quotationRequest.getTaxAmount()))
                    .replace("{{total_amt}}", String.valueOf(quotationRequest.getTotalAmount()));

            log.debug("Populated HTML for quotation number: {}", quotationId);

            // Path: /app/quotations/{companyId}/INV-{id}.pdf
            String directory = ApplicationConstants.QUOTATION_DIRECTORY + quotationRequest.getCustomerName().replace("/", "_");
            // Ensure directory exists
            Path dirPath = Paths.get(directory);
            if (!dirPath.toFile().exists()) {
                log.info("Creating directory for quotations at path: {}", dirPath.toAbsolutePath());
                Files.createDirectories(dirPath); // Use Files.createDirectories for better practice
            }
            String fileName = quotationId + ".pdf";
            String fullPath = directory + "/" + fileName;

            PdfGenerator.generatePdfFromHtml(populatedHtml, fullPath);
            log.info("Quotation PDF generated and saved to: {}", fullPath);

            quotationResponse.setId(quotationRequest.getId());
            quotationResponse.setQuotationNumber(quotationId);
            quotationResponse.setTotalAmount(quotationRequest.getTotalAmount());
            quotationResponse.setCustomerName(quotationRequest.getCustomerName());
            quotationResponse.setCustomerEmail(quotationRequest.getCustomerEmail());

            // save the invoiceinfo to database if needed
            quotationRepository.save(quotationResponse);
            log.info("Quotation created successfully with ID: {} and saved to database", quotationResponse.getId());

            return quotationResponse;

        } catch (Exception e) {
            log.error("Error creating quotation for customer {}: {}", quotationRequest.getCustomerName(), e.getMessage(), e);
            throw e;
        }
    }

    public void deleteQuotation(Long id) throws IOException {
        log.info("Attempting to delete quotation with ID: {}", id);
        Quotation quotation = quotationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Quotation not found with ID: " + id));
        Path quotationPath = getQuotationPath(quotation.getCustomerName(), id);
        Path dirPath = quotationPath.getParent();
        String getDirectoryName = dirPath.getFileName().toString();
        log.debug("Found quotation ID: {} belonging to customer: {}", id, getDirectoryName);

        String filePathPattern = ApplicationConstants.QUOTATION_DIRECTORY + getDirectoryName + "/EST-" + id + ".pdf";
        try {
            Files.deleteIfExists(quotationPath);
            Path dirPath = Paths.get(ApplicationConstants.QUOTATION_DIRECTORY + getDirectoryName);
            if (Files.exists(dirPath)) {
                try (var files = Files.list(dirPath)) {
                    if (files.findAny().isEmpty()) {
                        Files.deleteIfExists(dirPath);
                    }
                }
            }
            log.info("Deleted quotation file: {}", quotationPath.toAbsolutePath());
        } catch (IOException e) {
            log.error("Error deleting quotation file for ID {}: {}", id, e.getMessage(), e);
            throw e;
        }
        quotationRepository.deleteById(id);
        log.info("Quotation ID: {} deleted from database.", id);
    }

    public List<Quotation> getAll() {
        log.info("Fetching all quotations from the database.");
        List<Quotation> quotations = quotationRepository.findAll();
        log.info("Retrieved {} quotations from the database.", quotations.size());
        return quotations;
    }

    private String generateQuoteItemRowsHtml(List<InvoiceData.Item> items, String htmlTemplate) {
        log.debug("Generating item rows HTML for {} quotation items.", items.size());
        String itemTable = htmlTemplate;
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
        log.debug("Finished generating item rows HTML.");
        return itemTable;

    }

    private Path getQuotationPath(String customerName, Long id) {
        String safeCustomerName = customerName.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (safeCustomerName.equals(".") || safeCustomerName.equals("..")) {
            safeCustomerName = "_";
        }
        return Paths.get(QUOTATION_DIRECTORY, safeCustomerName, "EST-" + id + ".pdf");
    }

    public Resource downloadQuotation(long id) {
        log.info("Attempting to download quotation for ID: {}", id);
        try {
            Quotation quotation = quotationRepository.findById(id)
                    .orElseThrow(() -> {
                        log.error("Quotation not found with ID: {} for download.", id);
                        return new RuntimeException("Quotation not found with ID: " + id);
                    });
            String getDirectoryName = quotation.getCustomerName().replaceAll("[\\\\/:*?\"<>|]", "_");
            log.debug("Found quotation ID: {} belonging to customer: {}", id, getDirectoryName);

            Path filePath = getQuotationPath(quotation.getCustomerName(), id);
            if (!Files.exists(filePath)) {
                log.error("Quotation file not found on disk for ID: {}. Path: {}", id, filePath);
                throw new IOException("Quotation file not found on disk for ID: " + id);
            }
            ByteArrayResource resource = new ByteArrayResource(Files.readAllBytes(filePath));
            log.info("Streaming PDF for quotation ID: {} (Size: {} bytes)", id, resource.contentLength());
            return resource;
        } catch (IOException e) {
            log.error("Error downloading quotation file for ID {}: {}", id, e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}
