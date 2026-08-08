import {mergeConfig} from 'vite';
import eslint from 'vite-plugin-eslint';
import baseConfig from './vite.config.base';

export default mergeConfig(
    {
        //关闭eslint
        lintOnSave:false,
        mode: 'development',
        server: {
            open: true,
            fs: {
                strict: true
            },
            proxy: {
                '/api': {
                    target: 'http://localhost:7529',
                    changeOrigin: true,
                    // rewrite: (path) => path.replace(/^\/api/, '/api'),
                },
            },
        },
        plugins: [
            eslint({
                cache: false,
                include: ['src/**/*.ts', 'src/**/*.tsx', 'src/**/*.vue'],
                exclude: ['node_modules'],
            }),
        ],
    },
    baseConfig
);
