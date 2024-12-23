package ai.dto;

import lombok.*;

@Data
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TextToSqlRequest {
    private String text;
    private String sql;
    private String demand;
    private String tableName;
}
