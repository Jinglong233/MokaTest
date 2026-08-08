import {ServeParameter} from './ServeParameter';
import {RequestParameter} from './RequestParameter';

/**
 * 请求执行环境
 */
export class RequestExecuteInfo {
  // 待定
  database?: String;
  // 环境id
  envId?: number;
  // 环境名称
  envName?: string;
  // 基础url
  baseUrl?: string;
  // 服务器信息
  serve?: ServeParameter;
  // 环境级Cookie列表，自动附加到该环境的每个请求
  envCookies?: RequestParameter[];
  // 环境级Header列表，自动附加到该环境的每个请求
  envHeaders?: RequestParameter[];

  constructor() {
    this.serve = new ServeParameter();
    this.database = '';
    this.envId = 0;
    this.envName = '';
    this.baseUrl = '';
  }
}
