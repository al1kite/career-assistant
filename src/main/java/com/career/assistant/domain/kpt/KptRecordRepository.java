package com.career.assistant.domain.kpt;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface KptRecordRepository extends JpaRepository<KptRecord, Long> {

    List<KptRecord> findByDateBetweenOrderByDateDesc(LocalDate start, LocalDate end);

    List<KptRecord> findTop7ByOrderByDateDesc();
}
