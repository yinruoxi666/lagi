import json
import os
import random
import string
import sys
import time
import subprocess
from typing import List, Dict, Any, Optional


OFFICE_EXTS = {".doc", ".docx", ".ppt", ".pptx"}


def _skill_work_dir() -> str:
    base = os.environ.get("SKILL_OUTPUT_DIR") or os.environ.get("TMPDIR") or os.environ.get("TEMP") or "."
    base = os.path.abspath(base)
    os.makedirs(base, exist_ok=True)
    return base


def _rand_id() -> str:
    letters = string.ascii_lowercase
    return "".join(random.choice(letters) for _ in range(8)) + time.strftime("%y%m%d%H%M%S")


def _which(cmd: str) -> Optional[str]:
    from shutil import which
    return which(cmd)


def _resolve_soffice() -> Optional[str]:
    env_path = os.environ.get("SOFFICE_PATH")
    if env_path and os.path.exists(env_path):
        return env_path
    return _which("soffice") or _which("soffice.exe")


def _convert_office_to_pdf(soffice: str, src_path: str, out_dir: str) -> str:
    # LibreOffice: soffice --headless --convert-to pdf --outdir <out_dir> <src_path>
    subprocess.check_call([soffice, "--headless", "--convert-to", "pdf", "--outdir", out_dir, src_path])
    base = os.path.splitext(os.path.basename(src_path))[0]
    pdf_path = os.path.join(out_dir, base + ".pdf")
    if not os.path.exists(pdf_path):
        # Some conversions may change casing/spacing; best-effort scan.
        for fn in os.listdir(out_dir):
            if fn.lower().endswith(".pdf"):
                cand = os.path.join(out_dir, fn)
                if os.path.getmtime(cand) >= os.path.getmtime(src_path):
                    return cand
        raise FileNotFoundError("Converted pdf not found in out_dir")
    return pdf_path


def _split_text_chunks_by_len(text: str, max_len: int) -> List[str]:
    if not text:
        return []
    text = " ".join(text.split())
    if max_len <= 0:
        return [text]
    res = []
    i = 0
    while i < len(text):
        res.append(text[i:i + max_len])
        i += max_len
    return res


def _load_tokenizer_optional():
    """
    When TOKENIZER_DIR / MODEL_DIR is set, use the same tokenizer as VicunaIndex PdfUtil.get_chunks1().
    Otherwise return None and chunk by UTF-8 character length (chunk_size = max chars per chunk).
    """
    tokenizer_dir = os.environ.get("TOKENIZER_DIR") or os.environ.get("MODEL_DIR")
    if not tokenizer_dir:
        return None
    try:
        from transformers import AutoTokenizer
    except Exception as e:
        raise RuntimeError("transformers is required when TOKENIZER_DIR/MODEL_DIR is set") from e
    return AutoTokenizer.from_pretrained(tokenizer_dir)


def _text_exceeds_chunk_budget(tokenizer, text: str, chunk_size: int) -> bool:
    if tokenizer is not None:
        return len(tokenizer.encode(text)) > chunk_size
    return len(text) > chunk_size


def _mk_page_dir(work_root: str, pdf_path: str) -> (str, str):
    """
    Mirror VicunaIndex PdfUtil._mk_page_dir:
      page_dir = FILE_DIR + '/' + filename + '/pages'
      image_dir = FILE_DIR + '/' + filename + '/images'
    Here we treat work_root as FILE_DIR.
    """
    filename = os.path.splitext(os.path.basename(pdf_path))[0]
    page_dir = os.path.join(work_root, filename, "pages")
    image_dir = os.path.join(work_root, filename, "images")
    os.makedirs(page_dir, exist_ok=True)
    os.makedirs(image_dir, exist_ok=True)
    return page_dir, image_dir


def _get_page_image(fitz_mod, page, page_dir: str, i: int, zoom_size: int):
    """
    Mirror VicunaIndex PdfUtil.get_page_image:
      mat = fitz.Matrix(zoom_x, zoom_y)
      pix = page.get_pixmap(matrix=mat)
      pix.pil_save(page_image_path)
      page_image = Image.open(page_image_path)
    """
    from PIL import Image
    mat = fitz_mod.Matrix(zoom_size, zoom_size)
    pix = page.get_pixmap(matrix=mat)
    page_image_path = os.path.join(page_dir, str(i + 1) + ".png")
    try:
        pix.pil_save(page_image_path)
    except Exception:
        pix.save(page_image_path)
    page_image = Image.open(page_image_path)
    return page_image


def _image_list_to_str(chunks: List[Dict[str, Any]]):
    # Mirror VicunaIndex PdfUtil._image_list_to_str
    for chunk in chunks:
        if len(chunk.get("image", [])) == 0:
            image_str = ""
        else:
            image_str = json.dumps(chunk["image"], ensure_ascii=False)
        chunk["image"] = image_str


