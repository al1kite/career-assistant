package com.career.assistant.domain.experience;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_experiences")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserExperience {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private ExperienceCategory category;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String skills;

    private String period;

    // 포트폴리오 내용을 DB에 미리 입력해두는 용도
    public static UserExperience of(ExperienceCategory category, String title,
                                     String description, String skills, String period) {
        UserExperience exp = new UserExperience();
        exp.category = category;
        exp.title = title;
        exp.description = description;
        exp.skills = skills;
        exp.period = period;
        return exp;
    }
}
