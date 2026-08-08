<template>
  <component :is="wrapperComponent" v-bind="wrapperProps">
    <div class="env-manager-layout">
      <!-- 左侧菜单 -->
      <div class="left-menu">
        <div class="menu-header">
          <span class="title">全局</span>
        </div>

        <!-- 全局配置项 -->
        <div
          class="menu-item"
          :class="{
            active: selectedType === 'global' && selectedKey === 'params',
          }"
          @click="selectGlobalItem('params')"
        >
          <icon-settings class="menu-icon" />
          <span>全局参数</span>
        </div>
        <div
          class="menu-item"
          :class="{
            active: selectedType === 'global' && selectedKey === 'script',
          }"
          @click="selectGlobalItem('script')"
        >
          <icon-file class="menu-icon" />
          <span>全局脚本</span>
        </div>

        <div class="menu-divider"></div>

        <!-- 环境列表 -->
        <div class="menu-header">
          <span class="title">环境</span>
          <a-button v-permission="'auto:env:create'" type="text" size="mini" @click="openNewEnvModal">
            <icon-plus />
          </a-button>
        </div>

        <div class="env-list">
          <div
            v-for="env in envList"
            :key="env.id"
            class="menu-item env-item"
            :class="{
              active: selectedType === 'env' && selectedEnvId === env.id,
            }"
            @click="selectEnv(env.id)"
          >
            <icon-folder class="menu-icon" />
            <span class="env-name">{{ env.envName }}</span>
            <a-dropdown
              v-if="hasAnyEnvPermission"
              trigger="click"
              @select="(key: string) => handleEnvAction(key, env)"
            >
              <div class="env-actions" @click.stop>
                <icon-more />
              </div>
              <template #content>
                <a-doption v-permission="'auto:env:update'" key="rename" value="rename">重命名</a-doption>
                <a-doption v-permission="'auto:env:create'" key="copy" value="copy">复制</a-doption>
                <a-doption v-permission="'auto:env:delete'" key="delete" value="delete">删除</a-doption>
              </template>
            </a-dropdown>
          </div>
        </div>

        <a-button
          v-permission="'auto:env:create'"
          type="dashed"
          long
          size="small"
          @click="openNewEnvModal"
          class="add-env-btn"
        >
          <icon-plus />
          新建环境
        </a-button>
      </div>

      <!-- 右侧内容区 -->
      <div class="right-content">
        <!-- 全局参数（带 Tab） -->
        <div
          v-if="selectedType === 'global' && selectedKey === 'params'"
          class="content-panel"
        >
          <div class="panel-header">
            <span class="title">全局参数</span>
          </div>
          <div class="panel-body">
            <a-tabs v-model:active-key="globalParamsActiveTab" type="line">
              <a-tab-pane :key="GlobalRequestVarType.COOKIE">
                <template #title><a-badge :count="globalCookies.length" :number-style="tabBadgeStyle" :offset="[8, 0]">全局Cookie</a-badge></template>
                <div class="tab-description">
                  开启状态的全局cookie将自动带到该团队下的每一个接口内
                </div>
                <GlobalParameterTable
                  ref="globalCookieTableRef"
                  :type="GlobalRequestVarType.COOKIE"
                  :team-id="props.teamId"
                  :data="globalCookies"
                  :disabled="!hasGlobalVarUpdatePermission"
                  @add="handleGlobalVarAdd"
                  @update="handleGlobalVarUpdate"
                  @delete="handleGlobalVarDelete"
                />
              </a-tab-pane>
              <a-tab-pane :key="GlobalRequestVarType.HEADER">
                <template #title><a-badge :count="globalHeaders.length" :number-style="tabBadgeStyle" :offset="[8, 0]">全局Header</a-badge></template>
                <GlobalParameterTable
                  ref="globalHeaderTableRef"
                  :type="GlobalRequestVarType.HEADER"
                  :team-id="props.teamId"
                  :data="globalHeaders"
                  :disabled="!hasGlobalVarUpdatePermission"
                  @add="handleGlobalVarAdd"
                  @update="handleGlobalVarUpdate"
                  @delete="handleGlobalVarDelete"
                />
              </a-tab-pane>
              <a-tab-pane :key="GlobalRequestVarType.VARIABLE">
                <template #title><a-badge :count="globalVariables.length" :number-style="tabBadgeStyle" :offset="[8, 0]">全局变量</a-badge></template>
                <GlobalParameterTable
                  ref="globalVariableTableRef"
                  :type="GlobalRequestVarType.VARIABLE"
                  :team-id="props.teamId"
                  :data="globalVariables"
                  :disabled="!hasGlobalVarUpdatePermission"
                  @add="handleGlobalVarAdd"
                  @update="handleGlobalVarUpdate"
                  @delete="handleGlobalVarDelete"
                />
              </a-tab-pane>
              <a-tab-pane :key="GlobalRequestVarType.ASSERT">
                <template #title><a-badge :count="globalAsserts.length" :number-style="tabBadgeStyle" :offset="[8, 0]">全局断言</a-badge></template>
                <div class="tab-description">
                  开启状态的全局断言将自动应用到该团队下的每一个接口的响应校验中
                </div>
                <a-table
                  :data="globalAsserts"
                  bordered
                  :pagination="false"
                >
                  <template #columns>
                    <a-table-column title="状态" :width=80>
                      <template #cell="{ record }">
                        <a-switch
                          size="small"
                          v-model="record.disabled"
                          :checked-value="false"
                          :unchecked-value="true"
                          :disabled="!hasGlobalVarUpdatePermission"
                          @change="() => handleAssertStatusChange(record)"
                        />
                      </template>
                    </a-table-column>
                    <a-table-column title="断言名称" :width=150>
                      <template #cell="{ record }">
                        <span>{{ record.name }}</span>
                      </template>
                    </a-table-column>
                    <a-table-column title="断言规则">
                      <template #cell="{ record }">
                        <span class="assert-summary">{{ getAssertSummary(record) }}</span>
                      </template>
                    </a-table-column>
                    <a-table-column title="操作" :width=120>
                      <template #cell="{ rowIndex }">
                        <a-space>
                          <a-button :disabled="!hasGlobalVarUpdatePermission" type="text" size="small" @click="openEditAssertModal(rowIndex)">
                            <icon-edit />
                          </a-button>
                          <a-button :disabled="!hasGlobalVarUpdatePermission" type="text" status="danger" size="small" @click="handleAssertDelete(rowIndex)">
                            <icon-delete />
                          </a-button>
                        </a-space>
                      </template>
                    </a-table-column>
                  </template>
                </a-table>
                <a-button :disabled="!hasGlobalVarUpdatePermission" type="dashed" long size="small" @click="openAddAssertModal" class="add-btn">
                  <icon-plus />
                  添加全局断言
                </a-button>
              </a-tab-pane>
            </a-tabs>
          </div>
        </div>

        <!-- 全局脚本 -->
        <div
          v-if="selectedType === 'global' && selectedKey === 'script'"
          class="content-panel"
        >
          <div class="panel-header">
            <span class="title">全局脚本</span>
          </div>
          <div class="panel-body">
            <a-tabs v-model:active-key="globalScriptActiveTab" type="line">
              <a-tab-pane key="pre" title="前置脚本">
                <a-textarea
                  v-model="globalPreScript"
                  placeholder="前置脚本（在请求发送前执行）"
                  :auto-size="{ minRows: 15, maxRows: 20 }"
                  :disabled="!hasGlobalVarUpdatePermission"
                  @blur="saveGlobalScript"
                />
              </a-tab-pane>
              <a-tab-pane key="post" title="后置脚本">
                <a-textarea
                  v-model="globalPostScript"
                  placeholder="后置脚本（在请求响应后执行）"
                  :auto-size="{ minRows: 15, maxRows: 20 }"
                  :disabled="!hasGlobalVarUpdatePermission"
                  @blur="saveGlobalScript"
                />
              </a-tab-pane>
            </a-tabs>
          </div>
        </div>

        <!-- 环境详情 -->
        <div v-if="selectedType === 'env'" class="content-panel">
          <div class="panel-header">
            <a-space>
              <span class="title">环境名称</span>
              <a-input
                v-if="currentEnvData"
                aria-label="1"
                v-model="currentEnvData.envName"
                :disabled="!hasEnvUpdatePermission"
                @blur="saveCurrentEnvironment"
              />
            </a-space>
          </div>
          <div class="panel-body env-detail">
            <a-tabs v-model:active-key="envActiveTab" type="line">
              <a-tab-pane key="envVar">
                <template #title><a-badge :count="envVarCount" :number-style="tabBadgeStyle" :offset="[8, 0]">环境变量</a-badge></template>
                <EnvironmentParameterTable
                  ref="envVarTableRef"
                  :field-name="'envVar'"
                  :disabled="!hasEnvUpdatePermission"
                  @add="handleEnvVarAdd"
                  @update="handleEnvVarUpdate"
                  @delete="handleEnvVarDelete"
                />
              </a-tab-pane>
              <a-tab-pane key="cookies">
                <template #title><a-badge :count="envCookieCount" :number-style="tabBadgeStyle" :offset="[8, 0]">Cookie</a-badge></template>
                <div class="tab-description">
                  开启状态的Cookie将自动带到该环境下的每一个接口内
                </div>
                <EnvironmentParameterTable
                  ref="envCookieTableRef"
                  :field-name="'cookies'"
                  :disabled="!hasEnvUpdatePermission"
                  @add="handleEnvVarAdd"
                  @update="handleEnvVarUpdate"
                  @delete="handleEnvVarDelete"
                />
              </a-tab-pane>
              <a-tab-pane key="headers">
                <template #title><a-badge :count="envHeaderCount" :number-style="tabBadgeStyle" :offset="[8, 0]">Header</a-badge></template>
                <EnvironmentParameterTable
                  ref="envHeaderTableRef"
                  :field-name="'headers'"
                  :disabled="!hasEnvUpdatePermission"
                  @add="handleEnvVarAdd"
                  @update="handleEnvVarUpdate"
                  @delete="handleEnvVarDelete"
                />
              </a-tab-pane>
              <a-tab-pane key="serve">
                <template #title><a-badge :count="currentEnvServices.length" :number-style="tabBadgeStyle" :offset="[8, 0]">服务</a-badge></template>
                <a-table
                  :columns="serviceColumns"
                  :data="currentEnvServices"
                  bordered
                  :pagination="false"
                >
                  <template #name="{ record }">
                    <span class="cell-text">{{ record.name || '-' }}</span>
                  </template>
                  <template #address="{ record }">
                    <span class="cell-text">{{ record.address || '-' }}</span>
                  </template>
                  <template #operation="{ record, rowIndex }">
                    <a-space>
                      <a-button
                        :disabled="!hasEnvUpdatePermission"
                        type="text"
                        size="mini"
                        @click="editService(rowIndex)"
                      >编辑</a-button>
                      <a-button
                        :disabled="!hasEnvUpdatePermission"
                        type="text"
                        status="danger"
                        size="mini"
                        @click="deleteService(rowIndex)"
                      >删除</a-button>
                    </a-space>
                  </template>
                </a-table>
                <a-button
                  :disabled="!hasEnvUpdatePermission"
                  type="dashed"
                  long
                  size="small"
                  @click="addService"
                  class="add-btn"
                >
                  <icon-plus />
                  新增服务
                </a-button>
              </a-tab-pane>
              <a-tab-pane key="database">
                <template #title><a-badge :count="currentEnvDatabases.length" :number-style="tabBadgeStyle" :offset="[8, 0]">数据库</a-badge></template>
                <a-table
                  :columns="databaseColumns"
                  :data="currentEnvDatabases"
                  bordered
                  :pagination="false"
                >
                  <template #dataBaseType="{ record }">
                    <a-tag color="arcoblue" size="small">{{ record.dataBaseType || 'MYSQL' }}</a-tag>
                  </template>
                  <template #name="{ record }">
                    <span class="cell-text">{{ record.name || '-' }}</span>
                  </template>
                  <template #address="{ record }">
                    <span class="cell-text">{{ record.ip || '-' }}:{{ record.port || '-' }}</span>
                  </template>
                  <template #dbName="{ record }">
                    <span class="cell-text">{{ record.dbName || '-' }}</span>
                  </template>
                  <template #operation="{ record, rowIndex }">
                    <a-space>
                      <a-button
                        size="mini"
                        type="outline"
                        :loading="testConnLoading === record.name"
                        @click="testDbConnection(record)"
                      >
                        {{ testConnLoading === record.name ? '测试中' : '测试' }}
                      </a-button>
                      <a-button
                        :disabled="!hasEnvUpdatePermission"
                        type="text"
                        size="mini"
                        @click="editDatabase(rowIndex)"
                      >编辑</a-button>
                      <a-button
                        :disabled="!hasEnvUpdatePermission"
                        type="text"
                        status="danger"
                        size="mini"
                        @click="deleteDatabase(rowIndex)"
                      >删除</a-button>
                    </a-space>
                  </template>
                </a-table>
                <a-button
                  :disabled="!hasEnvUpdatePermission"
                  type="dashed"
                  long
                  size="small"
                  @click="addDatabase"
                  class="add-btn"
                >
                  <icon-plus />
                  新增数据库
                </a-button>
              </a-tab-pane>
            </a-tabs>
          </div>
        </div>
      </div>
    </div>

    <!-- 新建环境弹窗 -->
    <a-modal
      v-model:visible="newEnvModalVisible"
      title="新建环境"
      width="400px"
      @ok="createNewEnv"
      @cancel="newEnvModalVisible = false"
    >
      <a-form :model="newEnvForm" layout="vertical">
        <a-form-item label="环境名称" required>
          <a-input v-model="newEnvForm.envName" placeholder="请输入环境名称" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 全局断言添加/编辑弹窗 -->
    <a-modal
      v-model:visible="assertModalVisible"
      :title="assertModalTitle"
      width="560px"
      @ok="saveGlobalAssert"
      @cancel="assertModalVisible = false"
    >
      <a-form :model="assertForm" layout="vertical">
        <a-form-item label="断言名称" required>
          <a-input v-model="assertForm.name" placeholder="请输入断言名称" :disabled="!hasGlobalVarUpdatePermission" />
        </a-form-item>
        <a-form-item label="断言目标" required>
          <a-select v-model="assertForm.apiAssertType" :disabled="!hasGlobalVarUpdatePermission">
            <a-option value="BODY">响应体</a-option>
            <a-option value="HEADER">响应头</a-option>
            <a-option value="STATUS_CODE">响应码</a-option>
            <a-option value="RESPONSE_TIME">响应时间</a-option>
            <a-option value="CUSTOM">自定义</a-option>
          </a-select>
        </a-form-item>
        <a-form-item label="字段">
          <a-input
            v-model="assertForm.field"
            placeholder="字段名（如：$.data.token，响应码和响应时间可不填）"
            :disabled="assertForm.apiAssertType === 'STATUS_CODE' || assertForm.apiAssertType === 'RESPONSE_TIME' || !hasGlobalVarUpdatePermission"
          />
        </a-form-item>
        <a-form-item label="断言关系" required>
          <a-select v-model="assertForm.assertRelationship" :disabled="!hasGlobalVarUpdatePermission">
            <a-option value="EQUALS">等于</a-option>
            <a-option value="NOT_EQUALS">不等于</a-option>
            <a-option value="CONTAINS">包含</a-option>
            <a-option value="NOT_CONTAINS">不包含</a-option>
            <a-option value="GT">大于</a-option>
            <a-option value="LT">小于</a-option>
            <a-option value="GE">大于等于</a-option>
            <a-option value="LE">小于等于</a-option>
            <a-option value="REGULAR">正则匹配</a-option>
          </a-select>
        </a-form-item>
        <a-form-item label="期望值" required>
          <a-input v-model="assertForm.assertValue" placeholder="期望的值" :disabled="!hasGlobalVarUpdatePermission" />
        </a-form-item>
        <a-form-item label="状态">
          <a-switch v-model="assertForm.disabled" :checked-value="false" :unchecked-value="true" :disabled="!hasGlobalVarUpdatePermission" />
          <span style="margin-left: 8px">{{ assertForm.disabled ? '禁用' : '启用' }}</span>
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 服务新增/编辑弹窗 -->
    <a-modal
      v-model:visible="serviceModalVisible"
      :title="editingServiceIndex !== null ? '编辑服务' : '新增服务'"
      width="480px"
      @ok="saveServiceForm"
      @cancel="serviceModalVisible = false"
    >
      <a-form :model="serviceForm" layout="horizontal">
        <a-form-item label="服务名" required>
          <a-input v-model="serviceForm.name" placeholder="用于区分不同服务，如 gateway" />
        </a-form-item>
        <a-form-item label="服务地址" required>
          <a-input v-model="serviceForm.address" placeholder="如 http://192.168.1.10:8080" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 数据库新增/编辑弹窗 -->
