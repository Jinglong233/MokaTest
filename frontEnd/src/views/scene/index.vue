<template>
  <div class="scene-page">
  <Breadcrumb :items="breadcrumbItems"/>
  <div class="scene-main-row">
      <!--    左侧目录树-->
      <div class="scene-col scene-side-col" :style="sideColStyle">
        <a-card class="scene-card scene-folder-card">
          <div class="scene-tree-search-wrap">
            <a-input-search
                placeholder="输入场景名称筛选"
                class="scene-tree-search"
                v-model="searchKey"
            />
          </div>
          <a-button-group class="scene-tree-actions">
            <a-popover title="新建目录" style="flex: 1">
              <a-button v-permission="'auto:scene:create'" @click="userAddOrUpdateFolder('新建')" style="width: 100%;">
                <template #icon>
                  <icon-folder-add/>
                </template>
              </a-button>
            </a-popover>
            <a-popover title="新建场景" style="flex: 1">
              <a-button v-permission="'auto:scene:create'" @click="userAddOrUpdateScene('新建')" style="width: 100%;">
                <template #icon>
                  <icon-file/>
                </template>
              </a-button>
            </a-popover>
            <a-popover :title="isExpandAll ? '展开全部' : '折叠全部'" style="flex: 1">
              <a-button @click="handleSceneTreeExpand" style="width: 100%;">
                <template #icon>
                  <icon-menu-fold v-if="isExpandAll"/>
                  <icon-menu-unfold v-if="!isExpandAll"/>
                </template>
              </a-button>
            </a-popover>
            <a-popover title="导出场景" style="flex: 1">
              <a-button v-permission="'auto:scene:view'" @click="openExportSceneModal" style="width: 100%;">
                <template #icon>
                  <icon-export/>
                </template>
              </a-button>
            </a-popover>
            <a-popover title="导入场景" style="flex: 1">
              <a-button v-permission="'auto:scene:create'" @click="importScene" style="width: 100%;">
                <template #icon>
                  <icon-import/>
                </template>
              </a-button>
            </a-popover>
          </a-button-group>
          <div class="tree-scroll-wrapper">
            <a-scrollbar
                :disable-horizontal="true"
                :outer-style="{ height: '100%' }"
                style="height: 100%; overflow-x: hidden; overflow-y: auto;"
            >
              <a-tree :data="treeData"
                    style="margin-top: 10px; padding-right: 0"
                    :selected-keys="[selectedScene]"
                    block-node
                    show-line
                    ref="sceneTreeRef"
                    :draggable="hasSceneUpdatePermission"
                    @drop="onDrop"
                    @drag-start="isDragging = true"
                    @drag-end="isDragging = false"
                    @select="selectScene"
                    :fieldNames="{
                key: 'id',
                title: 'name',
                }"
            >
              <template #drag-icon> </template>
              <template #switcher-icon="node, { isLeaf }">
                <icon-folder v-if="node.sceneType==='FOLDER'" style="font-size: 16px;"/>
                <icon-desktop v-else-if="node.sceneType==='SCENE' && node.sceneCategory !== 'API'" style="font-size: 16px; color: rgb(var(--arcoblue-6));"/>
                <icon-code v-else-if="node.sceneType==='SCENE' && node.sceneCategory === 'API'" style="font-size: 16px; color: rgb(var(--green-6));"/>
              </template>
              <template #title="node">
                <div class="scene-node-title-wrap">
                  <a-tooltip
                    :content="node.name"
                    position="br"
                    :disabled="isDragging"
                    v-model:popup-visible="tooltipVisibleMap[node.id]"
                  >
                    <span class="scene-node-title">{{ node.name }}</span>
                  </a-tooltip>
                </div>
              </template>
              <template #extra="node">
                <span class="node-extra-actions" v-if="node.id !== '0'">
                  <a-dropdown position="bottom">
                    <a-button type="text" size="mini" class="node-extra-btn" @click.stop>
                      <template #icon>
                        <icon-more class="node-extra-icon"/>
                      </template>
                    </a-button>
                    <template #content v-if="node.sceneType==='FOLDER'">
                      <a-doption v-permission="'auto:scene:create'" @click="userAddSubFolder(node.id)">新建子目录
                      </a-doption>
                      <a-doption v-permission="'auto:scene:create'" @click="userAddSubScene(node.id)">新建场景
                      </a-doption>
                      <a-doption v-permission="'auto:scene:update'" @click="userAddOrUpdateFolder('编辑',node)">编辑目录
                      </a-doption>
                      <a-doption v-permission="'auto:scene:delete'" v-if="node.id != null && node.id != 0"
                                  @click="handleFolderDelete(node.id)">删除目录
                      </a-doption>
                    </template>
                    <template #content v-if="node.sceneType==='SCENE'">
                      <a-doption v-permission="'auto:scene:update'" @click="userAddOrUpdateScene('编辑',node)">编辑场景
                      </a-doption>
                      <a-doption v-permission="'auto:scene:delete'" @click="handleSceneDelete(node.id)">删除场景
                      </a-doption>
                      <a-doption v-permission="'auto:scene:create'" v-if="node.id != null && node.id != 0"
                                  @click="handleSceneCopy(node.id)">复制场景
                      </a-doption>
                    </template>
                  </a-dropdown>
                </span>
              </template>
              </a-tree>
            </a-scrollbar>
          </div>
        </a-card>
      </div>
      <div
          class="scene-main-resizer"
          :class="{ 'is-collapsed': isSidebarCollapsed }"
          @mousedown="startResizeSidebar"
      >
        <div v-if="isSidebarCollapsed" class="sidebar-expand-btn">
          <icon-right class="sidebar-expand-icon"/>
        </div>
        <span v-else class="resizer-line"/>
      </div>
      <!--    中间场景步骤列表-->
      <div class="scene-col scene-right-col">
        <template v-if="!currentEditScene?.id">
          <div class="scene-empty">
            <EmptyActionCard
                v-if="hasSceneCreatePermission"
                title="新建场景"
                :icon="isApiScene ? IconCode : IconDesktop"
                @click="userAddOrUpdateScene('新建')"
            />
            <div v-else class="scene-empty-tip">暂无场景编辑权限，请联系管理员开通</div>
          </div>
        </template>
        <template v-else>
          <!--      顶部场景数据-->
          <a-card class="scene-top-card">
            <div v-if="topSceneInfo && topSceneInfo.id != null && selectedScene!=null && selectedScene[0] != 0" class="scene-top-bar">
              <!-- 第一行：场景身份（名称即标题）+ 调试主操作 -->
              <div class="scene-top-main">
                <div class="scene-identity">
                  <a-input
                      v-model="topSceneInfo.name"
                      class="scene-name-input"
                      placeholder="场景名称"
                      :max-length="50"
                      :disabled="!hasSceneUpdatePermission"
                      @blur="handleTopSceneInfoUpdate"
                  />
                  <a-tag :color="topSceneInfo.sceneCategory === 'API' ? 'green' : 'arcoblue'" size="small" class="scene-category-tag">
                    {{ topSceneInfo.sceneCategory === 'API' ? 'API' : 'UI' }}
                  </a-tag>
                </div>
                <div class="scene-debug-actions">
                  <a-badge v-if="healSuggestions.length > 0" :count="pendingHealCount">
                    <a-button status="success" @click="healModalVisible = true" style="white-space: nowrap;">
                      <template #icon>
                        <icon-robot/>
                      </template>
                      AI 修复建议
                    </a-button>
                  </a-badge>
                  <a-button v-permission="'auto:scene:execute'" status="danger" @click="exit()" v-if="isDebugging" style="white-space: nowrap;">
                      <icon-record-stop/>
                      终止调试
                  </a-button>
                  <a-button v-permission="'auto:scene:execute'" :status="!isDebugging ? 'success' : 'danger' "
                            type="primary"
                            class="debug-main-btn"
                            @click="changeDebugStatus()"
                            :loading="(isDebugging && isApiScene) || pausePending">
                    <template #icon>
                      <icon-caret-right v-if="!isDebugging"/>
                      <icon-bug v-if="isDebugging && (debugStatus === '暂停' || debugStatus === '失败挂起')"/>
                      <icon-pause v-if="isDebugging && debugStatus === '运行中' && !pausePending"/>
                    </template>
                    <a-typography-text>{{ debugButtonLabel }}</a-typography-text>
                  </a-button>
                  <!-- 调试结束后显示 Trace 回放入口 -->
                  <a-button
                    v-if="!isDebugging && traceUrl"
                    status="normal"
                    @click="openTraceViewer()"
                    style="white-space: nowrap;"
                  >
                    <template #icon>
                      <icon-play-arrow/>
                    </template>
                    Trace 回放
                  </a-button>
                </div>
              </div>
              <!-- 第二行：描述 + 运行环境 + 辅助工具 + 视图切换 -->
              <div class="scene-top-secondary">
                <a-input
                    v-model="topSceneInfo.description"
                    class="scene-desc-input"
                    placeholder="添加场景描述…"
                    :max-length="200"
                    :disabled="!hasSceneUpdatePermission"
                    @blur="handleTopSceneInfoUpdate"
                />
                <div class="scene-tools">
                  <!-- 环境选择：API/UI 场景共用（UI 场景里的 HTTP/SQL 步骤执行时按环境合并 baseUrl/环境变量/数据库连接） -->
                  <a-select
                      v-model="selectedEnvironmentId"
                      placeholder="选择环境"
                      allow-clear
                      :disabled="!hasSceneUpdatePermission"
                      @change="onEnvironmentChange"
                      style="width: 140px;"
                  >
                    <a-option
                        v-for="env in envList"
                        :key="env.id"
                        :value="env.id"
                    >
                      {{ env.envName }}
                    </a-option>
                  </a-select>
                  <!-- 环境配置入口：与 API 测试页同一个配置弹窗，关闭后刷新下拉选项 -->
                  <a-tooltip content="环境配置">
                    <a-button v-if="hasEnvConfigPermission" type="text" shape="circle" aria-label="环境配置" @click="envConfigVisible = true">
                      <icon-settings/>
                    </a-button>
                  </a-tooltip>
                  <a-divider direction="vertical" style="margin: 0 4px;"/>
                  <a-button @click="openCommonFunctionDialog()">公共函数</a-button>
                  <a-button v-permission="'auto:scene:update'" @click="editSceneConfig()">场景配置</a-button>
                  <a-radio-group v-model="stepListShowModel" type="button">
                    <a-radio value="list">列表</a-radio>
                    <a-radio value="workflow">工作流</a-radio>
                  </a-radio-group>
                </div>
              </div>
            </div>
          </a-card>
          <!--      核心步骤列表-->
          <a-card v-if="stepListShowModel==='list'" class="scene-list-card">
            <template #title v-if="currentEditScene && currentEditScene.id != null">
              场景步骤
            </template>
            <template #extra>
              <a-button type="primary" v-if="stepDebugList && stepDebugList.length > 0 && !isApiScene"
                        @click="debugScreenShotDrawerVisible = true">调试预览
              </a-button>
              <!--              批量操作-->
              <a-dropdown v-if="hasStepUpdatePermission || hasStepDeletePermission">
                <a-button :disabled="mutationsLocked">
                  批量操作&emsp;
                  <icon-down/>
                </a-button>
                <template #content>
                  <a-doption v-permission="'auto:step:update'" @click="batchEnabledStep()">启用</a-doption>
                  <a-doption v-permission="'auto:step:update'" @click="batchDisableStep()">禁用</a-doption>
                  <a-doption v-permission="'auto:step:delete'" @click="batchDeleteStep()">删除</a-doption>
                </template>
              </a-dropdown>
            </template>
            <div class="step-scroll-wrapper">
              <div class="step-scroll">
                <NestedComponent
                    v-if="sceneStepList.length>0"
                  :scene-id="currentEditScene?.id"
                  v-model="sceneStepList"
                  :is-debugging="isDebugging"
                  :is-api-scene="isApiScene"
                  :scene-environment-id="selectedEnvironmentId"
                  :debug-status="debugStatus"
                  :paused-failure-step-id="pausedFailureStepId"
                  @refreshSceneStepList="reloadSelectedScene"
                  @addAdjacentStep="addAdjacentStep"
                  @reloadStepInfo="clearAdjacentStepInfo"
                  @runFromStep="runApiSceneFromStep"
                  @runSingleStep="runApiSceneSingleStep"
                  @runUntilStep="runUntilStep"
                  v-model:checked-keys="checkedStepNode"
                  :step-debug-list="stepDebugList"
              />
              <div v-else>
                <a-empty v-if="currentEditScene!= null && currentEditScene.id!=null" description="暂无步骤"/>
                <div v-if="currentEditFolder!=null && currentEditFolder.id!=null" class="protocol-cards-container">
                  <div
                      class="protocol-card"
                      v-permission="'auto:scene:create'"
                      @click="userAddSubScene(currentEditFolder.id)"
                  >
                    <div class="icon-wrapper" :style="{ backgroundColor: 'rgba(168, 85, 247, 0.3)' }">
                      <component :is="h(IconCopy, { style: { color: '#FF914D', fontSize: '24px' } })" class="icon"/>
                    </div>
                    <div class="card-title">新建场景</div>
                  </div>
                </div>
                </div>
              </div>
            </div>
            <!--          底部添加步骤footer（宽度不足时自动收纳进「更多操作」）-->
            <a-card class="step-footer-card" v-permission="'auto:step:create'" v-if="currentEditScene!=null && currentEditScene.id!=null">
              <div class="footer-btn-bar" ref="footerBarRef">
                <template v-for="(action, index) in footerActions" :key="action.key">
                  <a-dropdown v-if="action.children && index < visibleActionCount" position="top" :popup-max-height="250">
                    <a-button :style="action.style">
                      <component :is="action.icon"/>
                      {{ action.label }}
                      <icon-down/>
                    </a-button>
                    <template #content>
                      <a-doption v-for="child in action.children" :key="child.label" @click="child.onClick">
                        {{ child.label }}
                      </a-doption>
                    </template>
                  </a-dropdown>
                  <a-button v-else-if="index < visibleActionCount" :type="action.type" :style="action.style"
                            @click="action.onClick">
                    <component :is="action.icon"/>
                    {{ action.label }}
                  </a-button>
                </template>
                <!-- 展示不下的步骤统一收纳 -->
                <a-dropdown v-if="overflowActions.length > 0" position="top" :popup-max-height="400">
                  <a-button>
                    <icon-more/>
                    更多操作
                    <icon-down/>
                  </a-button>
                  <template #content>
                    <template v-for="action in overflowActions" :key="action.key">
                      <a-dsubmenu v-if="action.children" trigger="hover">
                        <component :is="action.icon"/>
                        {{ action.label }}
                        <template #content>
                          <a-doption v-for="child in action.children" :key="child.label" @click="child.onClick">
                            {{ child.label }}
                          </a-doption>
                        </template>
                      </a-dsubmenu>
                      <a-doption v-else @click="action.onClick">
                        <component :is="action.icon"/>
                        {{ action.label }}
                      </a-doption>
                    </template>
                  </template>
                </a-dropdown>
                <!-- 隐藏测量区：渲染全部按钮用于量宽，不参与布局 -->
                <div class="footer-measure-bar" ref="measureBarRef" aria-hidden="true">
                  <span v-for="action in footerActions" :key="'m-' + action.key" class="footer-measure-item">
                    <a-button :type="action.type" :style="action.style">
                      <component :is="action.icon"/>
                      {{ action.label }}
                      <icon-down v-if="action.children"/>
                    </a-button>
                  </span>
                  <span class="footer-measure-item footer-measure-more">
                    <a-button>
                      <icon-more/>
                      更多操作
                      <icon-down/>
                    </a-button>
                  </span>
                </div>
              </div>
            </a-card>

          </a-card>
          <a-card v-if="stepListShowModel==='workflow'" class="scene-workflow-card">
            <SceneWorkflowCanvas
                v-if="currentEditScene && currentEditScene.id != null"
                :scene-id="currentEditScene.id"
                :steps="sceneStepList"
                :is-debugging="isDebugging"
                :step-debug-list="stepDebugList"
                :is-api-scene="isApiScene"
                :scene-environment-id="selectedEnvironmentId"
                :debug-status="debugStatus"
                :paused-failure-step-id="pausedFailureStepId"
                :has-update-permission="hasStepUpdatePermission"
                :has-delete-permission="hasStepDeletePermission"
                @refresh="reloadSelectedScene"
                @dirty-change="hasDirtyWorkflowNodes = $event"
                @run-until-step="runUntilStep"
            />
          </a-card>
        </template>
      </div>

      <!--   步骤右侧编辑抽屉-->
      <StepDetailDraw
          :width="520"
          ref="stepDetailFormRef"
          v-model:visible="stepFormVisible"
          :title="getStepTypeChinese(currentStepDetail.stepType)"
          :form-fields="currentOperationFormConfig"
          :form-data="currentStepDetail"
          :show-setting-tab="showSettingTab"
          :scene-environment-id="selectedEnvironmentId"
          :submit-disabled="!hasStepCreatePermission"
          :disabled="!hasStepCreatePermission"
          @cancel="clearAdjacentStepInfo()"
          @submit="handleSubmit"
      />

      <!--   API/SQL 步骤「引入已有」来源选择器-->
      <ApiSourcePickerModal
          v-model:visible="apiSourcePickerVisible"
          :api-type="apiSourcePickerType"
          @select="handleApiSourcePicked"
      />

      <!--   环境配置弹窗（与 API 测试页同一个组件）-->
      <EvnConfig v-model="envConfigVisible" :team-id="teamStore.getTeamId"/>

    </div>


    <!--添加/编辑 目录对话框-->
    <a-modal v-model:visible="folderFormVisible" :title="`${modalMode}目录`" @cancel="handleCancel"
             @before-ok="handleFolderBeforeOk">
      <a-form ref="addOrUpdateFolderFormRef" :model="addOrUpdateFolderForm" :rules="addFormRules">
        <a-form-item field="parentId" label="父目录" required>
          <a-tree-select
              :data="selectableTreeNode"
              v-model="addOrUpdateFolderForm.parentId"
              placeholder="选择父目录"
              :fieldNames="{
                key: 'id',
                title: 'name',
            }"
          >
            <template #tree-slot-icon>
              <icon-folder/>
            </template>
          </a-tree-select>
        </a-form-item>
        <a-form-item field="name" label="目录名称" required>
          <a-input v-model="addOrUpdateFolderForm.name"/>
        </a-form-item>
      </a-form>
    </a-modal>

    <!--添加场景对话框-->
    <a-modal v-model:visible="sceneFormVisible" :title="`${modalMode}场景`" @cancel="handleSceneCancel"
             @before-ok="handleSceneBeforeOk">
      <a-form ref="addOrUpdateSceneFormRef" :model="addOrUpdateSceneForm" :rules="addSceneFormRules">
        <a-form-item field="parentId" label="所属目录" required>
          <a-tree-select
              :data="folderList"
              v-model="addOrUpdateSceneForm.parentId"
              placeholder="场景所属目录"
              :fieldNames="{
                key: 'id',
                title: 'name',
            }"
          >
            <template #tree-slot-icon>
              <icon-folder/>
            </template>
          </a-tree-select>
        </a-form-item>
        <a-form-item field="name" label="场景名称" required>
          <a-input v-model="addOrUpdateSceneForm.name"/>
        </a-form-item>
        <a-form-item field="description" label="场景描述">
          <a-textarea v-model="addOrUpdateSceneForm.description" allow-clear :max-length="50" show-word-limit
                      :auto-size="true"/>
        </a-form-item>
      </a-form>
    </a-modal>

    <!--导入场景对话框-->
    <a-modal v-model:visible="importSceneVisible" @cancel="handleImportSceneCancel"
             :width="800"
             :body-style="{ height: '600px', overflow: 'hidden' }"
             :ok-button-props="{ disabled: !canSubmitImport }"
             @before-ok="handleImportSceneBeforeOk">
      <template #title>
        <a-space>
          <icon-import style="color: rgb(var(--arcoblue-6));"/>
          <span style="font-weight: 600;">导入场景</span>
        </a-space>
      </template>

      <a-form ref="importSceneFormRef" :model="importSceneForm" :rules="importSceneFormRules"
            layout="vertical" class="import-scene-form">
        <!-- 第一层：数据来源 -->
        <a-form-item field="mode" hide-label>
          <a-radio-group v-model="importSceneForm.mode" class="import-source-radio-group">
            <a-radio v-if="props.sceneCategory === 'UI'" value="playwright">
              <div class="import-source-card">
                <icon-code class="import-source-icon"/>
                <div class="import-source-title">CRX</div>
                <div class="import-source-desc">Playwright 录制脚本</div>
              </div>
            </a-radio>
            <a-radio v-if="props.sceneCategory === 'UI'" value="platform">
              <div class="import-source-card">
                <icon-desktop class="import-source-icon"/>
                <div class="import-source-title">MokatestRecord</div>
                <div class="import-source-desc">Mokatest 浏览器录制插件</div>
              </div>
            </a-radio>
            <a-radio value="mokatest-json">
              <div class="import-source-card">
                <icon-file class="import-source-icon"/>
                <div class="import-source-title">Mokatest 场景 JSON</div>
                <div class="import-source-desc">导出的场景 JSON 文件</div>
              </div>
            </a-radio>
          </a-radio-group>
        </a-form-item>

        <!-- Mokatest 场景 JSON 模式 -->
        <template v-if="importSceneForm.mode === 'mokatest-json'">
          <!-- 第二层：所属目录 & 解析状态 -->
          <a-row :gutter="16" class="import-config-row">
            <a-col :span="12">
              <a-form-item field="parentId" label="所属目录" required>
                <a-tree-select
                    :data="folderList"
                    v-model="importSceneForm.parentId"
                    placeholder="请选择场景所属目录"
                    :fieldNames="{key: 'id', title: 'name'}"
                >
                  <template #tree-slot-icon>
                    <icon-folder/>
                  </template>
                </a-tree-select>
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="解析状态" hide-asterisk>
                <a-alert
                    :type="importJsonSceneCount > 0 ? 'success' : 'warning'"
                    :show-icon="true"
                    :content="importJsonSceneCount > 0
                      ? `解析成功，共包含 ${importJsonSceneCount} 个场景`
                      : '暂未解析到可用场景，请先上传文件'"
                />
              </a-form-item>
            </a-col>
          </a-row>

          <!-- 第三层：拖拽上传区 -->
          <a-form-item hide-label>
            <a-upload
                class="import-json-uploader"
                drag
                :auto-upload="false"
                accept=".json"
                :limit="1"
                :show-file-list="false"
                @change="onImportJsonFileChange"
            >
              <template #upload-button>
                <div v-if="!importJsonFileList?.length" class="import-drag-zone">
                  <icon-cloud style="font-size: 40px; color: rgb(var(--arcoblue-6));"/>
                  <div class="import-drag-title">点击上传或将 JSON 文件拖拽到此处</div>
                  <div class="import-drag-desc">仅支持 .json 格式，可包含单个或多个场景</div>
                </div>
                <div v-else class="import-file-card">
                  <div class="import-file-info">
                    <icon-file style="font-size: 24px; color: rgb(var(--arcoblue-6));"/>
                    <div>
                      <div class="import-file-name">{{ importJsonFileList[0]?.name }}</div>
                      <div class="import-file-size">{{ formatFileSize(importJsonFileList[0]?.size) }}</div>
                    </div>
                  </div>
                  <a-button type="text" status="danger" @click.stop="clearImportJsonFile">
                    <template #icon>
                      <icon-delete/>
                    </template>
                  </a-button>
                </div>
              </template>
            </a-upload>
          </a-form-item>
        </template>

        <!-- CRX 模式 -->
        <template v-if="importSceneForm.mode === 'playwright'">
          <a-row :gutter="16" class="import-config-row">
            <a-col :span="12">
              <a-form-item field="parentId" label="所属目录" required>
                <a-tree-select
                    :data="folderList"
                    v-model="importSceneForm.parentId"
                    placeholder="请选择场景所属目录"
                    :fieldNames="{key: 'id', title: 'name'}"
                >
                  <template #tree-slot-icon>
                    <icon-folder/>
                  </template>
                </a-tree-select>
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="格式说明" hide-asterisk>
                <a-alert type="warning" show-icon content="每行一个 JSON 对象，CRX 格式"/>
              </a-form-item>
            </a-col>
          </a-row>
          <a-form-item field="CrxData" label="CRX 数据" required>
            <a-textarea
                v-model="importSceneForm.CrxData"
                :auto-size="{minRows: 6, maxRows: 6}"
                placeholder="请粘贴 CRX 录制数据..."
            />
          </a-form-item>
          <a-row :gutter="16">
            <a-col :span="12">
              <a-form-item field="name" label="场景名称" required>
                <a-input v-model="importSceneForm.name" placeholder="导入后的场景名称"/>
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item field="description" label="场景描述">
                <a-input v-model="importSceneForm.description" placeholder="场景描述（可选）"/>
              </a-form-item>
            </a-col>
          </a-row>
        </template>

        <!-- MokatestRecord 模式 -->
        <template v-if="importSceneForm.mode === 'platform'">
          <a-row :gutter="16" class="import-config-row">
            <a-col :span="12">
              <a-form-item field="parentId" label="所属目录" required>
                <a-tree-select
                    :data="folderList"
                    v-model="importSceneForm.parentId"
                    placeholder="请选择场景所属目录"
                    :fieldNames="{key: 'id', title: 'name'}"
                >
                  <template #tree-slot-icon>
                    <icon-folder/>
                  </template>
                </a-tree-select>
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="状态" hide-asterisk>
                <a-alert
                    :type="recordSteps.length > 0 ? 'success' : 'info'"
                    :show-icon="true"
                    :content="recordSteps.length > 0
                      ? `解析成功，共 ${recordSteps.length} 个有效步骤`
                      : '请在左侧上传录制文件，系统将自动解析为可执行步骤'"
                />
              </a-form-item>
            </a-col>
          </a-row>
          <a-row :gutter="16">
            <a-col :span="12" class="record-import-left">
              <a-form-item required>
                <template #label>
                  录制导入
                  <a-link style="margin-left: 8px; font-size: 12px;" @click="recorderGuideVisible = true">
                    没有插件？点这里获取
                  </a-link>
                </template>
                <div class="record-import-uploader">
                  <RecordImportInline
                      ref="recordImportPanelRef"
                      :project-id="projectStore.getProjectId"
                      @stepsChange="onRecordStepsChange"
                  />
                </div>
              </a-form-item>
              <a-form-item field="name" label="场景名称" required>
                <a-input v-model="importSceneForm.name" placeholder="导入后的场景名称"/>
              </a-form-item>
              <a-form-item field="description" label="场景描述">
                <a-input v-model="importSceneForm.description" placeholder="场景描述（可选）"/>
              </a-form-item>
            </a-col>
            <a-col :span="12" class="record-import-right">
              <RecordStepList
                  v-model:steps="recordSteps"
                  :warnings="recordWarnings"
              />
            </a-col>
          </a-row>
        </template>
      </a-form>
    </a-modal>

    <!-- 录制插件获取引导弹窗 -->
    <RecorderGuideModal v-model:visible="recorderGuideVisible"/>

    <!--导出场景弹窗：树形勾选-->
    <a-modal v-model:visible="exportSceneVisible" title="导出场景"
             :width="520"
             :ok-button-props="{ disabled: exportSceneCheckedKeys.length === 0 }"
             @before-ok="handleExportSceneConfirm"
             @cancel="handleExportSceneCancel">
      <div class="export-scene-dialog">
        <!-- 搜索 + 快捷选择 -->
        <div class="export-scene-toolbar">
          <a-input v-model="exportSceneSearchKey" placeholder="搜索场景/目录名称" allow-clear>
            <template #prefix>
              <icon-search/>
            </template>
          </a-input>
          <a-button type="text" size="small" style="flex-shrink: 0;" @click="exportSceneSelectAll">全选</a-button>
          <a-button type="text" size="small" style="flex-shrink: 0;" @click="exportSceneCheckedKeys = []">清空</a-button>
        </div>
        <!-- 固定高度树区域，弹窗大小不随内容变化 -->
        <div class="export-scene-tree-wrapper">
          <a-scrollbar style="height: 100%; overflow: auto;">
            <a-tree
                v-if="filteredExportSceneTreeData.length > 0"
                :data="filteredExportSceneTreeData"
                checkable
                check-strictly
                v-model:checked-keys="exportSceneCheckedKeys"
                v-model:expanded-keys="exportSceneExpandedKeys"
                :fieldNames="{
                  key: 'id',
                  title: 'name',
                }"
            >
              <template #switcher-icon="node">
                <icon-folder v-if="node.sceneType==='FOLDER'" style="font-size: 16px;"/>
                <icon-desktop v-else-if="node.sceneType==='SCENE' && node.sceneCategory !== 'API'" style="font-size: 16px; color: rgb(var(--arcoblue-6));"/>
                <icon-code v-else-if="node.sceneType==='SCENE' && node.sceneCategory === 'API'" style="font-size: 16px; color: rgb(var(--green-6));"/>
              </template>
              <template #title="node">
                <span v-html="highlightExportSceneName(node.name)"></span>
              </template>
            </a-tree>
            <a-empty v-else description="未找到匹配的场景"/>
          </a-scrollbar>
        </div>
        <div class="export-scene-footer">
          已选择 <span class="export-scene-count">{{ exportSceneCheckedKeys.length }}</span> 个场景
        </div>
      </div>
    </a-modal>

    <DebugImageList :visible="debugScreenShotDrawerVisible" :image-list="stepDebugList"
                    @update:visible="cancelDebugImage"/>

    <!-- AI 定位自愈建议聚合弹窗：一轮调试的多条建议统一在此批量处理 -->
    <a-modal v-model:visible="healModalVisible" title="AI 修复建议" width="720px"
             ok-text="全部采纳" :ok-button-props="{disabled: pendingLibrarySuggestions.length === 0}"
             cancel-text="关闭" @ok="async (done: any) => { if (await applyAllHealSuggestions()) done(); }">
      <a-alert type="info" style="margin-bottom: 12px;">
        以下步骤的定位失效后由 AI 自动修复并通过真实页面验证。采纳后将写回元素库，所有引用该元素的场景同步生效。
      </a-alert>
      <a-list :data="healSuggestions" :bordered="false">
        <template #item="{ item }">
          <a-list-item>
            <div style="width: 100%;">
              <div style="display: flex; align-items: center; gap: 8px; margin-bottom: 4px;">
                <span style="font-weight: 600;">{{ item.stepName || ('步骤 #' + item.stepId) }}</span>
                <a-tag v-if="item.elementName" size="small">{{ item.elementName }}</a-tag>
                <a-tag v-if="item.locatorSource === 'CUSTOM'" size="small" color="orange">自定义定位</a-tag>
                <a-tag v-if="item.status === 'applied'" size="small" color="green">已写回</a-tag>
                <a-tag v-else-if="item.status === 'ignored'" size="small" color="gray">已忽略</a-tag>
              </div>
              <div style="font-size: 12px; line-height: 1.8;">
                <div>
                  <span style="color: var(--color-text-3);">旧定位（已失效）：</span>
                  <code style="color: rgb(var(--red-6)); word-break: break-all;">{{ item.oldType }} = {{ item.oldValue }}</code>
                </div>
                <div>
                  <span style="color: var(--color-text-3);">新定位（已验证）：</span>
                  <code style="color: rgb(var(--green-6)); word-break: break-all;">{{ item.newType }} = {{ item.newValue }}</code>
                </div>
                <div v-if="item.locatorSource === 'CUSTOM' && item.status === 'pending'" style="color: var(--color-text-3);">
                  自定义定位将写回该步骤的自定义定位；调试运行中受写保护时需调试结束后采纳。
                </div>
              </div>
            </div>
            <template #actions>
              <template v-if="item.status === 'pending'">
                <a-button type="text" size="small" status="success" :loading="item.applying"
                          @click="applyHealSuggestion(item)">采纳</a-button>
                <a-button type="text" size="small" @click="ignoreHealSuggestion(item)">忽略</a-button>
              </template>
            </template>
          </a-list-item>
        </template>
      </a-list>
      <a-empty v-if="healSuggestions.length === 0" description="暂无修复建议"/>
    </a-modal>

    <a-modal v-model:visible="sceneConfigFormVisible" title="编辑场景配置"
             width="auto"
             @before-ok="handleSceneConfigBeforeOk">
      <a-form
          style="width: 670px;"
          label-align="left"
          ref="updateSceneConfigFormRef" :model="updateSceneConfigForm" :rules="updateSceneConfigFormRules">
        <a-tabs :default-active-key="1">
          <!-- ========== API场景 ========== -->
          <template v-if="isApiScene">
            <a-tab-pane :key="1" title="场景Header">
              <ParameterTable ref="sceneHeaderTableRef" context="header" @change="onSceneConfigChange"/>
            </a-tab-pane>
            <a-tab-pane :key="2" title="场景Cookie">
              <ParameterTable ref="sceneCookieTableRef" context="cookie" @change="onSceneConfigChange"/>
            </a-tab-pane>
            <a-tab-pane :key="3" title="场景变量">
              <ParameterTable ref="sceneVarTableRef" context="variable" @change="onSceneConfigChange"/>
            </a-tab-pane>
            <a-tab-pane :key="4" title="场景断言">
              <AssertionTable ref="sceneAssertTableRef" @change="onSceneConfigChange"/>
            </a-tab-pane>
          </template>
          <!-- ========== UI场景 ========== -->
          <template v-else>
            <a-tab-pane :key="1" title="运行配置">
              <a-form-item field="sceneBrowserConfig.browserType" label="浏览器类型" required>
                <a-select v-model="updateSceneConfigForm.sceneBrowserConfig.browserType">
                  <a-option value="CHROME">谷歌</a-option>
                  <a-option value="EDGE">微软Edge</a-option>
                  <a-option value="FIREFOX">火狐</a-option>
                  <a-option value="SAFARI">safari</a-option>
                  <a-option value="IE">IE浏览器</a-option>
                </a-select>
              </a-form-item>
              <a-form-item field="sceneBrowserConfig.runningType" label="运行模式" required>
                <a-select v-model="updateSceneConfigForm.sceneBrowserConfig.runningType">
                  <a-option value="NORMAL">正常模式</a-option>
                  <a-option value="HEADLESS">无头模式</a-option>
                </a-select>
              </a-form-item>
              <a-form-item field="sceneBrowserConfig.windowMode" label="窗口模式" required>
                <a-select v-model="updateSceneConfigForm.sceneBrowserConfig.windowMode">
                  <a-option value="MAXIMIZE">窗口最大化</a-option>
                  <a-option value="CUSTOMSIZE">自定义尺寸</a-option>
                </a-select>
              </a-form-item>
              <a-form-item field="sceneBrowserConfig.deviceType" label="设备类型" required>
                <a-select v-model="updateSceneConfigForm.sceneBrowserConfig.deviceType">
                  <a-option value="MOBILE">移动端</a-option>
                  <a-option value="PC">PC端</a-option>
                </a-select>
              </a-form-item>
              <a-form-item v-if="updateSceneConfigForm.sceneBrowserConfig.windowMode === 'CUSTOMSIZE'"
                           field="sceneBrowserConfig.windowSize" label="窗口大小" required>
                <a-input v-model="updateSceneConfigForm.sceneBrowserConfig.windowSize"/>
              </a-form-item>
            </a-tab-pane>
            <a-tab-pane :key="2" title="步骤通用配置">
              <a-form-item field="setting.timeout" label="超时时间" required>
                <a-input-number :precision="0" hide-button="hide-button" :min="0"
                                v-model="updateSceneConfigForm.setting.timeout">
                  <template #suffix>秒</template>
                </a-input-number>
              </a-form-item>
              <a-form-item field="setting.preExecuteWaitingTime" label="执行前等待时间" required>
                <a-input-number :precision="0" hide-button="hide-button" :min="0"
                                v-model="updateSceneConfigForm.setting.preExecuteWaitingTime">
                  <template #suffix>秒</template>
                </a-input-number>
              </a-form-item>
              <a-form-item field="setting.waitingTimeAfterExecution" label="执行后等待时间" required>
                <a-input-number :precision="0" hide-button="hide-button" :min="0"
                                v-model="updateSceneConfigForm.setting.waitingTimeAfterExecution">
                  <template #suffix>秒</template>
                </a-input-number>
              </a-form-item>
              <a-form-item field="setting.screenshotConfiguration" label="截图策略" required>
                <a-select v-model="updateSceneConfigForm.setting.screenshotConfiguration">
                  <a-option value="NOT_SCREENSHOT">不截图</a-option>
                  <a-option value="SCREENSHOT">当前步骤截图</a-option>
                  <a-option value="SCREENSHOT_EXCEPTION">出现异常截图</a-option>
                </a-select>
              </a-form-item>
              <a-form-item field="setting.errorHandlingStrategy" label="错误处理策略" required>
                <a-select v-model="updateSceneConfigForm.setting.errorHandlingStrategy">
                  <a-option value="IGNORE">忽略</a-option>
                  <a-option value="STOP">终止</a-option>
                  <a-option value="RETRY" disabled>重试（暂时搁置）</a-option>
                </a-select>
              </a-form-item>
            </a-tab-pane>
          </template>
        </a-tabs>
      </a-form>
    </a-modal>

    <!--    公共函数对话框-->
    <CommonFunction :visible="commonFunctionVisible" @closeDialog="commonFunctionVisible = false"/>

    <ImportSceneStep v-model:visible="importStepDialogVisible" :tree-data="treeData"
                     :currentEditSceneId="currentEditScene?.id" @reloadStepList="reloadSelectedScene"/>

  </div>
