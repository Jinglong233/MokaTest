# ============================================
# Mokatest 前端 - Docker 构建文件
# 多阶段构建：Node 构建 → Nginx 托管静态文件
# 使用 yarn 安装依赖，配置国内镜像
# ============================================

# ---- 阶段 1：构建 ----
# 使用 slim 而非 alpine，避免 gifsicle/vite-plugin-imagemin 等原生依赖在 alpine 下编译失败
FROM node:22-slim AS builder

WORKDIR /app

# 配置国内 npm/yarn 镜像，加速依赖下载
RUN npm config set registry https://registry.npmmirror.com && \
    yarn config set registry https://registry.npmmirror.com

# 先复制 package.json 和 yarn.lock
COPY package.json yarn.lock ./

# 安装依赖（使用 yarn 锁定版本）
RUN yarn install --frozen-lockfile

# 复制完整源码（包含 config/ 目录）
COPY . .

# 构建生产包
RUN yarn build

# ---- 阶段 2：运行 ----
FROM nginx:alpine

# 复制构建产物
COPY --from=builder /app/dist /usr/share/nginx/html

# 复制 Nginx 配置
COPY nginx.conf /etc/nginx/conf.d/default.conf

# 确保时区正确
RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD wget -qO- http://localhost/ || exit 1

CMD ["nginx", "-g", "daemon off;"]
