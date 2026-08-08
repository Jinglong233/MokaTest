import {DEFAULT_LAYOUT} from '../base';
import {AppRouteRecordRaw} from '../types';

const REDIRECTS: AppRouteRecordRaw[] = [
    {path: '/api/apiList', redirect: '/interface-test/apiList', component: DEFAULT_LAYOUT, meta: {hideInMenu: true}},
    {path: '/element/elementList', redirect: '/ui-automation/elementList', component: DEFAULT_LAYOUT, meta: {hideInMenu: true}},
    {path: '/scene/sceneList', redirect: '/ui-automation/sceneList', component: DEFAULT_LAYOUT, meta: {hideInMenu: true}},
    {path: '/plan/planList', redirect: '/test-run/planList', component: DEFAULT_LAYOUT, meta: {hideInMenu: true}},
    {path: '/plan/planDetail/:planId', redirect: (to) => `/test-run/planDetail/${to.params.planId}`, component: DEFAULT_LAYOUT, meta: {hideInMenu: true}},
    {path: '/plan/webhook', redirect: '/test-run/webhook', component: DEFAULT_LAYOUT, meta: {hideInMenu: true}},
    {path: '/report/reportList', redirect: '/test-run/reportList', component: DEFAULT_LAYOUT, meta: {hideInMenu: true}},
    {path: '/report/reportDetail/:reportId', redirect: (to) => `/test-run/reportDetail/${to.params.reportId}`, component: DEFAULT_LAYOUT, meta: {hideInMenu: true}},
    {path: '/test-assets/apiList', redirect: '/interface-test/apiList', component: DEFAULT_LAYOUT, meta: {hideInMenu: true}},
    {path: '/test-assets/elementList', redirect: '/ui-automation/elementList', component: DEFAULT_LAYOUT, meta: {hideInMenu: true}},
    {path: '/test-assets/requirement', redirect: '/qa/requirement', component: DEFAULT_LAYOUT, meta: {hideInMenu: true}},
    {path: '/test-assets/bug', redirect: '/qa/bug', component: DEFAULT_LAYOUT, meta: {hideInMenu: true}},
    {path: '/test-assets/testCase', redirect: '/qa/testCase', component: DEFAULT_LAYOUT, meta: {hideInMenu: true}},
    {path: '/test-assets/testPlan', redirect: '/qa/testPlan', component: DEFAULT_LAYOUT, meta: {hideInMenu: true}},
    {path: '/test-assets/testPlan/detail/:planId', redirect: (to) => `/qa/testPlan/detail/${to.params.planId}`, component: DEFAULT_LAYOUT, meta: {hideInMenu: true}},
    {path: '/test-execution/sceneList', redirect: '/ui-automation/sceneList', component: DEFAULT_LAYOUT, meta: {hideInMenu: true}},
    {path: '/test-execution/planList', redirect: '/test-run/planList', component: DEFAULT_LAYOUT, meta: {hideInMenu: true}},
    {path: '/test-execution/planDetail/:planId', redirect: (to) => `/test-run/planDetail/${to.params.planId}`, component: DEFAULT_LAYOUT, meta: {hideInMenu: true}},
    {path: '/test-execution/webhook', redirect: '/test-run/webhook', component: DEFAULT_LAYOUT, meta: {hideInMenu: true}},
    {path: '/test-execution/reportList', redirect: '/test-run/reportList', component: DEFAULT_LAYOUT, meta: {hideInMenu: true}},
    {path: '/test-execution/reportDetail/:reportId', redirect: (to) => `/test-run/reportDetail/${to.params.reportId}`, component: DEFAULT_LAYOUT, meta: {hideInMenu: true}},
    {path: '/project/module', redirect: '/qa/testCase', component: DEFAULT_LAYOUT, meta: {hideInMenu: true}},
];

export default REDIRECTS;