</template>

<script lang="ts" setup>
import {IconCopy, IconCode, IconDesktop, IconFile, IconFolder, IconMore, IconRight} from '@arco-design/web-vue/es/icon';
import {computed, h, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch} from 'vue'
import Breadcrumb from '@/components/breadcrumb/index.vue';
import EmptyActionCard from '@/components/empty-action-card/index.vue';
import {Scene} from "@/types/domain/Scene";
import {Message, Modal, TreeNodeData} from "@arco-design/web-vue";
import {
  addScene,
  cpoyScene,
  deleteScene,
  exportScenes,
  getAllSceneList,
  getFolderList,
  getSceneById,
  importSceneInfo,
  importScenesJson,
  updateScene,
  updateSceneSetting,
  updateSort
} from "@/api/MyApi/scene";
import {SceneVO} from "@/types/vo/SceneVO";
import {
  addAdjacentTestStep,
  addStep,
  batchDelete,
  batchDisable,
  batchEnable,
  getStepList,
  getStepDetail,
  updateStep
} from "@/api/MyApi/step";
import {StepVO} from "@/types/vo/StepVO";
import {
  ApiRequest,
  Assert,
  Back,
  ClickForm,
  ClosePage,
  Dialog,
  Drag,
  ElementDomOperation,
  Extract,
  FileUpload,
  For,
  Forward,
  getStepConfig,
  Hover,
  IfAssert,
  Keyboard,
  OpenPage,
  Refresh,
  ScriptRequest,
  SqlRequest,
  SwitchIframe,
  SwitchTab,
  Wait,
  While
} from "@/schema/stepFormConfig/FormConfig";
import {closePageSchema} from "@/schema/operationSchema/browserOperation/closePageSchema";
import {keyboardInputSchema} from "@/schema/operationSchema/keyboardOperation/keyboardInputSchema";
import {assertSchema} from "@/schema/operationSchema/assertOperation/AssertSchema";
import {ifSchema} from "@/schema/operationSchema/ifOperation/IfSchema";
import {iframeSchema} from "@/schema/operationSchema/iframeOperation/iframeSchema";
import {clickSchema} from "@/schema/operationSchema/clickOperation/clickSchema";
import {switchTabSchema} from "@/schema/operationSchema/browserOperation/switchTabSchema";
import {SCHEMA_REGISTRY} from "@/schema/operationSchema";
import StepDetailDraw from "@/views/scene/component/StepDetailDraw.vue";
import {extractSchema} from "@/schema/operationSchema/extractOperation/extractSchema";
import {createStep, StepType} from "@/types/dto/StepDetailDTO";
import {TestStep} from "@/types/domain/TestStep";
import {waitFixedDurationSchema} from "@/schema/operationSchema/waitOperation/waitFixedDurationSchema";
import NestedComponent from "@/views/scene/component/NestedComponent.vue";
import ApiSourcePickerModal from "@/views/scene/component/ApiSourcePickerModal.vue";
import EvnConfig from "@/components/env-config/index.vue";
import {
  buildImportedStepData,
  buildNewHttpStepData,
  buildNewSqlStepData,
  type ApiStepSourceData
} from "@/views/scene/component/apiStepTemplate";
import {RecordUtilJsonFileParse} from "@/types/RecordUtilJsonFileParse";
import {getElementById, update as updateElement} from "@/api/MyApi/element";
import {getStepTypeChinese} from "@/types/enum/StepType";
import DebugImageList from "@/views/scene/component/DebugImageList.vue";
// TODO: SceneFlow.vue 文件缺失，临时注释以恢复构建
import {onBeforeRouteLeave, useRoute, useRouter} from "vue-router";
import {useProjectStore} from "@/store";
import usePermission from "@/hooks/permission";
import CommonFunction from "@/views/scene/component/CommonFunction.vue";
import {AddAdjacentStepDTO} from "@/types/dto/other/AddAdjacentStepDTO";
import ImportSceneStep from "@/views/scene/component/ImportSceneStep.vue";
import RecordImportInline from "@/views/scene/component/RecordImportInline.vue";
import RecorderGuideModal from "@/components/recorder-guide/RecorderGuideModal.vue";
import RecordStepList from "@/views/scene/component/RecordStepList.vue";
import SceneWorkflowCanvas from "@/views/scene/component/SceneWorkflowCanvas.vue";
import {useDebugStepLock} from "@/views/scene/component/useDebugStepLock";
import { saveRecord, RecordStepDraft } from "@/api/MyApi/record";
import {debugApiScene} from "@/api/MyApi/apiScene";
import {getEnvList} from "@/api/MyApi/environment";
import {getUserInfo} from "@/api/MyApi/user";
import {getToken} from "@/utils/auth";
import {Environment} from "@/types/domain/api/Environment";
import useTeamStore from "@/store/modules/team";
import {RequestExecuteInfo} from "@/types/domain/api/requestModel/RequestExecuteInfo";
import ParameterTable from "@/views/apiManager/component/ParameterTable.vue";
import AssertionTable from "@/views/apiManager/component/AssertionTable.vue";

