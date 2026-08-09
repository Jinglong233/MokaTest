<template>
  <div class="api-debug-form" :class="{ 'case-mode': mode === 'case' }">
    <!-- 基础信息：响应式工具栏 -->
    <div class="form-toolbar">
      <!-- 场景模式下步骤名称已承载命名，接口名显示隐藏，避免重复占一行 -->
      <div class="toolbar-name" v-if="mode !== 'scene'">
        <a-input
            v-if="nameEditing"
            ref="nameInputRef"
            v-model="localFormData.apiName"
            placeholder="接口名称"
            :max-length="50"
            show-word-limit
            :disabled="disabled"
            @input="onApiNameInput"
            @blur="handleNameBlur"
            @keydown.enter="(e: any) => (e.target as HTMLInputElement)?.blur()"
            @keydown.esc="cancelNameEdit"
        />
        <!-- 常态为文本，双击进入编辑；失焦自动保存（场景模式仅同步数据） -->
        <div
            v-else
            class="api-name-display"
            :class="{ 'is-disabled': disabled }"
            :title="disabled ? localFormData.apiName : '双击编辑名称'"
            @dblclick="enterNameEdit"
        >
          {{ localFormData.apiName || '未命名接口' }}
        </div>
      </div>
      <div class="toolbar-actions" v-if="mode !== 'scene'">
        <a-button v-permission="'auto:api:update'" type="primary" size="small" @click="saveData">保存</a-button>
        <template v-if="mode === 'case'">
          <a-button v-permission="'auto:api:execute'" type="primary" :status="isDdlStatement ? 'danger' : 'success'" size="small" @click="sendData">发送{{ isDdlStatement ? ' (DDL)' : '' }}</a-button>
        </template>
        <template v-else-if="mode !== 'scene'">
          <a-button v-if="hasApiUpdatePermission && hasApiExecutePermission" type="primary" :status="isDdlStatement ? 'danger' : 'normal'" size="small" @click="saveAndDebugApi">{{ isSqlMode ? '保存并执行' : '保存并调试' }}{{ isDdlStatement ? ' (DDL)' : '' }}</a-button>
          <a-button v-if="!isSqlMode" v-permission="'auto:api:create'" type="outline" size="small" @click="saveAsCase">保存为用例</a-button>
        </template>
      </div>
      <!-- 场景模式：服务地址下拉 + 单步调试入口合并为一行（紧凑布局，不保存，直接按当前表单配置发送） -->
      <div class="toolbar-actions scene-actions" v-else>
        <template v-if="!isSqlMode">
          <a-select
              v-if="currentEnv.serve && currentEnv.serve.length > 0"
              v-model="localFormData.envInfo.serve.id"
              @change="onServeChange"
              allow-clear
              placeholder="选择服务地址"
              :disabled="disabled"
              class="serve-select"
          >
            <a-option
                v-for="serve in currentEnv.serve"
                :key="serve.id"
                :value="serve.id"
                :label="serve.address ? serve.name + '（' + serve.address + '）' : serve.name"
            >
              <div class="serve-option">
                <span class="serve-option-name">{{ serve.name }}</span>
                <span class="serve-option-address">{{ serve.address }}</span>
              </div>
            </a-option>
          </a-select>
          <a-select
              v-else-if="currentEnv.id"
              disabled
              placeholder="该环境未配置服务地址"
              class="serve-select"
          />
        </template>
        <a-button v-permission="'auto:api:execute'" type="primary" :status="isDdlStatement ? 'danger' : 'success'" size="small" @click="sendData">发送{{ isDdlStatement ? ' (DDL)' : '' }}</a-button>
      </div>
    </div>

    <!-- 环境 + 服务/数据库连接：独立一行（场景 HTTP 模式服务下拉已并入工具行，不再重复占行；
         场景 SQL 模式仍保留此行展示数据库连接下拉） -->
    <div class="env-serve-row" v-if="mode !== 'scene' || isSqlMode">
      <!-- 环境选择（场景模式不显示） -->
      <template v-if="mode !== 'scene'">
        <a-select
            v-model="currentEnv.id"
            @change="onEnvChange"
            @clear="clearCurrentEnv"
            allow-clear
            :disabled="disabled"
            placeholder="选择环境"
            style="width: 200px; flex-shrink: 0;"
        >
          <a-option v-for="env in envList" :value="env.id" :key="env.id">{{
              env.envName
            }}
          </a-option>
        </a-select>
        <a-tooltip content="环境配置">
          <a-button v-if="mode !== 'case' && hasEnvConfigPermission" type="text" shape="circle" size="small" @click="openEnvConfig">
            <icon-settings/>
          </a-button>
        </a-tooltip>
      </template>
      <!-- HTTP 模式：服务地址下拉 -->
      <template v-if="!isSqlMode">
      <a-select
          v-if="currentEnv.serve && currentEnv.serve.length > 0"
          v-model="localFormData.envInfo.serve.id"
          @change="onServeChange"
          allow-clear
          :placeholder="mode === 'scene' ? '选择服务地址' : '选择服务'"
          :disabled="disabled"
          class="serve-select"
      >
        <a-option
            v-for="serve in currentEnv.serve"
            :key="serve.id"
            :value="serve.id"
            :label="serve.address ? serve.name + '（' + serve.address + '）' : serve.name"
        >
          <div class="serve-option">
            <span class="serve-option-name">{{ serve.name }}</span>
            <span class="serve-option-address">{{ serve.address }}</span>
          </div>
        </a-option
        >
      </a-select>
      <!-- 已选环境但未配置服务地址：显示禁用态占位，避免下拉凭空消失误以为没有该功能 -->
      <a-select
          v-else-if="currentEnv.id"
          disabled
          placeholder="该环境未配置服务地址"
          class="serve-select"
      />
      </template>
      <!-- SQL 模式：数据库连接下拉（选项为当前环境 dbs，与服务下拉同一交互模式） -->
      <template v-else>
        <a-select
            v-if="envDatabaseList.length > 0"
            v-model="sqlConfig.dbConnectionName"
            @change="onSqlConfigChange"
            allow-clear
            placeholder="选择数据库连接"
            :disabled="disabled"
            class="serve-select"
        >
          <a-option
              v-for="db in envDatabaseList"
              :key="db.name"
              :value="db.name"
              :label="db.name"
          >
            <div class="serve-option">
              <span class="serve-option-name">{{ db.name }}</span>
              <span class="serve-option-address">{{ db.ip }}:{{ db.port }}/{{ db.dbName }}</span>
            </div>
          </a-option>
        </a-select>
        <!-- 已选环境但未配置数据库连接：禁用态占位 -->

        <a-select
            v-else-if="currentEnv.id"
            disabled
            placeholder="该环境未配置数据库连接"
            class="serve-select"
        />
        <!-- 选中连接后显示实际连接信息（只读反馈，同 base-url-chip） -->
