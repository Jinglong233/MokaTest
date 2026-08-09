export const WHITE_LIST = [
  { name: 'notFound', children: [] },
  { name: 'login', children: [] },
];

export const NOT_FOUND = {
  name: 'notFound',
};

export const NO_PERMISSION = {
  name: '403',
};

export const REDIRECT_ROUTE_NAME = 'Redirect';

// 页签栏固定置顶的默认页签：项目级页面统一落在 DEFAULT_LAYOUT 内，
// 因此默认页签指向「项目概览」（/team 工作区走的是独立布局，不在页签体系内）
export const DEFAULT_ROUTE_NAME = 'ProjectInfo';

export const DEFAULT_ROUTE = {
  title: 'menu.project',
  name: DEFAULT_ROUTE_NAME,
  fullPath: '/project/projectInfo',
};
