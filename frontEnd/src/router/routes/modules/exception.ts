import { DEFAULT_LAYOUT } from '../base';
import { AppRouteRecordRaw } from '../types';

const EXCEPTION: AppRouteRecordRaw = {
  path: '/exception',
  name: 'exception',
  component: DEFAULT_LAYOUT,
  meta: {
    locale: 'menu.exception',
    requiresAuth: false,
    icon: 'icon-exclamation-circle',
    order: 6,
    hideInMenu:true
  },
  children: [
    {
      path: '403',
      name: '403',
      component: () => import('@/views/exception/403/index.vue'),
      meta: {
        locale: 'menu.exception.403',
        requiresAuth: false,
        roles: ['*'],
        hideInMenu:true
      },
    },
  ],
};

export default EXCEPTION;
