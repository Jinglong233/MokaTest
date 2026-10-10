<template>
  <a-modal width="900px" v-model:visible="visible" :hide-cancel="true" :hide-title="true" :footer="false" @cancel="emit('closeDialog')">
    <a-tabs position="left" :default-active-key="1">
      <a-tab-pane :key="1" :title="$t('scene.func.tabCommon')">
        <a-alert style="margin-bottom: 5px">{{ $t('scene.func.copyTip') }}</a-alert>
        <a-table :columns="columns" :scroll="{x:0,y:800}" :data="data" :pagination="false">
          <template #mark="{record}">
              <span style="color: #165dff; padding:2px 6px; border-radius:4px; cursor:pointer;"
                    @click="copyToClipboard(extractTemplateFunction(record.mark).function)"
              >
              {{ extractTemplateFunction(record.mark).function }}
              </span>
            {{ extractTemplateFunction(record.mark).content }}
          </template>
        </a-table>
      </a-tab-pane>
      <a-tab-pane :key="2" :title="$t('scene.func.tabCustom')">
        <a-alert style="margin-bottom: 5px">{{ $t('scene.func.customTip') }}</a-alert>
        <a-table :columns="customColumns" :scroll="{x:0,y:800}" :data="customFunctionList" :pagination="false" :loading="customLoading">
          <template #call="{record}">
            <span style="color: #d46b08; background: #fff7e6; border: 1px solid #ffd591; padding:2px 6px; border-radius:4px; cursor:pointer; font-family: monospace;"
                  @click="copyToClipboard(buildCustomDisplay(record))"
            >
              {{ buildCustomDisplay(record) }}
            </span>
          </template>
          <template #empty>
            <a-empty :description="$t('scene.func.customEmpty')"/>
          </template>
        </a-table>
      </a-tab-pane>
      <a-tab-pane :key="3" :title="$t('scene.func.tabOther')" :disabled="true">
      </a-tab-pane>
    </a-tabs>
  </a-modal>
</template>

<script setup lang="ts">

import {computed, ref, watch} from "vue";
import {useI18n} from 'vue-i18n';
import {Message} from "@arco-design/web-vue";
import {getCustomFunctionList} from "@/api/MyApi/customFunction";
import {CustomFunction} from "@/types/domain/api/CustomFunction";
import useProjectStore from "@/store/modules/project";

const projectStore = useProjectStore();
const { t } = useI18n();

const props = defineProps({
  visible: {
    type: Boolean,
    default: false,
  }
});

const visible = ref<Boolean>(<Boolean>props.visible);

// ==================== 自定义函数 ====================

const customColumns = computed(() => [
  {
    title: t('scene.func.colCall'),
    slotName: 'call',
    width: 260
  },
  {
    title: t('scene.func.colName'),
    dataIndex: 'funcName',
    width: 140
  },
  {
    title: t('scene.func.colDesc'),
    dataIndex: 'description',
    ellipsis: true,
    tooltip: {position: 'right'},
  },
]);

const customFunctionList = ref<CustomFunction[]>([]);
const customLoading = ref(false);

/** 显示/复制统一用 fn.名称(参数...) 形式：文本字段失焦自动转底层存储，脚本里原生可用 */
const buildCustomDisplay = (fn: CustomFunction) =>
    `fn.${fn.funcName}(${fn.funcParams || ''})`;

const loadCustomFunctions = async () => {
  const projectId = projectStore.getProjectId;
  if (!projectId) {
    customFunctionList.value = [];
    return;
  }
  customLoading.value = true;
  try {
    const res = await getCustomFunctionList(Number(projectId));
    customFunctionList.value = res.data || [];
  } catch (e) {
    customFunctionList.value = [];
  } finally {
    customLoading.value = false;
  }
};

const columns = computed(() => [
  {
    title: t('scene.func.colFunction'),
    dataIndex: 'function',
    width: 200
  },
  {
    title: t('scene.func.colName'),
    dataIndex: 'name',
    width: 150
  },
  {
    title: t('scene.func.colMark'),
    dataIndex: 'mark',
    ellipsis: true,
    tooltip: {position: 'right'},
    slotName: 'mark'
  },
]);

const data = computed(() => [
  {
    key: '1',
    function: 'md5(string)',
    name: t('scene.func.md5Name'),
    mark: t('scene.func.md5Mark'),
  },
  {
    key: '1',
    function: 'SHA256(string)',
    name: t('scene.func.sha256Name'),
    mark: t('scene.func.sha256Mark'),
  },
  {
    key: '1',
    function: 'SHA512(string)',
    name: t('scene.func.sha512Name'),
    mark: t('scene.func.sha512Mark'),
  },
  {
    key: '1',
    function: 'IdCard(isEighteen, address, birthday, sex)',
    name: t('scene.func.idCardName'),
    mark: t('scene.func.idCardMark'),
  },
  {
    key: '1',
    function: 'RandomIdCard()',
    name: t('scene.func.randomIdCardName'),
    mark: t('scene.func.randomIdCardMark'),
  },
  {
    key: '1',
    function: 'VerifyIdCard(cardId, strict)',
    name: t('scene.func.verifyIdCardName'),
    mark: t('scene.func.verifyIdCardMark'),
  },
  {
    key: '1',
    function: '{{__VerifyIdCard(231231, true)}}, 结果: false',
    name: t('scene.func.toStringLuName'),
    mark: t('scene.func.toStringLuMark'),
  }, {
    key: '1',
    function: 'RandomInt(start, end)',
    name: t('scene.func.randomIntName'),
    mark: t('scene.func.randomIntMark'),
  },
  {
    key: '1',
    function: 'RandomFloat0()',
    name: t('scene.func.randomFloatName'),
    mark: t('scene.func.randomFloatMark'),
  },
  {
    key: '1',
    function: 'RandomString(int length,int type)',
    name: t('scene.func.randomStringName'),
    mark: t('scene.func.randomStringMark'),
  },
  {
    key: '1',
    function: 'Uuid()',
    name: t('scene.func.uuidName'),
    mark: t('scene.func.uuidMark'),
  },
  {
    key: '1',
    function: 'ToTimeStamp(option)',
    name: t('scene.func.timestampName'),
    mark: t('scene.func.timestampMark'),
  },
  {
    key: '1',
    function: 'ToStandardTime(options int)',
    name: t('scene.func.standardTimeName'),
    mark: t('scene.func.standardTimeMark'),
  }]);

const emit = defineEmits(['closeDialog']);


// 提取函数
const extractTemplateFunction = (str: string) => {
  const pattern = /(\{\{__[^{}]+__\}\})(.*)/;

  const match = str.match(pattern);
  if (match) {
    return {
      function: match[1], // 第一部分：{{__函数__}}
      content: match[2],  // 第二部分：后面的内容
      fullMatch: match[0]
    };
  }
  return null;
}

// 复制函数
const copyToClipboard = (fun: string) => {
  const input = document.createElement('textarea');
  input.value = fun;
  document.body.appendChild(input);
  input.select();
  document.execCommand('copy');
  document.body.removeChild(input);

  // 简单提示
  Message.success({
    content: t('scene.common.copySuccess'),
    duration: 1000
  })

}

watch((() => props.visible), (newValue) => {
  visible.value = newValue;
  if (newValue) {
    loadCustomFunctions();
  }
}, {deep: true})

</script>

<style scoped>

</style>