<!--        <span v-if="selectedDbInfo" class="base-url-chip" :title="`${selectedDbInfo.ip}:${selectedDbInfo.port}/${selectedDbInfo.dbName}`">
          {{ selectedDbInfo.ip }}:{{ selectedDbInfo.port }}/{{ selectedDbInfo.dbName }}
        </span>-->
      </template>
    </div>

    <!-- 请求信息：响应式工具栏 (HTTP 模式) -->
    <div class="request-toolbar" v-if="!isSqlMode">
      <div class="url-input-bar">
        <a-select
            v-model="localFormData.requestMethod"
            @change="onDataChange"
            :disabled="disabled"
            :style="{ width: '80px' }"
            class="method-select"
        >
          <a-option
              v-for="method in Object.values(RequestMethod)"
              :key="method"
              :value="method"
          >{{ method }}
          </a-option
          >
        </a-select>
        <div class="url-divider" />
        <!-- 选中服务后显示服务地址（只读）；路径写完整 URL 时隐藏（执行时绝对路径优先，避免误导） -->
        <template v-if="showBaseUrlChip">
          <span class="base-url-chip" :title="localFormData.envInfo?.baseUrl">{{ localFormData.envInfo?.baseUrl }}</span>
          <div class="url-divider" />
        </template>
        <a-input
            v-model="localFormData.requestPath"
            placeholder="/api/xxx"
            :disabled="disabled"
            @input="onDataChange"
            @blur="handlePathBlur"
            class="path-input"
        />
        <a-button
            type="text"
            size="small"
            :disabled="disabled"
            @click="openCurlModal"
            class="curl-import-btn"
        >
          导入 cURL
        </a-button>
      </div>
    </div>

    <!-- 内层 Tab：请求参数 -->
    <a-tabs v-model:active-key="innerActiveTab" class="inner-tabs">
      <a-tab-pane key="header" v-if="!isSqlMode">
        <template #title>Header<span v-if="headerCount > 0" class="tab-count">{{ headerCount }}</span></template>
        <ParameterTable ref="headerTableRef" context="header" :disabled="disabled" @change="onDataChange" @count="(n: number) => (headerCount = n)"/>
      </a-tab-pane>
      <a-tab-pane key="cookie" v-if="!isSqlMode">
        <template #title>Cookie<span v-if="cookieCount > 0" class="tab-count">{{ cookieCount }}</span></template>
        <ParameterTable ref="cookieTableRef" context="cookie" :disabled="disabled" @change="onDataChange" @count="(n: number) => (cookieCount = n)"/>
      </a-tab-pane>
      <a-tab-pane key="query" v-if="!isSqlMode">
        <template #title>Query<span v-if="queryCount > 0" class="tab-count">{{ queryCount }}</span></template>
        <ParameterTable ref="queryTableRef" context="query" :disabled="disabled" @change="onDataChange" @count="(n: number) => (queryCount = n)"/>
      </a-tab-pane>
      <!-- 鉴权配置 -->
      <a-tab-pane key="auth" v-if="!isSqlMode">
        <template #title>鉴权</template>
        <div class="auth-panel">
          <a-form layout="horizontal" auto-label-width>
            <a-form-item label="鉴权类型">
              <a-radio-group
                  v-model="localFormData.authConfig.authType"
                  :disabled="disabled"
                  @change="onDataChange"
              >
                <a-radio :value="AuthType.NONE">无鉴权</a-radio>
                <a-radio :value="AuthType.BEARER">Bearer Token</a-radio>
                <a-radio :value="AuthType.BASIC">Basic Auth</a-radio>
                <a-radio :value="AuthType.API_KEY">API Key</a-radio>
              </a-radio-group>
            </a-form-item>
            <template v-if="localFormData.authConfig.authType === AuthType.BEARER">
              <a-form-item label="Token">
                <a-input
                    v-model="localFormData.authConfig.token"
                    placeholder="支持 ${var} 变量引用"
                    :disabled="disabled"
                    @input="onDataChange"
                    allow-clear
                />
              </a-form-item>
              <div class="auth-preview">请求头：Authorization: Bearer &lt;token&gt;</div>
            </template>
            <template v-else-if="localFormData.authConfig.authType === AuthType.BASIC">
              <a-form-item label="用户名">
                <a-input
                    v-model="localFormData.authConfig.username"
                    placeholder="支持 ${var} 变量引用"
                    :disabled="disabled"
                    @input="onDataChange"
                    allow-clear
                />
              </a-form-item>
              <a-form-item label="密码">
                <a-input-password
                    v-model="localFormData.authConfig.password"
                    placeholder="支持 ${var} 变量引用"
                    :disabled="disabled"
                    @input="onDataChange"
                    allow-clear
                />
              </a-form-item>
              <div class="auth-preview">请求头：Authorization: Basic base64(username:password)</div>
            </template>
            <template v-else-if="localFormData.authConfig.authType === AuthType.API_KEY">
              <a-form-item label="参数名">
                <a-input
                    v-model="localFormData.authConfig.keyName"
                    placeholder="如 X-Api-Key / api_key"
                    :disabled="disabled"
                    @input="onDataChange"
                    allow-clear
                />
              </a-form-item>
              <a-form-item label="参数值">
                <a-input
                    v-model="localFormData.authConfig.keyValue"
                    placeholder="支持 ${var} 变量引用"
                    :disabled="disabled"
                    @input="onDataChange"
                    allow-clear
                />
              </a-form-item>
              <a-form-item label="附加位置">
                <a-radio-group
                    v-model="localFormData.authConfig.keyIn"
                    :disabled="disabled"
                    @change="onDataChange"
                >
                  <a-radio value="header">Header</a-radio>
                  <a-radio value="query">Query</a-radio>
                </a-radio-group>
              </a-form-item>
            </template>
          </a-form>
          <div class="auth-tip">优先级说明：若 Header / Query 表中已手动配置同名参数，以手动配置为准；鉴权值支持 ${var} 变量，可配合前置脚本或提取规则动态刷新 token</div>
        </div>
      </a-tab-pane>
      <!-- HTTP Body 模式 -->
      <a-tab-pane key="body" v-if="!isSqlMode && localFormData.body">
        <template #title>Body<span v-if="bodyCount > 0" class="tab-count">{{ bodyCount }}</span></template>
        <div class="body-mode-panel">
          <a-radio-group
              v-model="localFormData.body.mode"
              @change="onDataChange"
              style="margin-bottom: 16px"
              :disabled="disabled"
          >
            <a-radio
                v-for="modeValue in Object.values(BodyMode)"
                :key="modeValue"
                :value="modeValue"
            >
              {{ modeValue.toLowerCase() }}
            </a-radio>
          </a-radio-group>
          <template v-if="localFormData.body?.mode === BodyMode.NONE">
            <a-empty class="body-empty" description="无请求体"/>
          </template>
          <template v-if="localFormData.body?.mode === BodyMode.FORM_DATA">
            <ParameterTable ref="formDataRef" context="formData" :disabled="disabled" :allow-file-type="true" @change="onDataChange" @count="(n: number) => (formDataCount = n)"/>
          </template>
          <template
              v-if="localFormData.body?.mode === BodyMode.X_WWW_FORM_URLENCODED"
          >
            <ParameterTable ref="xWwwFormUrlencodedRef" context="xWwwFormUrlencoded" :disabled="disabled" @change="onDataChange" @count="(n: number) => (urlencodedCount = n)"/>
          </template>
          <template v-if="localFormData.body?.mode === BodyMode.JSON">
            <a-radio-group
                v-model="bodyJsonSource"
                type="button"
                size="small"
                style="margin-bottom: 12px"
                :disabled="disabled"
            >
              <a-radio value="RAW">手写 JSON</a-radio>
              <a-radio value="SCHEMA">绑定结构定义</a-radio>
            </a-radio-group>
            <ResponseSchemaPanel
                v-if="bodyJsonSource === 'SCHEMA'"
                v-model="localFormData.body.schemaBinding"
                :disabled="disabled"
                hide-validate
                @change="onDataChange"
            />
            <BodyCodeEditor
                v-else
                v-model="localFormData.body.json"
                lang="json"
                :disabled="disabled"
                @change="onDataChange"
            />
          </template>
          <template v-if="localFormData.body?.mode === BodyMode.XML">
            <BodyCodeEditor
                v-model="localFormData.body.xml"
                lang="xml"
                :disabled="disabled"
                @change="onDataChange"
            />
          </template>
        </div>
      </a-tab-pane>
      <!-- SQL 编辑器模式 -->
      <a-tab-pane key="sql" v-if="isSqlMode">
        <template #title>SQL</template>
        <a-alert
            v-if="multiStatementInfo"
            type="warning"
            size="small"
            show-icon
            style="margin-bottom: 8px;"
            title="多语句不可执行"
        >
          检测到 {{ multiStatementInfo }} 条 SQL 语句，SQL 接口一次只能执行一条，请拆分后逐个调试
        </a-alert>
        <a-alert
            v-else-if="isDdlStatement"
            type="warning"
            size="small"
            show-icon
            style="margin-bottom: 8px;"
            title="DDL 警告"
        >
          此操作将直接修改数据库结构（建表/改表/删表/清空），请确认 SQL 无误后再执行
        </a-alert>
        <BodyCodeEditor
            v-model="sqlConfig.sql"
            lang="sql"
            :disabled="disabled"
            @change="onSqlConfigChange"
        />
      </a-tab-pane>
      <!-- SQL 数据库连接配置 -->
      <a-tab-pane key="database" v-if="isSqlMode">
        <template #title>数据库</template>
        <div class="sql-db-panel">
          <!-- 当前生效连接：步骤级覆盖优先，其次环境连接（连接选择已上移至环境同行） -->
          <div class="sql-db-effective">
            <span class="sql-db-effective-label">当前生效：</span>
            <template v-if="dbOverrideActive">
              <a-tag color="orange" size="small">步骤级覆盖</a-tag>
              <span>{{ sqlConfig.dbConfig.dataBaseType || 'MYSQL' }} {{ sqlConfig.dbConfig.ip }}:{{ sqlConfig.dbConfig.port }}/{{ sqlConfig.dbConfig.dbName || '-' }}</span>
            </template>
            <template v-else-if="selectedDbInfo">
              <a-tag color="blue" size="small">环境连接</a-tag>
              <span>{{ selectedDbInfo.name }}（{{ selectedDbInfo.ip }}:{{ selectedDbInfo.port }}/{{ selectedDbInfo.dbName }}）</span>
            </template>
            <span v-else class="sql-db-effective-empty">未配置数据库连接，请在上方选择环境和连接</span>
          </div>
          <!-- 连接名不在当前环境的连接列表中（含未选环境）：执行时会报「未找到数据库连接配置」，黄色警告引导重选 -->
          <div v-if="dbConnectionNotInEnv" class="sql-db-warning">
            <icon-exclamation-circle-fill/>
            <span>连接「{{ sqlConfig.dbConnectionName }}」不属于当前环境，执行时将报「未找到数据库连接配置」，请重新选择或改用下方步骤级覆盖</span>
          </div>
          <div class="sql-db-divider">
            <span>步骤级覆盖（选填，优先级高于环境配置）</span>
          </div>
          <a-form layout="horizontal" :model="sqlConfig.dbConfig" class="sql-db-form">
            <a-form-item label="数据库类型">
              <a-select v-model="sqlConfig.dbConfig.dataBaseType" placeholder="数据库类型" :disabled="disabled" @change="onSqlConfigChange" allow-clear>
                <a-option v-for="t in SUPPORTED_DATA_BASE_TYPES" :key="t" :value="t">{{ t }}</a-option>
              </a-select>
            </a-form-item>
            <a-form-item label="IP 地址">
              <a-input v-model="sqlConfig.dbConfig.ip" placeholder="127.0.0.1" :disabled="disabled" @input="onSqlConfigChange" />
            </a-form-item>
            <a-form-item label="端口">
              <a-input v-model="sqlConfig.dbConfig.port" placeholder="3306" :disabled="disabled" @input="onSqlConfigChange" />
            </a-form-item>
            <a-form-item label="数据库名">
              <a-input v-model="sqlConfig.dbConfig.dbName" placeholder="库名" :disabled="disabled" @input="onSqlConfigChange" />
            </a-form-item>
            <a-form-item label="用户名">
              <a-input v-model="sqlConfig.dbConfig.userName" placeholder="root" :disabled="disabled" @input="onSqlConfigChange" />
            </a-form-item>
            <a-form-item label="密码">
              <a-input-password v-model="sqlConfig.dbConfig.password" placeholder="密码" :disabled="disabled" @input="onSqlConfigChange" />
            </a-form-item>
            <a-form-item>
              <a-button type="outline" size="small" :loading="testDbLoading" :disabled="disabled || !canTestConnection" @click="testDbConnection">测试连接</a-button>
            </a-form-item>
          </a-form>
        </div>
      </a-tab-pane>
      <a-tab-pane v-if="!isSqlMode" key="extraction">
        <template #title>提取规则<span v-if="extractionCount > 0" class="tab-count">{{ extractionCount }}</span></template>
        <ExtractionTable ref="extractionTableRef" :disabled="disabled" @change="onDataChange" @count="(n: number) => (extractionCount = n)"/>
      </a-tab-pane>
      <a-tab-pane v-if="isSqlMode" key="extraction">
        <template #title>提取规则<span v-if="sqlExtractionCount > 0" class="tab-count">{{ sqlExtractionCount }}</span></template>
        <SqlExtractionTable ref="sqlExtractionTableRef" :disabled="disabled" @change="onSqlExtractionChange" @count="(n: number) => (sqlExtractionCount = n)"/>
      </a-tab-pane>
      <a-tab-pane v-if="!isSqlMode" key="assert">
        <template #title>断言<span v-if="assertionCount > 0" class="tab-count">{{ assertionCount }}</span></template>
        <AssertionTable ref="assertionTableRef" :disabled="disabled" @change="onDataChange" @count="(n: number) => (assertionCount = n)"/>
      </a-tab-pane>
      <a-tab-pane v-if="isSqlMode" key="assert">
        <template #title>断言<span v-if="sqlAssertionCount > 0" class="tab-count">{{ sqlAssertionCount }}</span></template>
        <SqlAssertionTable ref="sqlAssertionTableRef" :disabled="disabled" @change="onSqlAssertionChange" @count="(n: number) => (sqlAssertionCount = n)"/>
      </a-tab-pane>
      <a-tab-pane key="preScript" v-if="!isSqlMode">
        <template #title>前置脚本<span v-if="preScriptCount > 0" class="tab-count">{{ preScriptCount }}</span></template>
        <ScriptListPanel
            v-model="localFormData.preScript"
            script-type="pre"
            :disabled="disabled"
            @change="onDataChange"
        />
      </a-tab-pane>
      <a-tab-pane key="postScript" v-if="!isSqlMode">
        <template #title>后置脚本<span v-if="postScriptCount > 0" class="tab-count">{{ postScriptCount }}</span></template>
        <ScriptListPanel
            v-model="localFormData.postScript"
            script-type="post"
            :disabled="disabled"
            @change="onDataChange"
        />
      </a-tab-pane>
      <a-tab-pane key="mock" v-if="!isSqlMode" title="Mock">
        <MockConfigPanel
            v-model="localFormData.mockResponse"
            :response-schema="localFormData.responseSchema"
            :disabled="disabled"
            @change="onDataChange"
        />
      </a-tab-pane>
      <a-tab-pane key="responseSchema" v-if="!isSqlMode" title="响应定义">
        <ResponseSchemaPanel
            v-model="localFormData.responseSchema"
            :disabled="disabled"
            @change="onDataChange"
        />
      </a-tab-pane>
      <a-tab-pane key="responseExamples" v-if="!isSqlMode">
        <template #title>响应示例<span v-if="responseExampleCount > 0" class="tab-count">{{ responseExampleCount }}</span></template>
        <ApiResponseExamplesPanel
          :examples="localFormData.responseExamples"
          :disabled="disabled"
          @update:examples="(val) => { localFormData.responseExamples = val; onDataChange(); }"
        />
      </a-tab-pane>
    </a-tabs>

    <EvnConfig v-if="mode !== 'case'" v-model="envConfigVisible" :team-id="teamStore.getTeamId"></EvnConfig>

    <!-- 保存为用例弹窗 -->
    <a-modal
        v-model:visible="caseNameModalVisible"
        title="保存为用例"
        @ok="handleConfirmSaveAsCase"
        @cancel="caseNameModalVisible = false"
        :ok-button-props="{ disabled: !caseNameValid }"
    >
      <a-form :model="caseNameForm" layout="vertical">
        <a-form-item
            field="caseName"
            label="用例名称"
            :rules="[
              { required: true, message: '用例名称不能为空' },
              { maxLength: 50, message: '用例名称长度不能超过50个字符' },
              { match: /^[\u4e00-\u9fa5a-zA-Z0-9_\-.\s()\uff08\uff09]+$/, message: '用例名称只能包含中文、英文、数字、下划线、空格及 - . ( )' }
            ]"
            :validate-trigger="['change', 'input']"
        >
          <a-input
              v-model="caseNameForm.caseName"
              placeholder="请输入用例名称"
              :max-length="50"
              show-word-limit
              allow-clear
              @input="onCaseNameInput"
          />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- cURL 导入弹窗 -->
    <a-modal
        v-model:visible="curlModalVisible"
        title="导入 cURL"
        @before-ok="handleCurlImport"
        @cancel="curlModalVisible = false"
        :ok-button-props="{ disabled: !curlText.trim() }"
        width="680px"
    >
      <a-textarea
          v-model="curlText"
          placeholder='请粘贴 cURL 命令，例如：&#10;curl -X GET "https://httpbin.org/get?name=John&age=30" -H "Authorization: Bearer xxx"'
          :auto-size="{ minRows: 8, maxRows: 16 }"
          allow-clear
      />
      <a-alert type="info" style="margin-top: 12px;" :show-icon="false">
        支持 bash / Windows cmd 两种格式：请求方法、URL、Headers、Cookies、Body（JSON / x-www-form-urlencoded）。URL 中的 Query 参数会自动提取到下方 Query 标签页。
      </a-alert>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import {computed, nextTick, onMounted, reactive, ref, watch} from 'vue';
