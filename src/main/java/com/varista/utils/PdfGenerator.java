package com.varista.utils;

import com.itextpdf.html2pdf.HtmlConverter;
import org.springframework.stereotype.Component;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

@Component
public class PdfGenerator {


    public static void generateInvoicePdfToFile(String invoiceHtml, String outputFilePath) {
        // 1. Ensure the directory exists inside the container path (/app/invoices)
        File file = new File(outputFilePath);
        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            // 2. Convert HTML to PDF into the ByteArrayOutputStream
            HtmlConverter.convertToPdf(invoiceHtml, baos);
            byte[] pdfBytes = baos.toByteArray();

            // 3. Write those bytes to the actual file path (Docker Volume)
            Files.write(Paths.get(outputFilePath), pdfBytes);

        } catch (Exception e) {
            throw new RuntimeException("Error generating PDF file from HTML", e);
        }
    }

    public static void generatePdfFromHtml(String htmlContent, String outputPath) {
        try (OutputStream os = new FileOutputStream(outputPath)) {
            ITextRenderer renderer = new ITextRenderer();
            renderer.setDocumentFromString(htmlContent);
            renderer.layout();
            renderer.createPDF(os);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PDF", e);
        }
    }
}