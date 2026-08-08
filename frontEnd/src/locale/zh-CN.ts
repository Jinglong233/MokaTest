import localeMessageBox from '@/components/message-box/locale/zh-CN';
import localeLogin from '@/views/login/locale/zh-CN';





import localeProject from '@/views/project/locale/zh-CN';
import localeElement from '@/views/element/locale/zh-CN';
import localeScene from '@/views/scene/locale/zh-CN';
import localeReport from '@/views/report/locale/zh-CN';
import localePlan from '@/views/plan/locale/zh-CN';
import localeApi from '@/views/apiManager/locale/zh-CN';
import localeQa from '@/views/qa/locale/zh-CN';
import localeDataTemplate from '@/views/dataTemplate/locale/zh-CN';




import locale403 from '@/views/exception/403/locale/zh-CN';

import localeUserSetting from '@/views/user/setting/locale/zh-CN';

import localeSettings from './zh-CN/settings';

export default {
    'menu.dashboard': '仪表盘',
    'menu.server.dashboard': '仪表盘-服务端',
    'menu.server.workplace': '工作台-服务端',
    'menu.server.monitor': '实时监控-服务端',
    'menu.list': '列表页',
    'menu.result': '结果页',
    'menu.exception': '异常页',
    'menu.form': '表单页',
    'menu.profile': '详情页',
    'menu.visualization': '数据可视化',
    'menu.user': '个人中心',
    'menu.user.setting': '安全设置',
    'menu.user.message': '我的消息',
    'menu.system': '系统管理',
    'menu.system.operationLog': '操作日志',

    'menu.project': '项目概览',
    'menu.projectMember': '项目成员',
    'menu.projectConfig': '项目配置',
    'menu.knowledge': '知识库',

    'menu.interfaceTest': '接口测试',
    'menu.interfaceTest.apiList': 'API 测试',
    'menu.interfaceTest.sceneList': 'API 场景',
    'menu.interfaceTest.envConfig': '环境管理',
    'menu.interfaceTest.dataTemplate': '数据模板',
    'menu.interfaceTest.customFunction': '自定义函数',

    'menu.uiAutomation': 'UI 自动化',
    'menu.uiAutomation.sceneList': 'UI 场景',
    'menu.uiAutomation.elementList': '元素库',

    'menu.testRun': '测试运行',
    'menu.testRun.planList': '任务计划',
    'menu.testRun.planDetail': '任务详情',
    'menu.testRun.webhook': 'Webhook',
    'menu.testRun.reportList': '测试报告',
    'menu.testRun.reportDetail': '报告详情',
    'navbar.docs': '文档中心',
    'navbar.action.locale': '切换为中文',
    ...localeSettings,
    ...localeMessageBox,
    ...localeLogin,
    ...locale403,
    ...localeUserSetting,
    ...localeProject,
    ...localeElement,
    ...localeScene,
    ...localeReport,
    ...localePlan,
    ...localeApi,
    ...localeQa,
    ...localeDataTemplate
};
