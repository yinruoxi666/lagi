package ai.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
public class ChargeDetail {
    private String seq;
    private String userId;
    private BigDecimal amount;
    private Date time;
    private Integer status;
}
