# Lesson Web PoC Frontend

React + Vite + TypeScript 单页应用。页面只消费 `LessonDocument v1`，不读取 MinerU 原始结果；默认连接 Python 后端 `10011`，可通过 `VITE_API_PROXY_TARGET=http://localhost:10012` 切换至 Java 后端。

```bash
pnpm install --frozen-lockfile
pnpm dev
```

Node 与 pnpm 由 Volta 根据 `package.json` 自动切换；包源由 `.npmrc` 统一配置。开发服务器位于 `http://localhost:5173`，并代理 `/api` 到 `VITE_API_PROXY_TARGET` 指定的后端（默认 `http://localhost:10011`）。上传组件支持 PDF、PPT/PPTX、XLS/XLSX 与常见图片，单文件上限 200 MB（由后端执行最终校验）。

验证：

```bash
pnpm lint
pnpm build
```
