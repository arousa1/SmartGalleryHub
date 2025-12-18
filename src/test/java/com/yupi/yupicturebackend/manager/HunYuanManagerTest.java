package com.yupi.yupicturebackend.manager;

import com.yupi.yupicturebackend.model.vo.AIPictureInfoVO;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class HunYuanManagerTest {

    @Resource
    private HunYuanManager hunYuanManager;
    @Test
    void analyzeImage() {
        String cosUrl = "https://yu-picture-1370553403.cos.ap-shanghai.myqcloud.com/public/1992860225463242754/2025-11-24_83f2wZfbDhyN6RyE.";
        AIPictureInfoVO aiPictureInfoVO = hunYuanManager.analyzeImage(cosUrl);
        System.out.println(aiPictureInfoVO.getIntroduction());
        Assertions.assertNotNull(aiPictureInfoVO);
    }

    @Test
    void testAnalyzeImage() {
    }
}