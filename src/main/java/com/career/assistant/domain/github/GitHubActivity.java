package com.career.assistant.domain.github;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "github_activities",
    uniqueConstraints = @UniqueConstraint(name = "uk_repo_topic", columnNames = {"repoName", "topic"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GitHubActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String repoName;

    @Column(nullable = false, length = 100)
    private String topic;

    private LocalDateTime lastCommitAt;

    @Column(columnDefinition = "INT DEFAULT 0")
    private int commitCount;

    @Column(columnDefinition = "INT DEFAULT 0")
    private int gapDays;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ActivityStatus status;

    private LocalDateTime updatedAt;

    public static GitHubActivity of(String repoName, String topic, LocalDateTime lastCommitAt, int commitCount) {
        GitHubActivity activity = new GitHubActivity();
        activity.repoName = repoName;
        activity.topic = topic;
        activity.lastCommitAt = lastCommitAt;
        activity.commitCount = commitCount;
        activity.gapDays = lastCommitAt != null
            ? (int) ChronoUnit.DAYS.between(lastCommitAt.toLocalDate(), LocalDateTime.now().toLocalDate())
            : Integer.MAX_VALUE;
        activity.status = ActivityStatus.from(activity.gapDays);
        activity.updatedAt = LocalDateTime.now();
        return activity;
    }

    public void update(LocalDateTime lastCommitAt, int commitCount) {
        this.lastCommitAt = lastCommitAt;
        this.commitCount = commitCount;
        this.gapDays = lastCommitAt != null
            ? (int) ChronoUnit.DAYS.between(lastCommitAt.toLocalDate(), LocalDateTime.now().toLocalDate())
            : Integer.MAX_VALUE;
        this.status = ActivityStatus.from(this.gapDays);
        this.updatedAt = LocalDateTime.now();
    }
}
