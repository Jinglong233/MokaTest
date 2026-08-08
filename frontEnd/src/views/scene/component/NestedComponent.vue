<template>
  <div>
    <div style="padding: 10px;width: 100%">
      <a-row style="align-items: center; gap: 12px;">
        <a-checkbox v-if="hasStepUpdatePermission || hasStepDeletePermission" style="margin-left: 18px;margin-bottom: 20px" :disabled="mutationsLocked" @change="checkAllNode">全选</a-checkbox>
        <a-tree
            ref="treeRef"
            style="width: 100%"
            :data="displayList"
            :draggable="hasStepUpdatePermission && !props.isDebugging"
            blockNode
            :checkable="hasStepUpdatePermission || hasStepDeletePermission"
            :check-strictly="false"
            v-model:checked-keys="checkTreeNodes"
            checked-strategy="all"
            @drop="onDrop"
            :fieldNames="{
            key: 'id',
            title: 'stepName',
            children: 'children',
            // disabled:'isDisable',
            icon: 'customIcon'
          }"
            @select="editStep"
            :block-node="true"
        >
          <template #title="el">
            <a-card :style="{
                      width: '100%',
                      boxSizing: 'border-box',
                      margin: '0',
                      backgroundColor: el.isDisable === 1 ? 'rgba(0, 0, 0, 0.1)' : ''
                    }"
                    :class="{
                        'active': currentStep && currentStep.id === el.id,
                        'step-debug-success': getStepDebugStatus(el.id) === 'SUCCESS',
                        'step-debug-failure': getStepDebugStatus(el.id) === 'FAILURE',
                        'step-debug-skipped': getStepDebugStatus(el.id) === 'SKIPPED'
                    }"
            >
              <div style="display: flex;align-items: center;gap: 8px;width: 100%;">
                <!-- 序号 -->
                <div style="flex-shrink: 0; width: 32px;">
                  <a-badge :count="el.orderIndex"/>
                </div>
                <!-- 中间内容区域 -->
                <div style="display: flex;align-items: center;gap: 8px;flex: 1;min-width: 0;">
                  <a-avatar shape="square" :style="{ marginRight: '8px', backgroundColor: '#165DFF' }"
                            :size="28">
                    <icon-code-sandbox/>
                  </a-avatar>
                  <a-tag color="cyan" size="large">
                    {{ getStepTypeChinese(el.stepType) }}
                  </a-tag>
                  <!-- 文本区域 -->
                  <a-typography-text
                      style="flex: 1;min-width: 0;overflow: hidden;text-overflow: ellipsis;white-space: nowrap;">
                    {{ el.stepName }}
                  </a-typography-text>
                  <a-tag v-if="el.isDisable === 1" color="gray" size="small" style="flex-shrink: 0;">已禁用</a-tag>
                  <!-- 本步提取的变量（数据流可见） -->
                  <a-tooltip v-if="getStepExtractVars(el).length" :content="'提取变量：' + getStepExtractVars(el).map((v) => '${'+v+'}').join('  ')">
                    <a-tag color="arcoblue" size="small" style="flex-shrink: 0;">
                      <template #icon><icon-export/></template>
                      {{ getStepExtractVars(el).length }}
                    </a-tag>
                  </a-tooltip>
                </div>
                <!--  调试状态与结果查看 -->
                <div
                    v-if="(isDebugging && el.isDisable===0 && !isStepDebugFinished(el.id) && !isCurrentStepHasResult(el.id)) || (stepDebugList && stepDebugList.length > 0)"
                    style="flex-shrink: 0; display: flex; align-items: center; gap: 8px; margin-left: 8px;"
                >
                  <a-spin dot
                          v-if="isDebugging && el.isDisable===0 && !isStepDebugFinished(el.id) && !isCurrentStepHasResult(el.id)"/>
                  <template v-if="stepDebugList && stepDebugList.length > 0">
                    <!-- 缩略图 -->
                    <ScreenshotImage
                        v-if="getDebugScreenShotByStepId(el.id)"
                        width="80"
                        height="30"
                        :file-id="getDebugScreenShotByStepId(el.id)"
                        :image-style="{border: '1px solid #e5e5e5', borderRadius: '2px'}"
                    />
                    <!-- 有调试状态时：状态图标 + 查看结果，整体可点击 -->
                    <a-tooltip v-if="getStepDebugStatus(el.id)" content="点击查看调试结果">
                      <div class="debug-result-trigger" @click.stop="checkCurrentDebugResult(el.id, el.stepType)">
                        <icon-check-circle-fill v-if="getStepDebugStatus(el.id) === 'SUCCESS'"
                                                class="debug-status-icon debug-status-success"/>
                        <icon-close-circle-fill v-if="getStepDebugStatus(el.id) === 'FAILURE'"
                                                class="debug-status-icon debug-status-failure"/>
                        <icon-minus-circle v-if="getStepDebugStatus(el.id) === 'SKIPPED'"
                                           class="debug-status-icon debug-status-skipped"/>
                        <span class="debug-result-text">查看结果</span>
                      </div>
                    </a-tooltip>
                  </template>
                </div>
                <div>
                  <a-dropdown position="left">
                    <a-tooltip :content="mutationsLocked ? '调试运行中，暂停后可新增步骤' : '添加步骤'">
                      <a-button v-permission="'auto:step:create'" @click.stop="" shape="circle" type="text"
                                :disabled="mutationsLocked">
                        <icon-plus-circle style="font-size: 20px"/>
                      </a-button>
                    </a-tooltip>
                    <template #content>
                      <!-- API场景：级联子菜单 -->
                      <template v-if="props.isApiScene">
                        <a-dsubmenu position="lt" value="添加相邻步骤">
                          <template #default>添加相邻步骤</template>
                          <template #content>
                            <a-dsubmenu position="lt" value="api_request_adj">
                              <template #default>HTTP请求</template>
                              <template #content>
                                <a-doption @click="emits('addAdjacentStep',el,'api_request',false,'new')">新建接口</a-doption>
                                <a-doption @click="emits('addAdjacentStep',el,'api_request',false,'import')">引入已有用例</a-doption>
                              </template>
                            </a-dsubmenu>
                            <a-dsubmenu position="lt" value="sql_adj">
                              <template #default>SQL查询</template>
                              <template #content>
                                <a-doption @click="emits('addAdjacentStep',el,'sql',false,'new')">新建SQL接口</a-doption>
                                <a-doption @click="emits('addAdjacentStep',el,'sql',false,'import')">引入已有SQL接口</a-doption>
                              </template>
                            </a-dsubmenu>
                            <a-doption @click="emits('addAdjacentStep',el,'for',false)">for循环</a-doption>
                            <a-doption @click="emits('addAdjacentStep',el,'while',false)">while循环</a-doption>
                            <a-doption @click="emits('addAdjacentStep',el,'if',false)">if判断</a-doption>
                            <a-doption @click="emits('addAdjacentStep',el,'wait',false)">等待</a-doption>
                            <a-doption @click="emits('addAdjacentStep',el,'script',false)">脚本</a-doption>
                          </template>
                        </a-dsubmenu>
                        <!-- FOR/WHILE/IF 才能添加子步骤，再次联动出步骤类型 -->
                        <a-dsubmenu
                            v-if="el.stepType === 'WHILE' || el.stepType === 'FOR' || el.stepType === 'IF'"
                            position="lt" value="添加子步骤">
                          <template #default>添加子步骤</template>
                          <template #content>
                            <a-dsubmenu position="lt" value="api_request_child">
                              <template #default>HTTP请求</template>
                              <template #content>
                                <a-doption @click="emits('addAdjacentStep',el,'api_request',true,'new')">新建接口</a-doption>
                                <a-doption @click="emits('addAdjacentStep',el,'api_request',true,'import')">引入已有用例</a-doption>
                              </template>
                            </a-dsubmenu>
                            <a-dsubmenu position="lt" value="sql_child">
                              <template #default>SQL查询</template>
                              <template #content>
                                <a-doption @click="emits('addAdjacentStep',el,'sql',true,'new')">新建SQL接口</a-doption>
                                <a-doption @click="emits('addAdjacentStep',el,'sql',true,'import')">引入已有SQL接口</a-doption>
                              </template>
                            </a-dsubmenu>
                            <a-doption @click="emits('addAdjacentStep',el,'for',true)">for循环</a-doption>
                            <a-doption @click="emits('addAdjacentStep',el,'while',true)">while循环</a-doption>
                            <a-doption @click="emits('addAdjacentStep',el,'if',true)">if判断</a-doption>
                            <a-doption @click="emits('addAdjacentStep',el,'wait',true)">等待</a-doption>
                            <a-doption @click="emits('addAdjacentStep',el,'script',true)">脚本</a-doption>
                          </template>
                        </a-dsubmenu>
                      </template>
                      <!-- UI场景：原有菜单 -->
                      <template v-else>
                        <a-dsubmenu position="lt" value="添加相邻操作">
                          <template #default>添加相邻操作</template>
                          <template #content>
                            <a-dsubmenu position="lt" value="浏览器操作">
                              <template #default>浏览器操作</template>
                              <template #content>
                                <a-doption @click="emits('addAdjacentStep',el,'open_page',false)">打开网页</a-doption>
                                <a-doption @click="emits('addAdjacentStep',el,'close_page',false)">关闭网页</a-doption>
                                <a-doption @click="emits('addAdjacentStep',el,'switch_tab',false)">切换tab</a-doption>
                                <a-doption @click="emits('addAdjacentStep',el,'forward',false)">前进</a-doption>
                                <a-doption @click="emits('addAdjacentStep',el,'back',false)">后退</a-doption>
                                <a-doption @click="emits('addAdjacentStep',el,'refresh',false)">刷新</a-doption>
                              </template>
                            </a-dsubmenu>
                            <a-dsubmenu position="lt" value="鼠标操作">
                              <template #default>鼠标操作</template>
                              <template #content>
                                <a-doption @click="emits('addAdjacentStep',el,'click',false)">点击</a-doption>
                                <a-doption @click="emits('addAdjacentStep',el,'hover',false)">悬停</a-doption>
                                <a-doption @click="emits('addAdjacentStep',el,'drag',false)">鼠标拖拽</a-doption>
                              </template>
                            </a-dsubmenu>
                            <a-dsubmenu position="lt" value="循环">
                              <template #default>循环</template>
                              <template #content>
                                <a-doption @click="emits('addAdjacentStep',el,'for',false)">for循环</a-doption>
                                <a-doption @click="emits('addAdjacentStep',el,'while',false)">while循环</a-doption>
                              </template>
                            </a-dsubmenu>
                            <a-doption @click="emits('addAdjacentStep',el,'wait',false)">等待</a-doption>
                            <a-dsubmenu position="lt" value="api_request_adj_ui">
                              <template #default>HTTP请求</template>
                              <template #content>
                                <a-doption @click="emits('addAdjacentStep',el,'api_request',false,'new')">新建接口</a-doption>
                                <a-doption @click="emits('addAdjacentStep',el,'api_request',false,'import')">引入已有用例</a-doption>
                              </template>
                            </a-dsubmenu>
                            <a-dsubmenu position="lt" value="sql_adj_ui">
                              <template #default>SQL查询</template>
                              <template #content>
                                <a-doption @click="emits('addAdjacentStep',el,'sql',false,'new')">新建SQL接口</a-doption>
                                <a-doption @click="emits('addAdjacentStep',el,'sql',false,'import')">引入已有SQL接口</a-doption>
                              </template>
                            </a-dsubmenu>
                            <a-doption @click="emits('addAdjacentStep',el,'extract',false)">关联提取</a-doption>
                            <a-doption @click="emits('addAdjacentStep',el,'keyboard',false)">键盘操作</a-doption>
                            <a-doption @click="emits('addAdjacentStep',el,'element_dom_operation',false)">元素DOM操作</a-doption>
                            <a-doption @click="emits('addAdjacentStep',el,'file_upload',false)">上传文件</a-doption>
                            <a-doption @click="emits('addAdjacentStep',el,'assert',false)">断言</a-doption>
                            <a-doption @click="emits('addAdjacentStep',el,'if',false)">if判断</a-doption>
                            <a-doption @click="emits('addAdjacentStep',el,'script',false)">脚本</a-doption>
                            <a-doption @click="emits('addAdjacentStep',el,'iframe',false)">iframe切换</a-doption>
                            <a-doption @click="emits('addAdjacentStep',el,'dialog',false)">对话框</a-doption>
                          </template>
                        </a-dsubmenu>
                        <a-dsubmenu v-if="el.stepType === 'WHILE' || el.stepType === 'FOR' || el.stepType === 'IF'"
                                    position="lt" value="添加子操作">
                          <template #default>添加子操作</template>
                          <template #content>
                            <a-dsubmenu position="lt" value="浏览器操作">
                              <template #default>浏览器操作</template>
                              <template #content>
                                <a-doption @click="emits('addAdjacentStep',el,'open_page',true)">打开网页</a-doption>
                                <a-doption @click="emits('addAdjacentStep',el,'close_page',true)">关闭网页</a-doption>
                                <a-doption @click="emits('addAdjacentStep',el,'switch_tab',true)">切换tab</a-doption>
                                <a-doption @click="emits('addAdjacentStep',el,'forward',true)">前进</a-doption>
                                <a-doption @click="emits('addAdjacentStep',el,'back',true)">后退</a-doption>
                                <a-doption @click="emits('addAdjacentStep',el,'refresh',true)">刷新</a-doption>
                              </template>
                            </a-dsubmenu>
                            <a-dsubmenu position="lt" value="鼠标操作">
                              <template #default>鼠标操作</template>
                              <template #content>
                                <a-doption @click="emits('addAdjacentStep',el,'click',true)">点击</a-doption>
                                <a-doption @click="emits('addAdjacentStep',el,'hover',true)">悬停</a-doption>
                                <a-doption @click="emits('addAdjacentStep',el,'drag',true)">鼠标拖拽</a-doption>
                              </template>
                            </a-dsubmenu>
                            <a-dsubmenu position="lt" value="循环">
                              <template #default>循环</template>
                              <template #content>
                                <a-doption @click="emits('addAdjacentStep',el,'for',true)">for循环</a-doption>
                                <a-doption @click="emits('addAdjacentStep',el,'while',true)">while循环</a-doption>
                              </template>
                            </a-dsubmenu>
                            <a-doption @click="emits('addAdjacentStep',el,'wait',true)">等待</a-doption>
                            <a-dsubmenu position="lt" value="api_request_child_ui">
                              <template #default>HTTP请求</template>
                              <template #content>
                                <a-doption @click="emits('addAdjacentStep',el,'api_request',true,'new')">新建接口</a-doption>
                                <a-doption @click="emits('addAdjacentStep',el,'api_request',true,'import')">引入已有用例</a-doption>
                              </template>
                            </a-dsubmenu>
                            <a-dsubmenu position="lt" value="sql_child_ui">
                              <template #default>SQL查询</template>
                              <template #content>
                                <a-doption @click="emits('addAdjacentStep',el,'sql',true,'new')">新建SQL接口</a-doption>
                                <a-doption @click="emits('addAdjacentStep',el,'sql',true,'import')">引入已有SQL接口</a-doption>
                              </template>
                            </a-dsubmenu>
                            <a-doption @click="emits('addAdjacentStep',el,'extract',true)">关联提取</a-doption>
                            <a-doption @click="emits('addAdjacentStep',el,'keyboard',true)">键盘操作</a-doption>
                            <a-doption @click="emits('addAdjacentStep',el,'element_dom_operation',true)">元素DOM操作</a-doption>
                            <a-doption @click="emits('addAdjacentStep',el,'file_upload',true)">上传文件</a-doption>
                            <a-doption @click="emits('addAdjacentStep',el,'assert',true)">断言</a-doption>
                            <a-doption @click="emits('addAdjacentStep',el,'if',true)">if判断</a-doption>
                            <a-doption @click="emits('addAdjacentStep',el,'script',true)">脚本</a-doption>
                            <a-doption @click="emits('addAdjacentStep',el,'iframe',true)">iframe切换</a-doption>
                            <a-doption @click="emits('addAdjacentStep',el,'dialog',true)">对话框</a-doption>
                          </template>
                        </a-dsubmenu>
                      </template>
                    </template>
                  </a-dropdown>
                </div>
                <!-- 行内快捷操作：单步运行、启用/禁用、复制、删除 -->
                <div style="display: flex; align-items: center; flex-shrink: 0;">
                  <!-- UI 场景：执行到此步骤（未调试从头跑并停在该步骤前；暂停中继续跑到该步骤前再停） -->
                  <a-tooltip v-if="!props.isApiScene"
                             :content="isStepLocked(el.id) ? '该步骤已执行，无法作为目标' : '执行到此步骤'">
                    <a-button v-permission="'auto:scene:execute'" type="text" shape="circle" size="small"
                              :disabled="mutationsLocked || isStepLocked(el.id)"
                              @click.stop="emits('runUntilStep', el)">
                      <template #icon>
                        <icon-thunderbolt style="color: #ff7d00"/>
                      </template>
                    </a-button>
                  </a-tooltip>
                  <!-- API 场景顶级步骤：单步 / 从此处执行 -->
                  <a-dropdown v-if="props.isApiScene && (el.parentId == null || el.parentId === 0)"
                              position="left">
                    <a-tooltip content="单步 / 从此处执行">
                      <a-button v-permission="'auto:scene:execute'" type="text" shape="circle" size="small" :disabled="isDebugging" @click.stop>
                        <template #icon>
                          <icon-play-circle style="color: #00b42a"/>
                        </template>
                      </a-button>
                    </a-tooltip>
                    <template #content>
                      <a-doption @click="emits('runSingleStep', el)">仅执行此步骤</a-doption>
                      <a-doption @click="emits('runFromStep', el)">从此步骤开始执行</a-doption>
                    </template>
                  </a-dropdown>
                  <a-tooltip :content="isStepLocked(el.id) ? '调试中，已执行步骤不可修改' : (el.isDisable === 0 ? '禁用该步骤' : '启用该步骤')">
                    <a-button v-permission="'auto:step:update'" type="text" shape="circle" size="small"
                              :disabled="isStepLocked(el.id)"
                              @click.stop="disableStep(el.id)">
                      <template #icon>
                        <icon-eye v-if="el.isDisable === 0"/>
                        <icon-eye-invisible v-else style="color: #c9cdd4"/>
                      </template>
                    </a-button>
                  </a-tooltip>
                  <a-tooltip :content="mutationsLocked ? '调试运行中，暂停后可复制步骤' : '复制该步骤'">
                    <a-button v-permission="'auto:step:create'" type="text" shape="circle" size="small"
                              :disabled="mutationsLocked"
                              @click.stop="copyStep(el.id)">
                      <template #icon>
                        <icon-copy/>
                      </template>
                    </a-button>
                  </a-tooltip>
                  <a-tooltip :content="isStepLocked(el.id) ? '调试中，已执行步骤不可删除' : '删除该步骤'">
                    <a-button v-permission="'auto:step:delete'" type="text" shape="circle" size="small" status="danger"
                              :disabled="isStepLocked(el.id)"
                              @click.stop="deleteStep(el.id)">
                      <template #icon>
                        <icon-delete/>
                      </template>
                    </a-button>
                  </a-tooltip>
                </div>
              </div>
            </a-card>
          </template>
        </a-tree>
      </a-row>
    </div>
    <!--   步骤右侧编辑抽屉-->
    <StepDetailDraw
        :width="520"
        ref="stepDetailFormRef"
        v-model:visible="stepFormVisible"
        :title="`${getStepTypeChinese(currentStepDetail.stepType)}${!hasStepUpdatePermission ? '（只读）' : (isCurrentStepLocked ? '（调试中只读）' : '')}`"
        :form-fields="currentOperationFormConfig"
        :form-data="currentStepDetail"
        :show-setting-tab="showSettingTab"
        :scene-environment-id="props.sceneEnvironmentId"
        :submit-disabled="!hasStepUpdatePermission || isCurrentStepLocked"
        :disabled="!hasStepUpdatePermission || isCurrentStepLocked"
        @cancel="emits('reloadStepInfo')"
        @submit="handleSubmit"
    />

    <!--    步骤右侧调试结果抽屉-->
    <StepDebugResultDraw :visible="debugResultDrawerVisible" :step-result="currentStepDebugResult"
                         @cancel="debugResultDrawerVisible = false"/>

    <!--    API步骤调试结果抽屉-->
    <ApiStepDebugResult
        v-if="currentApiStepResult"
        :visible="apiStepDebugResultVisible"
        :step-result="currentApiStepResult"
        @update:visible="apiStepDebugResultVisible = $event"
    />
  </div>