def _get_chunks1_equivalent(pdf_path: str, chunk_size: int, work_root: str) -> List[Dict[str, Any]]:
    """
    Equivalent implementation of VicunaIndex PdfUtil.get_chunks1 (core logic).
    Differences vs remote:
      - work_root plays the role of Config.FILE_DIR
      - tokenizer dir is optional via env TOKENIZER_DIR / MODEL_DIR (else char-based chunking)
    """
    try:
        import fitz as fitz_mod
    except Exception as e:
        raise RuntimeError("PyMuPDF (fitz) is required") from e

    tokenizer = _load_tokenizer_optional()
    zoom_size = 2

    doc = fitz_mod.open(pdf_path)
    page_dir, image_dir = _mk_page_dir(work_root, pdf_path)

    chunks: List[Dict[str, Any]] = []
    current_chunk: Dict[str, Any] = {"text": "", "image": []}

    for i in range(len(doc)):
        page = doc.load_page(i)
        blocks = page.get_text("blocks", flags=fitz_mod.TEXTFLAGS_DICT)
        page_image = _get_page_image(fitz_mod, page, page_dir, i, zoom_size)
        page_width, page_height = page_image.size

        for block in blocks:
            x0, y0, x1, y1, content, block_no, block_type = block
            x0, y0, x1, y1 = zoom_size * x0, zoom_size * y0, zoom_size * x1, zoom_size * y1

            if block_type == 1:
                image_path = os.path.join(image_dir, f"{i + 1}_{block_no}.png")
                cropped_image = page_image.crop((x0, y0, x1, y1))

                if cropped_image.width > 0 and cropped_image.height > 0:
                    cropped_image.save(image_path)
                    classification, caption = "tag", "caption"
                    rel_path = os.path.relpath(image_path, work_root).replace("\\", "/")
                    image_info = {"path": rel_path, "tag": classification, "caption": caption}

                    if current_chunk["text"]:
                        current_chunk["image"].append(image_info)
                        chunks.append(json.loads(json.dumps(current_chunk, ensure_ascii=False)))
                        current_chunk = {"text": "", "image": []}
                    elif chunks:
                        chunks[-1]["image"].append(image_info)
                    else:
                        current_chunk["image"].append(image_info)
                        chunks.append(json.loads(json.dumps(current_chunk, ensure_ascii=False)))
                        current_chunk = {"text": "", "image": []}

            elif block_type == 0:
                if x0 < 0 or y0 < 0 or x1 > page_width or y1 > page_height:
                    continue
                if str(content).replace("-", "").strip().isnumeric():
                    continue

                temp_text = current_chunk["text"] + str(content).strip()
                if _text_exceeds_chunk_budget(tokenizer, temp_text, chunk_size):
                    if current_chunk["text"]:
                        chunks.append(json.loads(json.dumps(current_chunk, ensure_ascii=False)))
                    current_chunk = {"text": str(content).strip(), "image": []}
                else:
                    current_chunk["text"] = temp_text

        page_image.close()

    doc.close()

    if current_chunk["text"] or current_chunk["image"]:
        chunks.append(current_chunk)

    _image_list_to_str(chunks)

    # Mirror VicunaIndex cleanup: remove page_dir (keep images)
    try:
        import shutil
        if os.path.exists(page_dir):
            shutil.rmtree(page_dir)
    except Exception:
        pass

    return chunks


def main():
    try:
        if len(sys.argv) < 2:
            raise ValueError("file_path must be provided as argv[1]")

        src_path = sys.argv[1]
        src_path = os.path.abspath(src_path)

        if not os.path.exists(src_path):
            raise FileNotFoundError(src_path)

        extension = os.path.splitext(src_path)[1].lower()
        run_id = _rand_id()
        work_dir = os.path.join(_skill_work_dir(), "extract_content_with_image", run_id)
        files_dir = os.path.join(work_dir, "files")
        images_dir = os.path.join(work_dir, "images")
        os.makedirs(files_dir, exist_ok=True)
        os.makedirs(images_dir, exist_ok=True)

        local_input_path = os.path.join(files_dir, "input" + extension)
        with open(src_path, "rb") as f_in, open(local_input_path, "wb") as f_out:
            f_out.write(f_in.read())

        filepath = local_input_path
        if extension in OFFICE_EXTS:
            soffice = _resolve_soffice()
            if not soffice:
                raise RuntimeError("soffice not found; cannot convert office document to pdf")
            filepath = _convert_office_to_pdf(soffice, local_input_path, files_dir)

        chunk_size = int(os.environ.get("CHUNK_SIZE", "512"))
        # Core logic equivalent to VicunaIndex PdfUtil.get_chunks1
        # Use work_dir as FILE_DIR equivalent for relative image paths.
        result = _get_chunks1_equivalent(filepath, chunk_size, work_dir)
        print(
            json.dumps({
                "status": "success",
                "filepath": filepath,
                "data": result
            },
                       ensure_ascii=False))
    except Exception as e:
        print(
            json.dumps({
                "status": "failed",
                "msg": str(e),
            },
                       ensure_ascii=False))


if __name__ == "__main__":
    main()

