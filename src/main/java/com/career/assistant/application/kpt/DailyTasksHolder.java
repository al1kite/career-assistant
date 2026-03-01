package com.career.assistant.application.kpt;

import com.career.assistant.application.github.LearningRecommendation.DailyTask;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 아침 브리핑에서 생성된 오늘의 추천 태스크를 보관한다.
 * KPT 분석 시 아침 브리핑 추천과 실제 활동을 비교하기 위해 사용된다.
 */
@Component
public class DailyTasksHolder {

    private volatile List<DailyTask> todayTasks = List.of();

    public void update(List<DailyTask> tasks) {
        this.todayTasks = tasks != null ? List.copyOf(tasks) : List.of();
    }

    public List<DailyTask> get() {
        return todayTasks;
    }
}
