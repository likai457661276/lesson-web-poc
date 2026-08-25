# Lesson Web PoC Frontend

React + Vite + TypeScript 单页应用。页面只消费 `LessonDocument v1`，不读取 MinerU 原始结果。

```bash
pnpm install --frozen-lockfile
pnpm dev
```

Node 与 pnpm 由 Volta 根据 `package.json` 自动切换；包源由 `.npmrc` 统一配置。开发服务器位于 `http://localhost:5173`，并代理 `/api` 到后端 8000 端口。

验证：

```bash
pnpm lint
pnpm build
```
