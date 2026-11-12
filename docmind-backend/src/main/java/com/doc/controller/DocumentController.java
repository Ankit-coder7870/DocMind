package com.doc.controller;

import com.doc.dto.ApiResponse;
import com.doc.dto.DocumentResponseDto;
import com.doc.repository.DocumentMetaDataRepo;
import com.doc.service.DocumentMetaDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

@RequestMapping("/api/v1/documents")
@RestController
@Tag(
        name = "Document Management",
        description = "Endpoints for uploading, listing and managing documents and their vectors embeddings."
)
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentMetaDataService documentService;
    private final DocumentMetaDataRepo documentRepo;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload and index a document(PDF, DOCX, TEXT, MD, CSV)",
            description = "This api is used to upload and index documents files."

    )
    public ResponseEntity<ApiResponse<DocumentResponseDto>> uploadDocument(
            @RequestParam("file")MultipartFile file
            ){

       DocumentResponseDto documentResponseDto = documentService.UploadAndProcess(file);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<DocumentResponseDto>builder()
                        .data(documentResponseDto)
                        .timestamp(LocalDateTime.now())
                        .success(true)
                        .message("Documents uploaded and indexed successfully")
                        .build()
        );
    }
}
