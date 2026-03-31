(function() {
    const tHtmlOpenRouter = window.tHtml || ((s) => s);

    function hideChatPartsForStandalonePage() {
        $("#queryBox").hide();
        $("#footer-info").hide();
        $("#introduces").hide();
        $("#topTitle").hide();
        $("#item-content").show();
        $("#item-content").css("height", "calc(100vh - 60px)");
        $("#item-content").css("overflow-y", "auto");
        if (typeof hideBallDiv === "function") {
            hideBallDiv();
        }
    }

    function openRouterThStyle() {
        return "padding:10px;border-bottom:1px solid #e5e7eb;text-align:left;color:#6b7280;font-weight:600;white-space:nowrap;";
    }

    function openRouterTdStyle() {
        return "padding:10px;border-bottom:1px solid #f3f4f6;white-space:nowrap;";
    }

    function ensureApiKeysState() {
        if (!window.__apiKeysPageState) {
            window.__apiKeysPageState = {
                rows: [
                    { id: "k_1", name: "测试密钥", keyMasked: "sk-rt-v1-630...92b", expires: "永不过期", lastUsed: "4 天前", usage: "< ¥0.001", limit: "不限额" }
                ]
            };
        }
    }

    function renderApiKeysRows(rows) {
        if (!rows || !rows.length) {
            return `<tr><td colspan="7" style="padding:24px;text-align:center;color:#9ca3af;">暂无密钥</td></tr>`;
        }
        return rows.map(function(row) {
            return `
                <tr>
                    <td style="${openRouterTdStyle()}">${row.name || "—"}</td>
                    <td style="${openRouterTdStyle()}">${row.keyMasked || "—"}</td>
                    <td style="${openRouterTdStyle()}">${row.expires || "—"}</td>
                    <td style="${openRouterTdStyle()}">${row.lastUsed || "—"}</td>
                    <td style="${openRouterTdStyle()}">${row.usage || "—"}</td>
                    <td style="${openRouterTdStyle()}">${row.limit || "—"}</td>
                    <td style="${openRouterTdStyle()}"><a href="javascript:void(0)" class="api-key-delete-link" data-id="${row.id}" style="color:#ef4444;">删除</a></td>
                </tr>
            `;
        }).join("");
    }

    function bindApiKeysEvents() {
        $("#apiKeysCreateBtn").on("click", function() {
            $("#apiKeysCreateMask").css("display", "flex");
        });
        $("#apiKeysCloseBtn").on("click", function() {
            $("#apiKeysCreateMask").hide();
        });
        $("#apiKeysCreateMask").on("click", function(e) {
            if (e.target && e.target.id === "apiKeysCreateMask") {
                $("#apiKeysCreateMask").hide();
            }
        });
        $("#apiKeyCreateConfirmBtn").on("click", function() {
            applyCreateApiKey();
        });
        $("#api-keys-container").on("click", ".api-key-delete-link", function() {
            const id = String($(this).data("id") || "");
            deleteApiKeyById(id);
        });
    }

    function applyCreateApiKey() {
        const st = window.__apiKeysPageState;
        const name = ($("#apiKeyNameInput").val() || "").trim() || "未命名密钥";
        const limit = ($("#apiKeyLimitInput").val() || "").trim() || "不限额";
        const expires = ($("#apiKeyExpireSelect").val() || "永不过期").trim();
        const randomTail = Math.random().toString(16).slice(2, 8);
        st.rows.unshift({
            id: "k_" + Date.now(),
            name: name,
            keyMasked: "sk-rt-v1-" + randomTail + "...",
            expires: expires,
            lastUsed: "刚创建",
            usage: "¥0.000",
            limit: limit
        });
        $("#apiKeysCreateMask").hide();
        window.loadApiKeysPage();
    }

    function deleteApiKeyById(id) {
        if (!id) return;
        const st = window.__apiKeysPageState;
        st.rows = (st.rows || []).filter(function(item) { return String(item.id) !== String(id); });
        window.loadApiKeysPage();
    }

    window.loadApiKeysPage = function loadApiKeysPage() {
        hideChatPartsForStandalonePage();
        ensureApiKeysState();
        const st = window.__apiKeysPageState;
        const html = `
            <div id="api-keys-container" style="padding:20px;min-height:100%;background:#fff;">
                <div style="display:flex;justify-content:space-between;align-items:center;gap:12px;flex-wrap:wrap;margin-bottom:12px;">
                    <div>
                        <h2 style="margin:0;font-size:28px;font-weight:700;">API 密钥</h2>
                        <div style="margin-top:6px;color:#6b7280;font-size:13px;">创建并管理你的 API 密钥</div>
                    </div>
                    <button type="button" id="apiKeysCreateBtn" style="padding:10px 18px;border:none;border-radius:8px;background:#6366f1;color:#fff;font-size:14px;cursor:pointer;">创建密钥</button>
                </div>
                <div style="color:#6b7280;font-size:13px;margin-bottom:12px;">管理你的密钥以访问全部模型</div>
                <div style="border:1px solid #e5e7eb;border-radius:10px;background:#fff;overflow:hidden;">
                    <div style="overflow-x:auto;">
                        <table style="width:100%;border-collapse:collapse;font-size:13px;">
                            <thead><tr style="background:#fff;"><th style="${openRouterThStyle()}">名称</th><th style="${openRouterThStyle()}">密钥</th><th style="${openRouterThStyle()}">过期时间</th><th style="${openRouterThStyle()}">最后使用</th><th style="${openRouterThStyle()}">用量</th><th style="${openRouterThStyle()}">额度上限</th><th style="${openRouterThStyle()}">操作</th></tr></thead>
                            <tbody id="apiKeysTbody">${renderApiKeysRows(st.rows)}</tbody>
                        </table>
                    </div>
                </div>
            </div>
            <div id="apiKeysCreateMask" style="display:none;position:fixed;left:0;top:0;right:0;bottom:0;background:rgba(0,0,0,0.32);z-index:1200;align-items:center;justify-content:center;">
                <div style="width:min(560px,92vw);background:#fff;border:1px solid #e5e7eb;border-radius:12px;padding:16px;box-shadow:0 10px 30px rgba(15,23,42,.18);">
                    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px;">
                        <div style="font-size:20px;font-weight:600;">创建 API 密钥</div>
                        <button type="button" id="apiKeysCloseBtn" style="border:none;background:transparent;font-size:20px;cursor:pointer;color:#6b7280;">×</button>
                    </div>
                    <div style="display:grid;gap:10px;">
                        <label style="font-size:13px;color:#374151;">名称<input id="apiKeyNameInput" type="text" placeholder="例如：聊天机器人密钥" style="margin-top:6px;width:100%;padding:8px;border:1px solid #d1d5db;border-radius:6px;" /></label>
                        <label style="font-size:13px;color:#374151;">额度上限（可选）<input id="apiKeyLimitInput" type="text" placeholder="留空表示不限额" style="margin-top:6px;width:100%;padding:8px;border:1px solid #d1d5db;border-radius:6px;" /></label>
                        <label style="font-size:13px;color:#374151;">过期设置<select id="apiKeyExpireSelect" style="margin-top:6px;width:100%;padding:8px;border:1px solid #d1d5db;border-radius:6px;"><option value="永不过期">永不过期</option><option value="30 天">30 天</option><option value="90 天">90 天</option></select></label>
                    </div>
                    <button type="button" id="apiKeyCreateConfirmBtn" style="margin-top:14px;width:100%;padding:10px;border:none;border-radius:8px;background:#6366f1;color:#fff;cursor:pointer;">创建</button>
                </div>
            </div>
        `;
        $("#item-content").html(tHtmlOpenRouter(html));
        bindApiKeysEvents();
    };
})();