interface Props {
  sceneCategory: 'UI' | 'API';
  breadcrumbItems: string[];
}

const props = defineProps<Props>();

const isApiScene = computed(() => props.sceneCategory === 'API');

// ===== 左侧目录树：可拖拽伸缩 + 拖拽隐藏 =====
const SIDEBAR_DEFAULT_WIDTH = 240;
const SIDEBAR_MIN_WIDTH = 200;
const SIDEBAR_MAX_WIDTH = 480;
const SIDEBAR_COLLAPSE_THRESHOLD = 20;

const sidebarWidth = ref(SIDEBAR_DEFAULT_WIDTH);
const isSidebarCollapsed = ref(false);
const lastSidebarWidth = ref(SIDEBAR_DEFAULT_WIDTH);

const sideColStyle = computed(() => ({
  width: isSidebarCollapsed.value ? '0px' : `${sidebarWidth.value}px`,
  overflow: 'hidden',
}));

const collapseSidebar = () => {
  if (!isSidebarCollapsed.value) {
    lastSidebarWidth.value = Math.max(sidebarWidth.value, SIDEBAR_MIN_WIDTH);
  }
  isSidebarCollapsed.value = true;
  sidebarWidth.value = 0;
};

const expandSidebar = () => {
  isSidebarCollapsed.value = false;
  const expandedWidth = Math.round((lastSidebarWidth.value || SIDEBAR_DEFAULT_WIDTH) * 1.25);
  sidebarWidth.value = Math.min(expandedWidth, SIDEBAR_MAX_WIDTH);
};

const startResizeSidebar = (e: MouseEvent) => {
  const startX = e.clientX;
  const wasCollapsed = isSidebarCollapsed.value;
  const startWidth = wasCollapsed ? 0 : sidebarWidth.value;
  let moved = false;

  const onMove = (ev: MouseEvent) => {
    if (Math.abs(ev.clientX - startX) > 3) moved = true;
    if (wasCollapsed) {
      isSidebarCollapsed.value = false;
    }
    const newWidth = startWidth + ev.clientX - startX;
    sidebarWidth.value = Math.min(Math.max(newWidth, 0), SIDEBAR_MAX_WIDTH);
  };

  const onUp = () => {
    document.removeEventListener('mousemove', onMove);
    document.removeEventListener('mouseup', onUp);
    document.body.style.userSelect = '';

    if (!moved && wasCollapsed) {
      expandSidebar();
      return;
    }

    if (sidebarWidth.value <= SIDEBAR_COLLAPSE_THRESHOLD) {
      collapseSidebar();
    } else if (sidebarWidth.value < SIDEBAR_MIN_WIDTH) {
      sidebarWidth.value = SIDEBAR_MIN_WIDTH;
    }
  };

  document.addEventListener('mousemove', onMove);
  document.addEventListener('mouseup', onUp);
  document.body.style.userSelect = 'none';
};

const projectStore = useProjectStore();
const teamStore = useTeamStore();
const permission = usePermission();

const hasSceneUpdatePermission = computed(() => permission.hasPermission('auto:scene:update'));
const hasSceneCreatePermission = computed(() => permission.hasPermission('auto:scene:create'));
// 环境配置入口：与 ApiDebugForm 同一口径（查看环境或查看全局变量即可打开，弹窗内编辑由 update 权限自控）
const hasEnvConfigPermission = computed(() =>
    permission.hasPermission('auto:env:view') || permission.hasPermission('auto:globalvar:view'));
const hasStepCreatePermission = computed(() => permission.hasPermission('auto:step:create'));
const hasStepUpdatePermission = computed(() => permission.hasPermission('auto:step:update'));
const hasStepDeletePermission = computed(() => permission.hasPermission('auto:step:delete'));

const commonFunctionVisible = ref(false);

// 步骤展示类型
const stepListShowModel = ref('list');

// 工作流视图是否有未保存的节点
const hasDirtyWorkflowNodes = ref(false);

// 编辑场景配置表单引用
const updateSceneConfigFormRef = ref();

// 调试截图对话框开关
const debugScreenShotDrawerVisible = ref(false);

// 步骤调试列表
const stepDebugList = ref<any[]>([]);

// 是否debug状态
const isDebugging = ref(false);

// 是否debug结束
const isDebugEnd = ref(true);

// debug状态
const debugStatus = ref('未运行');

// UI 场景调试失败挂起状态
const isPausedOnFailure = ref(false);
const pausedFailureStepId = ref<number | null>(null);

// 暂停请求已发送、等待当前步骤执行完成（边界暂停生效前的提示态）
const pausePending = ref(false);

// 「执行到此步骤」的目标步骤 id（用于到达后给出提示）
const runUntilTargetId = ref<number | null>(null);

// 调试结束后 trace 回放 URL（Playwright Trace Viewer），调试结束后由后端 WebSocket 推送
const traceUrl = ref('');

// 调试期间的步骤编辑锁定（运行中全锁；暂停/失败挂起仅未执行步骤可改）
const {mutationsLocked, isStepLocked} = useDebugStepLock({
  isDebugging,
  debugStatus,
  stepDebugList,
  pausedFailureStepId,
  isApiScene,
});

// 暂停倒计时（失败挂起 / 手动暂停共用）
const pauseCountdown = ref(0);
let pauseTimer: any = null;

const clearPauseTimer = () => {
  if (pauseTimer) {
    clearInterval(pauseTimer);
    pauseTimer = null;
  }
};

const formatPauseCountdown = (seconds: number) => {
  const m = Math.floor(seconds / 60).toString().padStart(2, '0');
  const s = Math.floor(seconds % 60).toString().padStart(2, '0');
  return `${m}:${s}`;
};

// 调试主按钮文案：状态机单点收口，避免并列 v-if 分支互相打架
const debugButtonLabel = computed(() => {
  if (!isDebugging.value) return '调试场景';
  if (isApiScene.value) return '调试中...';
  if (debugStatus.value === '运行中') {
    return pausePending.value ? '等待步骤完成后暂停…' : '暂停调试';
  }
  if (debugStatus.value === '失败挂起') {
    return `继续重试（${formatPauseCountdown(pauseCountdown.value)} 后关闭）`;
  }
  if (debugStatus.value === '暂停') {
    return `继续调试（${formatPauseCountdown(pauseCountdown.value)} 后关闭）`;
  }
  return '调试中...';
});

const startPauseCountdown = (deadlineMs: number, onTimeout: () => void) => {
  clearPauseTimer();
  const calc = () => Math.max(0, Math.floor((deadlineMs - Date.now()) / 1000));
  pauseCountdown.value = calc();
  pauseTimer = setInterval(() => {
    pauseCountdown.value = calc();
    if (pauseCountdown.value <= 0) {
      clearPauseTimer();
      onTimeout();
    }
  }, 1000);
};

const sendContinueRetry = () => {
  console.log('[UI Debug] 发送继续重试命令 r, ws.readyState=', ws.value?.readyState);
  if (ws.value != null && ws.value.readyState === WebSocket.OPEN) {
    ws.value.send('r');
  }
  isPausedOnFailure.value = false;
  pausedFailureStepId.value = null;
  clearPauseTimer();
  debugStatus.value = '运行中';
};

const sendTerminateDebug = () => {
  console.log('[UI Debug] 挂起超时，发送终止命令 q, ws.readyState=', ws.value?.readyState);
  if (ws.value != null) {
    const socket = ws.value;
    try {
      if (socket.readyState === WebSocket.OPEN) {
        socket.send('q');
      }
    } catch (e) {
      // WebSocket 状态异常时忽略，直接关闭连接
    }
    // 主动关闭连接，触发后端 afterConnectionClosed -> forceStop()，确保浏览器被关闭
    if (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CLOSING) {
      socket.close();
    }
  }
  isPausedOnFailure.value = false;
  pausedFailureStepId.value = null;
  pausePending.value = false;
  runUntilTargetId.value = null;
  isDebugging.value = false;
  debugStatus.value = '未运行';
  clearPauseTimer();
};

// 打开 Playwright Trace Viewer 回放调试 trace
const openTraceViewer = () => {
  if (!traceUrl.value) return;
  // traceUrl 存的是 fileId，拼接后端代理 URL（带 CORS 头，通用方案）
  const apiBase = import.meta.env.VITE_API_BASE_URL || `${window.location.protocol}//${window.location.host}/`;
  const traceFileUrl = `${apiBase.replace(/\/$/, '')}/api/file/trace?fileId=${encodeURIComponent(traceUrl.value)}`;
  window.open(`https://trace.playwright.dev/?trace=${encodeURIComponent(traceFileUrl)}`, '_blank');
};


// 当前操作的表单配置
const currentOperationFormConfig = ref();

// 当前表单校验规则
const currentSchema = ref<any>(null);


// 动态表单引用
const stepDetailFormRef = ref();

// 搜索的场景
const searchKey = ref('');

// 当前选择的场景（场景的id）
const selectedScene = ref<number | null>(0);

// 编辑/添加 步骤开关
const stepFormVisible = ref(false);

// 当前步骤详情
const currentStepDetail = ref<StepType>({});

// 控制流步骤不需要设置/断言/关联提取标签页（仅 API 场景）
const showSettingTab = computed(() => {
  if (!isApiScene.value) return true;
  const controlStepTypes = ['FOR', 'WHILE', 'IF', 'WAIT'];
  return !controlStepTypes.includes(currentStepDetail.value?.stepType);
});

// 对话框的状态（新建/编辑）
const modalMode = ref<string>('');

// 当前编辑的目录
const currentEditFolder = ref<Scene>({});

// 当前编辑的场景
const currentEditScene = ref<Scene>({});

// 场景树引用
const sceneTreeRef = ref();

// 场景表单开关
const sceneFormVisible = ref(false);
// 目录表单开关
const folderFormVisible = ref(false);

// 新增/编辑 场景表单 引用
const addOrUpdateSceneFormRef = ref();

// 新增/编辑 场景表单
const addOrUpdateSceneForm = ref<Scene>({});

// 导入场景表单
const importSceneForm = ref<any>({
  mode: 'mokatest-json',
  name: '',
  parentId: 0,
  description: ''
});
// 导入场景表单引用
const importSceneFormRef = ref();

// Mokatest JSON 导入文件相关
const importJsonFileList = ref<any[]>([]);
const importJsonSceneData = ref<any>(null);

// 导出场景弹窗相关
const exportSceneVisible = ref(false);
const exportSceneCheckedKeys = ref<number[]>([]);
const exportSceneTreeData = computed(() => {
  const addDisabled = (nodes: any[]): any[] => {
    return nodes.map(node => ({
      ...node,
      disableCheckbox: node.sceneType === 'FOLDER',
      children: node.children ? addDisabled(node.children) : undefined
    }));
  };
  return addDisabled(treeData.value || []);
});

// 导出弹窗：搜索关键词、展开节点
const exportSceneSearchKey = ref('');
const exportSceneExpandedKeys = ref<number[]>([]);

// 搜索过滤后的导出树（复用 searchData 过滤逻辑，disableCheckbox 标记随节点保留）
const filteredExportSceneTreeData = computed(() => {
  if (!exportSceneSearchKey.value) return exportSceneTreeData.value;
  return searchData(exportSceneSearchKey.value, exportSceneTreeData.value);
});

// 收集树中所有文件夹节点 id（用于默认/搜索时展开）
const collectFolderIds = (nodes: any[]): number[] => {
  const ids: number[] = [];
  (nodes || []).forEach(n => {
    if (n.children && n.children.length > 0) {
      ids.push(n.id);
      ids.push(...collectFolderIds(n.children));
    }
  });
  return ids;
};

// 搜索时自动展开所有命中的目录，保证匹配节点可见
watch(exportSceneSearchKey, () => {
  exportSceneExpandedKeys.value = collectFolderIds(filteredExportSceneTreeData.value);
});

// 搜索命中文字高亮
const highlightExportSceneName = (name: string) => {
  const kw = exportSceneSearchKey.value?.trim();
  if (!kw || !name) return name;
  const escaped = kw.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  return String(name).replace(
      new RegExp(`(${escaped})`, 'gi'),
      '<span style="color: rgb(var(--arcoblue-6)); font-weight: 600;">$1</span>'
  );
};

// 全选当前过滤结果下的所有场景节点（不含目录）
const exportSceneSelectAll = () => {
  const ids: number[] = [];
  const collect = (nodes: any[]) => {
    (nodes || []).forEach(n => {
      if (n.sceneType !== 'FOLDER') ids.push(n.id);
      if (n.children) collect(n.children);
    });
  };
  collect(filteredExportSceneTreeData.value);
  exportSceneCheckedKeys.value = ids;
};

//  新增/编辑 目录表单 引用
const addOrUpdateFolderFormRef = ref();


// 编辑场景撇只表单
const updateSceneConfigForm = ref<any>({setting: {}, sceneBrowserConfig: {}});

// API场景环境配置
const sceneEnvConfig = ref<RequestExecuteInfo>(new RequestExecuteInfo());
const envList = ref<Environment[]>([]);
const currentSceneEnv = ref<Environment>({});
const sceneEnvVariablesText = ref('');

// 场景环境选择（API/UI 场景共用，存 apiSceneConfig.environmentId）
const selectedEnvironmentId = ref<number | undefined>(undefined);
const selectedEnvironmentName = computed(() => {
  if (!selectedEnvironmentId.value) return '';
  const env = envList.value.find((e: any) => e.id === selectedEnvironmentId.value);
  return env?.envName || '';
});

// API场景级配置（场景Header、场景Cookie、场景变量、场景断言）
const sceneHeaderTableRef = ref();
const sceneCookieTableRef = ref();
const sceneVarTableRef = ref();
const sceneAssertTableRef = ref();
const sceneConfigData = ref<any>({
  sceneHeaders: [],
  sceneCookies: [],
  sceneVariables: [],
  sceneAssertions: []
});

// 新增/编辑 目录表单
const addOrUpdateFolderForm = ref<Scene>({});

// 场景配置开关
const sceneConfigFormVisible = ref(false);

// 导入场景弹窗开关
const importSceneVisible = ref(false);

// 录制导入内联面板引用
const recordImportPanelRef = ref<InstanceType<typeof RecordImportInline> | null>(null);

// MokatestRecord 模式下的录制步骤与解析警告
const recordSteps = ref<RecordStepDraft[]>([]);
const recordWarnings = ref<string[]>([]);


// 场景树列表
const sceneTree = ref<SceneVO[]>([]);

// 目录树列表
const folderList = ref<SceneVO[]>([]);

// 是否相邻步骤添加的标识
const isAdjacentStepAdd = ref<boolean>(false);

// 添加 相邻步骤 关联的步骤 （也就是给哪个步骤添加相邻id）
const adjacentTargetStepId = ref<string>('');

// 是否是添加子步骤类的相邻步骤
const isAddChildStep = ref<boolean>(false);

// 选中的树节点
const checkedStepNode = ref<number[]>([]);

// 导入步骤对话框可见性标识
const importStepDialogVisible = ref(false);
// 录制插件获取引导弹窗
const recorderGuideVisible = ref(false);

const clearAllSelectSceneInfo = () => {
  // 场景步骤列表清空
  sceneStepList.value = []
  // 当前场景清空
  currentEditScene.value = {};
  // 当前所选的目录清空
  currentEditFolder.value = {};
  // 顶部信息也清空
  topSceneInfo.value = {};
  // 清空勾选的数据
  checkedStepNode.value = [];
  // 搜索值清空
  searchKey.value = '';
  selectedScene.value = 0;
  // 默认展开
  isExpandAll.value = true;

}


