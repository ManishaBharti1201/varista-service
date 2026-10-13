package com.varista.service;

import com.varista.constant.ApplicationConstants;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import com.varista.utils.IndianCurrencyConverter;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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



    public InvoiceResponse createInvoice(InvoiceRequest invoiceRequest) throws IOException {
        log.info("Initiating invoice creation for buyer ID: {}", invoiceRequest.getBuyerId());
        InvoiceResponse invoiceResponse = new InvoiceResponse();
        try {
            String htmlTemplate;
            InvoiceData invoiceData = new InvoiceData();

            Company buyerInfo = companyService.getCompanyById(invoiceRequest.getBuyerId());
            if (buyerInfo == null) {
                log.error("Buyer with ID {} not found for invoice creation.", invoiceRequest.getBuyerId());
                throw new RuntimeException("Buyer not found with ID: " + invoiceRequest.getBuyerId());
            }

            invoiceData = invoiceMapper.mapInvoiceRequestToInvoiceData(invoiceRequest, buyerInfo);
            log.debug("Mapped InvoiceRequest to InvoiceData for invoice number: {}", invoiceData.getInvoiceNo());

            ClassPathResource resource = new ClassPathResource("new_invoice_template.html");
            try (InputStream inputStream = resource.getInputStream()) {
                htmlTemplate = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
            log.debug("Loaded HTML template for invoice.");

            htmlTemplate = generateItemRowsHtml(invoiceData.getItems(), htmlTemplate);
            log.debug("Generated item rows HTML for invoice.");

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
                    // InvoiceData has no round-off value, so render neutral zero rather than repeating tax.
                    .replace("{{igst_roundoff}}", "0")
                    .replace("{{total_amount}}", String.valueOf(invoiceData.getTotalAmount()))
                    .replace("{{bank_name}}", invoiceData.getBankDetails().getBankName())
                    .replace("{{account_number}}", invoiceData.getBankDetails().getAccNo())
                    .replace("{{ifsc_code}}", invoiceData.getBankDetails().getIfsc())
                    .replace("{{company_name}}", "Varista Industrial Solutions");

            log.debug("Populated HTML template for invoice number: {}", invoiceData.getInvoiceNo());

            Path invoicePath = getInvoicePath(invoiceData.getBuyer().getName(), invoiceData.getInvoiceNo());
            Path dirPath = invoicePath.getParent();
            log.info("Creating directory for invoice at path: {}", dirPath.toAbsolutePath());
            Files.createDirectories(dirPath);
            PdfGenerator.generatePdfFromHtml(populatedHtml, invoicePath.toString());
            log.info("Invoice PDF generated and saved to: {}", invoicePath);

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
            log.info("Invoice created successfully with ID: {} and saved to database", invoice.getId());

            return invoiceResponse;
        } catch (Exception e) {
            log.error("Error creating invoice for buyer ID {}: {}", invoiceRequest.getBuyerId(), e.getMessage(), e);
            throw e;
        }
    }

    private String generateItemRowsHtml(List<InvoiceData.Item> items, String htmlTemplate) {
        log.debug("Generating item rows HTML for {} items.", items.size());
        String itemTable = htmlTemplate; // Use the passed htmlTemplate as base
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
        log.debug("Finished generating item rows HTML.");
        return itemTable;

    }

    public void deleteInvoice(Long id) throws IOException {
        log.info("Attempting to delete invoice with ID: {}", id);
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Invoice not found with ID: {} for deletion.", id);
                    return new RuntimeException("Invoice not found with ID: " + id);
                });
        String companyName = companyService.getCompanyById(invoice.getCompanyId()).getName();
        log.debug("Found invoice ID: {} belonging to company: {}", id, companyName);

        Path filePath = getInvoicePath(companyName, invoice.getInvoiceNumber());
        Path dirPath = filePath.getParent();
        String filePathPattern = ApplicationConstants.INVOICE_DIRECTORY + companyName + "/INV-" + id + ".pdf";
        try {
            if (Files.exists(dirPath)) {
                Files.deleteIfExists(filePath);
                try (var files = Files.list(dirPath)) {
                    if (files.findAny().isEmpty()) {
                        Files.deleteIfExists(dirPath);
                    }
                }
                log.info("Deleted invoice file: {}", filePath.toAbsolutePath());
            } else {
                log.warn("Invoice directory not found for company: {}", companyName);
            }
        } catch (IOException e) {
            log.error("Error deleting invoice file for ID {}: {}", id, e.getMessage(), e);
            throw e;
        }
        invoiceRepository.deleteById(id);
        log.info("Invoice ID: {} deleted from database.", id);
    }

    public List<InvoiceData> getAll() {
        log.info("Fetching all invoices from the database.");
        List<Invoice> invoices = invoiceRepository.findAll();
        log.info("Retrieved {} invoices from the database.", invoices.size());
        return invoiceMapper.mapToInvoiceData(invoices);
    }

    public Resource downloadInvoice(String id) throws IOException {
        log.info("Attempting to download invoice with ID: {}", id);
        try {
            Invoice invoice = invoiceRepository.findById(Long.valueOf(id))
                    .orElseThrow(() -> {
                        log.error("Invoice not found with ID: {} for download.", id);
                        return new RuntimeException("Invoice not found with ID: " + id);
                    });

            String companyName = companyService.getCompanyById(invoice.getCompanyId()).getName();
            log.debug("Found invoice ID: {} belonging to company: {}", id, companyName);

            Path filePath = getInvoicePath(companyName, invoice.getInvoiceNumber());
            if (!Files.exists(filePath)) {
                log.error("Invoice file not found on disk for ID: {}. Path: {}", id, filePath);
                throw new IOException("Invoice file not found on disk for ID: " + id);
            }
            ByteArrayResource resource = new ByteArrayResource(Files.readAllBytes(filePath));
            log.info("Streaming PDF for invoice ID: {} (Size: {} bytes)", id, resource.contentLength());
            return resource;
        } catch (IOException e) {
            log.error("Error downloading invoice file for ID {}: {}", id, e.getMessage(), e);
            throw e;
        }
    }

    private Path getInvoicePath(String companyName, String invoiceNumber) {
        String safeCompanyName = companyName.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (safeCompanyName.equals(".") || safeCompanyName.equals("..")) {
            safeCompanyName = "_";
        }
        String safeInvoiceNumber = invoiceNumber.replaceAll("[\\\\/:*?\"<>|]", "_");
        return Paths.get(INVOICE_DIRECTORY, safeCompanyName, safeInvoiceNumber + ".pdf");
    }

    public InvoiceResponse markAsPaid(Long id) {
        log.info("Attempting to mark invoice with ID: {} as Paid.", id);
        InvoiceResponse invoiceResponse = new InvoiceResponse();
        Invoice existingInvoice = invoiceRepository.findById(id)
                .map(invoice -> {
                    invoice.setStatus("Paid");
                    Invoice updatedInvoice = invoiceRepository.save(invoice);
                    log.info("Invoice with ID: {} marked as Paid and saved.", id);
                    return updatedInvoice;
                })
                .orElseThrow(() -> {
                    log.error("Invoice not found with ID: {} for marking as paid.", id);
                    return new RuntimeException("Invoice not found with ID: " + id);
                });

        invoiceResponse.setInvoiceId(existingInvoice.getId());
        invoiceResponse.setInvoiceNumber(existingInvoice.getInvoiceNumber());
        invoiceResponse.setCreatedAt(existingInvoice.getCreatedAt().toString());
        invoiceResponse.setAmount(existingInvoice.getTotalAmount().toString());
        invoiceResponse.setStatus("Paid");
        invoiceResponse.setMessage("Invoice marked as Paid successfully");
        log.info("Invoice response generated for marked as paid invoice ID: {}", id);
        return invoiceResponse;
    }

    public InvoiceResponse replaceInvoice(Long id, MultipartFile file) {
        log.info("Attempting to replace invoice file for ID: {} with new file: {}", id, file.getOriginalFilename());
        try {
            // 1. Fetch Invoice metadata
            Invoice invoice = invoiceRepository.findById(id)
                    .orElseThrow(() -> {
                        log.error("Invoice not found with ID: {} for file replacement.", id);
                        return new RuntimeException("Invoice not found with ID: " + id);
                    });

            String companyName = companyService.getCompanyById(invoice.getCompanyId())
                    .getName();
            log.debug("Found invoice ID: {} belonging to company: {}", id, companyName);

            // 2. Define Pathing
            Path filePath = getInvoicePath(companyName, invoice.getInvoiceNumber());
            Path directoryPath = filePath.getParent();
            log.debug("Target file path for replacement: {}", filePath);

            // 3. Ensure directory exists
            if (!Files.exists(directoryPath)) {
                Files.createDirectories(directoryPath);
                log.info("Created directory: {}", directoryPath);
            }

            // 4. Atomic-style Replacement:
            try (var inputStream = file.getInputStream()) {
                Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);
            }
            log.info("Invoice file for ID: {} replaced successfully at: {}", id, filePath);

            // 5. Update metadata if necessary and return response
            // Assuming InvoiceResponse is just a success indicator here,
            // you might want to populate it with actual invoice details if needed.
            InvoiceResponse response = new InvoiceResponse();
            response.setInvoiceId(id);
            response.setMessage("Invoice file replaced successfully.");
            response.setStatus("SUCCESS");
            return response;

        } catch (IOException e) {
            log.error("File system error during invoice replacement for ID {}: {}", id, e.getMessage(), e);
            throw new RuntimeException(" File system error: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to replace invoice for ID {}: {}", id, e.getMessage(), e);
            throw new RuntimeException("Failed to replace invoice: " + e.getMessage(), e);
        }
    }
}
