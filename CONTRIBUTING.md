# Contributing

感谢参与改进 YML。

## 开发环境

- Windows 10/11 x64
- JDK 26
- PowerShell 5.1+ 或 PowerShell 7

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-26"
.\build.ps1
.\run.ps1
```

## 提交前

1. 确认 `.\build.ps1` 通过
2. 不要提交 `build/`、`dist/`、日志、`hs_err_pid*` 或个人数据
3. 不要提交管理员密码、API Key、访问令牌
4. UI 改动请附截图
5. Java 代码使用 4 空格缩进，保持现有命名和结构

## Pull Request

- 一个 PR 聚焦一个功能或修复
- 说明修改内容、测试方式和影响的 Minecraft / 加载器版本
- 涉及 UI 时附前后截图
- 涉及下载源或外部服务时说明来源和失败回退

## Issue

使用模板提交，附上：

- 启动器版本
- Minecraft / 加载器版本
- 下载源
- 复现步骤
- 脱敏后的日志或诊断报告