</template>

<script setup lang="ts">
import {computed, nextTick, ref, toRaw, toRef, watch} from 'vue'
import {Message, Modal} from "@arco-design/web-vue";
import {
  addStep,
  copyStepById,
  deleteStepById,
  getStepDetail,
  updateDisableStatus,
  updateStep,
  updateStepSort
} from "@/api/MyApi/step";
import StepDebugResultDraw from "@/views/scene/component/StepDebugResultDraw.vue";
import ApiStepDebugResult from "@/views/scene/component/ApiStepDebugResult.vue";
import ScreenshotImage from "@/views/scene/component/ScreenshotImage.vue";
import StepDetailDraw from "@/views/scene/component/StepDetailDraw.vue";
import {collectSkippedDescendantIds} from "@/views/scene/component/skippedSteps";
import {StepType} from "@/types/dto/StepDetailDTO";
import {TestStep} from "@/types/domain/TestStep";
import {STEP_REGISTRY} from "@/schema/stepFormConfig/FormConfig";
import {StepVO} from "@/types/vo/StepVO";
import {getStepTypeChinese, isSpecifiedStepType} from "@/types/enum/StepType";
import {useProjectStore} from "@/store";
import usePermission from "@/hooks/permission";
import {useDebugStepLock} from "@/views/scene/component/useDebugStepLock";