<a-modal
      v-model:visible="dbModalVisible"
      :title="editingDbIndex !== null ? '编辑数据库' : '新增数据库'"
      width="520px"
      @ok="saveDbForm"
      @cancel="dbModalVisible = false"
    >
      <div class="db-modal-footer">
        <a-button
          type="outline"
          size="small"
          :loading="testConnLoading === dbForm.name"
          @click="testDbConnection(dbForm)"
        >
          {{ testConnLoading === dbForm.name ? '测试中...' : '测试连接' }}
        </a-button>
      </div>
      <a-form :model="dbForm" layout="horizontal">
        <a-form-item label="数据库类型">
          <a-select v-model="dbForm.dataBaseType" placeholder="选择数据库类型">
            <!-- 仅保留后端已有 JDBC 驱动的类型（与 SUPPORTED_DATA_BASE_TYPES 一致） -->
            <a-option value="MYSQL">MySQL</a-option>
            <a-option value="POSTGRESQL">PostgreSQL</a-option>
            <a-option value="SQLSERVER">SQL Server</a-option>
            <a-option value="ORACLE">Oracle</a-option>
            <a-option value="OTHER">其他</a-option>
          </a-select>
        </a-form-item>
        <a-form-item label="连接名称" required>
          <a-input v-model="dbForm.name" placeholder="用于区分不同数据库连接" />
        </a-form-item>
        <a-form-item label="IP地址" required>
          <a-input v-model="dbForm.ip" placeholder="127.0.0.1" />
        </a-form-item>
        <a-form-item label="端口" required>
          <a-input v-model="dbForm.port" placeholder="3306" />
        </a-form-item>
        <a-form-item label="数据库名" required>
          <a-input v-model="dbForm.dbName" placeholder="库名" />
        </a-form-item>
        <a-form-item label="用户名" required>
          <a-input v-model="dbForm.userName" placeholder="root" />
        </a-form-item>
        <a-form-item label="密码">
          <a-input-password v-model="dbForm.password" placeholder="密码" />
        </a-form-item>
        <a-form-item label="编码集">
          <a-input v-model="dbForm.charset" placeholder="utf8mb4" />
        </a-form-item>
        <a-form-item label="备注">
          <a-input v-model="dbForm.description" placeholder="备注说明" />
        </a-form-item>
      </a-form>
    </a-modal>
  </component>