import {IconSettings} from '@arco-design/web-vue/es/icon';
import ParameterTable from './ParameterTable.vue';
import ExtractionTable from './ExtractionTable.vue';
import SqlExtractionTable from './SqlExtractionTable.vue';
import AssertionTable from './AssertionTable.vue';
import SqlAssertionTable from './SqlAssertionTable.vue';
import BodyCodeEditor from './BodyCodeEditor.vue';
import ScriptListPanel from './ScriptListPanel.vue';
import MockConfigPanel from './MockConfigPanel.vue';
import ResponseSchemaPanel from './ResponseSchemaPanel.vue';
import ApiResponseExamplesPanel from './ApiResponseExamplesPanel.vue';
import EvnConfig from '@/components/env-config/index.vue';
import {RequestMethod} from '@/types/domain/api/apiEnum/RequestMethod';
import {ApiRequest} from '@/types/domain/api/ApiRequest';
import {ApiType} from '@/types/domain/api/apiEnum/ApiType';
import {BodyMode} from '@/types/domain/api/apiEnum/BodyMode';
import {Body} from '@/types/domain/api/requestModel/Body';
import {AddApiInterfaceDTO} from '@/types/domain/api/dto/AddApiInterfaceDTO';
import {ApiNodeType} from '@/types/domain/api/apiEnum/ApiNodeType';
import {MockResponse} from '@/types/domain/api/requestModel/MockResponse';
import {SqlConfig} from '@/types/domain/api/requestModel/SqlConfig';
import {DataBaseParameter} from '@/types/domain/api/requestModel/DataBaseParameter';
import {SUPPORTED_DATA_BASE_TYPES} from '@/types/domain/api/apiEnum/DataBaseType';
import {AuthType} from '@/types/domain/api/apiEnum/AuthType';
import {AuthConfig} from '@/types/domain/api/requestModel/AuthConfig';
import useTeamStore from '@/store/modules/team';
import usePermission from '@/hooks/permission';
import {Environment} from '@/types/domain/api/Environment';
import {getEnvList, testDbConnectionApi} from '@/api/MyApi/environment';
import {Message} from '@arco-design/web-vue';
import {RequestExecuteInfo} from '@/types/domain/api/requestModel/RequestExecuteInfo';
import {RequestParameter} from '@/types/domain/api/requestModel/RequestParameter';
import {ResponseSchema} from '@/types/domain/api/requestModel/ResponseSchema';
import {ParameterType} from '@/types/domain/api/apiEnum/ParameterType';

const props = defineProps<{
  modelValue?: ApiRequest;
  mode?: 'interface' | 'case' | 'scene';
  /**
   * 场景关联的环境ID（仅在 mode='scene' 时有效）
   * 传入后，接口配置中的环境选择会自动选中该环境
   */
  sceneEnvironmentId?: number;
  /**
   * 是否只读禁用
   */
  disabled?: boolean;
}>();

const emit = defineEmits<{
  (e: 'update:modelValue', value: ApiRequest): void;
  (e: 'change', hasChanges: boolean): void;
  (e: 'save'): void;
  (e: 'saveAndDebug', value: AddApiInterfaceDTO): void;
  (e: 'saveAsCase', value: AddApiInterfaceDTO): void;
  (e: 'send', value: AddApiInterfaceDTO): void;
}>();

const localFormData = ref<AddApiInterfaceDTO>(
    new AddApiInterfaceDTO(ApiNodeType.INTERFACE)
);
const originalData = ref<ApiRequest>(new ApiRequest());
const innerActiveTab = ref('header');

const headerTableRef = ref();
const cookieTableRef = ref();
const queryTableRef = ref();
const xWwwFormUrlencodedRef = ref();
const formDataRef = ref();
const extractionTableRef = ref();
const sqlExtractionTableRef = ref();
const sqlAssertionTableRef = ref();
const assertionTableRef = ref();

// ===== 各参数 tab 的有效参数个数，用于标题徽标（由 ParameterTable 的 @count 实时上报）=====
const headerCount = ref(0);
const cookieCount = ref(0);
const queryCount = ref(0);
const formDataCount = ref(0);
const urlencodedCount = ref(0);
const extractionCount = ref(0);
const sqlExtractionCount = ref(0);
const sqlAssertionCount = ref(0);
const assertionCount = ref(0);
// Body 徽标取当前 body 模式对应的参数个数
const bodyCount = computed(() => {
  const m = localFormData.value?.body?.mode;
  if (m === BodyMode.FORM_DATA) return formDataCount.value;
  if (m === BodyMode.X_WWW_FORM_URLENCODED) return urlencodedCount.value;
  return 0;
});

// JSON Body 来源：手写 JSON / 绑定结构定义（对齐 Apifox Body 引用数据模型）
const bodyJsonSource = computed<'RAW' | 'SCHEMA'>({
  get: () => {
    const binding = localFormData.value?.body?.schemaBinding;
    return binding && binding.mode && binding.mode !== 'NONE' ? 'SCHEMA' : 'RAW';
  },
  set: (v) => {
    const body = localFormData.value?.body;
    if (!body) return;
    if (v === 'SCHEMA') {
      if (!body.schemaBinding || !body.schemaBinding.mode || body.schemaBinding.mode === 'NONE') {
        const binding = new ResponseSchema();
        binding.mode = 'TEMPLATE';
        body.schemaBinding = binding;
      }
    } else if (body.schemaBinding) {
      // 切回手写：置 NONE 保留绑定配置，便于来回切换不丢数据
      body.schemaBinding.mode = 'NONE';
    }
    onDataChange();
  },
});

