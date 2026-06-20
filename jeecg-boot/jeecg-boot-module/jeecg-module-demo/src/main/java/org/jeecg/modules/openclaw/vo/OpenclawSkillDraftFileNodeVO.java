package org.jeecg.modules.openclaw.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class OpenclawSkillDraftFileNodeVO {
    private String name;
    private String path;
    private String type;
    private Long size;
    private List<OpenclawSkillDraftFileNodeVO> children = new ArrayList<>();
}
