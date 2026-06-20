package org.jeecg.modules.openclaw.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class OpenclawSkillDraftBatchTestDTO {
    private List<TestCase> cases = new ArrayList<>();

    @Data
    public static class TestCase {
        private String name;
        private String prompt;
        private String expectedOutput;
    }
}
