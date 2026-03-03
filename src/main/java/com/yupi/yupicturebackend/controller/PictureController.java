package com.yupi.yupicturebackend.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yupi.yupicturebackend.annotation.AuthCheck;
import com.yupi.yupicturebackend.api.aliyunai.AliYunAiApi;
import com.yupi.yupicturebackend.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.yupi.yupicturebackend.api.aliyunai.model.GetOutPaintingTaskResponse;
import com.yupi.yupicturebackend.api.imagesearch.ImageSearchApiFacade;
import com.yupi.yupicturebackend.api.imagesearch.model.ImageSearchResult;
import com.yupi.yupicturebackend.common.BaseResponse;
import com.yupi.yupicturebackend.common.DeleteRequest;
import com.yupi.yupicturebackend.common.ResultUtils;
import com.yupi.yupicturebackend.constant.UserConstant;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import com.yupi.yupicturebackend.manager.CacheManager;
import com.yupi.yupicturebackend.manager.auth.SpaceUserAuthManager;
import com.yupi.yupicturebackend.manager.auth.StpKit;
import com.yupi.yupicturebackend.manager.auth.annotation.SaSpaceCheckPermission;
import com.yupi.yupicturebackend.manager.auth.model.SpaceUserPermissionConstant;
import com.yupi.yupicturebackend.model.dto.picture.*;
import com.yupi.yupicturebackend.model.entity.Picture;
import com.yupi.yupicturebackend.model.entity.Space;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.enums.PictureReviewStatusEnum;
import com.yupi.yupicturebackend.model.vo.PictureTagCategory;
import com.yupi.yupicturebackend.model.vo.PictureVO;
import com.yupi.yupicturebackend.service.PictureService;
import com.yupi.yupicturebackend.service.SpaceService;
import com.yupi.yupicturebackend.service.UserService;
import com.yupi.yupicturebackend.utils.CacheKeyUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;

/**
 * @author arousal
 */
@Slf4j
@RestController
@RequestMapping("/picture")
public class PictureController {

    @Resource
    private UserService userService;

    @Resource
    private PictureService pictureService;

    @Resource
    private SpaceService spaceService;

    @Resource
    private AliYunAiApi aliYunAiApi;

    @Resource
    private SpaceUserAuthManager spaceUserAuthManager;

    @Resource
    private CacheManager cacheManager;

    // ============================================
    // 上传接口（纯业务，无缓存操作）
    // ============================================

