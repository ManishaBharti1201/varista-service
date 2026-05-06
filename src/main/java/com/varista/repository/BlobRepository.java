package com.varista.repository;

import com.varista.entity.Blob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BlobRepository extends JpaRepository<Blob, Long> {
}