interface IList {
  id: number
  stepType: string
  stepName: string
  description?: string
  parentId: number
  orderIndex: number
  projectId: string
  scenarioId: string
  elementId?: string
  customElementType?: string
  customElementValue?: string
  children: IList[]
}

interface Props {
  modelValue: IList[];
  // 是否debug状态
  isDebugging: false;
  // 步骤调试列表
  stepDebugList: any[];
  // 场景id
  sceneId: number;
  checkedKeys: number[];
  // 是否是API场景
  isApiScene?: boolean;
  // 场景关联的环境ID
  sceneEnvironmentId?: number;
  // 调试状态（未运行/运行中/暂停/失败挂起）
  debugStatus?: string;
  // 失败挂起的失败步骤id（该步骤在挂起期间允许修改）
  pausedFailureStepId?: number | null;
}

const treeRef = ref();

const projectStore = useProjectStore();
const permission = usePermission();

const hasStepCreatePermission = computed(() => permission.hasPermission('auto:step:create'));
const hasStepUpdatePermission = computed(() => permission.hasPermission('auto:step:update'));
const hasStepDeletePermission = computed(() => permission.hasPermission('auto:step:delete'));

// 当前操作的表单配置
const currentOperationFormConfig = ref();

// 当前步骤详情
const currentStepDetail = ref<StepType>({});

