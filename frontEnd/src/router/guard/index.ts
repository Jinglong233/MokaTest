import type {Router} from 'vue-router';
import {setRouteEmitter} from '@/utils/route-listener';
import setupUserLoginInfoGuard from './userLoginInfo';
import setupPermissionGuard from './permission';
import setupProjectGuard from './project';
import setupTeamGuard from './team';

function setupPageGuard(router: Router) {
    router.beforeEach(async (to, from) => {
        // emit route change
        setRouteEmitter(to);
        // console.log(`[Router] ${String(from.name || '-')} -> ${String(to.name || '-')} (${to.path})`);
    });
}

export default function createRouteGuard(router: Router) {
    setupPageGuard(router);
    setupUserLoginInfoGuard(router);
    setupTeamGuard(router);
    setupProjectGuard(router);
    setupPermissionGuard(router);
}