const reloadSceneTree = async () => {
  const projectId = projectStore.getProjectId;
  const result = await getAllSceneList(projectId, props.sceneCategory);
  sceneTree.value = result.data;
}


const reloadFolderLIst = async () => {
  const projectId = projectStore.getProjectId;
  const result = await getFolderList(projectId, props.sceneCategory);
  folderList.value = result.data;
}


const reloadSelectedScene = async () => {
  if (selectedScene.value === 0) {
    // 如果selectedScene.value是0，就说明他是根目录
    return;
  }
  // 切换场景时清空调试结果，同一场景重新加载（保存/新增/排序）则保留
  const isSwitchingScene = currentEditScene.value?.id != null && currentEditScene.value.id !== selectedScene.value;
  if (isSwitchingScene) {
    stepDebugList.value = [];
  }
  // 重新加载场景时重置调试状态，避免步骤列表变更后出现所有步骤加载中；
  // 但在“失败挂起”等待重试/“手动暂停”等待继续期间保持状态，避免重试/继续按钮消失（切场景除外）
  if (!(isPausedOnFailure.value || debugStatus.value === '暂停') || isSwitchingScene) {
    isDebugging.value = false;
    debugStatus.value = '未运行';
    clearPauseTimer();
    pausePending.value = false;
    runUntilTargetId.value = null;
    isPausedOnFailure.value = false;
    pausedFailureStepId.value = null;
  }

  const {data} = await getSceneById(selectedScene.value);
  if (!data && data?.trim() != '') {
    Message.warning(`未查找到相关场景信息(sceneId:${selectedScene.value})，请确认场景是否被删除！`);
  }
  currentEditScene.value = data;
  // 获取步骤列表
  const result = await getStepList(selectedScene.value);

  sceneStepList.value = result.data;
  topSceneInfo.value = data;
  // 加载环境列表并初始化环境选择
  await initSceneEnvironment();
}

// 初始化场景环境选择
const initSceneEnvironment = async () => {
  await loadEnvironmentList();
  if (currentEditScene.value?.sceneSetting) {
    try {
      const setting = JSON.parse(currentEditScene.value.sceneSetting);
      selectedEnvironmentId.value = setting.apiSceneConfig?.environmentId || undefined;
    } catch (e) {
      selectedEnvironmentId.value = undefined;
    }
  }
}

const router = useRouter();
const route = useRoute();
onMounted(async () => {
  // 获取场景列表
  await reloadSceneTree();

  // 获取目录列表
  await reloadFolderLIst();


  if (route.query.selectSceneId) {
    selectedScene.value = Number(route.query.selectSceneId);
    // 如果跳转携带有参数，就展开
    isExpandAll.value = true;
    sceneTreeRef.value.expandAll(isExpandAll.value);
    await reloadSelectedScene();
  }

  // 初始化的时候，默认展开场景树
  handleSceneTreeExpand();
})

// 调试期间刷新/关闭页面时给出浏览器原生拦截提示
const handleBeforeUnload = (e: BeforeUnloadEvent) => {
  if (isDebugging.value) {
    e.preventDefault();
    e.returnValue = '';
  }
};

watch(isDebugging, (val) => {
  if (val) {
    window.addEventListener('beforeunload', handleBeforeUnload);
  } else {
    window.removeEventListener('beforeunload', handleBeforeUnload);
  }
}, { immediate: true });

// 调试期间通过 Vue Router 跳转时给出原生 confirm 拦截
onBeforeRouteLeave(() => {
  if (isDebugging.value) {
    const confirmed = window.confirm('当前正在调试场景，离开页面将终止调试，是否继续？');
    if (confirmed) {
      exit();
    }
    return confirmed;
  }
});

// 场景列表拖拽
const onDrop = async ({
                        dragNode,
                        dropNode,
                        dropPosition
                      }: { dragNode: any, dropNode: any, dropPosition: number }) => {

  const data: any[] = treeData.value;

  // 检查是否允许拖拽：如果都是 SCENE 类型且要作为子节点，不允许
  if (dropPosition === 0 && dragNode.sceneType === 'SCENE' && dropNode.sceneType === 'SCENE') {
    console.warn('SCENE 类型的节点不能作为其他 SCENE 节点的子节点');
    return;
  }

  // 如果拖拽节点id是0 or null 就直接返回
  if (!dragNode.id || dragNode.id === 0) {
    Message.warning('根节点无法拖拽');
    return;
  }

  // **问题1: loop函数返回值类型声明错误**
  const loop = (
      data: any[],
      key: number,
      callback: (item: any, index: number, arr: any[]) => void
  ): boolean => {  // 这里需要返回boolean
    return data.some((item, index, arr) => {
      if (item.id === key) {
        callback(item, index, arr);
        return true;
      }
      if (item.children && item.children.length > 0) {
        return loop(item.children, key, callback);
      }
      return false;
    });
  };

  // 深拷贝拖拽节点，避免引用问题
  const dragNodeCopy = JSON.parse(JSON.stringify(dragNode));

  // 从原位置移除拖拽节点
  const removed = loop(data, dragNodeCopy.id, (_, index, arr) => {
    arr.splice(index, 1);
  });

  if (!removed) {
    console.error('未找到要拖拽的节点');
    return;
  }

  // **问题2: 需要先找到拖拽位置再执行操作**
  let inserted = false;

  // 根据拖拽位置插入到新位置
  if (dropPosition === 0) {
    // 作为子节点插入
    inserted = loop(data, dropNode.id, (item) => {
      // 检查目标节点是否允许有子节点
      if (item.sceneType !== 'FOLDER') {
        console.warn('只有 FOLDER 类型的节点才能有子节点');
        return;
      }

      item.children = item.children || [];

      // 更新拖拽节点的父节点ID
      dragNodeCopy.parentId = item.id;

      // 设置 sort 为子节点数量 + 1（从1开始）
      dragNodeCopy.sort = item.children.length + 1;
      item.children.push(dragNodeCopy);
    });
  } else {
    // 作为同级节点插入
    inserted = loop(data, dropNode.id, (_, index, arr) => {
      const insertIndex = dropPosition < 0 ? index : index + 1;

      // 更新拖拽节点的父节点ID
      // **问题3: 需要找到正确的parentId**
      // 如果是根节点的children，parentId应该是0
      dragNodeCopy.parentId = dropNode.parentId;

      // 插入节点
      arr.splice(insertIndex, 0, dragNodeCopy);

      // 重新排序所有节点，sort 从1开始递增
      arr.forEach((node, idx) => {
        node.sort = idx + 1;
      });
    });
  }

  if (!inserted) {
    console.error('插入节点失败');
    // 可以考虑恢复原状
    return;
  }


  // **问题4: 需要递归更新所有节点的sort**
  // 添加一个函数来递归更新整棵树的sort
  const updateTreeSorts = (nodes: any[], parentId: number | null = null) => {
    nodes.forEach((node, index) => {
      node.sort = index + 1;
      node.parentId = parentId;

      if (node.children && node.children.length > 0) {
        updateTreeSorts(node.children, node.id);
      }
    });
  };

  // 更新整棵树的sort
  if (data[0]?.children) {
    updateTreeSorts(data[0].children, 0); // 根节点的parentId为0
  }

  const result = await updateSort(data[0].children);
  if (result.data === true) {
    Message.success("更新成功");
  } else {
    Message.error({
      content: "更新失败",
      duration: 1000
    })
  }
  await reloadSceneTree(projectStore.getProjectId);

  return;
};


// 是否允许释放
const isAllowDropRelease = (dropNode: TreeNodeData) => {
  // 如果节点是 "场景" 类型,则不允许拖拽
  if (dropNode.dropNode.sceneType == 'SCENE') {
    return false;
  }
  return true;
}

const sceneStepList = ref<StepVO[]>([])


// 搜索场景/目录的结果树
const treeData = computed(() => {
  let data = sceneTree.value;
  if (!searchKey.value) return data;
  return searchData(searchKey.value, data);
})

// 搜索的目录
const searchData = (keyword: any, sourceData?: any[]) => {
  const loop = (data: any) => {
    const result: any = [];
    data.forEach((item: any) => {
      if (item.name.indexOf(keyword) > -1) {
        result.push({...item});
      } else if (item.children) {
        const filterData = loop(item.children);
        if (filterData.length) {
          result.push({
            ...item,
            children: filterData
          })
        }
      }
    })
    return result;
  }
  return loop(sourceData || sceneTree.value);
}

// 当前是否全部展开
const isExpandAll = ref(true);
// 拖拽期间禁用节点 title tooltip，避免遮挡落点
const isDragging = ref(false);
// 控制每个节点 tooltip 的显隐，拖拽开始时强制全部隐藏
const tooltipVisibleMap = reactive<Record<string | number, boolean>>({});
watch(isDragging, (dragging) => {
  if (dragging) {
    Object.keys(tooltipVisibleMap).forEach((key) => {
      tooltipVisibleMap[key] = false;
    });
  }
});

// 场景树的展开
const handleSceneTreeExpand = () => {
  isExpandAll.value = !isExpandAll.value
  // 等待 DOM 更新后再执行
  nextTick(() => {
    if (sceneTreeRef.value) {
      sceneTreeRef.value.expandAll(!isExpandAll.value);
    }
  })
}

// 新建/编辑（目录）表单提交校验
const addFormRules = {
  parentId: [
    {
      required: true,
      message: '请选择父目录',
    },
  ],
  name: [
    {
      required: true,
      message: '请输入目录名称',
    },
  ]
};


// 编辑场景配置表单 提交检验规则
const updateSceneConfigFormRules = {
  'sceneBrowserConfig.browserType': [
    {
      required: true,
      message: '请选择浏览器类型',
    },
  ],
  'sceneBrowserConfig.runningType': [
    {
      required: true,
      message: '请选择运行类型',
    },
  ],
  'sceneBrowserConfig.windowMode': [
    {
      required: true,
      message: '请选择窗口模式',
    },
  ],
  'sceneBrowserConfig.windowSize': [
    {
      required: true,
      validator: (value: any, callback: any) => {
        // 如果设置了跳过验证，则直接通过
        if (updateSceneConfigForm.value.sceneBrowserConfig?.windowMode === 'MAXIMIZE') {
          return callback();
        }
        // 否则检查是否为空
        if (!value || value.trim() === '') {
          return callback('请输入尺寸');
        }


        if (!/^(?!0)\d{1,4}x(?!0)\d{1,4}$/.test(value)) {
          return callback('请输入合法的屏幕尺寸');
        }
        callback();
        callback();
      }
    }
  ],
  'setting.timeout': [
    {
      required: true,
      message: '请输入超时时间',
    },
    {
      validator: (value: any, callback: any) => {
        if (value === undefined || value === null || value === '') {
          return callback();
        }
        const num = Number(value);
        if (Number.isNaN(num) || num < 0) {
          return callback('超时时间必须大于等于 0');
        }
        callback();
      }
    }
  ],
  'setting.preExecuteWaitingTime': [
    {
      required: true,
      message: '请输入执行前等待时间',
    },
    {
      validator: (value: any, callback: any) => {
        if (value === undefined || value === null || value === '') {
          return callback();
        }
        const num = Number(value);
        if (Number.isNaN(num) || num < 0) {
          return callback('执行前等待时间必须大于等于 0');
        }
        callback();
      }
    }
  ],
  'setting.waitingTimeAfterExecution': [
    {
      required: true,
      message: '请输入执行后等待时间',
    },
    {
      validator: (value: any, callback: any) => {
        if (value === undefined || value === null || value === '') {
          return callback();
        }
        const num = Number(value);
        if (Number.isNaN(num) || num < 0) {
          return callback('执行后等待时间必须大于等于 0');
        }
        callback();
      }
    }
  ],
  'setting.screenshotConfiguration': [
    {
      required: true,
      message: '请选择截图策略',
    },
  ],
  'setting.errorHandlingStrategy': [
    {
      required: true,
      message: '请选择异常处理策略',
    },
  ],
};


// 新建/编辑(场景)表单提交校验
const addSceneFormRules = {
  parentId: [
    {
      required: true,
      message: '请选择父目录',
    },
  ],
  name: [
    {
      required: true,
      message: '请输入场景名称',
    },
  ],
  CrxData: [
    {
      required: true,
      message: '请输入Crx数据',
    },
  ]

};

// 导入场景表单校验
const importSceneFormRules = computed(() => ({
  parentId: [
    {
      required: true,
      message: '请选择父目录',
    },
  ],
  name: importSceneForm.value.mode === 'mokatest-json'
      ? []
      : [
        {
          required: true,
          message: '请输入场景名称',
        },
      ],
  CrxData: importSceneForm.value.mode === 'playwright'
      ? [
        {
          required: true,
          message: '请输入Crx数据',
        },
      ]
      : [],
}));

// Mokatest JSON 模式下解析到的场景数量
const importJsonSceneCount = computed(() => {
  if (!importJsonSceneData.value) return 0;
  return importJsonSceneData.value.scenes?.length || (importJsonSceneData.value.meta ? 1 : 0);
});

// 导入按钮是否可用
const canSubmitImport = computed(() => {
  if (importSceneForm.value.parentId == null) return false;
  if (importSceneForm.value.mode === 'mokatest-json') {
    return importJsonSceneCount.value > 0;
  }
  if (importSceneForm.value.mode === 'playwright') {
    return !!importSceneForm.value.CrxData?.trim();
  }
  if (importSceneForm.value.mode === 'platform') {
    return recordSteps.value.length > 0;
  }
  return false;
});

// 清空已上传的 JSON 文件
const clearImportJsonFile = () => {
  importJsonFileList.value = [];
  importJsonSceneData.value = null;
};

