package com.mokatest.platform.demos.service;

import com.mokatest.platform.demos.domain.ui.Scene;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mokatest.platform.demos.domain.ui.dto.scene.SceneBatchExportDTO;
import com.mokatest.platform.demos.domain.ui.dto.scene.SceneBatchImportDTO;
import com.mokatest.platform.demos.domain.ui.dto.scene.SceneExportDTO;
import com.mokatest.platform.demos.domain.ui.vo.SceneVO;

import java.util.List;
import java.util.Map;

/**
* @author: JingLong
* @description 针对表【scene】的数据库操作Service
* @createDate 2025-08-02 11:10:17
*/
public interface SceneService extends IService<Scene> {

    List<SceneVO> allSceneList(Integer projectId);

    List<SceneVO> allSceneList(Integer projectId, String sceneCategory);

    Boolean addScene(Scene scene);

    Boolean updateScene(Scene scene);

    List<SceneVO> folderList(String projectId);

    List<SceneVO> folderList(String projectId, String sceneCategory);

    Boolean deleteFolderOrScene(Integer sceneId);

    Boolean debugScene(Integer sceneId);

    Boolean importScene(Map<String,Object>  sceneData);

    SceneExportDTO exportScene(Integer sceneId);

    SceneBatchExportDTO exportScenes(List<Integer> sceneIds);

    Boolean importScenesJson(SceneBatchImportDTO dto);

    List<Scene> getSceneListByIds(List<Integer> sceneIdList);

    Boolean copyScene(Integer sceneId);

    Boolean updateSceneSort(List<SceneVO> sceneList);

    Boolean updateSceneSetting(Map<String, Object> sceneSetting);
}
