# Lesson Web PoC Frontend

React + Vite + TypeScript 单页应用。页面只消费 `LessonDocument v1`，不读取 MinerU 原始结果；默认连接 Java 后端 `10012`，通过 `frontend/.env.local` 中的 `VITE_API_PROXY_TARGET` 配置代理。服务端文档库仅由 Java 提供，Python 后端不提供文档库接口。

```bash
pnpm install --frozen-lockfile
pnpm dev
```

Node 与 pnpm 由 Volta 根据 `package.json` 自动切换；包源由 `.npmrc` 统一配置。开发服务器位于 `http://localhost:5173`，并代理 `/api` 到 `VITE_API_PROXY_TARGET` 指定的后端（默认 `http://127.0.0.1:10012`）。上传组件仅支持 PDF，单文件上限 200 MB（由后端执行最终校验）。

上传开始即锁定提交和文件选择；状态查询失败会退避重试，连续 3 次失败后暂停，可手动恢复同一任务的查询。上传 POST 不自动重试，避免重复创建任务。

编辑通过 ETag / If-Match 防止多标签页覆盖。保存失败时保留内存草稿，可重试；冲突时需要明确选择重新加载服务端版本。未保存更改会拦截页面跳转及刷新/关闭。单篇和批量导出均使用保存成功后的服务端文档。查看模式不提供公式编辑入口。

验证：

```bash
pnpm lint
pnpm test
pnpm build
```
