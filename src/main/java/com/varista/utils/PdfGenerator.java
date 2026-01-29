package com.varista.utils;

import com.itextpdf.html2pdf.HtmlConverter;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;

@Component
public class PdfGenerator {


    public static void generateInvoicePdfToFile(String invoiceHtml, String outputFilePath) {
        try (OutputStream outputStream = new FileOutputStream(outputFilePath)) {
            // Convert HTML to PDF and write to file
            HtmlConverter.convertToPdf(invoiceHtml, outputStream);

        } catch (Exception e) {
            throw new RuntimeException("Error generating PDF file from HTML", e);
        }
    }
}