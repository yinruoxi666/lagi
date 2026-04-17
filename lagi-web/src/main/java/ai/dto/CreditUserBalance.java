package ai.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreditUserBalance {
    private String userId;
    private BigDecimal balance;
}
