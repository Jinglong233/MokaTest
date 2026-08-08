/**
 * Monaco Editor CDN 按需加载器
 *
 * 通过 CDN（jsdelivr）加载 Monaco Editor，不打包进项目，避免增大产物体积。
 * 全局只加载一次，多个编辑器实例共享同一份 monaco 运行时。
 *
 * 使用 AMD loader (loader.js) 方式加载，并通过 data URI worker
 * 解决跨域 Web Worker 的 CORS 限制（标准 CDN 集成方案）。
 */

// Monaco 版本，可按需升级
const MONACO_VERSION = '0.52.2'
const CDN_BASE = `https://cdn.jsdelivr.net/npm/monaco-editor@${MONACO_VERSION}/min/vs`
// workerMain.js 内部以 vs/xxx 为模块路径，baseUrl 需指向 vs 的父目录
const CDN_ROOT = CDN_BASE.replace(/\/vs$/, '')

let monacoPromise: Promise<any> | null = null

export function loadMonaco(): Promise<any> {
  if (monacoPromise) return monacoPromise

  monacoPromise = new Promise((resolve, reject) => {
    const win = window as any

    // 已加载则直接返回
    if (win.monaco) {
      resolve(win.monaco)
      return
    }

    // 配置 Worker 环境：通过 data URI 包装 CDN worker，规避跨域限制
    win.MonacoEnvironment = {
      getWorkerUrl(_moduleId: string, _label: string) {
        const workerScript = `
          self.MonacoEnvironment = { baseUrl: '${CDN_ROOT}' };
          importScripts('${CDN_BASE}/base/worker/workerMain.js');
        `
        return `data:text/javascript;charset=utf-8,${encodeURIComponent(workerScript)}`
      },
    }

    const onLoaderReady = () => {
      const amdRequire = win.require
      if (!amdRequire) {
        reject(new Error('Monaco AMD loader 未就绪'))
        return
      }
      amdRequire.config({ paths: { vs: CDN_BASE } })
      amdRequire(['vs/editor/editor.main'], () => {
        resolve(win.monaco)
      }, (err: any) => {
        monacoPromise = null
        reject(err)
      })
    }

    // 避免重复插入 loader.js
    const existing = document.querySelector('script[data-monaco-loader]') as HTMLScriptElement | null
    if (existing) {
      if (win.require) onLoaderReady()
      else existing.addEventListener('load', onLoaderReady)
      return
    }

    const loaderScript = document.createElement('script')
    loaderScript.src = `${CDN_BASE}/loader.js`
    loaderScript.async = true
    loaderScript.setAttribute('data-monaco-loader', 'true')
    loaderScript.onload = onLoaderReady
    loaderScript.onerror = () => {
      monacoPromise = null
      reject(new Error('Monaco loader.js 加载失败，请检查网络'))
    }
    document.body.appendChild(loaderScript)
  })

  return monacoPromise
}
