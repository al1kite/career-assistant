package com.career.assistant.domain.kpt;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "kpt_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KptRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate date;

    @Column(columnDefinition = "TEXT")
    private String userInput;

    @Column(columnDefinition = "TEXT")
    private String keepItems;

    @Column(columnDefinition = "TEXT")
    private String problemItems;

    @Column(columnDefinition = "TEXT")
    private String tryItems;

    @Column(columnDefinition = "TEXT")
    private String aiComment;

    private int completionRate;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public static KptRecord of(LocalDate date, String userInput, String keepItems,
                                String problemItems, String tryItems,
                                String aiComment, int completionRate) {
        KptRecord record = new KptRecord();
        record.date = date;
        record.userInput = userInput;
        record.keepItems = keepItems;
        record.problemItems = problemItems;
        record.tryItems = tryItems;
        record.aiComment = aiComment;
        record.completionRate = completionRate;
        record.createdAt = LocalDateTime.now();
        return record;
    }
}
