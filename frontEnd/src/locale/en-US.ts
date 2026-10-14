import localeMessageBox from '@/components/message-box/locale/en-US';
import localeComponents from '@/components/locale/en-US';
import localeLogin from '@/views/login/locale/en-US';




import localeScene from '@/views/scene/locale/en-US';
import localeReport from '@/views/report/locale/en-US';
import localePlan from '@/views/plan/locale/en-US';


import localeProject from '@/views/project/locale/en-US';
import localeElement from '@/views/element/locale/en-US';
import localeApi from '@/views/apiManager/locale/en-US';
import localeQa from '@/views/qa/locale/en-US';
import localeDataTemplate from '@/views/dataTemplate/locale/en-US';
import localeAiConfig from '@/views/aiConfig/locale/en-US';
import localeCustomFunction from '@/views/customFunction/locale/en-US';
import localeEnv from '@/views/env/locale/en-US';
import localeKnowledge from '@/views/knowledge/locale/en-US';
import localeProjectConfig from '@/views/projectConfig/locale/en-US';
import localeSystem from '@/views/system/locale/en-US';
import localeTeamManager from '@/views/teamManager/locale/en-US';

import locale403 from '@/views/exception/403/locale/en-US';

import localeUserSetting from '@/views/user/setting/locale/en-US';

import localeSettings from './en-US/settings';


export default {
    'menu.dashboard': 'Dashboard',
    'menu.server.dashboard': 'Dashboard-Server',
    'menu.server.workplace': 'Workplace-Server',
    'menu.server.monitor': 'Monitor-Server',
    'menu.list': 'List',
    'menu.result': 'Result',
    'menu.exception': 'Exception',
    'menu.form': 'Form',
    'menu.profile': 'Profile',
    'menu.visualization': 'Data Visualization',
    'menu.user': 'User Center',
    'menu.user.setting': 'Security',
    'menu.user.message': 'My Messages',
    'menu.system': 'System',
    'menu.system.operationLog': 'Operation Log',

    'menu.project': 'Project Overview',
    'menu.projectMember': 'Project Members',
    'menu.projectConfig': 'Project Config',
    'menu.ai': 'AI Center',
    'menu.aiConfig': 'AI Config',
    'menu.knowledge': 'Knowledge Base',

    'menu.interfaceTest': 'Interface Test',
    'menu.interfaceTest.apiList': 'API Test',
    'menu.interfaceTest.sceneList': 'API Scenes',
    'menu.interfaceTest.envConfig': 'Environment',
    'menu.interfaceTest.dataTemplate': 'Data Template',
    'menu.interfaceTest.customFunction': 'Custom Function',

    'menu.uiAutomation': 'UI Automation',
    'menu.uiAutomation.sceneList': 'UI Scenes',
    'menu.uiAutomation.elementList': 'Element Library',

    'menu.testRun': 'Test Run',
    'menu.testRun.planList': 'Task Plan',
    'menu.testRun.planDetail': 'Task Detail',
    'menu.testRun.webhook': 'Webhook',
    'menu.testRun.reportList': 'Test Report',
    'menu.testRun.reportDetail': 'Report Detail',
    'navbar.docs': 'Docs',
    'navbar.action.locale': 'Switch to English',
    ...localeSettings,
    ...localeMessageBox,
    ...localeComponents,
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
    ...localeDataTemplate,
    ...localeAiConfig,
    ...localeCustomFunction,
    ...localeEnv,
    ...localeKnowledge,
    ...localeProjectConfig,
    ...localeSystem,
    ...localeTeamManager
};
