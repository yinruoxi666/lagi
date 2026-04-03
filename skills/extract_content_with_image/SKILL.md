---
name: extract_content_with_image
description: 将本地文件（含图片的 office 文档）转换为 PDF（如需要），并提取按块的文本与图片信息（尽量与远程 extract_content_with_image 行为保持一致）。该实现不依赖 VicunaIndex 包可见。
version: "1.0.0"
author: "lagi"
tags:
  - extract
  - pdf
  - chunks
  - images
---

# extract_content_with_image

## 使用方式
- 参数（argv[1]）：`file_path`，指向磁盘上的待处理文件路径（如 `.docx/.ppt/.pptx` 等）

## 输出格式（stdout）
- JSON 字符串：
  - 成功：`{"status":"success","filepath":"...pdf","data":[...]}`
  - 失败：`{"status":"failed","msg":"<具体异常信息>"}`

## 运行依赖
- 可选：`soffice`（用于 `.doc/.docx/.ppt/.pptx` 转 pdf，可用环境变量 `SOFFICE_PATH` 指定）
- 必需：PyMuPDF（`fitz`）用于解析 pdf 并按 block 裁图
- 可选：`transformers` + 本地 tokenizer 目录（`TOKENIZER_DIR` 或 `MODEL_DIR`）：与 VicunaIndex 一致按 **token** 分块；未配置时按 **字符数** 分块（`CHUNK_SIZE` 表示最大字符数）

