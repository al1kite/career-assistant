package com.career.assistant.application.github;

import com.career.assistant.domain.github.GitHubActivity;
import com.career.assistant.domain.github.GitHubActivityRepository;
import com.career.assistant.infrastructure.github.GitHubClient;
import com.career.assistant.infrastructure.github.GitHubCommit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubAnalyzer {

    private final GitHubClient gitHubClient;
    private final GitHubActivityRepository activityRepository;

    @Value("${github.username}")
    private String username;

    @Value("${github.repos.coding-test}")
    private String codingTestRepo;

    @Value("${github.repos.blog}")
    private String blogRepo;

    @Value("${github.repos.cs-study}")
    private String csStudyRepo;

    private static final Pattern BAEKJOONHUB_LEVEL_PATTERN =
        Pattern.compile("\\[level (\\d+)]", Pattern.CASE_INSENSITIVE);

    private static final Pattern BAEKJOON_TIER_PATTERN =
        Pattern.compile("\\[(Gold|Silver|Bronze)", Pattern.CASE_INSENSITIVE);

    // 커밋 메시지에서 문제 제목 추출: [Gold III] Title #12345 또는 [level 2] Title #12345
    private static final Pattern PROBLEM_TITLE_PATTERN =
        Pattern.compile("\\[(?:Gold|Silver|Bronze|Platinum|Diamond|Ruby|level)\\s*[IVX\\d]+]\\s*(.+?)(?:\\s*#\\d+)?$",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    // 파일 경로에서 문제 제목 추출: 백준/Gold/12345. Title/ 또는 프로그래머스/3/12345. Title/
    private static final Pattern FILE_PROBLEM_PATTERN =
        Pattern.compile("(?:백준|프로그래머스)/[^/]+/(\\d+\\.\\s*.+?)/");

    private final List<String> solvedProblems = new CopyOnWriteArrayList<>();

    @Transactional
    public void syncAll() {
        log.info("Starting GitHub activity sync for user: {}", username);
        syncCodingTest();
        syncBlog();
        syncCsStudy();
        log.info("GitHub activity sync completed");
    }

    public List<GitHubActivity> getActivities() {
        return activityRepository.findAll();
    }

    public List<GitHubActivity> getActivitiesByRepo(String repoName) {
        return activityRepository.findByRepoName(repoName);
    }

    private void syncCodingTest() {
        log.info("Syncing coding-test repo: {}", codingTestRepo);
        LocalDateTime since = LocalDateTime.now().minusMonths(6);
        List<GitHubCommit> commits = gitHubClient.getCommits(username, codingTestRepo, since);

        if (commits.isEmpty()) {
            log.info("No commits found for coding-test repo");
            return;
        }

        // topic -> (lastCommitAt, count)
        Map<String, LocalDateTime> lastCommitMap = new HashMap<>();
        Map<String, Integer> countMap = new HashMap<>();
        Set<String> solved = new LinkedHashSet<>();

        for (GitHubCommit commit : commits) {
            String topic = classifyFromMessage(commit.getMessage());
            String problemTitle = extractProblemTitle(commit.getMessage());

            List<String> files = List.of();
            if (topic == null) {
                files = gitHubClient.getCommitFiles(username, codingTestRepo, commit.sha());
                topic = classifyFromFiles(files);
            }

            if (problemTitle == null && !files.isEmpty()) {
                problemTitle = extractProblemTitleFromFiles(files);
            }

            if (problemTitle != null) {
                solved.add(problemTitle);
            }

            if (topic == null) {
                topic = "기타";
            }

            countMap.merge(topic, 1, Integer::sum);
            LocalDateTime commitDate = commit.getDate();
            if (commitDate != null) {
                lastCommitMap.merge(topic, commitDate,
                    (existing, newDate) -> newDate.isAfter(existing) ? newDate : existing);
            }
        }

        solvedProblems.clear();
        solvedProblems.addAll(solved);
        log.info("[코테] 풀었던 문제 {}건 추출 완료", solved.size());

        saveActivities(codingTestRepo, lastCommitMap, countMap);
    }

    private void syncBlog() {
        log.info("Syncing blog repo: {}", blogRepo);
        LocalDateTime since = LocalDateTime.now().minusMonths(6);
        List<GitHubCommit> commits = gitHubClient.getCommits(username, blogRepo, since);

        if (commits.isEmpty()) {
            log.info("No commits found for blog repo");
            return;
        }

        String topic = "블로그";
        int count = 0;
        LocalDateTime lastCommitAt = null;

        for (GitHubCommit commit : commits) {
            String raw = commit.getMessage();
            if (raw == null || raw.isBlank()) continue;
            String message = raw.toLowerCase();

            // Skip deploy commits
            if (message.startsWith("chore: deploy") || message.startsWith("chore:deploy")) {
                continue;
            }

            // Count docs: or feat: commits as new posts
            if (message.startsWith("docs:") || message.startsWith("feat:")) {
                count++;
                LocalDateTime commitDate = commit.getDate();
                if (commitDate != null && (lastCommitAt == null || commitDate.isAfter(lastCommitAt))) {
                    lastCommitAt = commitDate;
                }
            }
        }

        if (count > 0) {
            saveActivity(blogRepo, topic, lastCommitAt, count);
        }
    }

    private void syncCsStudy() {
        log.info("Syncing cs-study repo: {}", csStudyRepo);
        LocalDateTime since = LocalDateTime.now().minusMonths(6);
        List<GitHubCommit> commits = gitHubClient.getCommits(username, csStudyRepo, since);

        if (commits.isEmpty()) {
            log.info("No commits found for cs-study repo (may not exist yet)");
            return;
        }

        Map<String, LocalDateTime> lastCommitMap = new HashMap<>();
        Map<String, Integer> countMap = new HashMap<>();

        for (GitHubCommit commit : commits) {
            // Use top-level directory from file paths as topic
            List<String> files = gitHubClient.getCommitFiles(username, csStudyRepo, commit.sha());
            Set<String> topics = new HashSet<>();

            for (String file : files) {
                String[] parts = file.split("/");
                if (parts.length > 1) {
                    topics.add(parts[0]);
                }
            }

            if (topics.isEmpty()) {
                // Fallback: extract topic from commit message
                String message = commit.getMessage();
                if (message != null && !message.isBlank()) {
                    String firstLine = message.split("\n")[0].trim();
                    topics.add(firstLine.length() > 50 ? firstLine.substring(0, 50) : firstLine);
                } else {
                    topics.add("기타");
                }
            }

            for (String topic : topics) {
                countMap.merge(topic, 1, Integer::sum);
                LocalDateTime commitDate = commit.getDate();
                if (commitDate != null) {
                    lastCommitMap.merge(topic, commitDate,
                        (existing, newDate) -> newDate.isAfter(existing) ? newDate : existing);
                }
            }
        }

        saveActivities(csStudyRepo, lastCommitMap, countMap);
    }

    public List<String> getSolvedProblems() {
        return Collections.unmodifiableList(solvedProblems);
    }

    String extractProblemTitle(String message) {
        if (message == null || message.isBlank()) return null;
        String firstLine = message.split("\n")[0].trim();
        Matcher m = PROBLEM_TITLE_PATTERN.matcher(firstLine);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }

    private String extractProblemTitleFromFiles(List<String> files) {
        for (String file : files) {
            Matcher m = FILE_PROBLEM_PATTERN.matcher(file);
            if (m.find()) {
                return m.group(1).trim();
            }
        }
        return null;
    }

    String classifyFromMessage(String message) {
        if (message == null || message.isBlank()) return null;

        // BaekjoonHub pattern: [level N] Title
        Matcher levelMatcher = BAEKJOONHUB_LEVEL_PATTERN.matcher(message);
        if (levelMatcher.find()) {
            int level = Integer.parseInt(levelMatcher.group(1));
            return "프로그래머스 Lv." + level;
        }

        // Baekjoon tier pattern: [Gold / [Silver / [Bronze
        Matcher tierMatcher = BAEKJOON_TIER_PATTERN.matcher(message);
        if (tierMatcher.find()) {
            String tier = tierMatcher.group(1);
            return "백준 " + capitalize(tier);
        }

        return null;
    }

    private String classifyFromFiles(List<String> files) {
        for (String file : files) {
            if (file.startsWith("백준/Gold/")) return "백준 Gold";
            if (file.startsWith("백준/Silver/")) return "백준 Silver";
            if (file.startsWith("백준/Bronze/")) return "백준 Bronze";
            if (file.startsWith("프로그래머스/3/")) return "프로그래머스 Lv.3";
            if (file.startsWith("프로그래머스/2/")) return "프로그래머스 Lv.2";
            if (file.startsWith("프로그래머스/1/")) return "프로그래머스 Lv.1";
        }
        return null;
    }

    private void saveActivities(String repoName, Map<String, LocalDateTime> lastCommitMap, Map<String, Integer> countMap) {
        for (String topic : countMap.keySet()) {
            saveActivity(repoName, topic, lastCommitMap.get(topic), countMap.get(topic));
        }
    }

    private void saveActivity(String repoName, String topic, LocalDateTime lastCommitAt, int commitCount) {
        Optional<GitHubActivity> existing = activityRepository.findByRepoNameAndTopic(repoName, topic);

        if (existing.isPresent()) {
            existing.get().update(lastCommitAt, commitCount);
            log.info("Updated activity: {}/{} - {} commits", repoName, topic, commitCount);
        } else {
            GitHubActivity activity = GitHubActivity.of(repoName, topic, lastCommitAt, commitCount);
            activityRepository.save(activity);
            log.info("Created activity: {}/{} - {} commits", repoName, topic, commitCount);
        }
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }
}
