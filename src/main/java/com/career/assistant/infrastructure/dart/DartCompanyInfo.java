package com.career.assistant.infrastructure.dart;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DartCompanyInfo {
    @JsonProperty("corp_name")
    private String corpName;

    @JsonProperty("ceo_nm")
    private String ceoName;

    @JsonProperty("induty_code")
    private String industryCode;

    @JsonProperty("induty_nm")
    private String industryName;

    @JsonProperty("est_dt")
    private String establishDate;

    @JsonProperty("hm_url")
    private String homepageUrl;

    @JsonProperty("stock_name")
    private String stockName;

    @JsonProperty("adres")
    private String address;

    @JsonProperty("acc_mt")
    private String fiscalMonth;

    public String toSummary() {
        StringBuilder sb = new StringBuilder();
        if (corpName != null) sb.append("회사명: ").append(corpName).append("\n");
        if (ceoName != null) sb.append("대표자: ").append(ceoName).append("\n");
        if (industryName != null) sb.append("업종: ").append(industryName).append("\n");
        if (establishDate != null && establishDate.length() == 8) {
            sb.append("설립일: ").append(establishDate, 0, 4).append("-")
              .append(establishDate, 4, 6).append("-").append(establishDate, 6, 8).append("\n");
        }
        if (homepageUrl != null && !homepageUrl.isBlank()) sb.append("홈페이지: ").append(homepageUrl).append("\n");
        if (stockName != null && !stockName.isBlank()) sb.append("종목명: ").append(stockName).append("\n");
        return sb.toString();
    }
}