// 前置/后置脚本数量徽标（只统计启用的脚本）
const preScriptCount = computed(() => (localFormData.value?.preScript || []).filter((s: any) => s.enabled !== false).length);
const postScriptCount = computed(() => (localFormData.value?.postScript || []).filter((s: any) => s.enabled !== false).length);
const responseExampleCount = computed(() => (localFormData.value?.responseExamples || []).length);

const teamStore = useTeamStore();
const permission = usePermission();

const hasApiCreatePermission = computed(() => permission.hasPermission('auto:api:create'));
const hasApiUpdatePermission = computed(() => permission.hasPermission('auto:api:update'));
const hasApiExecutePermission = computed(() => permission.hasPermission('auto:api:execute'));
const hasEnvViewPermission = computed(() => permission.hasPermission('auto:env:view'));
const hasGlobalVarViewPermission = computed(() => permission.hasPermission('auto:globalvar:view'));
const hasEnvUpdatePermission = computed(() => permission.hasPermission('auto:env:update'));
const hasGlobalVarUpdatePermission = computed(() => permission.hasPermission('auto:globalvar:update'));
const hasEnvConfigPermission = computed(() => hasEnvViewPermission.value || hasGlobalVarViewPermission.value);

const envList = ref<Environment[]>([]);

const currentEnv = ref<Environment>({});

// ========== SQL 模式 ==========
const isSqlMode = computed(() => localFormData.value.apiType === ApiType.SQL);
const DDL_PATTERN = /^\s*(CREATE|ALTER|DROP|TRUNCATE)\b/i;
const isDdlStatement = computed(() => {
  if (!isSqlMode.value) return false;
  const sql = sqlConfig.sql?.trim();
  return sql ? DDL_PATTERN.test(sql) : false;
});

// SQL 模式自动切换到 SQL tab，HTTP 模式默认 header
watch(isSqlMode, (sql) => {
  innerActiveTab.value = sql ? 'sql' : 'header';
});

/**
 * 按分号拆分 SQL 语句（识别单/双引号、反引号、行注释与块注释，字符串内的分号不计）。
 * 与后端 SqlRequestExecutor.splitSqlStatements 逻辑保持一致。
 */
const splitSqlStatements = (sql: string): string[] => {
  const list: string[] = [];
  if (!sql || !sql.trim()) return list;
  let current = '';
  let quote = '';        // 当前引号：' " `，空串表示不在引号内
  let inLineComment = false;
  let inBlockComment = false;
  for (let i = 0; i < sql.length; i++) {
    const c = sql[i];
    const next = i + 1 < sql.length ? sql[i + 1] : '';
    if (inLineComment) {
      current += c;
      if (c === '\n') inLineComment = false;
      continue;
    }
    if (inBlockComment) {
      current += c;
      if (c === '*' && next === '/') { current += next; i++; inBlockComment = false; }
      continue;
    }
    if (quote) {
      current += c;
      if (c === '\\' && quote !== '`' && next) { current += next; i++; continue; }
      if (c === quote) quote = '';
      continue;
    }
    if ((c === '-' && next === '-') || c === '#') { inLineComment = true; current += c; continue; }
    if (c === '/' && next === '*') { inBlockComment = true; current += next; i++; continue; }
    if (c === "'" || c === '"' || c === '`') { quote = c; current += c; continue; }
    if (c === ';') {
      if (current.trim()) list.push(current.trim());
      current = '';
      continue;
    }
    current += c;
  }
  if (current.trim()) list.push(current.trim());
  return list;
};

/** 多语句提示文案（发送按钮 tooltip / 校验消息共用） */
const multiStatementInfo = computed(() => {
  if (!isSqlMode.value) return null;
  const count = splitSqlStatements(sqlConfig.sql || '').length;
  return count > 1 ? count : null;
});

/** SQL 执行前校验：仅允许单条语句，拦截时给出友好提示，不发起请求 */
const validateSqlSingleStatement = (): boolean => {
  if (!isSqlMode.value) return true;
  const sql = (sqlConfig.sql || '').trim();
  if (!sql) {
    Message.warning({ content: '请输入要执行的 SQL 语句', duration: 3000 });
    return false;
  }
  const count = multiStatementInfo.value;
  if (count) {
    Message.warning({
      content: `检测到 ${count} 条 SQL 语句，SQL 接口一次只能执行一条，请拆分后逐个调试`,
      duration: 4000,
    });
    return false;
  }
  return true;
};

// 从当前环境获取数据库连接列表
const envDatabaseList = computed<DataBaseParameter[]>(() => {
  if (!currentEnv.value?.dbs) return [];
  return currentEnv.value.dbs;
});

// URL 栏服务地址只读块：选中服务（有 baseUrl）且路径不是完整 URL 时显示；
// 路径写绝对地址时执行语义是绝对路径优先，显示服务地址会误导，故隐藏
const showBaseUrlChip = computed(() => {
  const baseUrl = localFormData.value?.envInfo?.baseUrl;
  if (!baseUrl) return false;
  const path = (localFormData.value?.requestPath || '').trim();
  return !/^https?:\/\//i.test(path);
});

// 连接名不在当前环境的连接列表中：切换/清空环境后残留的失效引用（执行期场景无环境时旧 envId 会被忽略）
const dbConnectionNotInEnv = computed(() => {
  if (!sqlConfig.dbConnectionName) return false;
  return !envDatabaseList.value.some(db => db.name === sqlConfig.dbConnectionName);
});

// 当前选中连接的完整配置（用于只读反馈展示）
const selectedDbInfo = computed<DataBaseParameter | undefined>(() =>
    envDatabaseList.value.find(db => db.name === sqlConfig.dbConnectionName)
);

// 步骤级覆盖是否已填任何字段（覆盖优先级高于环境连接，需在界面上明确提示）
const dbOverrideActive = computed(() => {
  const db = sqlConfig.dbConfig;
  if (!db) return false;
  return Object.values(db).some(v => v !== undefined && v !== null && v !== '');
});

const sqlConfig = reactive<SqlConfig>({
  sql: '',
  dbConnectionName: undefined,
  dbConfig: {},
  timeout: 30,
  maxRows: 1000,
  params: [],
  sqlExtractions: [],
  sqlAssertions: [],
});

// 初始化 SQL 配置（从 localFormData 同步）
const initSqlConfig = (config?: SqlConfig) => {
  if (config) {
    sqlConfig.sql = config.sql || '';
    sqlConfig.dbConnectionName = config.dbConnectionName;
    sqlConfig.dbConfig = config.dbConfig ? { ...config.dbConfig } : {};
    sqlConfig.timeout = config.timeout ?? 30;
    sqlConfig.maxRows = config.maxRows ?? 1000;
    sqlConfig.params = config.params ? [...config.params] : [];
    sqlConfig.sqlExtractions = config.sqlExtractions ? [...config.sqlExtractions] : [];
    sqlConfig.sqlAssertions = config.sqlAssertions ? [...config.sqlAssertions] : [];
    nextTick(() => {
      sqlExtractionTableRef.value?.setData(sqlConfig.sqlExtractions);
      sqlAssertionTableRef.value?.setData(sqlConfig.sqlAssertions);
    });
  }
};

// 测试数据库连接
const testDbLoading = ref(false);
const canTestConnection = computed(() => {
  const db = sqlConfig.dbConfig;
  return db.ip && db.port && db.userName;
});

const testDbConnection = async () => {
  const db = sqlConfig.dbConfig;
  if (!db.ip || !db.port || !db.userName) {
    Message.warning('请先填写 IP、端口和用户名');
    return;
  }
  testDbLoading.value = true;
  try {
    const { data } = await testDbConnectionApi({
      dataBaseType: db.dataBaseType || 'MYSQL',
      name: db.name || '',
      dbName: db.dbName || '',
      ip: db.ip,
      port: db.port,
      userName: db.userName,
      password: db.password || '',
      charset: db.charset || 'utf8mb4',
    });
    if (data.success) {
      Message.success(`连接成功 — ${data.dbVersion || ''}，${data.latencyMs}ms`);
    } else {
      Message.error(data.message || '连接失败');
    }
  } catch (e: any) {
    Message.error(e?.response?.data?.msg || e?.message || '连接失败');
  } finally {
    testDbLoading.value = false;
  }
};

const onSqlExtractionChange = (data: any[]) => {
  if (sqlConfig.sqlExtractions !== data) {
    sqlConfig.sqlExtractions = data;
    onSqlConfigChange();
  }
};

const onSqlAssertionChange = (data: any[]) => {
  if (sqlConfig.sqlAssertions !== data) {
    sqlConfig.sqlAssertions = data;
    onSqlConfigChange();
  }
};

// 过滤 dbConfig 中的空值，避免后端 Jackson 反序列化失败
const buildCleanDbConfig = () => {
  const raw = sqlConfig.dbConfig;
  if (!raw) return undefined;
  const cleaned: any = {};
  let hasValue = false;
  for (const key of Object.keys(raw)) {
    const v = (raw as any)[key];
    if (v !== undefined && v !== null && v !== '') {
      cleaned[key] = v;
      hasValue = true;
    }
  }
  return hasValue ? { ...cleaned } : undefined;
};

const onSqlConfigChange = () => {
  localFormData.value.sqlConfig = {
    sql: sqlConfig.sql,
    dbConnectionName: sqlConfig.dbConnectionName,
    dbConfig: buildCleanDbConfig(),
    timeout: sqlConfig.timeout,
    maxRows: sqlConfig.maxRows,
    params: sqlConfig.params && sqlConfig.params.length > 0 ? [...sqlConfig.params] : undefined,
    sqlExtractions: sqlConfig.sqlExtractions && sqlConfig.sqlExtractions.length > 0 ? [...sqlConfig.sqlExtractions] : undefined,
    sqlAssertions: sqlConfig.sqlAssertions && sqlConfig.sqlAssertions.length > 0 ? [...sqlConfig.sqlAssertions] : undefined,
  };
  onDataChange();
};

