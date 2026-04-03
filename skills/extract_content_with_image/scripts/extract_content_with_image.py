import copy
import json
import os
import random
import string
import subprocess
import sys
import time
from typing import Any, Dict, List, Optional, Tuple


CONVERT_TO_PDF_EXTS = {".txt", ".doc", ".docx", ".ppt", ".pptx"}


def _configure_stdio() -> None:
    # SkillContainer reads stdout/stderr as UTF-8, so force Python's streams to match.
    for stream_name in ("stdout", "stderr"):
        stream = getattr(sys, stream_name, None)
        if stream is None or not hasattr(stream, "reconfigure"):
            continue
        try:
            stream.reconfigure(encoding="utf-8")
        except Exception:
            pass


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
    direct_hit = _which("soffice") or _which("soffice.exe")
    if direct_hit:
        return direct_hit

    candidates: List[str] = []
    for base in (os.environ.get("ProgramFiles"), os.environ.get("ProgramFiles(x86)")):
        if not base:
            continue
        candidates.extend([
            os.path.join(base, "LibreOffice", "program", "soffice.exe"),
            os.path.join(base, "OpenOffice 4", "program", "soffice.exe"),
        ])

    for candidate in candidates:
        if os.path.exists(candidate):
            return candidate
    return None


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


def _read_text_file(src_path: str) -> str:
    with open(src_path, "rb") as f:
        raw = f.read()

    for encoding in ("utf-8-sig", "utf-8", "gb18030", "gbk", "utf-16", "latin-1"):
        try:
            return raw.decode(encoding)
        except UnicodeDecodeError:
            continue
    return raw.decode("utf-8", errors="replace")


def _convert_txt_to_pdf(src_path: str, pdf_path: str) -> str:
    try:
        import fitz as fitz_mod
    except Exception as e:
        raise RuntimeError("PyMuPDF (fitz) is required to convert .txt to pdf") from e

    text = _read_text_file(src_path)
    doc = fitz_mod.open()
    page = doc.new_page()
    margin_x = 50
    margin_y = 50
    font_size = 11
    page_width = page.rect.width
    page_height = page.rect.height
    usable_width = max(page_width - margin_x * 2, 50)
    usable_height = max(page_height - margin_y * 2, 50)
    cursor_y = margin_y
    line_height = font_size * 1.5

    for raw_line in text.splitlines() or [""]:
        line = raw_line.rstrip()
        words = [line] if not line else line.split()
        current = ""

        if not words:
            if cursor_y + line_height > margin_y + usable_height:
                page = doc.new_page()
                cursor_y = margin_y
            page.insert_text((margin_x, cursor_y), "", fontsize=font_size)
            cursor_y += line_height
            continue

        for word in words:
            candidate = word if not current else current + " " + word
            candidate_width = fitz_mod.get_text_length(candidate, fontsize=font_size)
            if current and candidate_width > usable_width:
                if cursor_y + line_height > margin_y + usable_height:
                    page = doc.new_page()
                    cursor_y = margin_y
                page.insert_text((margin_x, cursor_y), current, fontsize=font_size)
                cursor_y += line_height
                current = word
            else:
                current = candidate

        if cursor_y + line_height > margin_y + usable_height:
            page = doc.new_page()
            cursor_y = margin_y
        page.insert_text((margin_x, cursor_y), current, fontsize=font_size)
        cursor_y += line_height

    doc.save(pdf_path)
    doc.close()
    return pdf_path


def _ensure_pdf(filepath: str, extension: str, files_dir: str, soffice: Optional[str] = None) -> str:
    if extension not in CONVERT_TO_PDF_EXTS:
        return filepath

    if soffice is None:
        soffice = _resolve_soffice()
    if soffice:
        return _convert_office_to_pdf(soffice, filepath, files_dir)

    if extension == ".txt":
        pdf_path = os.path.splitext(filepath)[0] + ".pdf"
        return _convert_txt_to_pdf(filepath, pdf_path)

    raise RuntimeError("soffice not found; cannot convert office document to pdf")


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


def _mk_page_dir(work_root: str, pdf_path: str) -> Tuple[str, str]:
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


def _get_text_only_chunks(text: str, chunk_size: int) -> List[Dict[str, Any]]:
    tokenizer = _load_tokenizer_optional()
    chunks: List[Dict[str, Any]] = []
    current_chunk: Dict[str, Any] = {"text": "", "image": []}

    for piece in [line.strip() for line in text.splitlines() if line.strip()]:
        temp_text = current_chunk["text"] + piece
        if _text_exceeds_chunk_budget(tokenizer, temp_text, chunk_size):
            if current_chunk["text"]:
                chunks.append(copy.deepcopy(current_chunk))
            current_chunk = {"text": piece, "image": []}
        else:
            current_chunk["text"] = temp_text

    if current_chunk["text"]:
        chunks.append(current_chunk)

    _image_list_to_str(chunks)
    return chunks


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
                    abs_path = os.path.abspath(image_path).replace("\\", "/")
                    image_info = {"path": abs_path, "tag": classification, "caption": caption}

                    if current_chunk["text"]:
                        current_chunk["image"].append(image_info)
                        chunks.append(copy.deepcopy(current_chunk))
                        current_chunk = {"text": "", "image": []}
                    elif chunks:
                        chunks[-1]["image"].append(image_info)
                    else:
                        current_chunk["image"].append(image_info)
                        chunks.append(copy.deepcopy(current_chunk))
                        current_chunk = {"text": "", "image": []}

            elif block_type == 0:
                if x0 < 0 or y0 < 0 or x1 > page_width or y1 > page_height:
                    continue
                if str(content).replace("-", "").strip().isnumeric():
                    continue

                temp_text = current_chunk["text"] + str(content).strip()
                if _text_exceeds_chunk_budget(tokenizer, temp_text, chunk_size):
                    if current_chunk["text"]:
                        chunks.append(copy.deepcopy(current_chunk))
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
        _configure_stdio()
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
        os.makedirs(files_dir, exist_ok=True)

        local_input_path = os.path.join(files_dir, run_id + extension)
        with open(src_path, "rb") as f_in, open(local_input_path, "wb") as f_out:
            f_out.write(f_in.read())

        soffice = _resolve_soffice() if extension in CONVERT_TO_PDF_EXTS else None
        filepath = _ensure_pdf(local_input_path, extension, files_dir, soffice)

        chunk_size = int(os.environ.get("CHUNK_SIZE", "512"))
        # Core logic equivalent to VicunaIndex PdfUtil.get_chunks1
        # Use work_dir as FILE_DIR equivalent for relative image paths.
        if extension == ".txt" and not soffice:
            result = _get_text_only_chunks(_read_text_file(local_input_path), chunk_size)
        else:
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

