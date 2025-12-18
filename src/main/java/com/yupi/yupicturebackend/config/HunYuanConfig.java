package com.yupi.yupicturebackend.config;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.region.Region;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.hunyuan.v20230901.HunyuanClient;
import com.tencentcloudapi.hunyuan.v20230901.models.ChatCompletionsRequest;
import com.tencentcloudapi.hunyuan.v20230901.models.ChatCompletionsResponse;
import com.tencentcloudapi.hunyuan.v20230901.models.Message;
import com.yupi.yupicturebackend.model.vo.AIPictureInfoVO;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;

@Configuration
@ConfigurationProperties(prefix = "tecent")
@Data
@Slf4j
public class HunYuanConfig {

    /**
     * secretId
     */
    private String secretId;

    /**
     * 秘钥
     */
    private String secretKey;

    /**
     * 终端
     */
    private String endpoint;

    /**
     * 地区
     */
    private String region;

    /**
     * 模型
     */
    private String model;

    /**
     * 注入混元大模型客户端
     * @return
     */
    @Bean
    public HunyuanClient hunyuanClient() {
        // 1. 构造腾讯云签名客户端
        Credential cred = new Credential(secretId, secretKey);
        HttpProfile httpProfile = new HttpProfile();
        httpProfile.setEndpoint(endpoint);
        ClientProfile clientProfile = new ClientProfile();
        clientProfile.setHttpProfile(httpProfile);
        return new HunyuanClient(cred, region, clientProfile);
    }
}