onMounted(async () => {
  if (hasEnvViewPermission.value) {
    try {
      const {data} = await getEnvList(teamStore.teamId);
      envList.value = data || [];
    } catch (e) {
      envList.value = [];
    }
  }

  if (props.modelValue) {
    initData(props.modelValue);
  }

  // 场景模式：如果场景有环境，设置 currentEnv 供服务地址选择使用
  // 注意：不在 onMounted 里修改 localFormData 触发 update:modelValue，避免挂载期强制重渲染
  if (props.mode === 'scene' && props.sceneEnvironmentId && envList.value.length > 0) {
    const sceneEnv = envList.value.find((e: any) => e.id === props.sceneEnvironmentId);
    if (sceneEnv) {
      currentEnv.value = JSON.parse(JSON.stringify(sceneEnv));
      // SQL 模式：注入场景环境 ID。后端按 envInfo.envId + dbConnectionName 解析环境级数据库连接，
      // 场景模式没有环境工具栏（不会走 onEnvChange 写 envId），必须在这里注入，
      // 否则调试时报「未找到数据库连接配置」
      if (isSqlMode.value) {
        if (!localFormData.value.envInfo) {
          localFormData.value.envInfo = {} as any;
        }
        localFormData.value.envInfo.envId = props.sceneEnvironmentId;
        localFormData.value.envInfo.envName = sceneEnv.envName;
      }
    }
  }
});

const initData = (data: ApiRequest) => {
  if (data) {
    let processedData = JSON.parse(JSON.stringify(data));

    // 场景模式：如果步骤的环境ID与场景环境不匹配，在初始化时就清空环境选择相关配置
    if (props.mode === 'scene') {
      if (props.sceneEnvironmentId) {
        // 场景有环境：步骤的 envId 必须与场景环境一致，否则清空
        if (processedData.envInfo?.envId && processedData.envInfo.envId !== props.sceneEnvironmentId) {
          processedData.envInfo.serve = undefined;
          processedData.envInfo.baseUrl = undefined;
          processedData.envInfo.envId = undefined;
          processedData.envInfo.envName = undefined;
          processedData.envInfo.envCookies = undefined;
          processedData.envInfo.envHeaders = undefined;
        }
      } else {
        // 场景没有环境：强制清空步骤的所有环境相关配置（包括服务地址）
        if (processedData.envInfo) {
          processedData.envInfo.serve = undefined;
          processedData.envInfo.baseUrl = undefined;
          processedData.envInfo.envId = undefined;
          processedData.envInfo.envName = undefined;
          processedData.envInfo.envCookies = undefined;
          processedData.envInfo.envHeaders = undefined;
        }
      }
    }

    // 确保 envInfo 和 serve 不为 undefined，避免 template 中 v-model 访问报错（仅 HTTP 模式）
    if (processedData.apiType !== ApiType.SQL) {
      if (processedData.envInfo && !processedData.envInfo.serve) {
        processedData.envInfo.serve = { id: undefined, name: undefined, address: undefined };
      }
      // 确保 mockResponse 不为 undefined
      if (!processedData.mockResponse) {
        processedData.mockResponse = new MockResponse();
      }
      // 确保 responseSchema 不为 undefined（响应定义 tab 直接 v-model 访问）
      if (!processedData.responseSchema) {
        processedData.responseSchema = new ResponseSchema();
      }
      // 确保 authConfig 不为 undefined（鉴权 tab 直接 v-model 访问）
      if (!processedData.authConfig) {
        processedData.authConfig = new AuthConfig();
      }
    }

    localFormData.value = processedData;
    originalData.value = JSON.parse(JSON.stringify(processedData));

    // SQL 模式：初始化 SQL 配置
    if (processedData.apiType === ApiType.SQL) {
      initSqlConfig(processedData.sqlConfig);
    } else {
      // 重置 SQL 配置和高级设置面板状态
      sqlConfig.sql = '';
      sqlConfig.dbConnectionName = undefined;
      sqlConfig.dbConfig = {};
      sqlConfig.params = [];
    }

    // 非场景模式下，根据接口自身配置的环境设置 currentEnv
    if (props.mode !== 'scene' && data.envInfo?.envId && envList.value.length > 0) {
      const targetEnv = envList.value.find(
          (e) => e.id === data.envInfo.envId
      );
      if (targetEnv) {
        currentEnv.value = targetEnv;
      } else if (envList.value.length > 0) {
        currentEnv.value = envList.value[0];
      }
    }

    nextTick(() => {
      if (headerTableRef.value && data.requestHeader) {
        headerTableRef.value.setData(data.requestHeader);
      }
      if (cookieTableRef.value && data.cookies) {
        cookieTableRef.value.setData(data.cookies);
      }
      if (queryTableRef.value && data.query) {
        queryTableRef.value.setData(data.query);
      }
      if (
          xWwwFormUrlencodedRef.value &&
          data.body &&
          localFormData.value.body?.mode === BodyMode.X_WWW_FORM_URLENCODED
      ) {
        xWwwFormUrlencodedRef.value.setData(data.body.xWwwFormUrlencoded);
      }
      if (
          formDataRef.value &&
          data.body &&
          localFormData.value.body?.mode === BodyMode.FORM_DATA
      ) {
        formDataRef.value.setData(data.body.formData);
      }
      if (extractionTableRef.value && data.associationExtraction) {
        extractionTableRef.value.setData(data.associationExtraction);
      }
      if (assertionTableRef.value && data.apiResultAssert) {
        assertionTableRef.value.setData(data.apiResultAssert);
      }
    });
  }
};

watch(
    () => props.modelValue,
    (newVal) => {
      if (newVal) {
        const newStr = JSON.stringify(newVal);
        const currentStr = JSON.stringify(localFormData.value);
        if (newStr !== currentStr) {
          initData(newVal);
        }
      }
    }
);

// 监听场景环境ID变化：场景清除/切换环境时，同步更新 currentEnv 和步骤环境配置
watch(() => props.sceneEnvironmentId, (newEnvId) => {
  if (props.mode !== 'scene') return;

  // 更新 currentEnv：有环境则设置对应环境，无环境则清空
  if (newEnvId && envList.value.length > 0) {
    const sceneEnv = envList.value.find((e: any) => e.id === newEnvId);
    if (sceneEnv) {
      currentEnv.value = JSON.parse(JSON.stringify(sceneEnv));
    }
  } else {
    currentEnv.value = {};
  }

  // 场景无环境时，强制清空步骤中的服务地址和 baseUrl
  if (!newEnvId && localFormData.value.envInfo) {
    localFormData.value.envInfo.serve = { id: undefined, name: undefined, address: undefined };
    localFormData.value.envInfo.baseUrl = undefined;
    localFormData.value.envInfo.envId = undefined;
    localFormData.value.envInfo.envName = undefined;
    localFormData.value.envInfo.envCookies = undefined;
    localFormData.value.envInfo.envHeaders = undefined;
    onDataChange();
  }
});

const collectAllData = () => {
  const result = JSON.parse(JSON.stringify(localFormData.value));

  if (headerTableRef.value) {
    result.requestHeader = headerTableRef.value.getData();
  }
  if (cookieTableRef.value) {
    result.cookies = cookieTableRef.value.getData();
  }
  if (queryTableRef.value) {
    result.query = queryTableRef.value.getData();
  }
  if (formDataRef.value) {
    result.body.formData = formDataRef.value.getData();
  }
  if (xWwwFormUrlencodedRef.value) {
    result.body.xWwwFormUrlencoded = xWwwFormUrlencodedRef.value.getData();
  }
  if (extractionTableRef.value) {
    result.associationExtraction = extractionTableRef.value.getData();
  }
  if (assertionTableRef.value) {
    result.apiResultAssert = assertionTableRef.value.getData();
  }
  console.log('[MockConfig] collectAllData query/header mockConfig', {
    query: result.query,
    requestHeader: result.requestHeader,
  });
  return result;
};

const checkHasChanges = () => {
  const currentData = collectAllData();
  const originalStr = JSON.stringify(originalData.value);
  const currentStr = JSON.stringify(currentData);
  return originalStr !== currentStr;
};

const onDataChange = () => {
  // scene 模式下实时同步数据到父组件，避免数据丢失和循环重置
  if (props.mode === 'scene') {
    const currentData = collectAllData();
    localFormData.value = JSON.parse(JSON.stringify(currentData));
    originalData.value = JSON.parse(JSON.stringify(currentData));
    emit('update:modelValue', currentData);
    return;
  }
  const hasChanges = checkHasChanges();
  emit('change', hasChanges);
};

const onApiNameInput = (value: string) => {
  const filtered = value.replace(/[^\u4e00-\u9fa5a-zA-Z0-9_\-.\s()\uff08\uff09]/g, '');
  if (filtered !== value) {
    localFormData.value.apiName = filtered;
  }
  onDataChange();
};

// ===== 接口名称：双击编辑 + 失焦自动保存 =====
const nameEditing = ref(false);
const nameInputRef = ref();
const nameBeforeEdit = ref('');

const enterNameEdit = () => {
  if (props.disabled) return;
  nameBeforeEdit.value = localFormData.value.apiName || '';
  nameEditing.value = true;
  nextTick(() => (nameInputRef.value as any)?.focus?.());
};

const cancelNameEdit = () => {
  localFormData.value.apiName = nameBeforeEdit.value;
  nameEditing.value = false;
};

const handleNameBlur = () => {
  if (!nameEditing.value) return;
  const name = (localFormData.value.apiName || '').trim();
  if (!name) {
    Message.warning('接口名称不能为空');
    localFormData.value.apiName = nameBeforeEdit.value;
    nameEditing.value = false;
    return;
  }
  nameEditing.value = false;
  // 名称没变不触发任何动作
  if (name === nameBeforeEdit.value) return;
  // 场景模式（步骤副本）无落库接口：只同步数据（走未保存确认那套）；
  // 接口/用例编辑：自动保存，等同点「保存」按钮
  if (props.mode === 'scene') {
    onDataChange();
  } else {
    saveData();
  }
};

/**
 * URL \u8f93\u5165\u6846\u5931\u7126\u65f6\u89e3\u6790 Query \u53c2\u6570\uff1a
 * \u5c06 ?key=value \u5f62\u5f0f\u7684\u53c2\u6570\u81ea\u52a8\u63d0\u53d6\u5230 Query \u53c2\u6570\u8868\u4e2d\uff0cURL \u53ea\u4fdd\u7559 path \u90e8\u5206\u3002
 * \u5df2\u5b58\u5728\u7684\u540c\u540d\u540c\u503c\u53c2\u6570\u4e0d\u4f1a\u91cd\u590d\u6dfb\u52a0\uff1b\u7eaf hash \u951a\u70b9\u4f1a\u4fdd\u7559\u5728 URL \u4e2d\u3002
 */