// 格式化文件大小
const formatFileSize = (size?: number): string => {
  if (size == null) return '';
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(2)} KB`;
  return `${(size / (1024 * 1024)).toFixed(2)} MB`;
};

// 取消新建/编辑 目录
const handleCancel = () => {
  folderFormVisible.value = false;
  addOrUpdateFolderFormRef.value.clearValidate();
  addOrUpdateFolderForm.value = {};
}

// 取消新建/编辑 场景
const handleSceneCancel = () => {
  sceneFormVisible.value = false;
  addOrUpdateSceneFormRef.value.clearValidate();
  addOrUpdateSceneForm.value = {};
}


// 添加/编辑 目录
const userAddOrUpdateFolder = (mode: string, folder: Scene | null) => {
  currentEditFolder.value = folder;
  modalMode.value = mode
  folderFormVisible.value = true;
  if (folder != null) {
    addOrUpdateFolderForm.value = folder;
  } else {
    addOrUpdateFolderForm.value.sceneType = 'FOLDER';
    addOrUpdateFolderForm.value.sceneCategory = props.sceneCategory;
  }
}

// 添加/编辑 场景
const userAddOrUpdateScene = (mode: string, scene: Scene | null = null) => {
  currentEditScene.value = scene;
  modalMode.value = mode
  sceneFormVisible.value = true;
  if (scene != null) {
    addOrUpdateSceneForm.value = scene;
  } else {
    addOrUpdateSceneForm.value.sceneType = 'SCENE';
    addOrUpdateSceneForm.value.sceneCategory = props.sceneCategory;
  }
}


// 添加子目录
const userAddSubFolder = (parentId: string) => {
  modalMode.value = '新建';
  folderFormVisible.value = true;
  addOrUpdateFolderForm.value.sceneType = 'FOLDER';
  addOrUpdateFolderForm.value.sceneCategory = props.sceneCategory;
  addOrUpdateFolderForm.value.parentId = parentId;
}

// 在目录下添加场景
const userAddSubScene = (parentId: string) => {
  modalMode.value = '新建';
  sceneFormVisible.value = true;
  addOrUpdateSceneForm.value.sceneType = 'SCENE';
  addOrUpdateSceneForm.value.sceneCategory = props.sceneCategory;
  addOrUpdateSceneForm.value.parentId = parentId;
}


// 确认添加目录
const handleFolderBeforeOk = async () => {
  const error = await addOrUpdateFolderFormRef.value?.validate();
  if (error) {
    return false;
  }

  let result;
  // 判断是否有id，如果有就是更新
  if (addOrUpdateFolderForm.value.id != null) {
    result = await updateScene(addOrUpdateFolderForm.value);
  } else {
    addOrUpdateFolderForm.value.projectId = projectStore.getProjectId;
    addOrUpdateFolderForm.value.createUserId = '1';
    addOrUpdateFolderForm.value.updateUserId = "1";
    result = await addScene(addOrUpdateFolderForm.value);
  }

  if (result.data === true) {
    Message.success({
      content: `${modalMode.value}成功`,
      duration: 1000
    })
    // 清空表单
    addOrUpdateFolderForm.value = {};
    await reloadSceneTree();
    await reloadFolderLIst();
  } else {
    Message.error({
      content: result.msg,
      duration: 1000
    })
  }
  return true;
};


// 确认添加场景
const handleSceneBeforeOk = async () => {
  const error = await addOrUpdateSceneFormRef.value?.validate();
  if (error) {
    return false;
  }

  let result;
  if (addOrUpdateSceneForm.value.id != null) {
    // id不为空代表是更新
    result = await updateScene(addOrUpdateSceneForm.value);
  } else { // 添加
    addOrUpdateSceneForm.value.projectId = projectStore.getProjectId;
    addOrUpdateSceneForm.value.createUserId = '1';
    addOrUpdateSceneForm.value.updateUserId = "1";
    result = await addScene(addOrUpdateSceneForm.value);
  }
  if (result.data === true) {
    Message.success({
      content: `${modalMode.value}成功`,
      duration: 1000
    })
    // 重新加载目录
    await reloadSceneTree();
    await reloadFolderLIst();
    // 清空表单
    addOrUpdateSceneForm.value = {};

  } else {
    Message.error({
      content: result.msg,
      duration: 1000
    })
  }
  return true;
}


// 过滤之后的选择目录
const selectableTreeNode = computed(() => {
  if (modalMode.value == '新建') return folderList.value;
  if (!currentEditFolder.value) return folderList.value;
  return filterNodeAndSubNode(currentEditFolder.value.id);
});

// 过滤目录节点及其子节点
const filterNodeAndSubNode = (nodeId: string) => {
  const deepCloneAndFilter = (node: any): any | null => {
    // 如果是目标节点，返回null表示过滤掉
    if (node.id === nodeId) return null;

    // 创建新节点（深拷贝）
    const newNode = {...node};

    // 递归处理子节点
    if (node.children?.length) {
      const filteredChildren = node.children
          .map(deepCloneAndFilter)
          .filter((child: any) => child !== null);

      newNode.children = filteredChildren.length ? filteredChildren : undefined;
    }

    return newNode;
  };

  // 处理根节点是目标的情况
  if (folderList.value[0]?.id === nodeId) return [];

  // 处理普通情况
  const filteredRoot = deepCloneAndFilter(folderList.value[0]);
  return filteredRoot ? [filteredRoot] : [];
};


// 删除目录的确认框
const handleFolderDelete = async (deleteId: string) => {
  Modal.warning({
    title: '确认删除？',
    content: () => '删除后，该目录及子目录下的场景将不再可见。',
    cancelText: '取消',
    okText: '确认',
    hideCancel: false,
    onOk: async () => {
      await deleteFolderOrScene(deleteId);
    }
  });
};


// 删除场景的确认框
const handleSceneDelete = async (deleteId: string) => {
  Modal.warning({
    title: '确认删除？',
    content: () => '确认删除该场景吗？',
    cancelText: '取消',
    okText: '确认',
    hideCancel: false,
    onOk: async () => {
      await deleteFolderOrScene(deleteId);
    }
  });
};


// 删除目录 or 场景
const deleteFolderOrScene = async (id: string) => {

  // 清空已选择节点的信息数据
  const clearSelectedSceneOrFolder = () => {
    currentEditFolder.value = treeData.value[0];
    currentEditScene.value = {};
    selectedScene.value = 0;
  }
  const result = await deleteScene(id);
  if (result.data === true) {
    Message.success({
      content: '删除成功',
      duration: 1000
    })
    clearSelectedSceneOrFolder();

    // 重新加载目录和场景
    await reloadSceneTree();
    await reloadFolderLIst();
    await reloadSelectedScene();

  } else {
    Message.error({
      content: result.msg,
      duration: 1000
    })
  }


}


// 场景信息备份
const topSceneInfo = ref<Scene>({});

// 选择场景
const selectScene = async (data: any, selectedNodes: TreeNodeData) => {
  // 切换场景前，如果正在调试，提示用户终止调试
  if (isDebugging.value) {
    const confirmed = window.confirm('当前正在调试场景，切换场景将终止调试，是否继续？');
    if (!confirmed) {
      return;
    }
    await exit();
  }
  selectedScene.value = data[0];
  if (selectedNodes.node != null && selectedNodes.node.sceneType === 'FOLDER') {
    currentEditFolder.value = selectedNodes.node;
    // 将当前步骤列表直接清空
    sceneStepList.value = [];
    // 将当前场景清空
    currentEditScene.value = {} as Scene;
    topSceneInfo.value = {} as Scene;
  } else if (selectedNodes.node != null && selectedNodes.node.sceneType === 'SCENE') {
    // 将当前文件清空
    currentEditFolder.value = {} as Scene;

    currentEditScene.value = selectedNodes.node;
    topSceneInfo.value = {...selectedNodes.node};
    // 获取步骤列表
    try {
      const result = await getStepList(currentEditScene.value.id);
      sceneStepList.value = result?.data || [];
    } catch (e) {
      console.error('获取步骤列表失败', e);
      sceneStepList.value = [];
    }
    // 加载环境列表并初始化环境选择
    await initSceneEnvironment();
  }
}

// 添加步骤
const addOperation = (config: any, schema: any, stepType: any) => {
  if (mutationsLocked.value) {
    Message.warning('调试运行中，暂停后可新增步骤');
    return;
  }
  stepFormVisible.value = true;
  currentOperationFormConfig.value = config;
  currentSchema.value = schema;
  currentStepDetail.value = createStep(stepType);
}


// 添加相邻步骤
const addAdjacentStep = (step: TestStep, stepType: any, isChildren: boolean, source?: 'new' | 'import') => {
  if (mutationsLocked.value) {
    Message.warning('调试运行中，暂停后可新增步骤');
    return;
  }
  // API/SQL 步骤走「新建/引入」拆分入口
  if (stepType === 'api_request' || stepType === 'sql') {
    addApiStep(stepType === 'sql' ? SqlRequest : ApiRequest, null, stepType, source || 'new', {step, isChildren});
    return;
  }
  stepFormVisible.value = true;
  // 将stepType转为大写
  currentOperationFormConfig.value = getStepConfig(stepType.toUpperCase());
  currentStepDetail.value = createStep(stepType);
  // 判断是否是添加子步骤
  if (isChildren) {
    // 如果是子步骤就得关联parentId
    currentStepDetail.value.parentId = step.id;
  } else {
    currentStepDetail.value.parentId = step.parentId;
  }
  // 改变标识
  isAdjacentStepAdd.value = true;
  adjacentTargetStepId.value = step.id;
  isAddChildStep.value = isChildren;
}

// 清空相邻步骤相关信息。重要：此方法保证 相邻步骤 取消保存之后，通过底部新增步骤时，不会触发相邻步骤的保存逻辑
const clearAdjacentStepInfo = () => {
  isAdjacentStepAdd.value = false;
  adjacentTargetStepId.value = '';
  isAddChildStep.value = false;
}


// ===== API/SQL 步骤：「新建 / 引入已有」入口拆分 =====
// 引入选择器
const apiSourcePickerVisible = ref(false);
const apiSourcePickerType = ref<'HTTP' | 'SQL'>('HTTP');
// 引入模式挂起的上下文：选定来源后继续打开步骤抽屉
const pendingApiStepContext = ref<{
  config: any;
  schema: any;
  stepType: any;
  adjacent?: { step: TestStep; isChildren: boolean };
} | null>(null);

// 将来源数据（新建模板 / 引入副本）注入步骤详情
const applyApiStepSource = (detail: any, sourceData: ApiStepSourceData) => {
  detail.apiRequestId = sourceData.apiRequestId;
  detail.apiName = sourceData.apiName;
  detail.apiConfig = sourceData.apiConfig;
  if (!detail.stepName || !String(detail.stepName).trim()) {
    detail.stepName = sourceData.apiName;
  }
};

// 打开 API/SQL 步骤抽屉（可携带来源数据；adjacent 存在时走相邻步骤新增语义）
const openApiStepDrawer = (config: any, schema: any, stepType: any, sourceData: ApiStepSourceData | null, adjacent?: {
  step: TestStep;
  isChildren: boolean
}) => {
  if (adjacent) {
    stepFormVisible.value = true;
    currentOperationFormConfig.value = getStepConfig(String(stepType).toUpperCase());
    currentStepDetail.value = {...createStep(stepType)};
    // 判断是否是添加子步骤
    if (adjacent.isChildren) {
      currentStepDetail.value.parentId = adjacent.step.id;
    } else {
      currentStepDetail.value.parentId = adjacent.step.parentId;
    }
    isAdjacentStepAdd.value = true;
    adjacentTargetStepId.value = adjacent.step.id;
    isAddChildStep.value = adjacent.isChildren;
  } else {
    stepFormVisible.value = true;
    currentOperationFormConfig.value = config;
    currentSchema.value = schema;
    currentStepDetail.value = {...createStep(stepType)};
  }
  if (sourceData) {
    applyApiStepSource(currentStepDetail.value, sourceData);
  }
};

// API/SQL 步骤入口：source='new' 带空白模板直接开抽屉；source='import' 先弹来源选择器
const addApiStep = (config: any, schema: any, stepType: any, source: 'new' | 'import', adjacent?: {
  step: TestStep;
  isChildren: boolean
}) => {
  if (mutationsLocked.value) {
    Message.warning('调试运行中，暂停后可新增步骤');
    return;
  }
  if (source === 'import') {
    apiSourcePickerType.value = stepType === 'sql' ? 'SQL' : 'HTTP';
    pendingApiStepContext.value = {config, schema, stepType, adjacent};
    apiSourcePickerVisible.value = true;
    return;
  }
  openApiStepDrawer(config, schema, stepType, stepType === 'sql' ? buildNewSqlStepData() : buildNewHttpStepData(), adjacent);
};

// 来源选择器确认：带着引入副本继续打开抽屉
const handleApiSourcePicked = (apiDetail: any) => {
  const ctx = pendingApiStepContext.value;
  pendingApiStepContext.value = null;
  if (!ctx) return;
  openApiStepDrawer(ctx.config, ctx.schema, ctx.stepType, buildImportedStepData(apiDetail), ctx.adjacent);
};


// 当前完整步骤信息
const currentStep = ref<TestStep>({} as TestStep);


// 添加/编辑步骤
const handleSubmit = async (stepDetail: any) => {
  // 调试锁定兜底：运行中禁止一切修改；暂停/失败挂起仅未执行步骤可保存
  if (mutationsLocked.value || isStepLocked(currentStep.value?.id)) {
    Message.warning('调试中，该步骤不可修改');
    return;
  }

  let testStep = new TestStep();
  testStep.stepName = stepDetail.stepName;
  testStep.stepDetail = stepDetail.stepDetail;
  testStep.stepType = stepDetail.stepType;
  testStep.parentId = stepDetail.parentId;
  // 详情中剔除parentId（为什么剔除？因为拖拽的时候只会修改testStep的parentId，不会修改StepDetail大json里边的parentId,这就会导致，进行拖拽操作之后，在对步骤进行编辑保存，顺序就会出错）
  delete stepDetail.parentId;
  testStep.stepDetail = stepDetail;

  // 判断是否是新增
  if (currentStep.value.id != null) {
    // 有id就是更新
    testStep.id = currentStep.value.id;
    testStep.projectId = currentStep.value.projectId;
    testStep.scenarioId = currentStep.value.scenarioId;
    const result = await updateStep(testStep);
    if (result.data) {
      Message.success({
        content: '更新成功',
        duration: 1000
      })
      // 重新加载步骤列表
      const result = await getStepList(currentEditScene.value.id);
      sceneStepList.value = result.data;

    }
  } else {
    // 没有id就是新增
    testStep.projectId = projectStore.getProjectId;
    testStep.scenarioId = currentEditScene.value.id;

    // 判断是否为相邻步骤新增
    if (!isAdjacentStepAdd.value) {
      const result = await addStep(testStep);
      // 插入成功，重新加载步骤列表
      if (result.data) {
        Message.success({
          content: '添加成功',
          duration: 1000
        })
        // 重新加载步骤列表
        const result = await getStepList(currentEditScene.value.id);
        sceneStepList.value = result.data;
      }
    } else {
      const addAdjacentStepDTO = new AddAdjacentStepDTO();
      addAdjacentStepDTO.addStep = testStep;
      addAdjacentStepDTO.isChildren = isAddChildStep.value;
      addAdjacentStepDTO.targetStepId = adjacentTargetStepId.value;
      const result = await addAdjacentTestStep(addAdjacentStepDTO);
      // 插入成功，重新加载步骤列表
      if (result.data) {
        Message.success({
          content: '添加成功',
          duration: 1000
        })
        // 重新加载步骤列表
        const result = await getStepList(currentEditScene.value.id);
        sceneStepList.value = result.data;
        // 重置添加相邻步骤标识信息
        clearAdjacentStepInfo();
      }
    }

  }

}


// 顶部场景信息更新
const handleTopSceneInfoUpdate = async () => {
  // 校验空信息
  if (topSceneInfo.value.name == null || topSceneInfo.value.name.trim() === '') {
    Message.error({
      content: '场景名称不能为空',
      duration: 1000
    })
    // 重新赋值
    topSceneInfo.value = {...currentEditScene.value};
    return;
  }
  let result = await updateScene(topSceneInfo.value);
  if (result.data === true) {
    Message.success({
      content: `${modalMode.value}成功`,
      duration: 1000
    })
    // 重新加载目录
    await reloadSceneTree();
    await reloadFolderLIst();
    // 清空表单
    addOrUpdateSceneForm.value = {};

  } else {
    Message.error({
      content: result.msg,
      duration: 1000
    })
  }
  return true;

}


const ws = ref();

// API场景调试（WebSocket 实时推送）
const startApiDebug = async (command?: string) => {
  if (sceneStepList.value.length === 0) {
    Message.warning({
      content: '当前场景步骤为空，请先添加步骤',
      duration: 1000
    })
    return;
  }

  isDebugging.value = true;
  stepDebugList.value = [];

  // 重置 UI 场景挂起状态，防止状态串到 API 调试
  isPausedOnFailure.value = false;
  pausedFailureStepId.value = null;
  clearPauseTimer();

  // 默认整场景执行；可传入 from_/only_ 命令实现「从此处执行」「单步执行」
  const startCommand = command || ('start_' + currentEditScene.value.id);

  const apiBase = import.meta.env.VITE_API_BASE_URL || `${window.location.protocol}//${window.location.host}/`;
  const wsBase = apiBase.replace(/^http/, 'ws').replace(/\/$/, '');
  ws.value = new WebSocket(`${wsBase}/api/ws/apiSceneDebug`);

  ws.value.onopen = function () {
    ws.value.send(startCommand);
  };

    let pendingUpdate: any = null;
    let debounceTimer: any = null;

    ws.value.onmessage = function (event: any) {
      // 先解析消息形态：步骤结果是 JSON 对象；控制消息是 JSON 编码的纯字符串。
      // 失败步骤结果的 errorMessage 含「执行异常」字样，若先用 includes 判控制消息会把步骤结果误判为会话结束并丢弃
      let parsedMsg: any = null;
      try {
        parsedMsg = JSON.parse(event.data);
      } catch {
        parsedMsg = null;
      }
      const isStepResultMsg = parsedMsg && typeof parsedMsg === 'object';

      // 处理文本消息（启动/结束通知）
      if (!isStepResultMsg && typeof event.data === 'string') {
        if (event.data.includes('启动成功')) {
          return;
        }
        // 登录失效或无权限，触发一次 HTTP 登录检查（拦截器会自动跳转登录页）
        if (event.data.includes('启动失败：未获取到登录信息') || event.data.includes('启动失败：无权调试该场景')) {
          getUserInfo().catch(() => {});
          isDebugging.value = false;
          debugStatus.value = '未运行';
          return;
        }
        if (event.data.includes('执行结束') || event.data.includes('执行异常')) {
          // 先 flush 待处理的 debounce 更新
          if (debounceTimer) {
            clearTimeout(debounceTimer);
            debounceTimer = null;
          }
          if (pendingUpdate) {
            applyDebugUpdate(pendingUpdate);
            pendingUpdate = null;
          }
          const allSuccess = stepDebugList.value.every((s: any) => s.result?.status === 'SUCCESS');
          if (event.data.includes('执行异常')) {
            Message.error({ content: 'API场景调试异常：' + event.data, duration: 2000 });
          } else if (allSuccess) {
            Message.success({ content: 'API场景调试完成，全部通过', duration: 2000 });
          } else {
            Message.error({ content: 'API场景调试完成，存在失败步骤', duration: 2000 });
          }
          isDebugging.value = false;
          debugStatus.value = '未运行';
          return;
        }
        // 未识别的字符串消息直接忽略
        return;
      }

      // 非 JSON 或空消息不处理
      if (!parsedMsg) return;

      const item = parsedMsg;
      // 递归映射后端结果为前端统一格式（含 childrenResults）
      const mapApiResult = (raw: any): any => ({
        step: { id: raw.stepId, stepName: raw.stepName, stepType: raw.stepType || 'API_REQUEST' },
        result: {
          status: raw.status,
          errorMessage: raw.errorMessage,
          timeConsuming: raw.timeConsuming,
          response: raw.response,
          assertionResults: raw.assertionResults,
          extractedVariables: raw.extractedVariables
        },
        childrenResults: (raw.childrenResults || []).map(mapApiResult)
      });
      pendingUpdate = mapApiResult(item);

      if (debounceTimer) clearTimeout(debounceTimer);
      debounceTimer = setTimeout(() => {
        if (pendingUpdate) {
          applyDebugUpdate(pendingUpdate);
          pendingUpdate = null;
        }
      }, 50);
    };

    function applyDebugUpdate(mapped: any) {
      // 同一 stepId 已存在则替换（用于控制流步骤的增量更新），否则追加
      const existingIndex = stepDebugList.value.findIndex((s: any) => s.step?.id == mapped.step?.id);
      if (existingIndex >= 0) {
        const updated = [...stepDebugList.value];
        updated[existingIndex] = mapped;
        stepDebugList.value = updated;
      } else {
        stepDebugList.value = [...stepDebugList.value, mapped];
      }
    }

  ws.value.onerror = function (event: any) {
    // 连接异常，可能是登录失效，触发一次 HTTP 登录检查（拦截器会自动跳转登录页）
    getUserInfo().catch(() => {});
    Message.error({
      content: 'API场景调试连接异常',
      duration: 2000
    });
    isDebugging.value = false;
    debugStatus.value = '未运行';
  };

  ws.value.onclose = function () {
    isDebugging.value = false;
    debugStatus.value = '未运行';
  };
}

// 从指定步骤开始执行（含该步骤到末尾）
const runApiSceneFromStep = (step: any) => {
  if (isDebugging.value) return;
  if (!step?.id || !currentEditScene.value?.id) return;
  Message.info({ content: `从步骤「${step.stepName}」开始执行`, duration: 1500 });
  startApiDebug(`from_${currentEditScene.value.id}_${step.id}`);
}

// 仅执行单个步骤
const runApiSceneSingleStep = (step: any) => {
  if (isDebugging.value) return;
  if (!step?.id || !currentEditScene.value?.id) return;
  Message.info({ content: `仅执行步骤「${step.stepName}」`, duration: 1500 });
  startApiDebug(`only_${currentEditScene.value.id}_${step.id}`);
}

// 场景调试
// startCommand 可指定启动命令，如 start_<sceneId>_until_<stepId>（执行到指定步骤前自动暂停）
// ==================== AI 定位自愈建议 ====================
/** 自愈建议聚合列表：一轮调试可能命中多条，统一在弹窗中批量处理，不挨个弹窗 */
const healSuggestions = ref<any[]>([]);
const healModalVisible = ref(false);

/** 收到自愈建议：入列表 + 轻提示，入口在调试栏「AI 修复建议」按钮 */
const showHealSuggestion = (msg: any) => {
  healSuggestions.value.push({...msg, status: 'pending', applying: false});
  Message.success({
    content: `AI 已自动修复步骤「${msg.stepName || '#' + msg.stepId}」的定位，可在调试栏「AI 修复建议」中处理`,
    duration: 4000,
  });
};

/** 待处理建议总数（角标计数） */
const pendingHealCount = computed(() => healSuggestions.value.filter(s => s.status === 'pending').length);

/** 待处理且可采纳的建议（库选写回元素库，自定义写回步骤） */
const pendingLibrarySuggestions = computed(() =>
  healSuggestions.value.filter(s => s.status === 'pending' && (s.elementId || s.stepId)));

/** 采纳单条建议：库选写回元素库（全局影响，二次确认），自定义写回步骤的 customLocator */
const applyHealSuggestion = async (item: any) => {
  if (item.locatorSource === 'CUSTOM' || !item.elementId) {
    return applyCustomHealSuggestion(item);
  }
  // 库选元素被所有引用它的步骤共享，写回前二次确认
  Modal.confirm({
    title: '写回元素库确认',
    content: `元素「${item.elementName || '#' + item.elementId}」可能被多个场景的步骤引用，写回后所有引用该元素的步骤都将使用新定位。确认继续？`,
    okText: '确认写回',
    cancelText: '再想想',
    onOk: () => applyLibraryHealSuggestion(item),
  });
};

/** 执行库选元素写回（已确认） */
const applyLibraryHealSuggestion = async (item: any) => {
  item.applying = true;
  try {
    const result: any = await getElementById(item.elementId);
    const el = result.data;
    if (!el) {
      Message.warning({content: '元素已被删除，无法写回', duration: 3000});
      item.status = 'ignored';
      return;
    }
    await updateElement({
      ...el,
      locatorType: String(item.newType || '').toLowerCase(),
      locatorValue: item.newValue,
    });
    item.status = 'applied';
    Message.success({content: `已将新定位写回元素库（${item.elementName || '元素 #' + item.elementId}）`, duration: 2500});
  } catch (e) {
    Message.error({content: '写回元素库失败，请稍后重试或手动修改', duration: 3000});
  } finally {
    item.applying = false;
  }
};

/**
 * 采纳自定义定位建议：拉步骤详情 → 替换 stepDetail 中的 customLocator → updateStep。
 * 调试运行中步骤受写保护，后端会拦截并提示，此时引导用户调试结束后重试。
 */
