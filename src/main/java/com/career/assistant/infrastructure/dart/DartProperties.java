package com.career.assistant.infrastructure.dart;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "dart")
public class DartProperties {
    private String apiKey;
    private String corpCodePath = "./data/dart-corp-codes.xml";
}