</template>

<script setup lang="ts">
  import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue';
  import { Message, Modal } from '@arco-design/web-vue';
  import {
    IconDelete,
    IconEdit,
    IconFile,
    IconFolder,
    IconMore,
    IconPlus,
    IconSettings,
  } from '@arco-design/web-vue/es/icon';
  import GlobalParameterTable from '@/components/env-config/components/GlobalParameterTable.vue';
  import EnvironmentParameterTable from '@/components/env-config/components/EnvironmentParameterTable.vue';
  import { GlobalVar } from '@/types/domain/api/GlobalVar';
  import { GlobalRequestVarType } from '@/types/domain/api/apiEnum/GlobalRequestVarType';
  import { AddEnvDTO } from '@/types/domain/api/dto/AddEnvDTO';
  import { Environment } from '@/types/domain/api/Environment';
  import { AssertParameter } from '@/types/domain/api/requestModel/AssertParameter';
  import {
    deleteGlobalVarById,
    getGlobalArgList,
    saveOrUpdateGlobalVar,
  } from '@/api/MyApi/globalVar';
  import { AddGlobalVarDTO } from '@/types/domain/api/dto/AddGlobalVarDTO';
  import {
    copyEnv,
    deleteEnvById,
    getEnvList,
    saveOrUpdate,
    testDbConnectionApi,
  } from '@/api/MyApi/environment';
  import usePermission from '@/hooks/permission';

  // Props & Emits
  const props = defineProps<{
    modelValue?: boolean;
    teamId?: number;
    inline?: boolean;
  }>();

  const emit = defineEmits<{
    (e: 'update:modelValue', visible: boolean): void;
    (
      e: 'save',
      data: { global: GlobalVar[]; environments: Environment[] }
    ): void;
    // 全局参数相关事件
    (e: 'addGlobalVar', type: GlobalRequestVarType, data: any): void;
    (
      e: 'updateGlobalVar',
      type: GlobalRequestVarType,
      id: number,
      data: any
    ): void;
    (e: 'deleteGlobalVar', type: GlobalRequestVarType, id: number): void;
    // 全局断言相关事件
    (e: 'addGlobalAssert', data: any): void;
    (e: 'updateGlobalAssert', id: number, data: any): void;
    (e: 'deleteGlobalAssert', id: number): void;
    // 全局脚本相关事件
    (
      e: 'saveGlobalScript',
      teamId: number,
      preScript: string,
      postScript: string
    ): void;
  }>();

  const globalArgList = ref<GlobalVar[]>([]);
  const envList = ref<Environment[]>([]);

  // 显示状态
  const visible = ref(props.modelValue);

  // 页面模式 / 弹窗模式容器
  const inline = computed(() => props.inline === true);
  const wrapperComponent = computed(() => (inline.value ? 'div' : 'a-modal'));
  const wrapperProps = computed(() => {
    if (inline.value) {
      return { class: 'env-manager-page' };
    }
    return {
      visible: visible.value,
      'onUpdate:visible': (v: boolean) => {
        visible.value = v;
        emit('update:modelValue', v);
      },
      title: '环境管理',
      width: '1000px',
      bodyStyle: { padding: '0', height: '600px' },
      footer: false,
      onCancel: handleCancel,
    };
  });

  // 左侧选中状态
  const selectedType = ref<'global' | 'env'>('global');
  const selectedKey = ref<string>('params');
  const selectedEnvId = ref<number | null>(null);

  // Tab 激活状态
  const globalParamsActiveTab = ref<GlobalRequestVarType>(
    GlobalRequestVarType.COOKIE
  );
  const globalScriptActiveTab = ref('pre');
  const envActiveTab = ref('envVar');

  // 全局数据（按类型分组）
  const globalCookies = ref<GlobalVar[]>([]);
  const globalHeaders = ref<GlobalVar[]>([]);
  const globalVariables = ref<GlobalVar[]>([]);
  const globalAsserts = ref<GlobalVar[]>([]);
  const globalPreScript = ref('');
  const globalPostScript = ref('');

  // 环境列表
  let nextEnvId = 3;

  // 当前环境名称
  const currentEnvName = computed(() => {
    const env = envList.value.find((e) => e.id === selectedEnvId.value);
    return env?.envName || '';
  });

  // 当前环境数据
  const currentEnvData = ref<Environment | null>(null);

  // Table Refs
  const globalCookieTableRef = ref();
  const globalHeaderTableRef = ref();
  const globalVariableTableRef = ref();
  const envVarTableRef = ref();
  const envCookieTableRef = ref();
  const envHeaderTableRef = ref();

  // 权限
  const permission = usePermission();
  const hasEnvCreatePermission = computed(() => permission.hasPermission('auto:env:create'));
  const hasEnvUpdatePermission = computed(() => permission.hasPermission('auto:env:update'));
  const hasEnvDeletePermission = computed(() => permission.hasPermission('auto:env:delete'));
  const hasGlobalVarUpdatePermission = computed(() => permission.hasPermission('auto:globalvar:update'));
  const hasAnyEnvPermission = computed(() =>
    hasEnvCreatePermission.value || hasEnvUpdatePermission.value || hasEnvDeletePermission.value
  );

  // 当前环境服务/数据库列表（计算属性）
  const currentEnvServices = computed(() => currentEnvData.value?.serve || []);
  const currentEnvDatabases = computed(() => currentEnvData.value?.dbs || []);
  // tab 徽标统一样式（灰色中性，不用默认红色——计数是信息展示而非告警）
  const tabBadgeStyle = {
    backgroundColor: 'var(--color-fill-3)',
    color: 'var(--color-text-2)',
    boxShadow: 'none',
  };
  // 环境详情 tab 徽标计数
  const envVarCount = computed(() => currentEnvData.value?.envVar?.length || 0);
  const envCookieCount = computed(() => currentEnvData.value?.cookies?.length || 0);
  const envHeaderCount = computed(() => currentEnvData.value?.headers?.length || 0);

  // 弹窗状态
  const newEnvModalVisible = ref(false);
  const newEnvForm = ref(new AddEnvDTO());

  // 表格列定义
  const serviceColumns = [
    { title: '服务名', slotName: 'name' },
    { title: '服务地址', slotName: 'address' },
    { title: '操作', slotName: 'operation', width: '130px' },
  ];

  const databaseColumns = [
    { title: '连接名称', slotName: 'name', width: '140px' },
    { title: '数据库类型', slotName: 'dataBaseType', width: '100px' },
    { title: '主机地址', slotName: 'address' },
    { title: '数据库名', slotName: 'dbName' },
    { title: '操作', slotName: 'operation', width: '160px' },
  ];

  // ========== 数据库连接测试 ==========
  const testConnLoading = ref<string | null>(null);
  const testConnResult = ref<{
    success: boolean;
    message: string;
    dbVersion?: string;
    latencyMs?: number;
  } | null>(null);

  const testDbConnection = async (record: any) => {
    if (!record.ip || !record.port || !record.userName) {
      Message.warning('请先填写IP、端口和用户名');
      return;
    }
    testConnLoading.value = record.name;
    testConnResult.value = null;
    try {
      const { data } = await testDbConnectionApi({
        dataBaseType: record.dataBaseType || 'MYSQL',
        name: record.name,
        dbName: record.dbName,
        ip: record.ip,
        port: record.port,
        userName: record.userName,
        password: record.password,
        charset: record.charset || 'utf8mb4',
      });
      testConnResult.value = data;
      if (data.success) {
        Message.success(`连接成功 — ${data.dbVersion || ''}，${data.latencyMs}ms`);
      } else {
        Message.error(data.message || '连接失败');
      }
    } catch (e: any) {
      testConnResult.value = {
        success: false,
        message: e?.response?.data?.msg || e?.message || '连接失败',
      };
      Message.error(testConnResult.value.message || '连接失败');
    } finally {
      testConnLoading.value = null;
    }
  };

  // ========== 全局参数 CRUD 操作 ==========

  const loadGlobalVars = async () => {
    // 重新获取全局参数列表
    const { data } = await getGlobalArgList(props.teamId as number);
    globalArgList.value = data;
    classifyGlobalArgs();
  };

  const loadEnvList = async () => {
    // 获取环境列表
    const { data } = await getEnvList(props.teamId as number);
    envList.value = data;
    classifyEnvArgs();
  };

  const handleGlobalVarAdd = async (type: GlobalRequestVarType, data: any) => {
    if (!hasGlobalVarUpdatePermission.value) return;
    if (!props.teamId) return;
    console.log(
      `[新增全局参数] type: ${type}, teamId: ${props.teamId}, data:`,
      data
    );

    const addDTO = new AddGlobalVarDTO();
    addDTO.teamId = props.teamId;
    addDTO.type = type;
    addDTO.name = data.name;
    addDTO.value = data.value;

    // 添加全局参数
    const res = await saveOrUpdateGlobalVar(addDTO);
    await loadGlobalVars();
  };

  const handleGlobalVarUpdate = async (
    type: GlobalRequestVarType,
    id: number,
    data: any
  ) => {
    if (!hasGlobalVarUpdatePermission.value) return;
    const updateDTO = new AddGlobalVarDTO();
    updateDTO.id = id;
    updateDTO.teamId = props.teamId;
    updateDTO.type = type;
    updateDTO.name = data.name;
    updateDTO.value = data.value;
    updateDTO.disabled = data.disabled;
    // 添加全局参数
    const res = await saveOrUpdateGlobalVar(updateDTO);
    await loadGlobalVars();
  };

  const handleGlobalVarDelete = async (
    type: GlobalRequestVarType,
    id: number
  ) => {
    if (!hasGlobalVarUpdatePermission.value) return;
    const res = await deleteGlobalVarById(id);
    await loadGlobalVars();
  };

  const getTypeName = (type: GlobalRequestVarType): string => {
    switch (type) {
      case GlobalRequestVarType.COOKIE:
        return 'Cookie';
      case GlobalRequestVarType.HEADER:
        return 'Header';
      case GlobalRequestVarType.VARIABLE:
        return '变量';
      case GlobalRequestVarType.ASSERT:
        return '断言';
      default:
        return '参数';
    }
  };

  // 断言相关映射
  const assertTypeMap: Record<string, string> = {
    HEADER: '响应头',
    BODY: '响应体',
    STATUS_CODE: '响应码',
    RESPONSE_TIME: '响应时间',
    CUSTOM: '自定义',
  };

  const assertRelationMap: Record<string, string> = {
    EQUALS: '等于',
    NOT_EQUALS: '不等于',
    CONTAINS: '包含',
    NOT_CONTAINS: '不包含',
    GT: '大于',
    LT: '小于',
    GE: '大于等于',
    LE: '小于等于',
    REGULAR: '正则匹配',
  };

  // 全局断言弹窗
  const assertModalVisible = ref(false);
  const assertModalTitle = ref('添加全局断言');
  const assertEditId = ref<number | null>(null);
  const assertForm = reactive({
    name: '',
    apiAssertType: 'BODY',
    field: '',
    assertRelationship: 'EQUALS',
    assertValue: '',
    disabled: false,
  });

  const resetAssertForm = () => {
    assertForm.name = '';
    assertForm.apiAssertType = 'BODY';
    assertForm.field = '';
    assertForm.assertRelationship = 'EQUALS';
    assertForm.assertValue = '';
    assertForm.disabled = false;
    assertEditId.value = null;
  };

  const getAssertSummary = (globalVar: GlobalVar): string => {
    if (!globalVar.globalAssert || globalVar.globalAssert.length === 0) return '-';
    const rule = globalVar.globalAssert[0];
    const type = assertTypeMap[rule.apiAssertType as string] || rule.apiAssertType;
    const relation = assertRelationMap[rule.assertRelationship as string] || rule.assertRelationship;
    return `${type} ${rule.field ? '[' + rule.field + ']' : ''} ${relation} ${rule.assertValue}`;
  };

  // ========== 全局断言 CRUD 操作 ==========

  const openAddAssertModal = () => {
    resetAssertForm();
    assertModalTitle.value = '添加全局断言';
    assertModalVisible.value = true;
  };

  const openEditAssertModal = (index: number) => {
    const globalVar = globalAsserts.value[index];
    if (!globalVar) return;
    assertEditId.value = globalVar.id || null;
    assertModalTitle.value = '编辑全局断言';
    assertForm.name = globalVar.name || '';
    assertForm.disabled = globalVar.disabled || false;
    if (globalVar.globalAssert && globalVar.globalAssert.length > 0) {
      const rule = globalVar.globalAssert[0];
      assertForm.apiAssertType = rule.apiAssertType || 'BODY';
      assertForm.field = rule.field || '';
      assertForm.assertRelationship = rule.assertRelationship || 'EQUALS';
      assertForm.assertValue = rule.assertValue || '';
    }
    assertModalVisible.value = true;
  };

  const saveGlobalAssert = async () => {
    if (!hasGlobalVarUpdatePermission.value) return;
    if (!props.teamId) return;
    if (!assertForm.name.trim()) {
      Message.warning('请输入断言名称');
      return;
    }

    const assertParam = new AssertParameter();
    assertParam.apiAssertType = assertForm.apiAssertType as any;
    assertParam.field = assertForm.field;
    assertParam.assertRelationship = assertForm.assertRelationship as any;
    assertParam.assertValue = assertForm.assertValue;
    assertParam.disabled = assertForm.disabled;

    const dto = new AddGlobalVarDTO();
    dto.teamId = props.teamId;
    dto.type = GlobalRequestVarType.ASSERT;
    dto.name = assertForm.name.trim();
    dto.disabled = assertForm.disabled;
    dto.globalAssert = [assertParam];

    if (assertEditId.value) {
      dto.id = assertEditId.value;
    }

    const res = await saveOrUpdateGlobalVar(dto);
    if (res.code === 200) {
      Message.success(assertEditId.value ? '更新成功' : '添加成功');
      assertModalVisible.value = false;
      await loadGlobalVars();
    }
  };

  const handleAssertDelete = async (index: number) => {
    if (!hasGlobalVarUpdatePermission.value) return;
    const globalVar = globalAsserts.value[index];
    if (!globalVar) return;
    Modal.confirm({
      title: '确认删除',
      content: `确定要删除全局断言"${globalVar.name}"吗？`,
      onOk: async () => {
        if (globalVar.id) {
          await deleteGlobalVarById(globalVar.id);
          await loadGlobalVars();
        }
      },
    });
  };

  const handleAssertStatusChange = async (globalVar: GlobalVar) => {
    if (!hasGlobalVarUpdatePermission.value) return;
    if (!globalVar.id || !props.teamId) return;
    const dto = new AddGlobalVarDTO();
    dto.id = globalVar.id;
    dto.teamId = props.teamId;
    dto.type = GlobalRequestVarType.ASSERT;
    dto.name = globalVar.name;
    dto.disabled = globalVar.disabled;
    dto.globalAssert = globalVar.globalAssert;
    await saveOrUpdateGlobalVar(dto);
    Message.success('状态更新成功');
  };

  // ========== 全局脚本操作 ==========

  const loadGlobalScript = async () => {
    if (!props.teamId) return;
    console.log(`[加载全局脚本] teamId: ${props.teamId}`);
  };

  const saveGlobalScript = async () => {
    if (!hasGlobalVarUpdatePermission.value) return;
    if (!props.teamId) return;
    console.log(
      `[保存全局脚本] teamId: ${props.teamId}, preScript: ${globalPreScript.value}, postScript: ${globalPostScript.value}`
    );
    emit(
      'saveGlobalScript',
      props.teamId,
      globalPreScript.value,
      globalPostScript.value
    );
    Message.success('脚本保存成功');
  };

  // 根据type分组获取参数
  const getParamsByType = (type: GlobalRequestVarType): GlobalVar[] => {
    if (globalArgList && globalArgList.value.length > 0) {
      return globalArgList.value.filter((arg) => arg.type === type);
    }
    return [];
  };

  // ========== 环境配置操作（每次操作都保存整个环境） ==========

  // 保存当前环境到后端
  const saveCurrentEnvironment = async () => {
    if (!hasEnvUpdatePermission.value) return;
    if (!currentEnvData.value || !selectedEnvId.value) return;

    const updateData: Partial<Environment> = {
      id: selectedEnvId.value,
      envName: currentEnvData.value.envName,
      envVar: currentEnvData.value.envVar,
      cookies: currentEnvData.value.cookies,
      headers: currentEnvData.value.headers,
      serve: currentEnvData.value.serve,
      dbs: currentEnvData.value.dbs,
    };

    console.log(
      `[保存环境配置] envId: ${selectedEnvId.value}, data:`,
      updateData
    );
    const res = await saveOrUpdate(updateData);
    if (res.code === 200) {
      Message.success('保存成功');
      // 重新获取环境列表
      await loadEnvList();
    }
    // 更新本地列表
    const index = envList.value.findIndex((e) => e.id === selectedEnvId.value);
    if (index !== -1) {
      envList.value[index] = { ...currentEnvData.value };
    }
  };

  // 环境变量操作（触发保存整个环境）
  const handleEnvVarAdd = async (fieldName: string, data: any) => {
    if (!hasEnvUpdatePermission.value) return;
    if (!currentEnvData.value) return;
    // 添加到当前环境数据
    const currentList = currentEnvData.value[fieldName] || [];
    const uuid = Math.floor(100000000 + Math.random() * 900000000);
    currentEnvData.value[fieldName] = [
      ...currentList,
      {
        id: uuid,
        name: data.name,
        value: data.value,
        disabled: data.disabled || false,
      },
    ];
    // 保存整个环境
    await saveCurrentEnvironment();
  };

  const handleEnvVarUpdate = async (
    fieldName: string,
    index: number,
    data: any
  ) => {
    if (!hasEnvUpdatePermission.value) return;
    if (!currentEnvData.value) return;

    // 更新当前环境数据
    const currentList = currentEnvData.value[fieldName] || [];
    if (currentList[index]) {
      currentList[index] = { ...currentList[index], ...data };
      currentEnvData.value[fieldName] = [...currentList];
    }

    // 保存整个环境
    await saveCurrentEnvironment();
  };

  const handleEnvVarDelete = async (fieldName: string, index: number) => {
    if (!hasEnvUpdatePermission.value) return;
    if (!currentEnvData.value) return;

    // 从当前环境数据中删除
    const currentList = currentEnvData.value[fieldName] || [];
    currentList.splice(index, 1);
    currentEnvData.value[fieldName] = [...currentList];

    // 保存整个环境
    await saveCurrentEnvironment();
  };

  // 服务操作（新增/编辑走弹窗，与数据库一致）
  const serviceModalVisible = ref(false);
  const editingServiceIndex = ref<number | null>(null);
  const serviceForm = reactive({
    id: undefined as number | undefined,
    name: '',
    address: '',
  });

  const addService = () => {
    if (!hasEnvUpdatePermission.value) return;
    if (!currentEnvData.value) return;
    editingServiceIndex.value = null;
    serviceForm.id = undefined;
    serviceForm.name = '';
    serviceForm.address = '';
    serviceModalVisible.value = true;
  };

  const editService = (index: number) => {
    if (!hasEnvUpdatePermission.value) return;
    const serve = currentEnvData.value?.serve?.[index];
    if (!serve) return;
    editingServiceIndex.value = index;
    serviceForm.id = serve.id;
    serviceForm.name = serve.name || '';
    serviceForm.address = serve.address || '';
    serviceModalVisible.value = true;
  };

  const saveServiceForm = async () => {
    if (!serviceForm.name?.trim() || !serviceForm.address?.trim()) {
      Message.warning('请填写完整的服务名和服务地址');
      return;
    }
    if (!currentEnvData.value) return;
    const services = [...(currentEnvData.value.serve || [])];
    if (editingServiceIndex.value !== null) {
      // 编辑：保留原 id（接口调试里按 serve.id 选中的引用不断）
      services[editingServiceIndex.value] = {
        ...services[editingServiceIndex.value],
        name: serviceForm.name.trim(),
        address: serviceForm.address.trim(),
      };
    } else {
      const randomId = Math.floor(100000000 + Math.random() * 900000000);
      services.push({ id: randomId, name: serviceForm.name.trim(), address: serviceForm.address.trim() });
    }
    currentEnvData.value.serve = services;
    await saveCurrentEnvironment();
    Message.success(editingServiceIndex.value !== null ? '服务更新成功' : '服务添加成功');
    serviceModalVisible.value = false;
  };

  const deleteService = async (index: number) => {
    if (!hasEnvUpdatePermission.value) return;
    if (!currentEnvData.value?.serve) return;
    const services = [...currentEnvData.value.serve];
    services.splice(index, 1);
    currentEnvData.value.serve = services;
    await saveCurrentEnvironment();
    Message.success('服务删除成功');
  };

  // 数据库操作
  const dbModalVisible = ref(false);
  const editingDbIndex = ref<number | null>(null);
  const dbForm = reactive({
    dataBaseType: 'MYSQL',
    name: '',
    dbName: '',
    ip: '',
    port: '',
    userName: '',
    password: '',
    charset: 'utf8mb4',
    description: '',
  });

  const addDatabase = () => {
    if (!hasEnvUpdatePermission.value) return;
    editingDbIndex.value = null;
    dbForm.dataBaseType = 'MYSQL';
    dbForm.name = '';
    dbForm.dbName = '';
    dbForm.ip = '';
    dbForm.port = '';
    dbForm.userName = '';
    dbForm.password = '';
    dbForm.charset = 'utf8mb4';
    dbForm.description = '';
    testConnResult.value = null;
    dbModalVisible.value = true;
  };

  const editDatabase = (index: number) => {
    if (!hasEnvUpdatePermission.value) return;
    const db = currentEnvData.value?.dbs?.[index];
    if (!db) return;
    editingDbIndex.value = index;
    dbForm.dataBaseType = db.dataBaseType || 'MYSQL';
    dbForm.name = db.name || '';
    dbForm.dbName = db.dbName || '';
    dbForm.ip = db.ip || '';
    dbForm.port = db.port || '';
    dbForm.userName = db.userName || '';
    dbForm.password = db.password || '';
    dbForm.charset = db.charset || 'utf8mb4';
    dbForm.description = db.description || '';
    testConnResult.value = null;
    dbModalVisible.value = true;
  };

  const saveDbForm = async () => {
    if (!dbForm.name || !dbForm.ip || !dbForm.port || !dbForm.dbName || !dbForm.userName) {
      Message.warning('请填写完整的数据库连接信息');
      return;
    }
    if (!currentEnvData.value) return;
    const databases = [...(currentEnvData.value.dbs || [])];
    const entry = { ...dbForm };
    if (editingDbIndex.value !== null) {
      databases[editingDbIndex.value] = entry;
    } else {
      databases.push(entry);
    }
    currentEnvData.value.dbs = databases;
    await saveCurrentEnvironment();
    Message.success(editingDbIndex.value !== null ? '数据库更新成功' : '数据库添加成功');
    dbModalVisible.value = false;
  };

  const deleteDatabase = async (index: number) => {
    if (!hasEnvUpdatePermission.value) return;
    if (!currentEnvData.value?.dbs) return;
    const databases = [...currentEnvData.value.dbs];
    databases.splice(index, 1);
    currentEnvData.value.dbs = databases;
    await saveCurrentEnvironment();
    Message.success('数据库删除成功');
  };

  const getEnvFieldName = (fieldName: string): string => {
    switch (fieldName) {
      case 'envVar':
        return '环境变量';
      case 'cookies':
        return 'Cookie';
      case 'headers':
        return 'Header';
      default:
        return fieldName;
    }
  };

  // ========== 环境操作 ==========

  const selectGlobalItem = (key: string) => {
    selectedType.value = 'global';
    selectedKey.value = key;
  };

  /**
   * 选择要编辑的环境
   * @param envId
   */
  const selectEnv = (envId: number) => {
    selectedType.value = 'env';
    selectedEnvId.value = envId;
    const env = envList.value.find((e) => e.id === envId);
    if (env) {
      currentEnvData.value = JSON.parse(JSON.stringify(env));
      nextTick(() => {
        classifyEnvArgs();
      });
    }
  };

  const handleEnvAction = async (key: string, env: Environment) => {
    if (key === 'copy') {
      if (!hasEnvCreatePermission.value) return;
      console.log(`[复制环境] env:`, env);
      const { data } = await copyEnv(env.id);
      if (data) {
        await loadEnvList();
        if (envList.value.length > 0) {
          selectEnv(data);
        } else {
          selectGlobalItem('params');
        }
      }
    } else if (key === 'delete') {
      if (!hasEnvDeletePermission.value) return;
      Modal.confirm({
        title: '确认删除',
        content: `确认删除环境"${env.envName}"吗？`,
        onOk: async () => {
          if (!env.id) return;
          await deleteEnvById(env.id);
          await loadEnvList();
          if (selectedEnvId.value === env.id) {
            if (envList.value.length > 0) {
              selectEnv(envList.value[0].id);
            } else {
              selectGlobalItem('params');
            }
          }
        },
      });
    }
  };

  const openNewEnvModal = () => {
    newEnvForm.value.envName = '';
    newEnvModalVisible.value = true;
  };

  const createNewEnv = async () => {
    if (!hasEnvCreatePermission.value) return;
    if (!newEnvForm.value.envName.trim()) {
      Message.warning('请输入环境名称');
      return;
    }
    if (envList.value.some((e) => e.envName === newEnvForm.value.envName)) {
      Message.warning('环境名称已存在');
      return;
    }

    const addEnvDTO = new AddEnvDTO();
    addEnvDTO.envName = newEnvForm.value.envName;
    addEnvDTO.teamId = props.teamId;
    const res = await saveOrUpdate(addEnvDTO);
    if (res.code === 200) {
      Message.success('创建成功');
      newEnvModalVisible.value = false;
      // 重新获取环境列表
      await loadEnvList();
      selectEnv(res.data);
    } else {
      Message.error('创建失败');
      return;
    }
  };

  // ========== 初始化数据 ==========

  const initData = async () => {
    try {
      const res = await getGlobalArgList(props.teamId as number);
      globalArgList.value = res.data || [];
    } catch (e) {
      globalArgList.value = [];
    }

    try {
      const { data } = await getEnvList(props.teamId as number);
      envList.value = data || [];
    } catch (e) {
      envList.value = [];
    }

    nextEnvId = 3;
    classifyEnvArgs();
    classifyGlobalArgs();
  };

  const classifyGlobalArgs = () => {
    globalCookies.value = getParamsByType(GlobalRequestVarType.COOKIE);
    globalHeaders.value = getParamsByType(GlobalRequestVarType.HEADER);
    globalVariables.value = getParamsByType(GlobalRequestVarType.VARIABLE);
    globalAsserts.value = getParamsByType(GlobalRequestVarType.ASSERT);
  };

  const classifyEnvArgs = () => {
    // 刷新子组件数据
    if (envVarTableRef.value) {
      envVarTableRef.value.setData(currentEnvData.value?.envVar || []);
    }
    if (envCookieTableRef.value) {
      envCookieTableRef.value.setData(currentEnvData.value?.cookies || []);
    }
    if (envHeaderTableRef.value) {
      envHeaderTableRef.value.setData(currentEnvData.value?.headers || []);
    }
  };
  const handleCancel = () => {
    visible.value = false;
    emit('update:modelValue', false);
  };

  // 监听弹窗打开
  watch(
    () => props.modelValue,
    async (val) => {
      visible.value = val;
      if (val) {
        await initData();
      }
    },
    {
      deep: true,
      immediate: true,
    }
  );
  // 页面模式下直接加载数据
  onMounted(async () => {
    if (inline.value) {
      await initData();
    }
  });