const applyCustomHealSuggestion = async (item: any) => {
  item.applying = true;
  try {
    const result: any = await getStepDetail(item.stepId);
    const step = result.data;
    const detail = JSON.parse(step.stepDetail);
    if (!detail.element) {
      detail.element = {locator: {}, customLocator: {}, locatorSource: 'CUSTOM'};
    }
    detail.element.customLocator = {
      ...(detail.element.customLocator || {}),
      locatorType: String(item.newType || '').toLowerCase(),
      locatorValue: item.newValue,
    };
    detail.element.locatorSource = 'CUSTOM';
    await updateStep({...step, stepDetail: detail});
    item.status = 'applied';
    Message.success({content: `已将新定位写回步骤「${item.stepName || '#' + item.stepId}」`, duration: 2500});
    // 刷新步骤列表，让步骤行展示最新定位
    reloadSelectedScene();
  } catch (e: any) {
    const errMsg = e?.response?.data?.message || '';
    if (errMsg.includes('调试')) {
      Message.warning({content: '调试运行中步骤受写保护，请在调试结束后再采纳', duration: 3500});
    } else {
      Message.error({content: '写回步骤失败，请稍后重试或手动修改', duration: 3000});
    }
  } finally {
    item.applying = false;
  }
};

/** 全部采纳：含库选元素时先弹一次汇总确认（全局影响），确认后逐条写回不再重复确认。
 *  返回 true 表示可以直接关闭建议弹窗；false 表示已弹二次确认，建议弹窗保持打开 */
const applyAllHealSuggestions = async (): Promise<boolean> => {
  const items = [...pendingLibrarySuggestions.value];
  const libraryCount = items.filter(i => i.locatorSource !== 'CUSTOM' && i.elementId).length;
  const doApplyAll = async () => {
    for (const item of items) {
      if (item.locatorSource === 'CUSTOM' || !item.elementId) {
        await applyCustomHealSuggestion(item);
      } else {
        await applyLibraryHealSuggestion(item);
      }
    }
  };
  if (libraryCount > 0) {
    Modal.confirm({
      title: '批量写回确认',
      content: `即将采纳 ${items.length} 条建议，其中 ${libraryCount} 条会写回元素库，所有引用这些元素的步骤都将使用新定位。确认继续？`,
      okText: '确认全部采纳',
      cancelText: '再想想',
      onOk: doApplyAll,
    });
    return false;
  }
  await doApplyAll();
  return true;
};

/** 忽略单条 */
const ignoreHealSuggestion = (item: any) => {
  item.status = 'ignored';
};

const startDebug = async (startCommand?: string) => {
  // 工作流视图有未保存节点时拦截
  if (stepListShowModel.value === 'workflow' && hasDirtyWorkflowNodes.value) {
    Message.warning({
      content: '工作流视图中有未保存的节点修改，请先保存后再调试',
      duration: 2000
    });
    return;
  }

  // 判断当前场景步骤是否为空
  if (sceneStepList.value.length === 0) {
    Message.warning({
      content: '当前场景步骤为空，请先添加步骤',
      duration: 1000
    })
    return;
  }

  // API场景走HTTP调试
  if (isApiScene.value) {
    return await startApiDebug();
  }

  // 清空上一次调试的 trace URL
  traceUrl.value = '';

  isDebugging.value = true;

  // 清空调试结果列表
  stepDebugList.value = [];

  // 清空上一轮调试遗留的 AI 修复建议
  healSuggestions.value = [];

  // 重置挂起重试状态，防止旧状态残留
  isPausedOnFailure.value = false;
  pausedFailureStepId.value = null;
  pausePending.value = false;
  clearPauseTimer();

  const apiBase = import.meta.env.VITE_API_BASE_URL || `${window.location.protocol}//${window.location.host}/`;
  const wsBase = apiBase.replace(/^http/, 'ws').replace(/\/$/, '');
  ws.value = new WebSocket(`${wsBase}/api/ws/debug`);

  ws.value.onopen = function () {
    ws.value.send(startCommand || ('start_' + currentEditScene.value.id));
    debugStatus.value = '运行中'
  };

  ws.value.onmessage = function (event: any) {
    // 先解析消息形态：步骤结果是 JSON 对象（含 step 字段）；控制消息是 JSON 编码的纯字符串。
    // 失败步骤结果的 errorMessage 含「执行异常/执行失败」字样，若先用 includes 判控制消息，
    // 会把该步骤结果误判为「会话结束」直接丢弃，导致失败挂起时失败步骤一直显示加载中
    let parsedMsg: any = null;
    try {
      parsedMsg = JSON.parse(event.data);
    } catch {
      parsedMsg = null;
    }
    const isStepResultMsg = parsedMsg && typeof parsedMsg === 'object' && parsedMsg.step;

    // ============ 控制消息（纯字符串） ============
    if (!isStepResultMsg) {
      // AI 定位自愈建议（JSON 对象消息）：步骤定位失败时后端 AI 推断并验证了新定位器
      if (parsedMsg && parsedMsg.type === 'HEAL_SUGGESTED') {
        showHealSuggestion(parsedMsg);
        return;
      }

      // 登录失效或无权限，触发一次 HTTP 登录检查（拦截器会自动跳转登录页）
      if (typeof event.data === 'string' && (event.data.includes('启动失败：未获取到登录信息') || event.data.includes('启动失败：无权调试该场景'))) {
        getUserInfo().catch(() => {});
        pausePending.value = false;
        isDebugging.value = false;
        debugStatus.value = '未运行';
        return;
      }

      // UI 场景调试手动暂停：进入倒计时等待用户点击继续调试
      if (typeof event.data === 'string' && event.data.includes('PAUSED:')) {
        const rawText = event.data.replace(/^"|"$/g, '');
        const parts = rawText.split(':');
        if (parts.length >= 2) {
          const deadlineMs = Number(parts[1]);
          console.log('[UI Debug] 收到 PAUSED, deadlineMs=', deadlineMs);
          pausePending.value = false;
          if (runUntilTargetId.value != null) {
            Message.success({content: '已执行到目标步骤并暂停', duration: 2000});
            runUntilTargetId.value = null;
          }
          isDebugging.value = true;
          debugStatus.value = '暂停';
          // 手动暂停超时后直接终止调试
          startPauseCountdown(deadlineMs, sendTerminateDebug);
        }
        return;
      }

      // UI 场景调试失败挂起：进入倒计时等待用户点击继续重试
      if (typeof event.data === 'string' && event.data.includes('PAUSED_ON_FAILURE:')) {
        const rawText = event.data.replace(/^"|"$/g, '');
        const parts = rawText.split(':');
        if (parts.length >= 3) {
          const stepId = Number(parts[1]);
          const deadlineMs = Number(parts[2]);
          console.log('[UI Debug] 收到 PAUSED_ON_FAILURE, stepId=', stepId, 'deadlineMs=', deadlineMs);
          pausePending.value = false;
          isPausedOnFailure.value = true;
          pausedFailureStepId.value = stepId;
          isDebugging.value = true;
          debugStatus.value = '失败挂起';
          // 失败挂起超时后直接终止调试，不再自动重试
          startPauseCountdown(deadlineMs, sendTerminateDebug);
        }
        return;
      }

      // 重试失败（超时或步骤已变更）
      if (typeof event.data === 'string' && event.data.includes('RETRY_FAILED:')) {
        const rawText = event.data.replace(/^"|"$/g, '');
        Message.error({ content: rawText, duration: 3000 });
        clearPauseTimer();
        pausePending.value = false;
        isPausedOnFailure.value = false;
        pausedFailureStepId.value = null;
        isDebugging.value = false;
        debugStatus.value = '未运行';
        return;
      }

      // 暂停期间步骤发生变更，后端热加载并从锚点重跑：清空旧调试结果，等待新结果流入
      if (typeof event.data === 'string' && event.data.includes('STEPS_RELOADED')) {
        stepDebugList.value = [];
        Message.info({ content: '检测到步骤变更，已从断点重新加载步骤并继续执行', duration: 3000 });
        return;
      }

      // Trace 回放 URL：调试结束后后端推送 trace.zip 下载地址
      if (typeof event.data === 'string' && event.data.includes('TRACE:')) {
        const rawText = event.data.replace(/^"|"$/g, '');
        traceUrl.value = rawText.substring('TRACE:'.length);
        return;
      }

      // 执行结束或异常/失败：重置调试状态，避免按钮一直显示
      if (typeof event.data === 'string' && (event.data.includes('执行结束') || event.data.includes('执行异常') || event.data.includes('执行失败'))) {
        console.log('[UI Debug] 收到执行结束类消息:', event.data);
        pausePending.value = false;
        runUntilTargetId.value = null;
        isDebugging.value = false;
        debugStatus.value = '未运行';
        isPausedOnFailure.value = false;
        pausedFailureStepId.value = null;
        clearPauseTimer();
        return;
      }

      // 启动成功等系统消息及未识别字符串直接忽略
      return;
    }

    // ============ 步骤结果消息 ============
    const stepResult = parsedMsg;

    // 混合场景：API/SQL 步骤的响应 VO 后端放在 result.apiResponse，
    // 归一化到 result.response 供 ApiStepDebugResult/ApiDebugResult 直接渲染
    if ((stepResult?.step?.stepType === 'API_REQUEST' || stepResult?.step?.stepType === 'SQL')
        && stepResult?.result?.apiResponse
        && !stepResult.result.response) {
      stepResult.result.response = stepResult.result.apiResponse;
    }

    let updatedList = [...stepDebugList.value]; // 创建副本

    if (updatedList.length > 0) {
      const re = replaceOrPush(updatedList, stepResult);
      if (!re) {
        updatedList = [...updatedList, stepResult];
      }
    } else {
      updatedList = [stepResult];
    }
    // 赋值新数组，确保子组件能检测到变化
    stepDebugList.value = updatedList;

    function replaceOrPush(
        stepList: any[],
        newData: any
    ): boolean {
      // 1. 遍历当前列表进行查找
      for (let i = 0; i < stepList.length; i++) {
        const currentItem = stepList[i];
        // 检查当前项的ID是否匹配
        if (currentItem.step.id === newData.step.id) {
          // 简单替换 result 字段：
          stepList[i] = null;
          stepList[i] = newData;
          return true; // 替换成功，结束
        }
      }
      // 3. 遍历结束后，如果没有找到，则返回 false
      return false;
    }
  };


  ws.value.onerror = function (event: any) {
    // 连接异常，可能是登录失效，触发一次 HTTP 登录检查（拦截器会自动跳转登录页）
    getUserInfo().catch(() => {});
    pausePending.value = false;
    runUntilTargetId.value = null;
    isDebugging.value = false;
    debugStatus.value = '未运行';
    isPausedOnFailure.value = false;
    pausedFailureStepId.value = null;
    clearPauseTimer();

  };
  ws.value.onclose = function () {
    console.log('[UI Debug] WebSocket 关闭');
    pausePending.value = false;
    runUntilTargetId.value = null;
    isDebugging.value = false;
    debugStatus.value = '未运行';
    isPausedOnFailure.value = false;
    pausedFailureStepId.value = null;
    clearPauseTimer();
  };
}


const changeDebugStatus = async () => {
  // 失败挂起状态下点击主按钮，发送继续重试命令
  if (isPausedOnFailure.value) {
    return sendContinueRetry();
  }
  // API场景调试中，不支持暂停/继续
  if (isApiScene.value && isDebugging.value) {
    return;
  }
  if (!isDebugging.value && debugStatus.value === '未运行') {
    return await startDebug();
  }
  if (isDebugging.value && debugStatus.value === '运行中') {
    // 暂停请求已提交，等待当前步骤执行完成，避免重复发送
    if (pausePending.value) return;
    return await pause();
  }
  if (isDebugging.value && debugStatus.value === '暂停') {
    return await continu();
  }
}


const pause = async () => {
  if (ws.value != null) {
    ws.value.send('p');
    // 边界暂停：步骤执行完才生效，先提示等待，等后端 PAUSED 回执再切状态
    pausePending.value = true;
    Message.info({content: '暂停请求已发送，将在当前步骤执行完成后暂停', duration: 2500});
  }
}


const continu = async () => {
  if (ws.value != null) {
    ws.value.send('c');
    debugStatus.value = '运行中'
  }
}

// 执行到此步骤：未调试时从头执行并停在目标步骤前；暂停/失败挂起中继续执行到目标步骤前再停
const runUntilStep = async (step: any) => {
  if (!step?.id || !currentEditScene.value?.id) return;
  if (!isDebugging.value) {
    await startDebug(`start_${currentEditScene.value.id}_until_${step.id}`);
    runUntilTargetId.value = step.id;
    return;
  }
  // 暂停/失败挂起中：发送 until 命令，继续执行到目标步骤前再次暂停
  if (ws.value != null && ws.value.readyState === WebSocket.OPEN) {
    ws.value.send(`until ${step.id}`);
    runUntilTargetId.value = step.id;
    isPausedOnFailure.value = false;
    pausedFailureStepId.value = null;
    clearPauseTimer();
    debugStatus.value = '运行中';
  }
}

const exit = async () => {
  if (ws.value != null) {
    const socket = ws.value;
    try {
      socket.send('q');
    } catch (e) {
      // WebSocket 状态异常时忽略，直接关闭连接
    }
    isDebugging.value = false;
    debugStatus.value = '未运行';
    clearPauseTimer();
    pausePending.value = false;
    runUntilTargetId.value = null;
    isPausedOnFailure.value = false;
    pausedFailureStepId.value = null;
    // 主动关闭连接，触发后端 afterConnectionClosed -> forceStop()，确保浏览器被关闭
    if (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CLOSING) {
      socket.close();
    }
  }
}


// 导入场景
const importScene = () => {
  importSceneVisible.value = true;
  importSceneForm.value = {
    mode: 'mokatest-json',
    name: '',
    parentId: 0,
    description: ''
  };
  importJsonFileList.value = [];
  importJsonSceneData.value = null;
  recordSteps.value = [];
  recordWarnings.value = [];
  nextTick(() => {
    importSceneFormRef.value?.resetFields();
    recordImportPanelRef.value?.reset();
  });
}

// MokatestRecord 上传文件后步骤变化
const onRecordStepsChange = (steps: RecordStepDraft[]) => {
  recordSteps.value = steps;
  recordWarnings.value = [];
}

const handleImportSceneCancel = () => {
  importSceneVisible.value = false;
  importSceneForm.value = {
    mode: 'mokatest-json',
    name: '',
    parentId: 0,
    description: ''
  };
  importJsonFileList.value = [];
  importJsonSceneData.value = null;
  recordSteps.value = [];
  recordWarnings.value = [];
}

// Mokatest JSON 文件变更
const onImportJsonFileChange = (fileList: any[]) => {
  importJsonFileList.value = fileList;
  const fileItem = fileList?.[0];
  if (!fileItem || !fileItem.file) {
    importJsonSceneData.value = null;
    return;
  }
  const reader = new FileReader();
  reader.onload = (e) => {
    try {
      const json = JSON.parse(e.target?.result as string);
      importJsonSceneData.value = json;
      const scenes = json?.scenes || (json?.meta ? [json] : []);
      if (scenes.length > 0 && !importSceneForm.value.name) {
        importSceneForm.value.name = `批量导入${scenes.length}个场景`;
      }
    } catch (err) {
      Message.error('JSON 解析失败，请检查文件格式');
      importJsonSceneData.value = null;
    }
  };
  reader.readAsText(fileItem.file);
}

// 打开导出场景弹窗
const openExportSceneModal = () => {
  exportSceneVisible.value = true;
  exportSceneCheckedKeys.value = [];
  exportSceneSearchKey.value = '';
  // 默认展开所有目录，便于直接勾选
  exportSceneExpandedKeys.value = collectFolderIds(exportSceneTreeData.value);
}

// 确认导出场景
const handleExportSceneConfirm = async () => {
  const sceneIds = exportSceneCheckedKeys.value;
  if (!sceneIds || sceneIds.length === 0) {
    Message.error('请至少勾选一个场景');
    return false;
  }
  const loadingMsg = Message.loading({content: '正在导出...', duration: 0});
  try {
    const result = await exportScenes(sceneIds);
    const data = result.data;
    const blob = new Blob([JSON.stringify(data, null, 2)], {type: 'application/json'});
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `scenes_${data?.scenes?.length || 0}_${Date.now()}.json`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
    Message.success('导出成功');
  } catch (err: any) {
    Message.error(err?.response?.data?.msg || err?.message || '导出失败');
    return false;
  } finally {
    loadingMsg.close?.();
  }
  exportSceneVisible.value = false;
  exportSceneCheckedKeys.value = [];
  return true;
}

// 取消导出场景
const handleExportSceneCancel = () => {
  exportSceneVisible.value = false;
  exportSceneCheckedKeys.value = [];
}


// 确认导入场景
const handleImportSceneBeforeOk = async () => {
  const error = await importSceneFormRef.value?.validate();
  if (error) {
    return false;
  }

  // CRX 数据导入
  if (importSceneForm.value.mode === 'playwright') {
    const stepInfo = importSceneForm.value.CrxData.split('\n')
        .filter((line: any) => line.trim()) // 过滤空行
        .map((line: any) => JSON.parse(line)); // 解析每行JSON
    const data = RecordUtilJsonFileParse.buildSceneInfoStepList(stepInfo);
    if (data.length <= 0) return false;

    const stepList = new Array();
    data.forEach((item: any) => {
      const testStep = new TestStep();
      testStep.stepName = item.stepName;
      testStep.stepType = item.stepType;
      testStep.description = item.description;
      testStep.orderIndex = item.orderIndex;
      testStep.parentId = item.parentId;
      testStep.stepDetail = item;
      testStep.projectId = projectStore.getProjectId;
      stepList.push(testStep);
    })
    importSceneForm.value.projectId = projectStore.getProjectId;
    let result = await importSceneInfo({
      scene: importSceneForm.value,
      stepList: stepList
    });

    if (result.data === true) {
      Message.success({
        content: '导入成功',
        duration: 1000
      })
      // 重新加载目录
      await reloadSceneTree();
      await reloadFolderLIst();
    } else {
      Message.error({
        content: result.msg,
        duration: 1000
      })
    }
    handleImportSceneCancel();
    return true;
  }

  // Mokatest 场景 JSON 批量导入
  if (importSceneForm.value.mode === 'mokatest-json') {
    const scenes = importJsonSceneData.value?.scenes || (importJsonSceneData.value?.meta ? [importJsonSceneData.value] : []);
    if (!scenes || scenes.length === 0) {
      Message.error('请先上传有效的场景 JSON 文件');
      return false;
    }
    try {
      const result = await importScenesJson({
        parentId: importSceneForm.value.parentId,
        projectId: String(projectStore.getProjectId),
        sceneCategory: props.sceneCategory,
        sceneDataList: scenes
      });
      if (result.data === true) {
        Message.success({content: `成功导入 ${scenes.length} 个场景`, duration: 1000});
        await reloadSceneTree();
        await reloadFolderLIst();
      } else {
        Message.error({content: result.msg || '导入失败', duration: 1000});
      }
      handleImportSceneCancel();
      return true;
    } catch (err: any) {
      Message.error(err?.response?.data?.msg || err?.message || '导入失败');
      return false;
    }
  }

  // MokatestRecord 录制导入
  if (importSceneForm.value.mode !== 'platform') {
    return false;
  }
  if (recordSteps.value.length === 0) {
    Message.error('请先上传录制文件，并确保至少保留一个步骤');
    return false;
  }

  try {
    const result = await saveRecord({
      projectId: projectStore.getProjectId,
      name: importSceneForm.value.name,
      parentId: importSceneForm.value.parentId,
      description: importSceneForm.value.description,
      steps: recordSteps.value
    });
    const sceneId = result.data;
    Message.success({
      content: '保存成功',
      duration: 1000
    });
    await reloadSceneTree();
    await reloadFolderLIst();
    selectedScene.value = sceneId;
    await reloadSelectedScene();
    handleImportSceneCancel();
    return true;
  } catch (err: any) {
    Message.error(err?.response?.data?.msg || err?.message || '保存失败');
    return false;
  }
}