const handlePathBlur = () => {
  const rawPath = localFormData.value.requestPath?.trim();
  if (!rawPath) return;

  // \u5206\u79bb hash \u951a\u70b9\uff0c\u907f\u514d\u88ab URLSearchParams \u8bef\u89e3\u6790
  const hashIndex = rawPath.indexOf('#');
  const pathPart = hashIndex >= 0 ? rawPath.substring(0, hashIndex) : rawPath;
  const hashPart = hashIndex >= 0 ? rawPath.substring(hashIndex) : '';

  const queryIndex = pathPart.indexOf('?');
  if (queryIndex < 0) return;

  const basePath = pathPart.substring(0, queryIndex);
  const queryString = pathPart.substring(queryIndex + 1);

  // \u6e05\u7406 URL \u4e2d\u7684 query \u90e8\u5206\uff08\u81f3\u5c11\u53bb\u6389\u7a7a ?\uff09
  localFormData.value.requestPath = basePath + hashPart;

  if (!queryString) {
    onDataChange();
    return;
  }

  const existingParams = queryTableRef.value?.getData() || [];
  const existingPairs = new Set(
      existingParams
          .filter((p: RequestParameter) => p.name != null && String(p.name).trim() !== '')
          .map((p: RequestParameter) => `${String(p.name).trim()}=${p.value ?? ''}`)
  );
  const addedPairs = new Set<string>();
  const newParams: RequestParameter[] = [];

  const params = new URLSearchParams(queryString);
  params.forEach((value, key) => {
    const trimmedKey = key.trim();
    if (!trimmedKey) return;
    const pairKey = `${trimmedKey}=${value}`;
    if (existingPairs.has(pairKey) || addedPairs.has(pairKey)) return;

    const param = new RequestParameter();
    param.name = trimmedKey;
    param.value = value;
    param.type = ParameterType.STRING;
    param.disabled = false;
    newParams.push(param);
    addedPairs.add(pairKey);
  });

  if (newParams.length > 0) {
    queryTableRef.value?.setData([...existingParams, ...newParams]);
    // \u5207\u6362\u5230 Query \u6807\u7b7e\u9875\uff0c\u8ba9\u7528\u6237\u770b\u5230\u89e3\u6790\u7ed3\u679c
    innerActiveTab.value = 'query';
    Message.info({
      content: `\u5df2\u81ea\u52a8\u4ece\u8def\u5f84\u4e2d\u63d0\u53d6 ${newParams.length} \u4e2a Query \u53c2\u6570\uff0c\u8bf7\u67e5\u770b\u4e0b\u65b9\u300cQuery\u300d\u6807\u7b7e\u9875`,
      duration: 2500,
    });
  }

  onDataChange();
};

const validateApiName = (): boolean => {
  const apiName = localFormData.value.apiName?.trim();
  if (!apiName) {
    Message.error({ content: '接口名称不能为空', duration: 2000 });
    return false;
  }
  if (apiName.length > 50) {
    Message.error({ content: '接口名称长度不能超过50个字符', duration: 2000 });
    return false;
  }
  const regex = /^[\u4e00-\u9fa5a-zA-Z0-9_\-.\s()\uff08\uff09]+$/;
  if (!regex.test(apiName)) {
    Message.error({ content: '接口名称只能包含中文、英文、数字、下划线、空格及 - . ( )', duration: 3000 });
    return false;
  }
  return true;
};

const validateRequestPath = (): boolean => {
  const requestPath = localFormData.value.requestPath?.trim();
  if (!requestPath) {
    Message.error({ content: '请求路径不能为空', duration: 2000 });
    return false;
  }
  if (requestPath.length > 500) {
    Message.error({ content: '请求路径长度不能超过500个字符', duration: 2000 });
    return false;
  }
  return true;
};

