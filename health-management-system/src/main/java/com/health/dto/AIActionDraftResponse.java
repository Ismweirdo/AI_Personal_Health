package com.health.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AIActionDraftResponse {

    private String instruction;
    private String summary;
    private AIActionContextSnapshot contextSnapshot;
    private AIGoalDraft goalDraft;
    private AIReminderDraft reminderDraft;
    private List<String> warnings = new ArrayList<>();
}