// 控制流步骤不需要设置/断言/关联提取标签页（仅 API 场景）
const showSettingTab = computed(() => {
  if (!props.isApiScene) return true;
  const controlStepTypes = ['FOR', 'WHILE', 'IF', 'WAIT'];
  return !controlStepTypes.includes(currentStepDetail.value?.stepType);
});

// 当前步骤的调试结果
const currentStepDebugResult = ref();

// 调试结果抽屉开关
const debugResultDrawerVisible = ref(false);

// API步骤调试结果抽屉开关
const apiStepDebugResultVisible = ref(false);

// 当前API步骤的调试结果
const currentApiStepResult = ref();

// 当前完整步骤信息
const currentStep = ref<TestStep>({} as TestStep);


const props = defineProps<Props>()

const stepList = ref([...props.modelValue]);
const stepDebugList = ref<any[]>([]);

interface Emits {
  (e: 'update:modelValue', value: IList[]): void

  (e: 'refreshSceneStepList'): void

  (e: 'addAdjacentStep', step: TestStep, stepType: string, isChildren: boolean, source?: 'new' | 'import')

  (e: 'reloadStepInfo'): void

  (e: 'update:checkedKeys', value: number[]): void

  (e: 'runFromStep', step: TestStep): void

  (e: 'runSingleStep', step: TestStep): void