    /**
     * 上传图片（可重新上传）
     * Canal会自动监听insert语句，处理缓存（如有必要）
     */
    @PostMapping("/upload")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_UPLOAD)
    public BaseResponse<PictureVO> uploadPicture(
            @RequestPart("file") MultipartFile multipartFile,
            PictureUploadRequest pictureUploadRequest,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        PictureVO pictureVO = pictureService.uploadPicture(multipartFile, pictureUploadRequest, loginUser);
        // 无需手动处理缓存，Canal会监听数据库insert
        return ResultUtils.success(pictureVO);
    }

    /**
     * 通过 URL 上传图片（可重新上传）
     */
    @PostMapping("/upload/url")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_UPLOAD)
    public BaseResponse<PictureVO> uploadPictureByUrl(
            @RequestBody PictureUploadRequest pictureUploadRequest,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        String fileUrl = pictureUploadRequest.getFileUrl();
        PictureVO pictureVO = pictureService.uploadPicture(fileUrl, pictureUploadRequest, loginUser);
        // 无需手动处理缓存
        return ResultUtils.success(pictureVO);
    }

    // ============================================
    // 删除接口（纯业务，无缓存操作 - 关键改造）
    // ============================================

    /**
     * 删除图片
     *
     * 【改造说明】
     * 改造前：需要手动查询缓存并删除
     * 改造后：只操作数据库，Canal监听binlog自动失效缓存
     */
    @PostMapping("/delete")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_DELETE)
    public BaseResponse<Boolean> deletePicture(@RequestBody DeleteRequest deleteRequest,
                                               HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);

        // 纯业务操作：只删除数据库
        pictureService.deletePicture(deleteRequest.getId(), loginUser);

        // 移除：手动缓存删除逻辑
        // 改造前：
        // PictureVO pictureVoById = cacheManager.getPictureVoById(deleteRequest.getId());
        // if(pictureVoById != null) {
        //     cacheManager.deleteCacheByKey(CacheKeyUtils.getPictureVoByIdKey(deleteRequest.getId()));
        // }

        // Canal会自动监听DELETE语句，失效相关缓存：
        // 1. picture:id:{id}
        // 2. picture:list:*
        // 3. space:{spaceId}:pictures:*

        return ResultUtils.success(true);
    }

    // ============================================
    // 更新接口（纯业务，无缓存操作 - 关键改造）
    // ============================================

    /**
     * 更新图片（仅管理员可用）
     *
     * 【改造说明】
     * 改造前：更新数据库后需手动处理缓存
     * 改造后：只更新数据库，Canal监听binlog自动失效缓存
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updatePicture(@RequestBody PictureUpdateRequest pictureUpdateRequest,
                                               HttpServletRequest request) {
        if (pictureUpdateRequest == null || pictureUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        // 将实体类和 DTO 进行转换
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureUpdateRequest, picture);
        // 注意将 list 转为 string
        picture.setTags(JSONUtil.toJsonStr(pictureUpdateRequest.getTags()));

        // 数据校验
        pictureService.validPicture(picture);

        // 判断是否存在
        long id = pictureUpdateRequest.getId();
        Picture oldPicture = pictureService.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);

        // 补充审核参数
        User loginUser = userService.getLoginUser(request);
        pictureService.fillReviewParams(oldPicture, loginUser);

        // 纯业务操作：只更新数据库
        boolean result = pictureService.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);

        // 移除：手动缓存删除逻辑
        // Canal会自动监听UPDATE语句，根据变更字段精准失效缓存：
        // 1. 如果spaceId变更：清除新旧空间的列表缓存
        // 2. 如果tags变更：清除标签相关缓存
        // 3. 始终清除：picture:id:{id} 和 picture:list:*

        return ResultUtils.success(true);
    }

    // ============================================
    // 查询接口（使用缓存，但只读不写）
    // ============================================

    /**
     * 根据 id 获取图片（仅管理员可用）- 无缓存，直接查库
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Picture> getPictureById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(picture);
    }

    /**
     * 根据 id 获取图片（封装类）- 无缓存，直接查库
     */
    @GetMapping("/get/vo")
    public BaseResponse<PictureVO> getPictureVOById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);

        // 空间权限校验
        Long spaceId = picture.getSpaceId();
        Space space = null;
        if (spaceId != null) {
            boolean hasPermission = StpKit.SPACE.hasPermission(SpaceUserPermissionConstant.PICTURE_VIEW);
            ThrowUtils.throwIf(!hasPermission, ErrorCode.NO_AUTH_ERROR);
            space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        }

        User loginUser = userService.getLoginUser(request);
        List<String> permissionList = spaceUserAuthManager.getPermissionList(space, loginUser);
        PictureVO pictureVO = pictureService.getPictureVO(picture, request);
        pictureVO.setPermissionList(permissionList);

        return ResultUtils.success(pictureVO);
    }

    /**
     * 根据 id 获取图片（封装类，有缓存）- 核心查询接口
     *
     * 【逻辑】
     * 1. 先查多级缓存（L1 Caffeine -> L2 Redis）
     * 2. 缓存命中直接返回
     * 3. 缓存未命中查DB，回填缓存（下次命中）
     * 4. 数据更新时Canal自动失效缓存
     */
    @GetMapping("/get/vo/cache")
    public BaseResponse<PictureVO> getPictureVOByIdWithCache(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);

        // 1. 查询多级缓存
        PictureVO pictureVO = cacheManager.getPictureVoById(id);

        // 2. 缓存命中直接返回
        if (pictureVO != null) {
            log.debug("图片缓存命中，id: {}", id);
            return ResultUtils.success(pictureVO);
        }

        // 3. 缓存未命中，查询数据库
        log.debug("图片缓存未命中，查询数据库，id: {}", id);
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);

        // 4. 空间权限校验
        Long spaceId = picture.getSpaceId();
        Space space = null;
        if (spaceId != null) {
            boolean hasPermission = StpKit.SPACE.hasPermission(SpaceUserPermissionConstant.PICTURE_VIEW);
            ThrowUtils.throwIf(!hasPermission, ErrorCode.NO_AUTH_ERROR);
            space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        }

        // 5. 组装VO
        User loginUser = userService.getLoginUser(request);
        List<String> permissionList = spaceUserAuthManager.getPermissionList(space, loginUser);
        pictureVO = pictureService.getPictureVO(picture, request);
        pictureVO.setPermissionList(permissionList);

        // 6. 回填缓存（下次查询命中）
        // 注意：这里只写入缓存，不处理更新/删除
        String cacheKey = CacheKeyUtils.getPictureVoByIdKey(id);
        cacheManager.setCacheForValue(cacheKey, JSONUtil.toJsonStr(pictureVO));

        return ResultUtils.success(pictureVO);
    }

    // ============================================
    // 分页查询接口
    // ============================================

    /**
     * 分页获取图片列表（仅管理员可用）- 无缓存
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Picture>> listPictureByPage(@RequestBody PictureQueryRequest pictureQueryRequest) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size),
                pictureService.getQueryWrapper(pictureQueryRequest));
        return ResultUtils.success(picturePage);
    }

    /**
     * 分页获取图片列表（封装类）- 无缓存
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<PictureVO>> listPictureVOByPage(@RequestBody PictureQueryRequest pictureQueryRequest,
                                                             HttpServletRequest request) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);

        // 空间权限校验
        Long spaceId = pictureQueryRequest.getSpaceId();
        if (spaceId == null) {
            pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            pictureQueryRequest.setNullSpaceId(true);
        } else {
            boolean hasPermission = StpKit.SPACE.hasPermission(SpaceUserPermissionConstant.PICTURE_VIEW);
            ThrowUtils.throwIf(!hasPermission, ErrorCode.NO_AUTH_ERROR);
        }

        Page<Picture> picturePage = pictureService.page(new Page<>(current, size),
                pictureService.getQueryWrapper(pictureQueryRequest));
        return ResultUtils.success(pictureService.getPictureVOPage(picturePage, request));
    }

    /**
     * 分页获取图片列表（封装类，有缓存）- 已废弃
     *
     * 【废弃原因】
     * 分页查询参数复杂，缓存命中率低，且Canal难以精准失效
     * 建议：只缓存单条热点数据，列表查询走数据库+ES
     */
    @Deprecated
    @PostMapping("/list/page/vo/cache")
    public BaseResponse<Page<PictureVO>> listPictureVOByPageWithCache(@RequestBody PictureQueryRequest pictureQueryRequest,
                                                                      HttpServletRequest request) {
        // 直接转发到无缓存版本
        return listPictureVOByPage(pictureQueryRequest, request);
    }

    // ============================================
    // 其他业务接口（均无缓存操作）
    // ============================================

    /**
     * 编辑图片（给用户使用）
     * Canal自动监听UPDATE，失效相关缓存
     */
    @PostMapping("/edit")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_EDIT)
    public BaseResponse<Boolean> editPicture(@RequestBody PictureEditRequest pictureEditRequest,
                                             HttpServletRequest request) {
        if (pictureEditRequest == null || pictureEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        pictureService.editPicture(pictureEditRequest, loginUser);
        // 无需手动处理缓存，Canal自动处理
        return ResultUtils.success(true);
    }

    @GetMapping("/tag_category")
    public BaseResponse<PictureTagCategory> listPictureTagCategory() {
        PictureTagCategory pictureTagCategory = new PictureTagCategory();
        List<String> tagList = Arrays.asList("热门", "搞笑", "生活", "高清", "艺术", "校园", "背景", "简历", "创意");
        List<String> categoryList = Arrays.asList("模板", "电商", "表情包", "素材", "海报");
        pictureTagCategory.setTagList(tagList);
        pictureTagCategory.setCategoryList(categoryList);
        return ResultUtils.success(pictureTagCategory);
    }

    /**
     * 审核图片
     * Canal自动监听UPDATE，失效相关缓存
     */
    @PostMapping("/review")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> doPictureReview(@RequestBody PictureReviewRequest pictureReviewRequest,
                                                 HttpServletRequest request) {
        ThrowUtils.throwIf(pictureReviewRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        pictureService.doPictureReview(pictureReviewRequest, loginUser);
        // 无需手动处理缓存，Canal自动处理
        return ResultUtils.success(true);
    }

    /**
     * 批量抓取并创建图片
     * Canal自动监听INSERT
     */
    @PostMapping("/upload/batch")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Integer> uploadPictureByBatch(@RequestBody PictureUploadByBatchRequest pictureUploadByBatchRequest,
                                                      HttpServletRequest request) {
        ThrowUtils.throwIf(pictureUploadByBatchRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        int uploadCount = pictureService.uploadPictureByBatch(pictureUploadByBatchRequest, loginUser);
        // 无需手动处理缓存
        return ResultUtils.success(uploadCount);
    }

    /**
     * 以图搜图 - 无缓存
     */
    @PostMapping("/search/picture")
    public BaseResponse<List<ImageSearchResult>> searchPictureByPicture(@RequestBody SearchPictureByPictureRequest searchPictureByPictureRequest) {
        ThrowUtils.throwIf(searchPictureByPictureRequest == null, ErrorCode.PARAMS_ERROR);
        Long pictureId = searchPictureByPictureRequest.getPictureId();
        ThrowUtils.throwIf(pictureId == null || pictureId <= 0, ErrorCode.PARAMS_ERROR);
        Picture picture = pictureService.getById(pictureId);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        List<ImageSearchResult> resultList = ImageSearchApiFacade.searchImage(picture.getUrl());
        return ResultUtils.success(resultList);
    }

    /**
     * 按照颜色搜索 - 无缓存
     */
    @PostMapping("/search/color")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_VIEW)
    public BaseResponse<List<PictureVO>> searchPictureByColor(@RequestBody SearchPictureByColorRequest searchPictureByColorRequest,
                                                              HttpServletRequest request) {
        ThrowUtils.throwIf(searchPictureByColorRequest == null, ErrorCode.PARAMS_ERROR);
        String picColor = searchPictureByColorRequest.getPicColor();
        Long spaceId = searchPictureByColorRequest.getSpaceId();
        User loginUser = userService.getLoginUser(request);
        List<PictureVO> pictureVOList = pictureService.searchPictureByColor(spaceId, picColor, loginUser);
        return ResultUtils.success(pictureVOList);
    }

    /**
     * 批量编辑图片
     * Canal自动监听UPDATE，批量失效缓存
     */
    @PostMapping("/edit/batch")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_EDIT)
    public BaseResponse<Boolean> editPictureByBatch(@RequestBody PictureEditByBatchRequest pictureEditByBatchRequest,
                                                    HttpServletRequest request) {
        ThrowUtils.throwIf(pictureEditByBatchRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        pictureService.editPictureByBatch(pictureEditByBatchRequest, loginUser);
        // 无需手动处理缓存，Canal会监听批量UPDATE语句
        return ResultUtils.success(true);
    }

    /**
     * 创建 AI 扩图任务 - 无缓存
     */
    @PostMapping("/out_painting/create_task")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_EDIT)
    public BaseResponse<CreateOutPaintingTaskResponse> createPictureOutPaintingTask(
            @RequestBody CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest,
            HttpServletRequest request) {
        if (createPictureOutPaintingTaskRequest == null || createPictureOutPaintingTaskRequest.getPictureId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        CreateOutPaintingTaskResponse response = pictureService.createPictureOutPaintingTask(
                createPictureOutPaintingTaskRequest, loginUser);
        return ResultUtils.success(response);
    }

    /**
     * 查询 AI 扩图任务 - 无缓存
     */
    @GetMapping("/out_painting/get_task")
    public BaseResponse<GetOutPaintingTaskResponse> getPictureOutPaintingTask(String taskId) {
        ThrowUtils.throwIf(StrUtil.isBlank(taskId), ErrorCode.PARAMS_ERROR);
        GetOutPaintingTaskResponse task = aliYunAiApi.getOutPaintingTask(taskId);
        return ResultUtils.success(task);
    }
}