</script>

<style scoped lang="scss">
  .env-manager-layout {
    display: flex;
    height: 600px;

    .left-menu {
      width: 220px;
      display: flex;
      flex-direction: column;
      background-color: transparent;

      .menu-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding: 12px 16px;
        font-weight: 500;
        color: var(--color-text-1);

        .title {
          font-size: 14px;
        }
      }

      .menu-item {
        display: flex;
        align-items: center;
        padding: 10px 16px;
        margin: 0 8px;
        border-radius: 4px;
        cursor: pointer;
        transition: all 0.2s;
        color: var(--color-text-2);

        &:hover {
          background-color: var(--color-fill-2);
        }

        &.active {
          background-color: rgb(var(--primary-1));
          color: rgb(var(--primary-6));
        }

        .menu-icon {
          margin-right: 8px;
          font-size: 16px;
        }

        &.env-item {
          justify-content: space-between;

          .env-name {
            flex: 1;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
          }

          .env-actions {
            opacity: 0;
            transition: opacity 0.2s;
            padding: 0 4px;
          }

          &:hover .env-actions {
            opacity: 1;
          }
        }
      }

      .menu-divider {
        height: 1px;
        background-color: var(--color-border-2);
        margin: 8px 12px;
      }

      .env-list {
        flex: 1;
        overflow-y: auto;
      }

      .add-env-btn {
        margin: 12px;
        width: calc(100% - 24px);
      }
    }

    .right-content {
      flex: 1;
      overflow-y: auto;
      padding: 16px;

      .content-panel {
        height: 100%;
        display: flex;
        flex-direction: column;

        .panel-header {
          display: flex;
          justify-content: space-between;
          align-items: center;
          margin-bottom: 16px;
          padding-bottom: 12px;
          border-bottom: 1px solid var(--color-border-2);

          .title {
            font-size: 16px;
            font-weight: 500;
            color: var(--color-text-1);
          }
        }

        .panel-body {
          flex: 1;

          .tab-description {
            font-size: 12px;
            color: var(--color-text-3);
            margin-bottom: 12px;
            padding: 8px 12px;
            background-color: var(--color-fill-1);
            border-radius: 4px;
          }

          .assert-list {
            .assert-item {
              margin-bottom: 16px;
              padding: 12px;
              background-color: var(--color-fill-1);
              border-radius: 4px;

              .assert-header {
                display: flex;
                justify-content: space-between;
                align-items: center;
                margin-bottom: 12px;
              }

              .assert-expression {
                margin-bottom: 8px;
              }

              .assert-message {
                margin-top: 8px;
              }
            }
          }

          .add-btn {
            margin-top: 12px;
          }
        }

        .env-detail {
          .env-section {
            margin-bottom: 24px;
          }
        }
      }
    }
  }
  .env-manager-page {
    width: 100%;
    height: 100%;

    .env-manager-layout {
      height: 100%;

      // 与接口测试其它页面（API 测试/API 场景/数据模板）左侧树宽度统一为 a-col span=3
      .left-menu {
        width: 12.5%;
      }
    }
  }

  .cell-text {
    font-size: 13px;
    color: var(--color-text-2);
    word-break: break-all;
  }

  .db-modal-footer {
    display: flex;
    align-items: center;
    margin-bottom: 16px;
  }
</style>