  (e: 'runUntilStep', step: IList): void
}

const emits = defineEmits<Emits>()

// ===== 调试期间的步骤编辑锁定 =====
const debugStatusRef = computed(() => props.debugStatus || '未运行')
const pausedFailureStepIdRef = computed(() => props.pausedFailureStepId ?? null)
const {mutationsLocked, isStepLocked} = useDebugStepLock({
  isDebugging: toRef(props, 'isDebugging'),
  debugStatus: debugStatusRef,
  stepDebugList: toRef(props, 'stepDebugList'),
  pausedFailureStepId: pausedFailureStepIdRef,
  isApiScene: toRef(props, 'isApiScene'),
})

// 当前抽屉中步骤是否被调试锁定（只读）
const isCurrentStepLocked = computed(() => {
  return currentStep.value?.id != null && isStepLocked(currentStep.value.id)
})

// 调试锁定时给树节点禁用复选框，批量操作天然排除锁定步骤
const decorateTreeLock = (nodes: any[]): any[] => {
  return (nodes || []).map((node: any) => ({
    ...node,
    disableCheckbox: isStepLocked(node.id),
    children: Array.isArray(node.children) ? decorateTreeLock(node.children) : node.children,
  }))
}

// ===== 步骤间变量数据流（可见性） =====
// 读取某步骤 API 提取规则里配置的变量名
const getStepExtractVars = (el: any): string[] => {
  try {
    const detail = typeof el?.stepDetail === 'string' ? JSON.parse(el.stepDetail) : el?.stepDetail;
    const list = detail?.apiConfig?.associationExtraction;
    if (!Array.isArray(list)) return [];
    return list
        .map((r: any) => r?.variableName)
        .filter((n: any) => n != null && String(n).trim() !== '');
  } catch (e) {
    return [];
  }
};

const list = computed({
  get: () => props.modelValue,
  set: value => emits('update:modelValue', value)
})

// 树展示数据：调试期间给锁定步骤禁用复选框（未调试时直接透传，避免多余克隆）
const displayList = computed(() => {
  if (!props.isDebugging) return props.modelValue;
  return decorateTreeLock(props.modelValue as any[]);
})

// 清除 Arco 树残留的拖拽落点指示（蓝条）：
// drop 后触发数据刷新，树内部 dragOver 状态可能未复位，导致 gap/highlight 类一直挂在节点标题上
const clearTreeDragIndicator = () => {
  nextTick(() => {
    const root = treeRef.value?.$el as HTMLElement | undefined;
    root?.querySelectorAll('.arco-tree-node-title-gap-top, .arco-tree-node-title-gap-bottom, .arco-tree-node-title-highlight')
        .forEach((el) => el.classList.remove(
            'arco-tree-node-title-gap-top',
            'arco-tree-node-title-gap-bottom',
            'arco-tree-node-title-highlight'
        ));
  });
};

// 步骤列表刷新后清一次（drop 成功 → 父组件重载列表 → 新数据渲染完成后清除残留指示）
watch(() => props.modelValue, () => clearTreeDragIndicator());

// 进入调试时也清一次，避免调试前的拖拽残留带进调试态
watch(() => props.isDebugging, (v) => {
  if (v) clearTreeDragIndicator();
});
// 选择的树节点
const checkTreeNodes = computed({
  get: () => props.checkedKeys,
  set: value => emits('update:checkedKeys', value)
})

// 递归在调试结果中查找指定步骤（支持 childrenResults）
// 注意：使用 == 比较，兼容 number/string 类型差异
const findStepInDebugResults = (arr: any[], stepId: string | number): any | null => {
  for (const item of arr) {
    if (item.step && item.step.id == stepId) {
      return item;
    }
    if (item.childrenResults && item.childrenResults.length > 0) {
      const found = findStepInDebugResults(item.childrenResults, stepId);
      if (found) return found;
    }
  }
  return null;
};

// 调试结果缓存 Map：stepId → 结果项（含 childrenResults）
// 用于 O(1) 查找，避免每次渲染都递归搜索
const debugResultMap = computed(() => {
  const map = new Map<string | number, any>();
  const traverse = (arr: any[]) => {
    for (const item of arr) {
      if (item.step?.id != null && !map.has(item.step.id)) {
        map.set(item.step.id, item);
      }
      if (item.childrenResults?.length > 0) {
        traverse(item.childrenResults);
      }
    }
  };
  if (stepDebugList.value?.length > 0) {
    traverse(stepDebugList.value);
  }
  return map;
});

