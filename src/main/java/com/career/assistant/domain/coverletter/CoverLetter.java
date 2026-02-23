package com.career.assistant.domain.coverletter;

import com.career.assistant.domain.jobposting.JobPosting;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "cover_letters")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoverLetter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_posting_id")
    private JobPosting jobPosting;

    @Column(name = "ai_model")
    private String aiModel;

    @Column(columnDefinition = "TEXT")
    private String content;

    private int version;

    @Column(name = "question_index")
    private Integer questionIndex;

    @Column(name = "question_text", columnDefinition = "TEXT")
    private String questionText;

    @Column(columnDefinition = "TEXT")
    private String feedback;

    @Column(name = "review_score")
    private Integer reviewScore;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public static CoverLetter of(JobPosting jobPosting, String aiModel, String content) {
        CoverLetter letter = new CoverLetter();
        letter.jobPosting = jobPosting;
        letter.aiModel = aiModel;
        letter.content = content;
        letter.version = 1;
        letter.createdAt = LocalDateTime.now();
        return letter;
    }

    public static CoverLetter of(JobPosting jobPosting, String aiModel, String content,
                                  Integer questionIndex, String questionText) {
        CoverLetter letter = new CoverLetter();
        letter.jobPosting = jobPosting;
        letter.aiModel = aiModel;
        letter.content = content;
        letter.version = 1;
        letter.questionIndex = questionIndex;
        letter.questionText = questionText;
        letter.createdAt = LocalDateTime.now();
        return letter;
    }

    public static CoverLetter ofVersion(JobPosting jobPosting, String aiModel, String content,
                                         int version, Integer questionIndex, String questionText) {
        CoverLetter letter = new CoverLetter();
        letter.jobPosting = jobPosting;
        letter.aiModel = aiModel;
        letter.content = content;
        letter.version = version;
        letter.questionIndex = questionIndex;
        letter.questionText = questionText;
        letter.createdAt = LocalDateTime.now();
        return letter;
    }

    public void addFeedback(String feedback) {
        this.feedback = feedback;
    }

    public void addReview(String feedbackJson, int score) {
        this.feedback = feedbackJson;
        this.reviewScore = score;
    }
}
