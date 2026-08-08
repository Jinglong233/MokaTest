import {computed, reactive, ref, watch} from 'vue';
import {ParameterType} from '@/types/domain/api/apiEnum/ParameterType';
import {DataTemplate} from '@/types/domain/api/DataTemplate';
import {MockConfig} from '@/types/domain/api/requestModel/MockConfig';
import {getDataTemplateList} from '@/api/MyApi/dataTemplate';
import {generateMock} from '@/api/MyApi/mock';
import useProjectStore from '@/store/modules/project';
import {Message} from '@arco-design/web-vue';

export interface UseMockDataFormOptions {
  parameterType?: ParameterType;
  mockConfig?: MockConfig;
}

export interface MockTypeOption {
  value: string;
  label: string;
  types: ParameterType[];
}

export interface MockCategory {
  value: string;
  label: string;
  children: MockTypeOption[];
}

const ALL_TYPES = [ParameterType.STRING, ParameterType.INTEGER, ParameterType.NUMBER, ParameterType.BOOLEAN, ParameterType.JSON, ParameterType.ARRAY];
const STRING_TYPES = [ParameterType.STRING];
const NUMBER_TYPES = [ParameterType.STRING, ParameterType.NUMBER];
const INT_TYPES = [ParameterType.STRING, ParameterType.INTEGER, ParameterType.NUMBER];

const mockCategories: MockCategory[] = [
  {
    value: 'basic',
    label: '基础变量',
    children: [
      {value: 'fixed', label: '固定值', types: ALL_TYPES},
      {value: 'choice', label: '枚举选择', types: ALL_TYPES},
      {value: 'boolean', label: '布尔值', types: [ParameterType.STRING, ParameterType.BOOLEAN]},
      {value: 'timestamp', label: '时间戳', types: STRING_TYPES},
    ],
  },
  {
    value: 'string',
    label: '字符串',
    children: [
      {value: 'text', label: '随机字符串', types: STRING_TYPES},
      {value: 'character', label: '随机字符', types: STRING_TYPES},
      {value: 'uuid', label: 'UUID', types: STRING_TYPES},
    ],
  },
  {
    value: 'personal',
    label: '个人信息',
    children: [
      {value: 'name', label: '姓名', types: STRING_TYPES},
      {value: 'cname', label: '中文姓名', types: STRING_TYPES},
      {value: 'ename', label: '英文姓名', types: STRING_TYPES},
      {value: 'phone', label: '手机号', types: STRING_TYPES},
      {value: 'email', label: '邮箱', types: STRING_TYPES},
      {value: 'idCard', label: '身份证号', types: STRING_TYPES},
      {value: 'bankcard', label: '银行卡号', types: STRING_TYPES},
    ],
  },
  {
    value: 'organization',
    label: '组织信息',
    children: [
      {value: 'company', label: '公司名', types: STRING_TYPES},
      {value: 'address', label: '地址', types: STRING_TYPES},
    ],
  },
  {
    value: 'number',
    label: '数字',
    children: [
      {value: 'int', label: '整数', types: INT_TYPES},
      {value: 'long', label: '长整数', types: INT_TYPES},
      {value: 'float', label: '浮点数', types: NUMBER_TYPES},
      {value: 'double', label: '双精度', types: NUMBER_TYPES},
    ],
  },
  {
    value: 'datetime',
    label: '日期时间',
    children: [
      {value: 'date', label: '日期', types: STRING_TYPES},
      {value: 'datetime', label: '日期时间', types: STRING_TYPES},
      {value: 'time', label: '时间', types: STRING_TYPES},
    ],
  },
];

/**
 * 查找类型所属分类
 */
export function findMockCategory(type?: string): string | undefined {
  if (!type) return undefined;
  for (const category of mockCategories) {
    if (category.children.some(item => item.value === type)) {
      return category.value;
    }
  }
  return undefined;
}

/**
 * 将 mockConfig 同步到表单
 */
function applyMockConfigToForm(form: any, config?: MockConfig) {
  if (!config || !config.type) {
    return;
  }
  form.type = config.type;
  if (config.locale !== undefined) form.locale = config.locale;
  if (config.caseType !== undefined) form.caseType = config.caseType;
  if (config.min !== undefined) form.min = config.min;
  if (config.max !== undefined) form.max = config.max;
  if (config.scale !== undefined) form.scale = config.scale;
  if (config.length !== undefined) form.length = config.length;
  if (config.format !== undefined) form.format = config.format;
  if (config.choices !== undefined) form.choices = config.choices;
  if (config.fixedValue !== undefined) form.fixedValue = config.fixedValue;
  if (config.templateId !== undefined) form.templateId = config.templateId;
}

