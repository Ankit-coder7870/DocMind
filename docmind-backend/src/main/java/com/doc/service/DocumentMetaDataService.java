package com.doc.service;

import com.doc.dto.DocumentResponseDto;
import com.doc.entity.DocumentMetaData;
import com.doc.entity.DocumentStatus;
import com.doc.repository.DocumentMetaDataRepo;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DocumentMetaDataService {

    private static final Logger log = LoggerFactory.getLogger(DocumentMetaDataService.class);
    private final DocumentMetaDataRepo documentMetaDataRepo;
    private final DocumentParseService parseService;
    private final DocumentIngestionService ingestService;

    //method to upload and parse document
    public DocumentResponseDto UploadAndProcess(MultipartFile file) {

        String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "document";
        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";

        //document metadata create
        DocumentMetaData documentMetaData = DocumentMetaData.builder().filename(fileName).
                contentType(contentType)
                .fileSize(file.getSize())
                .status(DocumentStatus.UPLOADING)
                .createdAt(LocalDateTime.now())
                .build();

        //save the document metadata
          documentMetaData = documentMetaDataRepo.save(documentMetaData);

         //parse the file
        List<Document> parseDocs = parseService.parse(file);

        //ingest service
        int chunkCreated = ingestService.ingest(documentMetaData,parseDocs);


        return DocumentResponseDto.builder()
                .id(documentMetaData.getId())
                .fileName(documentMetaData.getFilename())
                .fileSize(documentMetaData.getFileSize())
                .chunksCreated(chunkCreated)
                .status(documentMetaData.getStatus())
                .message("Document successfully processed and indexed")
                .build();
    }
}
