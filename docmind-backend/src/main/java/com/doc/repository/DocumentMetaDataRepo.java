package com.doc.repository;


import com.doc.entity.DocumentMetaData;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DocumentMetaDataRepo extends JpaRepository<DocumentMetaData, UUID> {
}