// 判断步骤是否执行完毕
// IF 条件不成立被跳过的子步骤不会有结果推送，按已结束（跳过）处理，否则一直显示加载中
const skippedDescendantIds = computed(() =>
    collectSkippedDescendantIds(stepDebugList.value, props.modelValue as any[]));

const isStepDebugFinished = (stepId: string | number) => {
  if (skippedDescendantIds.value.has(stepId)) {
    return true;
  }
  if (props.stepDebugList.length === 0) {
    return false;
  }
  return debugResultMap.value.has(stepId);
}


// 判断步骤是否调试成功
const getStepDebugStatus = (stepId: string | number) => {
  if (stepDebugList.value.length === 0) {
    return undefined;
  }

  const item = debugResultMap.value.get(stepId);
  if (!item) {
    // 容器条件不成立被跳过的后代步骤：按 SKIPPED 展示
    if (skippedDescendantIds.value.has(stepId)) return 'SKIPPED';
    return undefined;
  }

  // 如果有 childrenResults（API 场景的循环/条件步骤）
  if (item.childrenResults && item.childrenResults.length > 0) {
    if (item.childrenResults.some((r: any) => (r.result?.status || r.status) === 'FAILURE')) return 'FAILURE';
    if (item.childrenResults.every((r: any) => (r.result?.status || r.status) === 'SUCCESS')) return 'SUCCESS';
    if (item.childrenResults.every((r: any) => (r.result?.status || r.status) === 'SKIPPED')) return 'SKIPPED';
    return 'FAILURE';
  }

  // 原有 UI 场景的循环判断逻辑
  if (item.isLoop > 0) {
    if (item.iterations && Object.keys(item.iterations).length > 0) {
      const iterationValues = Object.values(item.iterations);
      if (iterationValues.some((iter: any) => iter.status === 'FAILURE')) return 'FAILURE';
      if (iterationValues.every((iter: any) => iter.status === 'SKIPPED')) return 'SKIPPED';
      if (iterationValues.every((iter: any) => iter.status === 'SUCCESS')) return 'SUCCESS';
      return 'FAILURE';
    }
    return 'SKIPPED';
  }

  const hasResult = item.result && Object.keys(item.result).length > 0;
  if (!hasResult) return 'SKIPPED';
  return item.result.status;
};


// 当前步骤是否有结果
const isCurrentStepHasResult = (stepId: string | number) => {
  if (!stepId || stepDebugList.value.length === 0) {
    return false;
  }

  const item = debugResultMap.value.get(stepId);
  if (!item) return false;

  if (item.childrenResults && item.childrenResults.length > 0) return true;
  if (item.result && Object.keys(item.result).length > 0) return true;
  if (item.iterations && Object.keys(item.iterations).length > 0) return true;
  return false;
}


// 查看当前步骤的调试结果
const checkCurrentDebugResult = (stepId: string | number, stepType?: string) => {
  if (stepDebugList.value.length === 0) {
    return false;
  }

  const result = debugResultMap.value.get(stepId);
  if (!result) return;

  // API/SQL请求步骤 或 包含子结果的循环/条件步骤 使用 ApiStepDebugResult
  const isApiOrLoop = stepType === 'API_REQUEST' || stepType === 'SQL'
      || result.step?.stepType === 'API_REQUEST' || result.step?.stepType === 'SQL'
      || ['FOR', 'WHILE', 'IF'].includes(stepType || result.step?.stepType);

  if (isApiOrLoop) {
    // 需要补充步骤的stepDetail信息（用于重新发送时获取apiRequestId）
    const stepInfo = stepList.value.find((s: any) => s.id == stepId);
    if (stepInfo) {
      result.step.stepDetail = stepInfo.stepDetail;
    }

    // 如果该步骤是子步骤（在父步骤的 childrenResults 中），收集它的所有执行记录
    const isTopLevel = stepDebugList.value.some((s: any) => s.step?.id == stepId);
    if (!isTopLevel) {
      const allExecutions: any[] = [];
      stepDebugList.value.forEach((s: any) => {
        if (s.childrenResults) {
          s.childrenResults.forEach((r: any) => {
            if (r.step?.id == stepId) {
              allExecutions.push(r);
            }
          });
        }
      });
      // 如果有多次执行，把执行记录包装成 childrenResults 供迭代查看
      if (allExecutions.length > 1) {
        currentApiStepResult.value = {
          step: result.step,
          result: result.result,
          childrenResults: allExecutions
        };
        apiStepDebugResultVisible.value = true;
        return;
      }
    }

    currentApiStepResult.value = result;
    apiStepDebugResultVisible.value = true;
  } else {
    debugResultDrawerVisible.value = true;
    currentStepDebugResult.value = result;
  }
}

const deleteStep = (id: number) => {
  if (isStepLocked(id)) {
    Message.warning('调试中，已执行步骤不可删除')
    return;
  }
  Modal.warning({
    title: '确认删除？',
    content: () => '确认删除该步骤？删除后，步骤不可恢复！',
    cancelText: '取消',
    okText: '确认',
    hideCancel: false,
    onOk: async () => {
      const result = await deleteStepById(id);
      if (result.data) {
        Message.success({
          content: '删除成功',
          duration: 1000
        })
        // 重新加载步骤列表
        emits('refreshSceneStepList')
      } else {
        Message.error({
          content: '删除失败',
          duration: 1000
        })
      }
    }
  });
}

const disableStep = async (id: number) => {
  if (isStepLocked(id)) {
    Message.warning('调试中，已执行步骤不可修改')
    return;
  }
  // 获取当前步骤详情
  const result = await getStepDetail(id);
  const temp = result.data;
  let name = temp.isDisable;
  const update = await updateDisableStatus(id);
  if (update.data === true) {
    Message.success({
      content: name === 0 ? '禁用成功' : '启用成功',
      duration: 1000
    })
  } else {
    Message.error({
      content: name === 0 ? '禁用失败' : '启用失败',
      duration: 1000
    })
  }
  emits('refreshSceneStepList');
}

