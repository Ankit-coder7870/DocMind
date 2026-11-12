package com.doc.service;

import com.doc.exception.DocumentProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
public class DocumentParseService {

    private final static Logger log = LoggerFactory.getLogger(DocumentParseService.class);

    public List<Document> parse(MultipartFile file) {

        String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "Document";
        String contentType = file.getContentType() != null ? file.getContentType() : "";

        log.info("Parsing Document : {}, Size : {} bytes, Content type : {}", fileName, file.getSize(), contentType);

        try {

            Resource resource = new ByteArrayResource(file.getBytes());

            if (fileName.toLowerCase().endsWith(".pdf") || contentType.contains("pdf")) {
                return parsePdf(resource);
            } else {
                return parseGeneric(resource);
            }

        } catch (IOException e) {
            log.error("failed to read file bytes {}", fileName, e);
            throw new DocumentProcessingException("could not read uploaded file : " + fileName, e);
        } catch (Exception e) {
            log.error("Error during document parsing: {}", fileName, e);
            throw new DocumentProcessingException("failed to parse document content : " + fileName, e);
        }


    }

    private List<Document> parseGeneric(Resource resource) {

        TikaDocumentReader tikaDocumentReader = new TikaDocumentReader(resource);
        return tikaDocumentReader.read();

    }

    private List<Document> parsePdf(Resource resource) {
        PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder().build();

        PagePdfDocumentReader documentReader = new PagePdfDocumentReader(resource, config);
        return documentReader.read();
    }
}