// 确认提交场景配置
const handleSceneConfigBeforeOk = async () => {

  const error = await updateSceneConfigFormRef.value?.validate();
  if (error) {
    Message.error({
      content: '检验失败',
      duration: 1000
    })
    return false;
  }

  // API场景：校验参数表和断言表
  if (isApiScene.value) {
    const refs = [
      { ref: sceneHeaderTableRef, label: '场景Header' },
      { ref: sceneCookieTableRef, label: '场景Cookie' },
      { ref: sceneVarTableRef, label: '场景变量' },
    ];
    for (const item of refs) {
      if (item.ref.value?.validateAll) {
        const result = item.ref.value.validateAll();
        if (!result.valid) {
          Message.error({ content: `${item.label}：${result.message}`, duration: 3000 });
          return false;
        }
      }
    }
    if (sceneAssertTableRef.value?.validateAll) {
      const result = sceneAssertTableRef.value.validateAll();
      if (!result.valid) {
        Message.error({ content: `场景断言：${result.message}`, duration: 3000 });
        return false;
      }
    }

    const headers = sceneHeaderTableRef.value?.getData?.() || [];
    const cookies = sceneCookieTableRef.value?.getData?.() || [];
    const variables = sceneVarTableRef.value?.getData?.() || [];
    const assertions = sceneAssertTableRef.value?.getData?.() || [];
    updateSceneConfigForm.value.apiSceneConfig = {
      sceneHeaders: headers,
      sceneCookies: cookies,
      sceneVariables: variables,
      sceneAssertions: assertions,
      environmentId: selectedEnvironmentId.value,
      environmentName: selectedEnvironmentName.value
    };
  }

  currentEditScene.value.sceneSetting = JSON.stringify(updateSceneConfigForm.value);

  const result = await updateSceneSetting(currentEditScene.value.id, JSON.stringify(updateSceneConfigForm.value));
  if (result.data === true) {
    Message.success({
      content: '保存成功',
      duration: 1000
    })
  } else {
    Message.error({
      content: '保存失败',
      duration: 1000
    })

  }
}

const cancelDebugImage = (value: any) => {
  debugScreenShotDrawerVisible.value = value;
}

const editSceneConfig = async () => {
  if (currentEditScene.value.sceneSetting != null && currentEditScene.value.sceneSetting != '') {
    sceneConfigFormVisible.value = true;
    updateSceneConfigFormRef.value?.clearValidate();
    updateSceneConfigForm.value = JSON.parse(currentEditScene.value.sceneSetting);

    // API场景：加载场景级配置
    if (isApiScene.value) {
      nextTick(() => {
        if (updateSceneConfigForm.value.apiSceneConfig) {
          const cfg = updateSceneConfigForm.value.apiSceneConfig;
          if (sceneHeaderTableRef.value && cfg.sceneHeaders) {
            sceneHeaderTableRef.value.setData(cfg.sceneHeaders);
          }
          if (sceneCookieTableRef.value && cfg.sceneCookies) {
            sceneCookieTableRef.value.setData(cfg.sceneCookies);
          }
          if (sceneVarTableRef.value && cfg.sceneVariables) {
            sceneVarTableRef.value.setData(cfg.sceneVariables);
          }
          if (sceneAssertTableRef.value && cfg.sceneAssertions) {
            sceneAssertTableRef.value.setData(cfg.sceneAssertions);
          }
        } else {
          sceneConfigData.value = { sceneHeaders: [], sceneCookies: [], sceneVariables: [], sceneAssertions: [] };
          sceneHeaderTableRef.value?.setData?.([]);
          sceneCookieTableRef.value?.setData?.([]);
          sceneVarTableRef.value?.setData?.([]);
          sceneAssertTableRef.value?.setData?.([]);
        }
      });
    }
  }
}


// 复制场景
const handleSceneCopy = async (sceneId: number) => {
  let result = await cpoyScene(sceneId);
  if (result.data === true) {
    Message.success({
      content: `复制成功`,
      duration: 1000
    })
    // 刷新列表
    await reloadSceneTree();
    await reloadFolderLIst();
  } else {
    Message.error({
      content: result.msg,
      duration: 1000
    })
  }

}

// 打开公共函数弹窗
const openCommonFunctionDialog = () => {
  commonFunctionVisible.value = true;
}

// API场景：环境选择变化
const onSceneEnvChange = (envId: number) => {
  if (!envId) {
    clearSceneEnv();
    return;
  }
  const selectedEnv = envList.value.find((e: any) => e.id === envId);
  if (selectedEnv) {
    currentSceneEnv.value = JSON.parse(JSON.stringify(selectedEnv));
    if (!sceneEnvConfig.value) {
      sceneEnvConfig.value = new RequestExecuteInfo();
    }
    sceneEnvConfig.value.envId = selectedEnv.id;
    sceneEnvConfig.value.envName = selectedEnv.envName;
    sceneEnvConfig.value.envCookies = selectedEnv.cookies
        ? JSON.parse(JSON.stringify(selectedEnv.cookies))
        : [];
    sceneEnvConfig.value.envHeaders = selectedEnv.headers
        ? JSON.parse(JSON.stringify(selectedEnv.headers))
        : [];
    // 如果有服务列表且未选择，默认选择第一个
    if (selectedEnv.serve && selectedEnv.serve.length > 0) {
      sceneEnvConfig.value.serve = JSON.parse(JSON.stringify(selectedEnv.serve[0]));
      sceneEnvConfig.value.baseUrl = selectedEnv.serve[0].address;
    } else {
      sceneEnvConfig.value.serve = undefined;
      sceneEnvConfig.value.baseUrl = undefined;
    }
  }
};

// API场景：服务地址选择变化
const onSceneServeChange = () => {
  if (currentSceneEnv.value.serve && sceneEnvConfig.value.serve) {
    const selectedServe = currentSceneEnv.value.serve.find(
        (s: any) => s.id === sceneEnvConfig.value.serve.id
    );
    if (selectedServe) {
      sceneEnvConfig.value.serve = JSON.parse(JSON.stringify(selectedServe));
      sceneEnvConfig.value.baseUrl = selectedServe.address;
    }
  }
};

// API场景：清空环境
const clearSceneEnv = () => {
  currentSceneEnv.value = {};
  sceneEnvConfig.value = new RequestExecuteInfo();
  sceneEnvVariablesText.value = '';
};

// API场景：解析环境变量文本
const parseSceneEnvVariables = () => {
  if (!sceneEnvVariablesText.value || !sceneEnvVariablesText.value.trim()) {
    sceneEnvConfig.value.envVariables = {};
    return;
  }
  try {
    const parsed = JSON.parse(sceneEnvVariablesText.value);
    if (typeof parsed === 'object' && parsed !== null) {
      sceneEnvConfig.value.envVariables = parsed;
    }
  } catch (e) {
    Message.warning({content: '环境变量JSON格式不正确', duration: 2000});
  }
};

// 加载环境列表
const loadEnvironmentList = async () => {
  if (!teamStore.teamId) return;
  try {
    const result = await getEnvList(Number(teamStore.teamId));
    envList.value = result?.data || [];
  } catch (e) {
    console.error('加载环境列表失败', e);
  }
};

// 环境配置弹窗：关闭后刷新下拉选项；当前选中的环境若在弹窗里被删除，清空选择避免挂着失效 environmentId
const envConfigVisible = ref(false);
watch(envConfigVisible, async (visible) => {
  if (visible) return;
  await loadEnvironmentList();
  if (selectedEnvironmentId.value
      && !envList.value.some((e: any) => e.id === selectedEnvironmentId.value)) {
    onEnvironmentChange(undefined as any);
  }
});

// 统计步骤树中引用了环境数据库连接的 SQL 步骤数（用于切换/清空环境时提示）
const countSqlEnvLinkedSteps = (steps: any[]): number => {
  let count = 0;
  const walk = (list: any[]) => {
    for (const s of list || []) {
      try {
        const detail = typeof s.stepDetail === 'string' ? JSON.parse(s.stepDetail || '{}') : (s.stepDetail || {});
        if (detail.apiConfig?.sqlConfig?.dbConnectionName) count++;
      } catch { /* 忽略解析失败的步骤 */ }
      if (s.children?.length) walk(s.children);
    }
  };
  walk(steps);
  return count;
};

// 环境选择变化（API/UI 场景共用，自动保存到场景配置）
const onEnvironmentChange = async (envId: number) => {
  const prevEnvId = selectedEnvironmentId.value;
  await persistEnvironmentChange(envId);
  // 切换/清空环境后，提示引用了环境数据库连接的 SQL 步骤数，引导用户确认同名连接在新环境下存在
  if (prevEnvId !== selectedEnvironmentId.value) {
    const sqlCount = countSqlEnvLinkedSteps(sceneStepList.value);
    if (sqlCount > 0) {
      if (selectedEnvironmentId.value) {
        Message.info({content: `当前有 ${sqlCount} 个 SQL 步骤引用了环境数据库连接，请确认新环境下存在同名连接`, duration: 3000});
      } else {
        Message.info({content: `已清空环境，${sqlCount} 个 SQL 步骤的环境数据库连接将失效，执行时将报「未找到数据库连接配置」`, duration: 3000});
      }
    }
  }
};

// 保存环境选择到场景配置
const persistEnvironmentChange = async (envId: number | undefined) => {
  selectedEnvironmentId.value = envId || undefined;

  // 自动保存环境选择到场景配置
  if (currentEditScene.value?.id) {
    try {
      // 确保 updateSceneConfigForm 中有 apiSceneConfig
      let sceneSettingObj: any = {};
      if (currentEditScene.value.sceneSetting) {
        sceneSettingObj = JSON.parse(currentEditScene.value.sceneSetting);
      }
      if (!sceneSettingObj.apiSceneConfig) {
        sceneSettingObj.apiSceneConfig = {
          sceneHeaders: [],
          sceneCookies: [],
          sceneVariables: [],
          sceneAssertions: []
        };
      }
      sceneSettingObj.apiSceneConfig.environmentId = envId || undefined;
      sceneSettingObj.apiSceneConfig.environmentName = selectedEnvironmentName.value;

      const sceneSettingStr = JSON.stringify(sceneSettingObj);
      currentEditScene.value.sceneSetting = sceneSettingStr;
      await updateSceneSetting(currentEditScene.value.id, sceneSettingStr);
      Message.success({ content: '环境配置已更新', duration: 1000 });
    } catch (e) {
      console.error('自动保存环境配置失败', e);
      Message.error({ content: '环境配置保存失败', duration: 2000 });
    }
  }
};

// API场景：场景配置变化
const onSceneConfigChange = () => {
  // 数据通过ref组件的getData获取，保存时再收集
};

// 批量启用步骤
// 批量操作前过滤掉调试锁定的步骤（调试开始前勾选的步骤可能在调试中已执行，变为不可修改）
const filterUnlockedCheckedSteps = (): number[] => {
  const ids = checkedStepNode.value || [];
  const unlocked = ids.filter((id: number) => !isStepLocked(id));
  if (unlocked.length < ids.length) {
    Message.warning(`已跳过 ${ids.length - unlocked.length} 个调试中不可修改的步骤`);
  }
  return unlocked;
}

const batchEnabledStep = async () => {
// 判断checkedKey是否为空
  if (checkedStepNode.value == null || checkedStepNode.value.length <= 0) {
    Message.error({
      content: "请先选择步骤节点",
      duration: 1000
    })
    return;
  }
  const ids = filterUnlockedCheckedSteps();
  if (ids.length === 0) {
    Message.error({content: "所选步骤调试中不可修改", duration: 1000})
    return;
  }
  Modal.warning({
    title: '确认批量启用',
    content: () => '确认批量启用步骤？',
    cancelText: '取消',
    okText: '确认',
    hideCancel: false,
    onOk: async () => {
      const result = await batchEnable(ids);
      if (result.data) {
        Message.success({
          content: '启用成功',
          duration: 1000
        })
        await reloadSelectedScene();
        // 清空所选节点
        checkedStepNode.value = [];
      }
    }
  })
}

// 批量禁用步骤
const batchDisableStep = async () => {
// 判断checkedKey是否为空
  if (checkedStepNode.value == null || checkedStepNode.value.length <= 0) {
    Message.error({
      content: "请先选择步骤节点",
      duration: 1000
    })
    return;
  }
  const ids = filterUnlockedCheckedSteps();
  if (ids.length === 0) {
    Message.error({content: "所选步骤调试中不可修改", duration: 1000})
    return;
  }


  Modal.warning({
    title: '确认批量禁用',
    content: () => '确认批量禁用步骤？',
    cancelText: '取消',
    okText: '确认',
    hideCancel: false,
    onOk: async () => {
      const result = await batchDisable(ids);
      if (result.data) {
        Message.success({
          content: '禁用成功',
          duration: 1000
        })
        await reloadSelectedScene();
        // 清空所选节点
        checkedStepNode.value = [];
      }
    }
  })

}

// 批量删除步骤
const batchDeleteStep = async () => {
  // 判断checkedKey是否为空
  if (checkedStepNode.value == null || checkedStepNode.value.length <= 0) {
    Message.error({
      content: "请先选择步骤节点",
      duration: 1000
    })
    return;
  }
  const ids = filterUnlockedCheckedSteps();
  if (ids.length === 0) {
    Message.error({content: "所选步骤调试中不可修改", duration: 1000})
    return;
  }


  Modal.warning({
    title: '确认批量删除',
    content: () => '确认批量删除步骤？删除后不可恢复！！',
    cancelText: '取消',
    okText: '确认',
    hideCancel: false,
    onOk: async () => {
      const result = await batchDelete(ids);
      if (result.data) {
        Message.success({
          content: '删除成功',
          duration: 1000
        })
        await reloadSelectedScene();
        // 清空所选节点
        checkedStepNode.value = [];
      }
    }
  })
}

// 导入已有步骤
const importExistStep = () => {
  importStepDialogVisible.value = true;
}


// ==================== 底部步骤操作栏（宽度自适应收纳） ====================

// 操作项定义：children 存在时渲染为下拉按钮
type FooterAction = {
  key: string;
  label: string;
  icon: string;
  type?: 'primary';
  style?: string;
  onClick?: () => void;
  children?: { label: string; onClick: () => void }[];
};

// 底部操作栏按钮清单（数据驱动，顺序即展示顺序）
const footerActions = computed<FooterAction[]>(() => {
  if (isApiScene.value) {
    return [
      {
        key: 'api_request', label: 'HTTP请求', icon: 'icon-thunderbolt', type: 'primary',
        children: [
          {label: '新建接口', onClick: () => addApiStep(ApiRequest, null, 'api_request', 'new')},
          {label: '引入已有用例', onClick: () => addApiStep(ApiRequest, null, 'api_request', 'import')},
        ]
      },
      {
        key: 'sql', label: 'SQL查询', icon: 'icon-storage', style: 'background-color:rgb(230, 245, 255)',
        children: [
          {label: '新建SQL接口', onClick: () => addApiStep(SqlRequest, null, 'sql', 'new')},
          {label: '引入已有SQL接口', onClick: () => addApiStep(SqlRequest, null, 'sql', 'import')},
        ]
      },
      {
        key: 'loop', label: '循环', icon: 'icon-loop', style: 'background-color:rgb(230, 230, 255)',
        children: [
          {label: 'for循环', onClick: () => addOperation(For, clickSchema, 'for')},
          {label: 'while循环', onClick: () => addOperation(While, null, 'while')},
        ]
      },
      {key: 'if', label: 'if判断', icon: 'icon-branch', style: 'background-color:rgb(230, 255, 240)', onClick: () => addOperation(IfAssert, ifSchema, 'if')},
      {key: 'script', label: '脚本', icon: 'icon-code-sandbox', style: 'background-color:rgb(255, 240, 230)', onClick: () => addOperation(ScriptRequest, null, 'script')},
      {key: 'wait', label: '等待', icon: 'icon-clock-circle', style: 'background-color:rgb(255, 255, 230)', onClick: () => addOperation(Wait, waitFixedDurationSchema, 'wait')},
    ];
  }
  return [
    {
      key: 'import', label: '导入步骤', icon: 'icon-import', style: 'background-color:#ed4d8b',
      children: [
        {label: '导入已有步骤', onClick: () => importExistStep()},
      ]
    },
    {
      key: 'api_request', label: 'HTTP请求', icon: 'icon-thunderbolt', type: 'primary',
      children: [
        {label: '新建接口', onClick: () => addApiStep(ApiRequest, null, 'api_request', 'new')},
        {label: '引入已有用例', onClick: () => addApiStep(ApiRequest, null, 'api_request', 'import')},
      ]
    },
    {
      key: 'sql', label: 'SQL查询', icon: 'icon-storage', style: 'background-color:rgb(230, 245, 255)',
      children: [
        {label: '新建SQL接口', onClick: () => addApiStep(SqlRequest, null, 'sql', 'new')},
        {label: '引入已有SQL接口', onClick: () => addApiStep(SqlRequest, null, 'sql', 'import')},
      ]
    },
    {
      key: 'browser', label: '浏览器操作', icon: 'icon-compass', style: 'background-color:rgb(255, 230, 230)',
      children: [
        {label: '打开网页', onClick: () => addOperation(OpenPage, SCHEMA_REGISTRY['OPEN_PAGE'], 'open_page')},
        {label: '关闭网页', onClick: () => addOperation(ClosePage, closePageSchema, 'close_page')},
        {label: '切换tab', onClick: () => addOperation(SwitchTab, switchTabSchema, 'switch_tab')},
        {label: '前进', onClick: () => addOperation(Forward, null, 'forward')},
        {label: '后退', onClick: () => addOperation(Back, null, 'back')},
        {label: '刷新', onClick: () => addOperation(Refresh, null, 'refresh')},
      ]
    },
    {
      key: 'mouse', label: '鼠标操作', icon: 'icon-interaction', style: 'background-color:rgb(230, 255, 230)',
      children: [
        {label: '点击', onClick: () => addOperation(ClickForm, clickSchema, 'click')},
        {label: '悬停', onClick: () => addOperation(Hover, null, 'hover')},
        {label: '鼠标拖拽', onClick: () => addOperation(Drag, null, 'drag')},
      ]
    },
    {
      key: 'loop', label: '循环', icon: 'icon-loop', style: 'background-color:rgb(230, 230, 255)',
      children: [
        {label: 'for循环', onClick: () => addOperation(For, clickSchema, 'for')},
        {label: 'while循环', onClick: () => addOperation(While, null, 'while')},
      ]
    },
    {key: 'wait', label: '等待', icon: 'icon-clock-circle', style: 'background-color:rgb(255, 255, 230)', onClick: () => addOperation(Wait, waitFixedDurationSchema, 'wait')},
    {key: 'extract', label: '关联提取', icon: 'icon-export', style: 'background-color:rgb(255, 230, 255)', onClick: () => addOperation(Extract, extractSchema, 'extract')},
    {key: 'keyboard', label: '键盘操作', icon: 'icon-command', style: 'background-color:rgb(230, 255, 255)', onClick: () => addOperation(Keyboard, keyboardInputSchema, 'keyboard')},
    {key: 'element_dom_operation', label: '元素DOM操作', icon: 'icon-code', style: 'background-color:rgb(235, 245, 255)', onClick: () => addOperation(ElementDomOperation, null, 'element_dom_operation')},
    {key: 'file_upload', label: '上传文件', icon: 'icon-upload', style: 'background-color:rgb(255, 245, 230)', onClick: () => addOperation(FileUpload, null, 'file_upload')},
    {key: 'assert', label: '断言', icon: 'icon-check-circle', style: 'background-color:rgb(240, 230, 255)', onClick: () => addOperation(Assert, assertSchema, 'assert')},
    {key: 'if', label: 'if判断', icon: 'icon-branch', style: 'background-color:rgb(230, 255, 240)', onClick: () => addOperation(IfAssert, ifSchema, 'if')},
    {key: 'script', label: '脚本', icon: 'icon-code-sandbox', style: 'background-color:rgb(255, 240, 230)', onClick: () => addOperation(ScriptRequest, null, 'script')},
    {key: 'iframe', label: 'iframe切换', icon: 'icon-layout', style: 'background-color:rgb(255, 230, 240)', onClick: () => addOperation(SwitchIframe, iframeSchema, 'iframe')},
    {key: 'dialog', label: '对话框', icon: 'icon-message', style: 'background-color:rgb(255, 230, 240)', onClick: () => addOperation(Dialog, null, 'dialog')},
  ];
});

