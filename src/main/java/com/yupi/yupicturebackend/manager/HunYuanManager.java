package com.yupi.yupicturebackend.manager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tencentcloudapi.hunyuan.v20230901.HunyuanClient;
import com.tencentcloudapi.hunyuan.v20230901.models.ChatCompletionsRequest;
import com.tencentcloudapi.hunyuan.v20230901.models.ChatCompletionsResponse;
import com.tencentcloudapi.hunyuan.v20230901.models.Message;
import com.yupi.yupicturebackend.config.HunYuanConfig;
import com.yupi.yupicturebackend.model.vo.AIPictureInfoVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.*;

@Slf4j
@Component
public class HunYuanManager {
    @Resource
    private HunYuanConfig hunYuanConfig;

    @Resource
    private HunyuanClient hunyuanClient;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 支持两种入参：
     * 1. MultipartFile 本地上传
     * 2. cosUrl        已存 COS 的公网地址
     */
    public AIPictureInfoVO analyzeImage(MultipartFile file) throws IOException {
        String base64 = Base64.getEncoder().encodeToString(file.getBytes());
        return callHunYuan(base64, null);
    }

    public AIPictureInfoVO analyzeImage(String cosUrl) {
        return callHunYuan(null, cosUrl);
    }

    private AIPictureInfoVO callHunYuan(String base64, String cosUrl) {
        try {
            // 1. 构造提示词
            String promptText = "请用中文返回三个字段：1）简介（10~20字）；2）标签（3~5个，用逗号分隔）；3）分类（每个图片只有一个分类）。";

            // 2. 图片地址
            String imageUrl = base64 != null
                    ? "data:image/jpeg;base64," + base64
                    : cosUrl;

            // 3. 混元 Vision 支持的 Markdown 格式
            String mdContent = promptText + "\n![](" + imageUrl + ")";

            // 4. 塞进 Message
            Message msg = new Message();
            msg.setRole("user");
            msg.setContent(mdContent);   // ← 关键改动

            // 3. 构造请求
            ChatCompletionsRequest req = new ChatCompletionsRequest();
            req.setModel(hunYuanConfig.getModel());
            req.setMessages(new Message[]{msg});
            req.setStream(false);
            req.setTemperature(0.3f);   // 越低越稳定

            // 4. 调用
            // 4. 调用
            ChatCompletionsResponse resp = hunyuanClient.ChatCompletions(req); // 不要 try-with-resources
            String text = Optional.ofNullable(resp.getChoices())
                    .filter(arr -> arr.length > 0)
                    .map(arr -> arr[0].getMessage().getContent())
                    .orElseThrow(() -> new RuntimeException("混元返回内容为空"));

            // 5. 简单正则解析（也可让模型直接 JSON）
            AIPictureInfoVO meta = new AIPictureInfoVO();
            String[] lines = text.split("\\n");
            for (String line : lines) {
                if (line.startsWith("1）简介")) {
                    meta.setIntroduction(line.replaceFirst("1）简介[:：]", "").trim());
                } else if (line.startsWith("2）标签")) {
                    meta.setTags(line.replaceFirst("2）标签[:：]", ""));
                } else if (line.startsWith("3）分类")) {
                    meta.setCategory(line.replaceFirst("3）分类[:：]", "").trim());
                }
            }
            return meta;
        } catch (Exception e) {
            log.error("混元调用失败", e);
            throw new RuntimeException("混元调用失败", e);
        }
    }
}