const validateParameters = (): boolean => {
  const refs = [
    { ref: headerTableRef, label: 'Header' },
    { ref: cookieTableRef, label: 'Cookie' },
    { ref: queryTableRef, label: 'Query' },
    { ref: formDataRef, label: 'FormData' },
    { ref: xWwwFormUrlencodedRef, label: 'x-www-form-urlencoded' },
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
  return true;
};

const validateExtractions = (): boolean => {
  const ref = isSqlMode.value ? sqlExtractionTableRef.value : extractionTableRef.value;
  if (ref) {
    const result = ref.validateAll();
    if (!result.valid) {
      Message.error({ content: result.message, duration: 3000 });
      return false;
    }
  }
  return true;
};

const validateAssertions = (): boolean => {
  const ref = isSqlMode.value ? sqlAssertionTableRef.value : assertionTableRef.value;
  if (ref) {
    const result = ref.validateAll();
    if (!result.valid) {
      Message.error({ content: result.message, duration: 3000 });
      return false;
    }
  }
  return true;
};

const saveData = () => {
  if (!validateApiName()) return;
  if (!isSqlMode.value && !validateRequestPath()) return;
  if (!validateParameters()) return;
  if (!validateExtractions()) return;
  if (!validateAssertions()) return;

  const savedData = collectAllData();
  originalData.value = JSON.parse(JSON.stringify(savedData));
  emit('update:modelValue', savedData);
  emit('save');
  emit('change', false);
};

const saveAndDebugApi = () => {
  if (!validateApiName()) return;
  if (!isSqlMode.value && !validateRequestPath()) return;
  if (!validateSqlSingleStatement()) return;
  if (!validateParameters()) return;
  if (!validateExtractions()) return;
  if (!validateAssertions()) return;

  const savedData = collectAllData();
  originalData.value = JSON.parse(JSON.stringify(savedData));
  emit('update:modelValue', savedData);
  emit('saveAndDebug', savedData)
  emit('change', false);
}

const sendData = () => {
  if (!validateApiName()) return;
  if (!isSqlMode.value && !validateRequestPath()) return;
  if (!validateSqlSingleStatement()) return;
  if (!validateParameters()) return;
  if (!validateExtractions()) return;
  if (!validateAssertions()) return;

  const savedData = collectAllData();
  // 发送 ≠ 保存：只同步数据给调试，不重置 originalData、不清除变更标记，
  // 否则发送后直接关闭抽屉不会提示未保存修改，用户改动静默丢失
  emit('update:modelValue', savedData);
  emit('send', savedData);
}

const resetChanges = () => {
  initData(originalData.value);
  emit('change', false);
};

const getCurrentData = () => {
  return collectAllData();
};

const onServeChange = (serveId: any) => {
  if (!localFormData.value.envInfo) {
    localFormData.value.envInfo = new RequestExecuteInfo();
  }
  // 清空服务地址
  if (!serveId) {
    if (localFormData.value.envInfo.serve) {
      localFormData.value.envInfo.serve.id = undefined;
      localFormData.value.envInfo.serve.name = undefined;
      localFormData.value.envInfo.serve.address = undefined;
    }
    localFormData.value.envInfo.baseUrl = undefined;
    onDataChange();
    return;
  }
  // 选择服务地址
  if (currentEnv.value.serve) {
    const selectedServe = currentEnv.value.serve.find(
        (s: any) => s.id === serveId
    );
    if (selectedServe) {
      localFormData.value.envInfo.serve = JSON.parse(
          JSON.stringify(selectedServe)
      );
      localFormData.value.envInfo.baseUrl = selectedServe.address;
      // 场景模式下不覆盖 envId/envName/envCookies/envHeaders（由场景调试后端合并）
      if (props.mode !== 'scene') {
        localFormData.value.envInfo.envId = currentEnv.value.id;
        localFormData.value.envInfo.envName = currentEnv.value.envName;
        localFormData.value.envInfo.envCookies = currentEnv.value.cookies
            ? JSON.parse(JSON.stringify(currentEnv.value.cookies))
            : [];
        localFormData.value.envInfo.envHeaders = currentEnv.value.headers
            ? JSON.parse(JSON.stringify(currentEnv.value.headers))
            : [];
      }
      onDataChange();
    }
  }
};

const clearCurrentEnv = () => {
  currentEnv.value = {};
  sqlConfig.dbConnectionName = undefined;
  if (localFormData.value.envInfo) {
    localFormData.value.envInfo.envId = undefined;
    localFormData.value.envInfo.envName = undefined;
    localFormData.value.envInfo.serve = undefined;
    localFormData.value.envInfo.baseUrl = undefined;
    localFormData.value.envInfo.database = undefined;
    localFormData.value.envInfo.envCookies = undefined;
    localFormData.value.envInfo.envHeaders = undefined;
  }
  onSqlConfigChange();
  onDataChange();
};

const onEnvChange = (envId: any) => {
  const selectedEnv = envList.value.find((e: any) => e.id === envId);
  if (selectedEnv) {
    currentEnv.value = JSON.parse(JSON.stringify(selectedEnv));
    if (!localFormData.value.envInfo) {
      localFormData.value.envInfo = new RequestExecuteInfo();
    }
    localFormData.value.envInfo.envId = selectedEnv.id;
    localFormData.value.envInfo.envName = selectedEnv.envName;
    localFormData.value.envInfo.envCookies = selectedEnv.cookies
        ? JSON.parse(JSON.stringify(selectedEnv.cookies))
        : [];
    localFormData.value.envInfo.envHeaders = selectedEnv.headers
        ? JSON.parse(JSON.stringify(selectedEnv.headers))
        : [];
    if (isSqlMode.value) {
      // SQL 模式：与环境联动的对象是数据库连接（同 HTTP 自动选第一个服务的交互）；
      // 新环境有连接则自动选第一个，没有则清空，避免残留旧环境连接名执行时报错
      const dbs = selectedEnv.dbs || [];
      sqlConfig.dbConnectionName = dbs.length > 0 ? dbs[0].name : undefined;
      onSqlConfigChange();
    } else if (selectedEnv.serve && selectedEnv.serve.length > 0) {
      localFormData.value.envInfo.serve = JSON.parse(
          JSON.stringify(selectedEnv.serve[0])
      );
      localFormData.value.envInfo.baseUrl = selectedEnv.serve[0].address;
    } else {
      // 新环境没有服务地址：清空上一环境残留的 serve/baseUrl，避免静默打到旧环境
      localFormData.value.envInfo.serve = { id: undefined, name: undefined, address: undefined };
      localFormData.value.envInfo.baseUrl = undefined;
    }
    onDataChange();
  }
};

const envConfigVisible = ref(false);
const openEnvConfig = () => {
  if (!hasEnvConfigPermission.value) {
    Message.warning('无权访问环境配置');
    return;
  }
  envConfigVisible.value = true;
};

// 保存为用例弹窗
const caseNameModalVisible = ref(false);
const caseNameForm = reactive({ caseName: '' });
const caseNameValid = ref(false);
let pendingCaseData: AddApiInterfaceDTO | null = null;

// cURL 导入弹窗
const curlModalVisible = ref(false);
const curlText = ref('');

const openCurlModal = () => {
  curlText.value = '';
  curlModalVisible.value = true;
};

/**
 * 按 shell 规则分词 cURL 命令：
 * - 支持 bash：单双引号、\\ 转义、\\ 续行
 * - 支持 Windows cmd：双引号、^ 转义、^ 续行
 */
const tokenizeCurl = (cmd: string): string[] => {
  const tokens: string[] = [];
  let current = '';
  let inQuote: '"' | "'" | null = null;
  let escaped = false;

  /**
   * 跳过行尾换行符（支持 \\r\\n、\\n、\\r），返回新索引。
   */
  const skipLineBreak = (idx: number): number => {
    if (cmd[idx + 1] === '\r' && cmd[idx + 2] === '\n') return idx + 2;
    if (cmd[idx + 1] === '\n') return idx + 1;
    if (cmd[idx + 1] === '\r') return idx + 1;
    return idx;
  };

  /**
   * 判断从 idx 开始是否是 cmd 续行：^ 后面允许有空白，然后紧跟换行。
   */
  const isCmdLineContinuation = (idx: number): boolean => {
    let j = idx + 1;
    while (j < cmd.length && (cmd[j] === ' ' || cmd[j] === '\t')) {
      j++;
    }
    return cmd[j] === '\n' || cmd[j] === '\r';
  };

  /**
   * 跳过 cmd 续行后的换行符，返回新索引。
   */
  const skipCmdLineBreak = (idx: number): number => {
    let j = idx + 1;
    while (j < cmd.length && (cmd[j] === ' ' || cmd[j] === '\t')) {
      j++;
    }
    if (cmd[j] === '\r' && cmd[j + 1] === '\n') return j + 1;
    if (cmd[j] === '\n' || cmd[j] === '\r') return j;
    return idx;
  };

  for (let i = 0; i < cmd.length; i++) {
    const char = cmd[i];
    const nextChar = i + 1 < cmd.length ? cmd[i + 1] : null;

    if (escaped) {
      current += char;
      escaped = false;
      continue;
    }

    // bash 转义 / 续行
    if (char === '\\') {
      if (nextChar === '\n' || nextChar === '\r') {
        i = skipLineBreak(i);
        continue;
      }
      escaped = true;
      continue;
    }

    // Windows cmd 转义 / 续行：
    // - ^ 后紧跟换行（中间允许空白）：续行
    // - ^"        只输出字符 "，不切换引号模式
    // - ^\\^"     输出字符 "（浏览器导出 cmd cURL 中用于表示带引号的值）
    // - ^x        其他字符原样输出（如 ^&→&）
    if (char === '^') {
      if (isCmdLineContinuation(i)) {
        i = skipCmdLineBreak(i);
        continue;
      }
      if (nextChar === '"') {
        current += '"';
        i += 1;
        continue;
      }
      // ^\\^" 整体输出一个双引号（不切换引号模式）
      if (
          nextChar === '\\' &&
          i + 3 < cmd.length &&
          cmd[i + 2] === '^' &&
          cmd[i + 3] === '"'
      ) {
        current += '"';
        i += 3;
        continue;
      }
      if (nextChar !== null) {
        current += nextChar;
        i += 1;
      } else {
        current += char;
      }
      continue;
    }

    if (inQuote) {
      if (char === inQuote) {
        inQuote = null;
      } else {
        current += char;
      }
      continue;
    }

    if (char === '"' || char === "'") {
      inQuote = char;
      continue;
    }

    if (char === ' ' || char === '\t' || char === '\n' || char === '\r') {
      if (current) {
        tokens.push(current);
        current = '';
      }
      continue;
    }

    current += char;
  }

  if (current) {
    tokens.push(current);
  }

  return tokens;
};

/**
 * 解析 cURL 命令，提取 method、url、headers、cookies、body、contentType。
 */
const parseCurl = (cmd: string) => {
  const tokens = tokenizeCurl(cmd.trim());
  console.log('[cURL import] tokens:', tokens);

  const CURL_OPTIONS = new Set([
    '-X', '--request',
    '-H', '--header',
    '-b', '--cookie',
    '-d', '--data', '--data-raw', '--data-binary', '--data-urlencode',
    '-u', '--user',
    '-L', '--location',
    '-v', '--verbose',
    '-s', '--silent',
    '-A', '--user-agent',
    '-e', '--referer',
    '-F', '--form',
    '--url',
    '-I', '--head',
    '-i', '--include',
    '-k', '--insecure',
    '--compressed',
  ]);

  const isCurlOption = (t: string) => CURL_OPTIONS.has(t);

  const stripQuotes = (str: string): string => {
    if (
        (str.startsWith('"') && str.endsWith('"')) ||
        (str.startsWith("'") && str.endsWith("'"))
    ) {
      return str.slice(1, -1);
    }
    return str;
  };

  /**
   * 从 startIdx 开始连续取 token，直到遇到下一个 cURL 选项或命令结束。
   * 用于还原被空格拆分的 Header / Cookie / Body / URL 值。
   */
  const collectValue = (startIdx: number): { value: string; endIdx: number } => {
    const parts: string[] = [];
    let j = startIdx;
    while (j < tokens.length && !isCurlOption(tokens[j])) {
      parts.push(tokens[j]);
      j++;
    }
    return { value: stripQuotes(parts.join(' ')), endIdx: Math.max(startIdx - 1, j - 1) };
  };

  const result: {
    method?: string;
    url?: string;
    headers: Record<string, string>;
    cookies: Record<string, string>;
    body?: string;
    contentType?: string;
  } = { headers: {}, cookies: {} };

  let i = 0;
  while (i < tokens.length) {
    const token = tokens[i];
    const lowerToken = token.toLowerCase();

    // 跳过 curl 命令本身（支持 curl、curl.exe、绝对路径等）
    if (
        lowerToken === 'curl' ||
        lowerToken.endsWith('/curl') ||
        lowerToken.endsWith('\\curl') ||
        lowerToken.endsWith('curl.exe')
    ) {
      i++;
      continue;
    }

    if (token === '-X' || token === '--request') {
      if (i + 1 < tokens.length && !isCurlOption(tokens[i + 1])) {
        result.method = tokens[i + 1].toUpperCase();
        i++;
      }
    } else if (token === '-H' || token === '--header') {
      if (i + 1 < tokens.length && !isCurlOption(tokens[i + 1])) {
        const { value, endIdx } = collectValue(i + 1);
        i = endIdx;
        const colonIndex = value.indexOf(':');
        if (colonIndex > 0) {
          const name = value.substring(0, colonIndex).trim();
          const val = value.substring(colonIndex + 1).trim();
          console.log('[cURL import] parsed header:', { name, value: val });
          result.headers[name] = val;
          if (name.toLowerCase() === 'content-type') {
            result.contentType = val;
          }
        }
      }
    } else if (token === '-b' || token === '--cookie') {
      if (i + 1 < tokens.length && !isCurlOption(tokens[i + 1])) {
        const { value, endIdx } = collectValue(i + 1);
        i = endIdx;
        value.split(';').forEach((pair) => {
          const eqIndex = pair.indexOf('=');
          if (eqIndex > 0) {
            result.cookies[pair.substring(0, eqIndex).trim()] = pair.substring(eqIndex + 1).trim();
          }
        });
      }
    } else if (
        token === '-d' ||
        token === '--data' ||
        token === '--data-raw' ||
        token === '--data-binary' ||
        token === '--data-urlencode'
    ) {
      if (i + 1 < tokens.length && !isCurlOption(tokens[i + 1])) {
        const { value, endIdx } = collectValue(i + 1);
        i = endIdx;
        result.body = value;
      }
    } else if (token === '--url') {
      if (i + 1 < tokens.length && !isCurlOption(tokens[i + 1])) {
        result.url = stripQuotes(tokens[i + 1]);
        i++;
      }
    } else if (!token.startsWith('-') && !result.url) {
      // URL 可能跨越多个 token（如 cmd 下 ^" 把 URL 拆散了），需要拼接
      const { value, endIdx } = collectValue(i);
      i = endIdx;
      const stripped = stripQuotes(value);
      if (stripped) {
        result.url = stripped;
      }
    }

    i++;
  }

  console.log('[cURL import] parse result:', result);
  return result;
};

const handleCurlImport = (done: (closed: boolean) => void) => {
  const cmd = curlText.value.trim();
  if (!cmd) {
    Message.warning({ content: '请输入 cURL 命令', duration: 2000 });
    done(false);
    return;
  }

  try {
    const parsed = parseCurl(cmd);
    if (!parsed.url) {
      Message.error({ content: '无法解析 URL，请检查 cURL 命令', duration: 3000 });
      done(false);
      return;
    }

    // 方法：显式指定 > body 存在默认 POST > 默认 GET
    const upperMethod = parsed.method || (parsed.body ? 'POST' : 'GET');
    if (Object.values(RequestMethod).includes(upperMethod as RequestMethod)) {
      localFormData.value.requestMethod = upperMethod as RequestMethod;
    } else {
      localFormData.value.requestMethod = parsed.body ? RequestMethod.POST : RequestMethod.GET;
    }

    // URL：先回填完整 URL，再走 handlePathBlur 自动提取 Query 参数
    localFormData.value.requestPath = parsed.url;
    handlePathBlur();

    // Headers
    const headers = Object.entries(parsed.headers).map(([name, value]) => {
      const p = new RequestParameter();
      p.name = name;
      p.value = value;
      p.type = ParameterType.STRING;
      p.disabled = false;
      return p;
    });
    if (headers.length > 0) {
      headerTableRef.value?.setData(headers);
    }

    // Cookies
    const cookies = Object.entries(parsed.cookies).map(([name, value]) => {
      const p = new RequestParameter();
      p.name = name;
      p.value = value;
      p.type = ParameterType.STRING;
      p.disabled = false;
      return p;
    });
    if (cookies.length > 0) {
      cookieTableRef.value?.setData(cookies);
    }

    // Body
    if (parsed.body) {
      const contentType = parsed.contentType?.toLowerCase() || '';
      const body = localFormData.value.body || new Body();

      if (contentType.includes('application/json')) {
        body.mode = BodyMode.JSON;
        body.json = parsed.body;
      } else if (contentType.includes('application/x-www-form-urlencoded')) {
        body.mode = BodyMode.X_WWW_FORM_URLENCODED;
        const params = new URLSearchParams(parsed.body);
        const formParams: RequestParameter[] = [];
        params.forEach((value, key) => {
          const p = new RequestParameter();
          p.name = key;
          p.value = value;
          p.type = ParameterType.STRING;
          p.disabled = false;
          formParams.push(p);
        });
        body.xWwwFormUrlencoded = formParams;
        nextTick(() => xWwwFormUrlencodedRef.value?.setData(formParams));
      } else if (contentType.includes('multipart/form-data')) {
        Message.info({ content: 'multipart/form-data 暂不支持自动解析，请手动配置', duration: 3000 });
      } else {
        // 未识别 Content-Type 时按 JSON/文本兜底
        body.mode = BodyMode.JSON;
        body.json = parsed.body;
      }

      localFormData.value.body = body;
    }

    curlText.value = '';
    Message.success({ content: 'cURL 导入成功', duration: 2000 });
    onDataChange();
    done(true);
  } catch (e) {
    console.error(e);
    Message.error({ content: 'cURL 解析失败，请检查命令格式', duration: 3000 });
    done(false);
  }
};

const onCaseNameInput = (value: string) => {
  // 过滤非法字符
  const filtered = value.replace(/[^\u4e00-\u9fa5a-zA-Z0-9_\-.\s()\uff08\uff09]/g, '');
  if (filtered !== value) {
    caseNameForm.caseName = filtered;
  }
  // 校验
  const name = caseNameForm.caseName?.trim();
  caseNameValid.value = !!name && name.length <= 50 && /^[\u4e00-\u9fa5a-zA-Z0-9_\-.\s()\uff08\uff09]+$/.test(name);
};

const saveAsCase = () => {
  if (!localFormData.value.id) {
    Message.error({ content: '接口未保存，请先保存接口后再保存为用例', duration: 3000 });
    return;
  }
  if (!validateApiName()) return;
  if (!validateRequestPath()) return;
  if (!validateParameters()) return;
  if (!validateExtractions()) return;
  if (!validateAssertions()) return;

  const savedData = collectAllData();
  savedData.sourceDratId = savedData.id;
  pendingCaseData = savedData;

  // 默认用例名称为接口名 + _用例
  caseNameForm.caseName = savedData.apiName + '_用例';
  onCaseNameInput(caseNameForm.caseName);
  caseNameModalVisible.value = true;
};

const handleConfirmSaveAsCase = () => {
  const name = caseNameForm.caseName?.trim();
  if (!name) {
    Message.error({ content: '用例名称不能为空', duration: 2000 });
    return;
  }
  if (name.length > 50) {
    Message.error({ content: '用例名称长度不能超过50个字符', duration: 2000 });
    return;
  }
  const regex = /^[\u4e00-\u9fa5a-zA-Z0-9_\-.\s()\uff08\uff09]+$/;
  if (!regex.test(name)) {
    Message.error({ content: '用例名称只能包含中文、英文、数字、下划线、空格及 - . ( )', duration: 3000 });
    return;
  }

  if (pendingCaseData) {
    pendingCaseData.apiName = name;
    emit('saveAsCase', pendingCaseData);
    caseNameModalVisible.value = false;
    pendingCaseData = null;
  }
};

defineExpose({
  getCurrentData,
  resetChanges,
  saveData,
  hasUnsavedChanges: () => checkHasChanges(),
  validateApiName,
  validateRequestPath,
  validateParameters,
  validateExtractions,
  validateAssertions,
  validateSql: validateSqlSingleStatement,
});
</script>

<style scoped>
.api-debug-form {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* 基础信息工具栏：响应式，允许窄屏换行 */
.form-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.form-toolbar .toolbar-name {
  flex: 1 1 220px;
  min-width: 180px;
}


.form-toolbar .toolbar-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  flex: 1 1 auto;
  justify-content: flex-end;
}

/* 场景模式：服务下拉 + 发送按钮单行，服务下拉占剩余宽度 */
.form-toolbar .scene-actions {
  flex-wrap: nowrap;
  width: 100%;
}

.form-toolbar .scene-actions .serve-select {
  flex: 1 1 auto;
  min-width: 0;
}

/* 请求信息工具栏：响应式 */
/* 环境 + 服务独立行：服务下拉占剩余宽度，窄面板下也不会被挤到下一行 */
.env-serve-row {
  display: flex;
  flex-wrap: nowrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.env-serve-row .serve-select {
  flex: 1 1 auto;
  min-width: 0;
  max-width: 100%;
}

.request-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

/* 组合式 URL 地址栏：方法 + 路径一体化 */
.url-input-bar {
  display: flex;
  align-items: center;
  flex: 1 1 auto;
  min-width: 0;
  border: 1px solid var(--color-border);
  border-radius: 4px;
  overflow: hidden;
  background-color: var(--color-bg-2);
  transition: border-color 0.2s, box-shadow 0.2s;
}

/* URL 栏内的服务地址只读块 */
.base-url-chip {
  flex-shrink: 1;
  min-width: 0;
  max-width: 45%;
  align-self: stretch;
  display: inline-flex;
  align-items: center;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--color-text-3);
  font-size: 13px;
  line-height: normal;
  padding: 0 4px;
  user-select: none;
}

/* 接口名称展示态（双击进入编辑） */
.api-name-display {
  min-width: 120px;
  max-width: 320px;
  padding: 4px 8px;
  border: 1px solid transparent;
  border-radius: 4px;
  font-size: 14px;
  line-height: 22px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: text;
}

.api-name-display:hover:not(.is-disabled) {
  background: var(--color-fill-2);
  border-color: var(--color-border-2);
}

.api-name-display.is-disabled {
  cursor: not-allowed;
  color: var(--color-text-3);
}

.url-input-bar:hover {
  border-color: var(--color-primary-light-3);
}

.url-input-bar:focus-within {
  border-color: rgb(var(--primary-6));
  box-shadow: 0 0 0 2px var(--color-primary-light-2);
}

.url-input-bar :deep(.arco-select-view),
.url-input-bar :deep(.arco-input-wrapper) {
  border: none;
  border-radius: 0;
  background: transparent;
}

.url-input-bar :deep(.arco-input-wrapper .arco-input) {
  background: transparent;
}

.url-input-bar .method-select {
  width: 80px !important;
  flex: 0 0 80px;
}

.url-input-bar .url-divider {
  width: 1px;
  height: 22px;
  background-color: var(--color-border);
  flex-shrink: 0;
}

.url-input-bar .path-input {
  flex: 1 1 auto;
  width: auto;
  min-width: 0;
}

.url-input-bar .curl-import-btn {
  flex-shrink: 0;
  margin-right: 4px;
  color: var(--color-text-3);
}

.url-input-bar .curl-import-btn:hover {
  color: rgb(var(--primary-6));
}

/* 服务地址下拉选项：紧凑展示 */
.serve-option {
  display: flex;
  flex-direction: column;
  gap: 2px;
  line-height: 1.3;
}

.serve-option-name {
  font-weight: 500;
}

.serve-option-address {
  color: var(--color-text-3);
  font-size: 12px;
}

.inner-tabs {
  height: 100%;
  display: flex;
  flex-direction: column;
}

/* tab 标题上的参数个数徽标：小而克制，不抢眼 */
.tab-count {
  display: inline-block;
  min-width: 15px;
  height: 15px;
  padding: 0 4px;
  margin-left: 5px;
  font-size: 11px;
  line-height: 15px;
  text-align: center;
  color: var(--color-text-3);
  background-color: var(--color-fill-3);
  border-radius: 8px;
  vertical-align: middle;
}

.inner-tabs :deep(.arco-tabs) {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.inner-tabs :deep(.arco-tabs-nav) {
  flex-shrink: 0;
  margin-bottom: 8px;
}

.inner-tabs :deep(.arco-tabs-content) {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  padding: 0;
}

.inner-tabs :deep(.arco-tabs-pane) {
  height: 100%;
  padding: 0;
  overflow: hidden;
}

:deep(.arco-tabs-content-list) {
  height: 100%;
}

/* Body 标签页：radio + 内容 垂直 flex */
.body-mode-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.body-mode-panel > .arco-radio-group {
  flex-shrink: 0;
  margin-bottom: 12px;
}

.body-mode-panel > .parameter-table,
.body-mode-panel > .body-code-editor,
.body-mode-panel > .body-empty {
  flex: 1;
  min-height: 0;
}

/* ===== 鉴权配置面板 ===== */
.auth-panel {
  padding: 12px 0;
}

.auth-preview {
  margin: -8px 0 12px 0;
  font-size: 12px;
  color: var(--color-text-3);
}

.auth-tip {
  font-size: 12px;
  color: var(--color-text-3);
}

/* ===== SQL 数据库配置面板 ===== */.sql-db-panel {
  padding: 12px 0;
}

.sql-db-effective {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  font-size: 13px;
  color: var(--color-text-2);
}

.sql-db-effective-label {
  color: var(--color-text-3);
  flex-shrink: 0;
}

.sql-db-effective-empty {
  color: var(--color-text-3);
}

.sql-db-warning {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: -4px 0 12px 0;
  font-size: 12px;
  color: #ff7d00;
}

.sql-db-divider {
  display: flex;
  align-items: center;
  margin: 16px 0 12px;
  font-size: 12px;
  color: var(--color-text-3);
}

.sql-db-divider::before,
.sql-db-divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: var(--color-border-2);
}

.sql-db-divider::before {
  margin-right: 12px;
}

.sql-db-divider::after {
  margin-left: 12px;
}

.sql-db-form {
  :deep(.arco-form-item) {
    margin-bottom: 12px;
  }
}
</style>