// 操作栏宽度自适应：展示不下的按钮收纳进「更多操作」
const FOOTER_BTN_GAP = 8;
const footerBarRef = ref<HTMLElement | null>(null);
const measureBarRef = ref<HTMLElement | null>(null);
const actionWidths = ref<number[]>([]);
const moreBtnWidth = ref(0);
// 默认全部展示，测量后再按实际宽度裁剪
const visibleActionCount = ref(Number.MAX_SAFE_INTEGER);

// 放不下的操作项
const overflowActions = computed<FooterAction[]>(() => {
  if (visibleActionCount.value >= footerActions.value.length) return [];
  return footerActions.value.slice(visibleActionCount.value);
});

// 根据容器宽度和已测按钮宽度，计算能放得下多少个按钮
const updateVisibleActionCount = () => {
  const bar = footerBarRef.value;
  if (!bar || actionWidths.value.length === 0) return;
  const containerWidth = bar.clientWidth;
  if (containerWidth <= 0) return;
  const widths = actionWidths.value;
  let used = 0;
  let count = widths.length;
  for (let i = 0; i < widths.length; i++) {
    const itemWidth = widths[i] + FOOTER_BTN_GAP;
    // 不是最后一项时，需要为「更多操作」按钮预留位置；最后一项放得下就不需要预留
    const reserve = i === widths.length - 1 ? 0 : moreBtnWidth.value + FOOTER_BTN_GAP;
    if (used + itemWidth + reserve > containerWidth) {
      count = i;
      break;
    }
    used += itemWidth;
  }
  visibleActionCount.value = count;
}

// 通过隐藏测量区量出每个按钮和「更多操作」按钮的实际宽度
const measureFooterActions = async () => {
  await nextTick();
  const bar = measureBarRef.value;
  if (!bar) return;
  const items = Array.from(bar.querySelectorAll('.footer-measure-item')) as HTMLElement[];
  if (items.length === 0) return;
  const moreItem = bar.querySelector('.footer-measure-more') as HTMLElement | null;
  moreBtnWidth.value = moreItem ? moreItem.offsetWidth : 0;
  actionWidths.value = items
      .filter((el) => !el.classList.contains('footer-measure-more'))
      .map((el) => el.offsetWidth);
  updateVisibleActionCount();
}

// 监听操作栏容器尺寸变化，动态调整展示数量
let footerResizeObserver: ResizeObserver | null = null;

watch(() => footerBarRef.value, (el) => {
  if (footerResizeObserver) {
    footerResizeObserver.disconnect();
    footerResizeObserver = null;
  }
  if (el) {
    measureFooterActions();
    footerResizeObserver = new ResizeObserver(() => updateVisibleActionCount());
    footerResizeObserver.observe(el);
  }
}, {flush: 'post', immediate: true});

// API/UI 场景切换时按钮清单变化，需要重新量宽
watch(footerActions, () => {
  visibleActionCount.value = Number.MAX_SAFE_INTEGER;
  measureFooterActions();
});

onBeforeUnmount(() => {
  if (footerResizeObserver) {
    footerResizeObserver.disconnect();
    footerResizeObserver = null;
  }
});


watch(() => [currentEditScene.value, currentEditFolder.value], () => {
  // 仅做监听，不再自动清空调试列表
  // 切换场景的清空逻辑在 reloadSelectedScene 中处理
})


watch(
    () => projectStore.getProjectId,
    async (newProjectId, oldProjectId) => {
      if (newProjectId) {
        // 获取场景列表
        await reloadSceneTree();

        // 获取目录列表
        await reloadFolderLIst();
        // 初始化的时候，默认展开场景树
        handleSceneTreeExpand();


        clearAllSelectSceneInfo();


      }
    },
    {immediate: true} // 立即执行一次
);


</script>

<style scoped>

/* 导出场景弹窗：固定尺寸布局 */
.export-scene-dialog {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.export-scene-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
}

.export-scene-tree-wrapper {
  height: 380px;
  border: 1px solid var(--color-neutral-3);
  border-radius: 4px;
  padding: 8px;
  box-sizing: border-box;
}

.export-scene-footer {
  color: var(--color-text-2);
  font-size: 13px;
}

.export-scene-count {
  color: rgb(var(--arcoblue-6));
  font-weight: 600;
}

/* 场景树节点：名称过长省略 + 悬浮显示全名（与 API 测试目录树一致） */
/* 让 Arco 树节点标题容器允许收缩，标题宽度自适应剩余空间，避免溢出产生横向滚动条。
   保留 min-width:0；不设置 overflow:hidden，否则拖拽落点指示线会被裁掉。
   给标题加 1px 上下外边距，使相邻节点的上下指示线重合，避免切换 target 时闪烁。 */
.tree-scroll-wrapper :deep(.arco-tree-node-title) {
  min-width: 0;
  margin: 1px 0;
}

.tree-scroll-wrapper :deep(.arco-tree-node-title-text) {
  display: flex !important;
  align-items: center;
  flex: 1 !important;
  min-width: 0 !important;
}

.scene-node-title-wrap {
  display: flex;
  align-items: center;
  flex: 1;
  max-width: 100%;
  min-width: 0;
  overflow: hidden;
  padding-right: 24px;
}

.scene-node-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* a-tooltip 内部会套一层 display:inline-block 的 .arco-trigger-wrapper，
   不处理会破坏 .scene-node-title-wrap 的 flex 布局，导致省略失效 / tooltip 定位异常。
   让该 wrapper 参与 flex 并占满剩余宽度。 */
.scene-node-title-wrap :deep(.arco-trigger-wrapper) {
  flex: 1;
  min-width: 0;
  display: flex;
}

/* 更多操作默认隐藏，悬浮时绝对定位到节点标题右侧 */
.tree-scroll-wrapper :deep(.arco-tree-node) .node-extra-actions {
  position: absolute;
  right: 4px;
  top: 50%;
  transform: translateY(-50%);
  visibility: hidden;
  z-index: 1;
}

.tree-scroll-wrapper :deep(.arco-tree-node:hover) .node-extra-actions {
  visibility: visible;
}

.node-extra-btn {
  padding: 0 4px;
}

.node-extra-icon {
  color: rgb(var(--primary-6));
  font-size: 12px;
}

.scene-page {
  padding: 0 16px 12px;
  display: flex;
  flex-direction: column;
  height: var(--page-container-height, calc(100vh - 60px));
}

.scene-main-row {
  display: flex;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.scene-col {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.scene-side-col {
  flex-shrink: 0;
  transition: none;
}

.scene-card {
  display: flex;
  flex-direction: column;
  flex: 1;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.scene-card :deep(.arco-card-body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.scene-folder-card :deep(.arco-card-body) {
  padding: 12px 0 12px 12px;
}

.scene-tree-search-wrap {
  padding: 0 12px;
  margin-bottom: 8px;
}

.scene-tree-search {
  width: 100%;
}

.scene-tree-actions {
  display: flex;
  width: 100%;
  padding: 0 12px;
}

.tree-scroll-wrapper {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  margin-top: 8px;
}

.tree-scroll-wrapper :deep(.arco-scrollbar-container) {
  overflow-x: hidden !important;
  overflow-y: auto !important;
  padding-right: 12px;
}

.tree-scroll-wrapper :deep(.arco-tree-node) {
  border-radius: 4px;
}

.tree-scroll-wrapper :deep(.arco-tree-node-selected) {
  background-color: var(--color-primary-light-1);
}

.tree-scroll-wrapper :deep(.arco-tree-node-selected .arco-tree-node-title) {
  background-color: transparent;
}

.tree-scroll-wrapper :deep(.arco-tree-node:hover:not(.arco-tree-node-selected)) {
  background-color: var(--color-fill-1);
}

/* 隐藏 Arco Tree 默认拖拽手柄 */
.tree-scroll-wrapper :deep(.arco-tree-node-drag-icon) {
  display: none;
}

/* 左侧伸缩分隔条 */
.scene-main-resizer {
  flex-shrink: 0;
  width: 6px;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: col-resize;
  background: transparent;
  user-select: none;
}

.scene-main-resizer.is-collapsed {
  width: 4px;
  cursor: col-resize;
  background: transparent;
  border-right: 1px solid var(--color-border-2);
  position: relative;
}

.sidebar-expand-btn {
  position: absolute;
  left: 4px;
  top: 50%;
  transform: translateY(-50%);
  width: 20px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-left: none;
  border-radius: 0 4px 4px 0;
  cursor: pointer;
  z-index: 10;
}

.sidebar-expand-btn:hover {
  background: var(--color-fill-2);
}

.sidebar-expand-icon {
  color: rgb(var(--primary-6));
  font-size: 14px;
}

.scene-main-resizer:hover .resizer-line,
.scene-main-resizer:active .resizer-line {
  background: rgb(var(--primary-6));
}

.resizer-line {
  width: 2px;
  height: 24px;
  border-radius: 1px;
  background: var(--color-border-2);
}

.scene-right-col {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 100%;
  flex: 1;
}

.scene-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: var(--color-fill-1);
}

.scene-empty-tip {
  color: var(--color-text-3);
  font-size: 13px;
}

.scene-top-card {
  flex: 0 0 auto;
}

/* 顶部栏：第一行身份+调试主操作，第二行描述+工具；窄屏各自换行 */
.scene-top-bar {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.scene-top-main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.scene-identity {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  min-width: 240px;
}

.scene-category-tag {
  flex: 0 0 auto;
}

/* 场景名即标题：无边框输入，hover/focus 才显形（class 落在 arco-input-wrapper 根上） */
.scene-top-bar :deep(.arco-input-wrapper.scene-name-input) {
  background: transparent;
  border-color: transparent;
  transition: border-color 0.15s, background 0.15s;
  flex: 1;
  min-width: 0;
  max-width: 420px;
}

.scene-top-bar :deep(.arco-input-wrapper.scene-name-input:hover) {
  background: var(--color-fill-2);
}

.scene-top-bar :deep(.arco-input-wrapper.scene-name-input:focus-within) {
  background: var(--color-bg-2);
  border-color: rgb(var(--primary-6));
}

.scene-top-bar :deep(.arco-input-wrapper.scene-name-input .arco-input) {
  font-size: 17px;
  font-weight: 600;
  color: var(--color-text-1);
}

.scene-debug-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.debug-main-btn {
  min-width: 132px;
  white-space: nowrap;
}

.debug-main-btn :deep(.arco-typography) {
  color: inherit;
}

.scene-top-secondary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

/* 描述：次级文字、无边框，占位引导补充（class 落在 arco-input-wrapper 根上） */
.scene-top-bar :deep(.arco-input-wrapper.scene-desc-input) {
  background: transparent;
  border-color: transparent;
  transition: border-color 0.15s, background 0.15s;
  flex: 1;
  min-width: 200px;
  max-width: 480px;
}

.scene-top-bar :deep(.arco-input-wrapper.scene-desc-input:hover) {
  background: var(--color-fill-2);
}

.scene-top-bar :deep(.arco-input-wrapper.scene-desc-input:focus-within) {
  background: var(--color-bg-2);
  border-color: rgb(var(--primary-6));
}

.scene-top-bar :deep(.arco-input-wrapper.scene-desc-input .arco-input) {
  font-size: 13px;
  color: var(--color-text-3);
}

.scene-tools {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.scene-list-card,
.scene-flow-card,
.scene-workflow-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.scene-list-card :deep(.arco-card-body),
.scene-flow-card :deep(.arco-card-body),
.scene-workflow-card :deep(.arco-card-body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  padding: 12px;
}

.step-scroll-wrapper {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.step-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  scrollbar-width: thin;
  scrollbar-color: var(--color-text-4) transparent;
}

.step-footer-card {
  flex: 0 0 auto;
  margin-top: 12px;
}

.step-footer-card :deep(.arco-card-body) {
  padding: 12px;
}

/* 底部步骤操作栏：单行排列，放不下的收纳进「更多操作」 */
.footer-btn-bar {
  display: flex;
  align-items: center;
  flex-wrap: nowrap;
  gap: 8px;
  overflow: hidden;
}

/* 隐藏测量区：用于量出每个按钮的实际宽度，不可见不参与布局 */
.footer-measure-bar {
  position: absolute;
  height: 0;
  overflow: hidden;
  visibility: hidden;
  pointer-events: none;
  white-space: nowrap;
  display: flex;
  gap: 8px;
}

.arco-dropdown-open .arco-icon-down {
  transform: rotate(180deg);
}

.custom-light-green-border {
  border: 1px solid #a0d911;
}

.protocol-cards-container {
  display: flex;
  gap: 24px;
  padding: 60px;
  height: 100%;
  min-height: 70vh;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
}

.protocol-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 200px;
  height: 240px;
  background: #ffffff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  cursor: pointer;
  transition: all 0.3s ease;
  padding: 20px;
}

.protocol-card:hover {
  transform: translateY(-4px);
  border-color: #a855f7;
}

.icon-wrapper {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16px;
  transition: transform 0.3s ease;
}

.protocol-card:hover .icon-wrapper {
  transform: scale(1.1);
}

.icon {
  font-size: 24px;
}

.card-title {
  font-size: 14px;
  color: #4e5969;
  font-weight: 500;
  text-align: center;
  line-height: 1.5;
}

/* 导入场景弹窗 - 卡片式数据源 */
.import-source-radio-group {
  display: flex;
  gap: 12px;
  width: 100%;
}

.import-source-radio-group :deep(.arco-radio) {
  flex: 1;
  margin-right: 0;
  padding: 0;
}

.import-source-radio-group :deep(.arco-radio-label) {
  display: block;
  width: 100%;
  padding: 0;
}

.import-source-radio-group :deep(.arco-radio-icon),
.import-source-radio-group :deep(.arco-radio-icon-hover),
.import-source-radio-group :deep(.arco-radio-icon-hover::before),
.import-source-radio-group :deep(.arco-radio-icon-hover::after) {
  display: none !important;
  width: 0 !important;
  height: 0 !important;
  overflow: hidden !important;
}

.import-source-radio-group :deep(.arco-radio-icon::before),
.import-source-radio-group :deep(.arco-radio-icon::after),
.import-source-radio-group :deep(.arco-radio-icon-hover::before),
.import-source-radio-group :deep(.arco-radio-icon-hover::after) {
  display: none !important;
}

.import-source-card {
  position: relative;
  border: 1px solid var(--color-neutral-3);
  border-radius: 8px;
  padding: 16px;
  cursor: pointer;
  transition: all 0.2s ease;
  background: var(--color-bg-2);
  text-align: center;
}

.import-source-card:hover {
  border-color: rgb(var(--arcoblue-4));
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.import-source-icon {
  font-size: 28px;
  color: rgb(var(--arcoblue-6));
  margin-bottom: 8px;
}

.import-source-radio-group :deep(.arco-radio-checked) .import-source-card {
  border-color: rgb(var(--arcoblue-6));
  box-shadow: 0 0 0 2px rgba(var(--arcoblue-6), 0.15);
}

.import-source-radio-group :deep(.arco-radio-checked) .import-source-card::after {
  content: '✓';
  position: absolute;
  top: 0;
  right: 0;
  width: 22px;
  height: 22px;
  line-height: 22px;
  font-size: 12px;
  color: #fff;
  background: rgb(var(--arcoblue-6));
  border-bottom-left-radius: 8px;
  border-top-right-radius: 7px;
}

.import-source-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-1);
  margin-bottom: 4px;
}

.import-source-desc {
  font-size: 12px;
  color: var(--color-text-3);
}

.import-config-row {
  margin-bottom: 8px;
}

/* 拖拽上传区 */
.import-drag-zone {
  width: 100%;
  min-height: 180px;
  border: 2px dashed var(--color-neutral-3);
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  background: var(--color-fill-1);
  transition: all 0.2s ease;
}

.import-drag-zone:hover {
  border-color: rgb(var(--arcoblue-6));
  background: rgba(var(--arcoblue-6), 0.04);
}

.import-drag-title {
  font-size: 14px;
  color: var(--color-text-1);
  font-weight: 500;
}

.import-drag-desc {
  font-size: 12px;
  color: var(--color-text-3);
}

.import-file-card {
  width: 100%;
  min-height: 80px;
  border: 1px solid var(--color-neutral-3);
  border-radius: 8px;
  padding: 16px 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--color-bg-2);
}

.import-file-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.import-file-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text-1);
}

.import-file-size {
  font-size: 12px;
  color: var(--color-text-3);
  margin-top: 2px;
}

.import-json-uploader {
  width: 100%;
}

.import-json-uploader :deep(.arco-upload-wrapper) {
  width: 100%;
}

.record-import-uploader {
  width: 100%;
}

.record-import-uploader :deep(.arco-upload-wrapper),
.record-import-uploader :deep(.arco-upload-drag) {
  width: 100%;
}

.import-scene-form :deep(.arco-form-item) {
  margin-bottom: 12px;
}

.import-scene-form :deep(.arco-form-item-label) {
  line-height: 22px;
  padding-bottom: 4px;
}

.record-import-uploader :deep(.record-upload-area) {
  flex-direction: row;
  align-items: center;
  justify-content: flex-start;
  padding: 6px 12px;
  gap: 8px;
}

.record-import-uploader :deep(.record-upload-area svg) {
  font-size: 16px;
}

.record-import-uploader :deep(.record-upload-area div) {
  margin-top: 0 !important;
}

.record-import-uploader :deep(.record-upload-area div:first-of-type) {
  font-size: 13px;
}

.record-import-uploader :deep(.record-upload-area div:last-of-type) {
  font-size: 11px;
  color: var(--color-text-3);
}

.record-import-right :deep(.record-step-list-card .arco-scrollbar) {
  height: 180px !important;
}

.record-import-right :deep(.record-step-list-card) {
  margin-bottom: 0;
}

.record-import-right :deep(.record-step-name) {
  max-width: 120px;
}
</style>