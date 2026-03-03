package com.yupi.yupicturebackend.controller;

import com.yupi.yupicturebackend.common.BaseResponse;
import com.yupi.yupicturebackend.common.ResultUtils;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import com.yupi.yupicturebackend.model.dto.ai.*;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.vo.ai.AiTaskSubmitVO;
import com.yupi.yupicturebackend.model.vo.ai.AiTaskVO;
import com.yupi.yupicturebackend.service.AiTaskService;
import com.yupi.yupicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * AI 任务统一接口（混元+千问）
 */
@Slf4j
@RestController
@RequestMapping("/ai")
public class AiTaskController {

    @Resource
    private AiTaskService aiTaskService;

    @Resource
    private UserService userService;

    /* ========================= 混元：自动打标 ========================= */

    /**
     * 提交图片自动打标任务（混元）
     * 
     * 前端调用后立即返回 taskId，然后通过 /task/status/{taskId} 轮询
     */
    @PostMapping("/tag/submit")
    public BaseResponse<AiTaskSubmitVO> submitTagTask(
            @RequestBody AiTagSubmitRequest request,
            HttpServletRequest httpRequest) {
        
        User loginUser = userService.getLoginUser(httpRequest);
        
        Map<String, Object> params = new HashMap<>();
        params.put("imageUrl", request.getImageUrl());
        params.put("pictureId", request.getPictureId());

        AiTaskSubmitRequest taskRequest = new AiTaskSubmitRequest();
        taskRequest.setTaskType("TAG");
        taskRequest.setBizId(request.getPictureId());
        taskRequest.setParameters(params);

        Long taskId = aiTaskService.submitTask(taskRequest, loginUser.getId());

        return ResultUtils.success(new AiTaskSubmitVO(taskId, "打标任务已提交"));
    }

    /* ========================= 千问：AI 扩图 ========================= */

    /**
     * 提交 AI 扩图任务（千问）
     * 
     * 改造原有同步接口：/picture/out_painting/create_task
     */
    @PostMapping("/expand/submit")
    public BaseResponse<AiTaskSubmitVO> submitExpandTask(
            @RequestBody AiExpandSubmitRequest request,
            HttpServletRequest httpRequest) {
        
        User loginUser = userService.getLoginUser(httpRequest);
        
        Map<String, Object> params = new HashMap<>();
        params.put("originalUrl", request.getOriginalUrl());
        params.put("pictureId", request.getPictureId());
        params.put("xScale", request.getXScale());
        params.put("yScale", request.getYScale());

        AiTaskSubmitRequest taskRequest = new AiTaskSubmitRequest();
        taskRequest.setTaskType("EXPAND");
        taskRequest.setBizId(request.getPictureId());
        taskRequest.setParameters(params);

        Long taskId = aiTaskService.submitTask(taskRequest, loginUser.getId());

        return ResultUtils.success(new AiTaskSubmitVO(taskId, "扩图任务已提交"));
    }

    /* ========================= 通用：进度查询 ========================= */

    /**
     * 查询任务状态（前端短轮询）
     * 
     * 轮询策略：
     * - 混元打标：每1-2秒查询，预计3-5秒完成
     * - 千问扩图：每3-5秒查询，预计30-60秒完成
     */
    @GetMapping("/task/status/{taskId}")
    public BaseResponse<AiTaskVO> getTaskStatus(@PathVariable Long taskId) {
        ThrowUtils.throwIf(taskId == null || taskId <= 0, ErrorCode.PARAMS_ERROR);
        
        AiTaskVO vo = aiTaskService.getTaskStatus(taskId);
        
        // 脱敏：非所有者只能看到基础状态
        // 实际项目中需要鉴权
        
        return ResultUtils.success(vo);
    }

    /* ========================= 兼容旧接口（可选） ========================= */

    /**
     * 兼容旧版扩图查询接口
     * 实际转发到统一任务查询
     */
    @GetMapping("/out_painting/get_task")
    public BaseResponse<AiTaskVO> getOutPaintingTask(String taskId) {
        // 解析 taskId（如果旧格式需要转换）
        Long id = Long.valueOf(taskId);
        return getTaskStatus(id);
    }
}