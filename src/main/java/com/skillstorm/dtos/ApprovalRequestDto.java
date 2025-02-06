package com.skillstorm.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalRequestDto implements Serializable {

    private UUID formId;
    private String username;
    private String requester;
    private String eventDate;
}
