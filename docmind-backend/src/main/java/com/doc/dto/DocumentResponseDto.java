package com.doc.dto;

import com.doc.entity.DocumentStatus;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DocumentResponseDto {

    private UUID id;
    private String fileName;
    private long fileSize;
    private String message;
    private Integer chunksCreated;
    private DocumentStatus status;
}
