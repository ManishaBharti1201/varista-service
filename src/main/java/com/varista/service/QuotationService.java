package com.varista.service;

import com.varista.entity.Company;
import com.varista.entity.Quotation;
import com.varista.mapper.QuotationMapper;
import com.varista.model.InvoiceData;
import com.varista.repository.QuotationRepository;
import com.varista.utils.IndianCurrencyConverter;
import com.varista.utils.PdfGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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
    public  String INVOICE_DIRECTORY ;


    public Quotation createQuotation(Quotation quotationRequest) throws IOException {
        // Convert QuotationRequest to Quotation (mocked for this example)
        Quotation quotationResponse = new Quotation();
        try {
            String htmlTemplate;
            Quotation quotationData = new Quotation();
            Company buyerInfo = companyService.getCompanyById((long) quotationRequest.getId());
            quotationData = quotationMapper.mapQuotationRequestToQuotation(quotationRequest, buyerInfo);
            ClassPathResource resource = new ClassPathResource("new_quotation_template.html");
            try (InputStream inputStream = resource.getInputStream()) {
                htmlTemplate = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }

            System.out.println("HTML template populated. Ready for PDF conversion.");

            // Converthtml to pdf and save to cloud storage or return the quotation id

            // Path: /app/quotations/{companyId}/INV-{id}.pdf
            String directory = INVOICE_DIRECTORY + buyerInfo.getId();
            // Ensure directory exists
            Path dirPath = Paths.get(directory);
            if (!dirPath.toFile().exists()) {
                log.info("Creating directory for quotations at path: {}", dirPath.toAbsolutePath());
                dirPath.toFile().mkdirs();
            }
            return quotationResponse;
        } catch (Exception e) {
            log.error("Error creating quotation: {}", e.getMessage());

            throw e;
        }
    }

    private String generateItemRowsHtml(List<Quotation> items, String itemTable) {
        for (int i = 0; i < 15; i++) {
            int row = i + 1; // because placeholders start from 1


        }

        return itemTable;

    }

    public void deleteQuotation(Long id) {
        quotationRepository.deleteById(id);
    }

    public byte[] generateQuotationPdf(Long id) {
        var pdf = quotationRepository.getReferenceById(id);

        return null;
    }

    public List<Quotation> getAll() {
        List<Quotation> quotations = quotationRepository.findAll();

        return null;
    }

}
