---
name: extract_content_with_image
description: 将本地 PDF、TXT、Word、PPT 文件按远端 extract_content_with_image 的逻辑尽量一致地转为 PDF（如需要），并输出带图片信息的分块结果。
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
- 脚本入口：`scripts/extract_content_with_image.py`
- 参数：`argv[1]` 为待处理文件的本地绝对路径
- 支持输入：`.pdf`、`.txt`、`.doc`、`.docx`、`.ppt`、`.pptx`

## 输出格式（stdout）
- 仅输出一个 JSON 对象，便于 Java 侧直接 `json.loads` / `parseJsonObject`
- 成功：`{"status":"success","filepath":"...pdf","data":[...]}`
- 失败：`{"status":"failed","msg":"<具体异常信息>"}`
- `data` 中每个元素形如：`{"text":"...", "image":"<图片列表的 JSON 字符串或空串>"}`

## 运行依赖
- 必需：`PyMuPDF`（`fitz`）和 `Pillow`
- 可选：`soffice`，用于 `.doc/.docx/.ppt/.pptx/.txt` 转 PDF；可用环境变量 `SOFFICE_PATH` 指定
- 无 `soffice` 时：
  - `.txt` 会直接用 `fitz` 生成 PDF，并基于原始文本做分块
  - `.doc/.docx/.ppt/.pptx` 会返回失败 JSON
- 可选：`transformers` + `TOKENIZER_DIR` 或 `MODEL_DIR`
  - 配置后按 tokenizer 的 token 数分块，和 VicunaIndex 更接近
  - 未配置时按字符数分块，默认 `CHUNK_SIZE=512`

## 行为说明
- 脚本会先把输入文件复制到 `SKILL_OUTPUT_DIR/extract_content_with_image/<run_id>/files/`
- 若发生格式转换，`filepath` 返回转换后的 PDF 路径
- 图片会裁剪到同一个运行目录下，并在 `image` 字段中以绝对路径返回
- `page_dir` 会在结束时清理，保留裁剪后的图片文件