const stepFormVisible = ref(false);

// 编辑步骤
const editStep = async (stepId: number[]) => {
  stepFormVisible.value = true;
  // 获取当前步骤详情
  const result = await getStepDetail(stepId[0]);
  currentStep.value = result.data;
  currentStepDetail.value = {};
  currentStepDetail.value = JSON.parse(result.data.stepDetail);
  // 插入一个步骤顺序数据
  currentStepDetail.value.orderIndex = result.data.orderIndex;
  currentOperationFormConfig.value = STEP_REGISTRY[result.data.stepType];
}

// 添加/编辑步骤
const handleSubmit = async (stepDetail: any) => {
  // 调试锁定兜底：运行中或已执行步骤不允许保存
  if (mutationsLocked.value || isCurrentStepLocked.value) {
    Message.warning('调试中，该步骤不可修改')
    return;
  }
  let testStep = new TestStep();
  testStep.stepName = stepDetail.stepName;
  testStep.stepDetail = stepDetail.stepDetail;
  testStep.stepType = stepDetail.stepType;
  testStep.parentId = stepDetail.parentId;
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
      emits('refreshSceneStepList')
    }
  } else {
    // 没有id就是新增
    testStep.projectId = projectStore.getProjectId;
    testStep.scenarioId = props.sceneId.id;
    const result = await addStep(testStep);
    // 插入成功，重新加载步骤列表

    if (result.data) {
      Message.success({
        content: '添加成功',
        duration: 1000
      })
      // 重新加载步骤列表
      emits('refreshSceneStepList')

    }
  }

}


const onDrop = async (params: any) => {
  // drop 结束后无条件清理 Arco 树残留的拖拽指示蓝条
  try {
    await doDrop(params);
  } finally {
    clearTreeDragIndicator();
  }
}

const doDrop = async (params: any) => {
  // 调试期间禁止拖拽排序（模板中 draggable 已禁用，这里兜底）
  if (props.isDebugging) {
    Message.warning('调试期间不可调整步骤顺序')
    return;
  }
  const {dragNode, dropNode, dropPosition} = params

  /*console.log("dropPosition", dropPosition)
  console.log("dragNode", dragNode)
  console.log("dropNode", dropNode)*/


// 验证必要参数
  if (!dragNode || !dropNode || typeof dropPosition !== "number") {
    console.error("拖拽参数不完整")
    return
  }

// 防止节点拖拽到自身
  if (dragNode.id === dropNode.id) {
    console.warn("不能将节点拖拽到自身")
    return
  }
  const findNodeById = (data: StepVO[], targetId: number): StepVO | undefined => {
    if (!data || data.length === 0) return undefined;

    for (const item of data) {
      if (item.id === targetId) {
        return item;
      }

      if (item.children && item.children.length > 0) {
        const found = findNodeById(item.children, targetId);
        if (found) return found;
      }
    }
    return undefined;
  };

  const findNodeIndex = (data: StepVO[], targetId: number): number | undefined => {
    if (!data || data.length === 0) return undefined;

    for (let i = 0; i < data.length; i++) {
      const item = data[i];

      if (item.id === targetId) {
        return i;
      }

      if (item.children && item.children.length > 0) {
        const found = findNodeIndex(item.children, targetId);
        if (found !== undefined) return found;
      }
    }
    return undefined;
  };

// 创建数据的深拷贝
  const data = JSON.parse(JSON.stringify(stepList.value)) as StepVO[]
  // 判断目标节点是不是允许拖拽的
  if (dropPosition === 0 && !isSpecifiedStepType(dropNode.stepType)) {
    Message.warning({
      content: '不支持的拖拽',
      duration: 1000
    })
    return;
  } else {
    const node = findNodeById(data, dropNode.parentId);
    console.log("node", node);
    if (dragNode.parentId === 0 && node && node?.parentId !== 0 && !isSpecifiedStepType(node?.stepType)) {
      Message.warning({
        content: '不支持的拖拽',
        duration: 1000
      })
      return;
    }
  }


  // 1. 移除被拖拽的节点
  const removeNode = (data: StepVO[], removeId: number) => {
    data.forEach((item: StepVO, index: number) => {
      if (item.id === removeId) {
        data.splice(index, 1)
      } else {
        if (item.children) {
          removeNode(item.children, removeId)
        }
      }
    })
  }

  // 2. 将被拖拽的节点放到目标节点的children里边
  const insertNode = (data: StepVO[], targetId: number, insertToNode: StepVO) => {
    const targetNode = findNodeById(data, targetId);
    // 深拷贝即将插入的节点（被拖拽的节点）
    const childrenNode = JSON.parse(JSON.stringify(insertToNode));
    if (targetNode != null) {
      // 修改父节点
      childrenNode.parentId = targetNode.id;
      if (targetNode.children) {
        targetNode.children.push(childrenNode);
      }
    }
  }

  // 3. 更新orderIndex
  const updateOrderIndex = (data: StepVO[]) => {
    if (!data || data.length <= 0) return;
    data.forEach((item: StepVO, index: number) => {
      item.orderIndex = index + 1;
      if (item.children) {
        updateOrderIndex(item.children)
      }
    })
  }


  // 插入兄弟节点
  const insertBroNode = (data: StepVO[], parentId: number, insertToNode: StepVO, dropPosition: number) => {
    const targetNode = findNodeById(data, parentId);
    // 深拷贝即将插入的节点（被拖拽的节点）
    const childrenNode = JSON.parse(JSON.stringify(insertToNode));
    const insertIndex = findNodeIndex(data, dropNode.id) as number;
    if (parentId === 0) {
      if (dropPosition === 1) {
        // 向下插入
        data.splice(insertIndex + 1, 0, childrenNode)
      } else if (dropPosition === -1) {
        // 向上插入
        data.splice(insertIndex, 0, childrenNode)
      }
      // 更新此节点的父节点
      childrenNode.parentId = 0;
      return;
    }
    if (targetNode != null) {
      // 修改父节点
      childrenNode.parentId = parentId;
      targetNode.children = targetNode.children || []

      if (dropPosition === 1) {

        // 向下插入
        targetNode.children.splice(insertIndex + 1, 0, childrenNode)
      } else if (dropPosition === -1) {
        // 向上插入
        targetNode.children.splice(insertIndex, 0, childrenNode)
      }

    }

  }

  // 如果 dropPosition 是0，那就一定是成为子节点
  if (dropPosition === 0) {
    // console.log("子节点")
    removeNode(data, dragNode.id!)
    updateOrderIndex(data)

    insertNode(data, dropNode.id!, dragNode)

  } else {
    // 插入兄弟节点
    // console.log("插入兄弟节点")
    // 获取父节点id（父节点就是目标节点的父节点（拥有共同的父节点））
    const parentId = dropNode.parentId | 0;

    removeNode(data, dragNode.id!)
    updateOrderIndex(data)

    insertBroNode(data, parentId, dragNode, dropPosition)

  }

  // 更新位置
  updateOrderIndex(data)
  // emits('update:modelValue', data);
  // return;
  const axiosResponse = await updateStepSort(data)
  if (axiosResponse.data) {
    Message.success({
      content: "更新成功",
      duration: 1000,
    })
  } else {
    Message.error({
      content: "更新失败",
      duration: 1000,
    })
  }
  emits("refreshSceneStepList")
}

