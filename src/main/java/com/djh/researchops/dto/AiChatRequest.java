package com.djh.researchops.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AiChatRequest {

    @NotBlank(message = "请输入消息内容")
    @Size(max = 4000, message = "消息内容不能超过4000个字符")
    private String message;
}
