<template>
  <a-modal width="900px" v-model:visible="visible" :hide-cancel="true" :hide-title="true" :footer="false" @cancel="emit('closeDialog')">
    <a-tabs position="left" :default-active-key="1">
      <a-tab-pane :key="1" title="公共函数列表">
        <a-alert style="margin-bottom: 5px">点击高亮函数即可完成复制</a-alert>
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
      <a-tab-pane :key="2" title="自定义函数">
        <a-alert style="margin-bottom: 5px">点击高亮表达式复制调用代码：脚本中写 fn.名称(...)；参数值/步骤字段中写 @fn.名称(...)（也兼容 fn. 写法，失焦自动转换）；在「接口测试 → 自定义函数」中维护</a-alert>
        <a-table :columns="customColumns" :scroll="{x:0,y:800}" :data="customFunctionList" :pagination="false" :loading="customLoading">
          <template #call="{record}">
            <span style="color: #d46b08; background: #fff7e6; border: 1px solid #ffd591; padding:2px 6px; border-radius:4px; cursor:pointer; font-family: monospace;"
                  @click="copyToClipboard(buildCustomDisplay(record))"
            >
              {{ buildCustomDisplay(record) }}
            </span>
          </template>
          <template #empty>
            <a-empty description="当前项目还没有自定义函数"/>
          </template>
        </a-table>
      </a-tab-pane>
      <a-tab-pane :key="3" title="其他（待做...）" :disabled="true">
      </a-tab-pane>
    </a-tabs>
  </a-modal>
</template>

<script setup lang="ts">

import {reactive, ref, watch} from "vue";
import {Message} from "@arco-design/web-vue";
import {getCustomFunctionList} from "@/api/MyApi/customFunction";
import {CustomFunction} from "@/types/domain/api/CustomFunction";
import useProjectStore from "@/store/modules/project";

const projectStore = useProjectStore();

const props = defineProps({
  visible: {
    type: Boolean,
    default: false,
  }
});

const visible = ref<Boolean>(<Boolean>props.visible);

// ==================== 自定义函数 ====================

const customColumns = [
  {
    title: '调用表达式',
    slotName: 'call',
    width: 260
  },
  {
    title: '函数名称',
    dataIndex: 'funcName',
    width: 140
  },
  {
    title: '描述',
    dataIndex: 'description',
    ellipsis: true,
    tooltip: {position: 'right'},
  },
];

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

const columns = [
  {
    title: '函数',
    dataIndex: 'function',
    width: 200
  },
  {
    title: '函数名称',
    dataIndex: 'name',
    width: 150
  },
  {
    title: '备注',
    dataIndex: 'mark',
    ellipsis: true,
    tooltip: {position: 'right'},
    slotName: 'mark'
  },
];

const data = reactive([
  {
    key: '1',
    function: 'md5(string)',
    name: 'md5加密',
    mark: '{{__MD5(ABC)__}}, 加密字符串',
  },
  {
    key: '1',
    function: 'SHA256(string)',
    name: 'sha256加密',
    mark: '{{__SHA256(ABC)__}}, 加密字符串',
  },
  {
    key: '1',
    function: 'SHA512(string)',
    name: 'sha512加密',
    mark: '{{__SHA512(ABC)__}}, 加密字符串',
  },
  {
    key: '1',
    function: 'IdCard(isEighteen, address, birthday, sex)',
    name: '身份证号生成',
    mark: '{{__IdCard(true, 北京市, 2000, 1)__}}, 北京市男2000年出生18位身份证号。 IdCard 根据参数生成身份证号。 isEighteen 是否生成18位号码。 address 省市县三级地区官方全称: 如"北京市"、"台湾省"、"香港特别行政区"、"深圳市"、"黄浦区"。 birthday 出生日期: 如 "2000"、"199801"、"19990101"。 sex 性别: 1为男性, 0为女性。',
  },
  {
    key: '1',
    function: 'RandomIdCard()',
    name: '随机生成身份证号',
    mark: '{{__RandomIdCard()__}}, 随机身份证号',
  },
  {
    key: '1',
    function: 'VerifyIdCard(cardId, strict)',
    name: '身份证号校验',
    mark: '{{__VerifyIdCard(231231, true)__}}, 结果: false',
  },
  {
    key: '1',
    function: '{{__VerifyIdCard(231231, true)}}, 结果: false',
    name: '改变字符串大小写',
    mark: '{{__ToStringLU(abc, L)__}}, 全部小写',
  }, {
    key: '1',
    function: 'RandomInt(start, end)',
    name: '随机数生成(整数)',
    mark: '{{__RandomInt(start, end)__}}, 随机生成start-end之间的整数',
  },
  {
    key: '1',
    function: 'RandomFloat0()',
    name: '随机数生成(小数)',
    mark: '{{__RandomFloat0()__}}, 随机生成0-1之间的小数',
  },
  {
    key: '1',
    function: 'RandomString(int length,int type)',
    name: '随机数生成(字符串)',
    mark: "{{__RandomString(5,0)__}}, 生成一个5位的随机字符串，包含a-z、0-9、A-Z组成的字符串参数：* length: 需要生成的字符串长度（整数）* type: 字符串类型（整数）- 0: 包含小写字母a-z、数字0-9、大写字母A-Z - 1: 仅包含小写字母a-z和大写字母A-Z - 2: 仅包含小写字母a-z - 3: 仅包含大写字母A-Z",
  },
  {
    key: '1',
    function: 'Uuid()',
    name: '生成uuid',
    mark: '{{__GetUUid()__}}, 随机生成uuid',
  },
  {
    key: '1',
    function: 'ToTimeStamp(option)',
    name: '时间戳',
    mark: '{{__ToTimeStamp(s)__}}, 生成秒级时间戳字符串 option: s, ms, ns, ws; 分别是秒; 毫秒; 纳秒; 微秒',
  },
  {
    key: '1',
    function: 'ToStandardTime(options int)',
    name: '标准时间格式',
    mark: '{{__ToStandardTime(0)__}},0,1,2,3,4,5,6,7,8,9,10, 默认为0',
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
    content: "复制成功",
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