const copyStep = async (copyId: number) => {
  if (!copyId) return;
  if (mutationsLocked.value) {
    Message.warning('调试运行中，暂停后可复制步骤')
    return;
  }
  const result = await copyStepById(copyId);
  if (result.data) {
    Message.success({
      content: '复制成功',
      duration: 1000
    })
    emits('refreshSceneStepList',)
  } else {
    Message.error({
      content: '复制失败',
      duration: result.msg
    })
  }
}

// 获取对应场景的截图
const getDebugScreenShotByStepId = (stepId: number): string | undefined => {
  if (!stepId || !stepDebugList.value || stepDebugList.value.length <= 0) {
    return undefined;
  }

  const foundItem = stepDebugList.value.find((item: any) => {
    const parsed = item;
    return parsed?.step?.id === stepId && ((parsed?.isLoop > 0 && parsed?.iterations) || (parsed?.isLoop === 0 && parsed?.result));
  });

  if (foundItem) {
    // 判断isLoop如果>0，那就取iterations（是一个map）最后一个元素的value
    if (foundItem?.isLoop > 0 && foundItem?.iterations) {
      const iterationValues = Object.values(foundItem.iterations);
      if (iterationValues.length > 0) {
        // 取最后一个元素的value
        const lastIteration = iterationValues[iterationValues.length - 1] as any;
        return lastIteration?.screenshotPath || undefined;
      }
    }
    // 如果isLoop=0，那就返回result中的screenshotPath
    else if (foundItem?.isLoop === 0 && foundItem?.result) {
      return foundItem?.result?.screenshotPath || undefined;
    }
    // 如果不符合上述条件，返回原始的screenshotPath
    else {
      return foundItem?.screenshotPath;
    }
  }
  return undefined;
}

// 全选节点
const checkAllNode = (checked: boolean) => {
  treeRef.value.checkAll(checked);
}


watch(() => stepFormVisible.value, (newVal: any) => {
  // 只要newVal关闭，就清空当前步骤信息
  if (!newVal) {
    currentStep.value = {};
    currentStepDetail.value = {};
  }
})

watch(() => props.modelValue, (newVal) => {
  stepList.value = newVal;
}, {deep: true})

watch(() => props.stepDebugList, async (newVal) => {
  stepDebugList.value = newVal;
  // 如果 API 步骤调试抽屉正在打开，同步更新为最新结果（解决 replaceOrPush 后引用失效问题）
  if (apiStepDebugResultVisible.value && currentApiStepResult.value?.step?.id != null) {
    const latest = findStepInDebugResults(stepDebugList.value, String(currentApiStepResult.value.step.id));
    if (latest) {
      currentApiStepResult.value = latest;
    }
  }
}, {deep: true})


</script>

<style scoped>
.container {
  margin-bottom: 20px;
  height: 100%;
  width: 100%;
}

.active {
  border: 1px solid #409EFF;
  background-color: #ecf5ff;
}


:deep(.arco-tree-node) {
  width: 100% !important;
}

:deep(.arco-tree-node-title) {
  width: 100% !important;
  flex: 1 !important;
}

:deep(.arco-tree-node-title-text) {
  width: 100% !important;
  flex: 1 !important;
}

:deep(.arco-card) {
  width: 100% !important;
  margin: 0 !important;
}

:deep(.arco-card-body) {
  padding: 12px !important;
}

.debug-result-trigger {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  padding: 4px 10px;
  border-radius: 4px;
  transition: background-color 0.2s;
  user-select: none;
  white-space: nowrap;
}

.debug-result-trigger:hover {
  background-color: var(--color-fill-2);
}

.debug-status-icon {
  font-size: 18px;
}

.debug-status-success {
  color: #3cc071;
}

.debug-status-failure {
  color: #f53f3f;
}

.debug-status-skipped {
  color: #86909c;
}

.debug-result-text {
  color: var(--color-text-2);
  font-size: 13px;
}

/* ===== 调试状态卡片边框条 ===== */
/* 已执行完的步骤：整条边框显示状态色（成功绿/失败红/跳过灰） */
:deep(.arco-card).step-debug-success {
  border-color: #3cc071;
}

:deep(.arco-card).step-debug-failure {
  border-color: #f53f3f;
}

:deep(.arco-card).step-debug-skipped {
  border-color: #a9aeb8;
}

</style>