export function useMockDataForm(options: UseMockDataFormOptions = {}) {
  const {parameterType, mockConfig} = options;
  const projectStore = useProjectStore();
  const templateList = ref<DataTemplate[]>([]);
  const templateLoading = ref(false);

  const mockTypes = computed((): MockTypeOption[] => {
    const flat = mockCategories.flatMap(category => category.children);
    if (parameterType === undefined) return flat;
    return flat.filter(item => item.types.includes(parameterType));
  });

  const form = reactive({
    type: 'name',
    locale: 'zh',
    caseType: 'lower',
    min: 0,
    max: 100,
    scale: 2,
    length: 10,
    format: 'yyyy-MM-dd HH:mm:ss',
    choices: 'A,B,C',
    fixedValue: '',
    templateId: undefined as number | undefined,
  });

  const showLocale = computed(() => ['name', 'company', 'address'].includes(form.type));
  const showCaseType = computed(() => form.type === 'character');
  const showMinMax = computed(() => ['int', 'long', 'float', 'double'].includes(form.type));
  const showScale = computed(() => ['float', 'double'].includes(form.type));
  const showLength = computed(() => form.type === 'text' || form.type === 'character');
  const showFormat = computed(() => ['date', 'datetime', 'time'].includes(form.type));
  const showChoices = computed(() => form.type === 'choice');
  const showFixed = computed(() => form.type === 'fixed');
  const showTemplate = computed(() => form.type === 'template');

  const formatPresets = computed(() => {
    switch (form.type) {
      case 'date':
        return [
          {label: 'yyyy-MM-dd', value: 'yyyy-MM-dd'},
          {label: 'yyyy/MM/dd', value: 'yyyy/MM/dd'},
          {label: 'yyyyMMdd', value: 'yyyyMMdd'},
          {label: 'yyyy年MM月dd日', value: 'yyyy年MM月dd日'},
        ];
      case 'time':
        return [
          {label: 'HH:mm:ss', value: 'HH:mm:ss'},
          {label: 'HH:mm', value: 'HH:mm'},
          {label: 'HH时mm分ss秒', value: 'HH时mm分ss秒'},
          {label: 'HHmmss', value: 'HHmmss'},
        ];
      case 'datetime':
      default:
        return [
          {label: 'yyyy-MM-dd HH:mm:ss', value: 'yyyy-MM-dd HH:mm:ss'},
          {label: 'yyyy/MM/dd HH:mm:ss', value: 'yyyy/MM/dd HH:mm:ss'},
          {label: "yyyy-MM-dd'T'HH:mm:ss'Z'", value: "yyyy-MM-dd'T'HH:mm:ss'Z'"},
          {label: 'yyyy-MM-dd HH:mm:ss.SSS', value: 'yyyy-MM-dd HH:mm:ss.SSS'},
        ];
    }
  });

  const canInsert = computed(() => {
    if (!form.type) return false;
    if (form.type === 'fixed') return !!form.fixedValue;
    if (form.type === 'template') return !!form.templateId;
    return true;
  });

  /**
   * 当前表单对应的结构化配置
   */
  const currentMockConfig = computed((): MockConfig => {
    const config = new MockConfig();
    config.type = form.type;
    switch (form.type) {
      case 'name':
      case 'company':
      case 'address':
        config.locale = form.locale;
        break;
      case 'cname':
      case 'ename':
        config.locale = form.type === 'cname' ? 'zh' : 'en';
        break;
      case 'character':
        config.caseType = form.caseType;
        config.length = form.length;
        break;
      case 'text':
        config.length = form.length;
        break;
      case 'int':
      case 'long':
        config.min = form.min;
        config.max = form.max;
        break;
      case 'float':
      case 'double':
        config.min = form.min;
        config.max = form.max;
        config.scale = form.scale;
        break;
      case 'date':
      case 'datetime':
      case 'time':
        config.format = form.format;
        break;
      case 'choice':
        config.choices = form.choices;
        break;
      case 'fixed':
        config.fixedValue = form.fixedValue;
        break;
      case 'template':
        config.templateId = form.templateId;
        break;
    }
    return config;
  });

  /**
   * 判断参数值是否需要加引号
   */
  function needsQuote(value: string): boolean {
    return /[\s,():'"\\]/.test(value) || value === '';
  }

  /**
   * 格式化单个参数：需要时加单引号
   */
  function formatArg(value: any): string {
    if (value === undefined || value === null) return '';
    const str = String(value);
    if (needsQuote(str)) {
      return `'${str}'`;
    }
    return str;
  }

  /**
   * 根据表单生成 @type(args) 表达式
   */
  const previewExpression = computed(() => {
    if (form.type === 'fixed') {
      return `@fixed(${formatArg(form.fixedValue)})`;
    }
    if (form.type === 'template') {
      return form.templateId ? `@template(${form.templateId})` : '@template()';
    }

    const args: string[] = [];
    if (showLocale.value) {
      args.push(formatArg(form.locale));
    } else if (showCaseType.value) {
      args.push(formatArg(form.caseType));
      if (form.length !== undefined && form.length !== null) {
        args.push(formatArg(form.length));
      }
    } else if (showMinMax.value) {
      args.push(formatArg(form.min));
      args.push(formatArg(form.max));
      if (showScale.value) {
        args.push(formatArg(form.scale));
      }
    } else if (showLength.value) {
      args.push(formatArg(form.length));
    } else if (showFormat.value) {
      args.push(formatArg(form.format));
    } else if (showChoices.value) {
      args.push(formatArg(form.choices));
    }

    if (args.length === 0) {
      return `@${form.type}()`;
    }
    return `@${form.type}(${args.join(', ')})`;
  });

  const previewResult = ref('');
  const previewLoading = ref(false);
  let previewTimer: ReturnType<typeof setTimeout> | null = null;

  const refreshPreview = async () => {
    const expression = previewExpression.value;
    if (!expression) {
      previewResult.value = '';
      return;
    }
    previewLoading.value = true;
    try {
      const {data} = await generateMock(expression);
      previewResult.value = data ?? '';
    } catch (e) {
      previewResult.value = '';
    } finally {
      previewLoading.value = false;
    }
  };

  const debouncedRefreshPreview = () => {
    if (previewTimer) clearTimeout(previewTimer);
    previewTimer = setTimeout(() => {
      refreshPreview();
    }, 300);
  };

  // 表单变化时实时刷新预览（防抖 300ms）
  watch(previewExpression, debouncedRefreshPreview, {immediate: true});

  const loadTemplates = async () => {
    const projectId = projectStore.getProjectId;
    if (!projectId) return;
    templateLoading.value = true;
    try {
      const {data} = await getDataTemplateList(projectId);
      templateList.value = data || [];
    } catch (e) {
      Message.error('加载模板列表失败');
    } finally {
      templateLoading.value = false;
    }
  };

  const onTypeChange = () => {
    if (form.type === 'int' || form.type === 'long') {
      form.min = 0;
      form.max = 100;
    } else if (form.type === 'float' || form.type === 'double') {
      form.min = 0;
      form.max = 100;
      form.scale = 2;
    } else if (form.type === 'text' || form.type === 'character') {
      form.length = 10;
    } else if (form.type === 'date') {
      form.format = 'yyyy-MM-dd';
    } else if (form.type === 'time') {
      form.format = 'HH:mm:ss';
    } else if (form.type === 'datetime') {
      form.format = 'yyyy-MM-dd HH:mm:ss';
    } else if (form.type === 'template') {
      loadTemplates();
    }
  };

  const resetForm = () => {
    const firstType = mockTypes.value[0]?.value || 'name';
    form.type = firstType;
    form.locale = 'zh';
    form.caseType = 'lower';
    form.min = 0;
    form.max = 100;
    form.scale = 2;
    form.length = 10;
    form.format = 'yyyy-MM-dd HH:mm:ss';
    form.choices = 'A,B,C';
    form.fixedValue = '';
    form.templateId = undefined;
    // 如果有初始 mockConfig，按其值覆盖
    applyMockConfigToForm(form, mockConfig);
    if (form.type === 'template') {
      loadTemplates();
    }
  };

  watch(() => [parameterType, mockConfig], () => {
    resetForm();
  }, {immediate: true, deep: true});

  return {
    form,
    templateList,
    templateLoading,
    mockTypes,
    mockCategories,
    showLocale,
    showCaseType,
    showMinMax,
    showScale,
    showLength,
    showFormat,
    showChoices,
    showFixed,
    showTemplate,
    formatPresets,
    canInsert,
    currentMockConfig,
    previewExpression,
    previewResult,
    previewLoading,
    refreshPreview,
    onTypeChange,
    resetForm,
    loadTemplates,
  };
}
