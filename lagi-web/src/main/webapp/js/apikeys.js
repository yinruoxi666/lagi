(function() {
    const tTextOpenRouter = window.tText || ((s) => s);
    const tHtmlOpenRouter = window.tHtml || ((s) => s);
    const OPENAI_COMPATIBLE = "OpenAICompatible";
    const LANDING = "Landing";

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
                rows: [],
                loading: false,
                types: []
            };
        }
    }

    function maskApiKey(v) {
        var s = String(v || "");
        if (!s) return "—";
        if (s.length <= 8) return s;
        return s.slice(0, 4) + "..." + s.slice(-4);
    }

    function renderApiKeysRows(rows) {
        if (!rows || !rows.length) {
            return `<tr><td colspan="5" style="padding:24px;text-align:center;color:#9ca3af;">暂无数据</td></tr>`;
        }
        return rows.map(function(row) {
            return `
                <tr>
                    <td style="${openRouterTdStyle()}">${row.name || "—"}</td>
                    <td style="${openRouterTdStyle()}">${row.provider || "—"}</td>
                    <td style="${openRouterTdStyle()}">${maskApiKey(row.api_key)}</td>
                    <td style="${openRouterTdStyle()}">${row.api_address || "—"}</td>
                    <td style="${openRouterTdStyle()}">
                        ${row.provider === LANDING ? "" : `<a href="javascript:void(0)" class="api-key-edit-link" data-name="${row.name}" style="margin-right:12px;color:#6366f1;">编辑</a>`}
                        <a href="javascript:void(0)" class="api-key-delete-link" data-name="${row.name}" style="color:#ef4444;">删除</a>
                    </td>
                </tr>
            `;
        }).join("");
    }

    function setApiKeysLoading(loading) {
        const st = window.__apiKeysPageState;
        st.loading = !!loading;
        if (loading) {
            $("#apiKeysTbody").html('<tr><td colspan="5" style="padding:24px;text-align:center;color:#9ca3af;">加载中...</td></tr>');
        }
    }

    function renderProviderOptions(providers) {
        return (providers || []).map(function(provider) {
            return '<option value="' + provider + '">' + provider + "</option>";
        }).join("");
    }

    function loadProviders(done) {
        $.ajax({
            type: "GET",
            contentType: "application/json;charset=utf-8",
            url: "/apiKey/providers",
            success: function(res) {
                const st = window.__apiKeysPageState;
                if (res && res.status === "success" && Array.isArray(res.data)) {
                    st.types = res.data;
                } else {
                    st.types = [];
                }
                if (typeof done === "function") {
                    done(st.types);
                }
            },
            error: function() {
                const st = window.__apiKeysPageState;
                st.types = [];
                if (typeof done === "function") {
                    done(st.types);
                }
            }
        });
    }

    function toggleAddDialogFields() {
        const provider = ($("#apiKeyAddProviderSelect").val() || "").trim();
        const isOpenAICompatible = provider === OPENAI_COMPATIBLE;
        const isLanding = provider === LANDING;
        $("#apiKeyAddOpenAIFields").toggle(isOpenAICompatible);
        $("#apiKeyAddApiAddressWrap").toggle(isOpenAICompatible);
        $("#apiKeyAddStandardKeyBlock").toggle(!isLanding);
        $("#apiKeyLandingHint").toggle(isLanding);
    }

    function resetAddDialog() {
        $("#apiKeyAddProviderSelect").val("");
        $("#apiKeyAddApiKeyInput").val("");
        $("#apiKeyAddNameInput").val("");
        $("#apiKeyAddModelInput").val("");
        $("#apiKeyAddApiAddressInput").val("");
        toggleAddDialogFields();
    }

    function loadApiKeys() {
        ensureApiKeysState();
        setApiKeysLoading(true);
        $.ajax({
            type: "GET",
            contentType: "application/json;charset=utf-8",
            url: "/apiKey/list",
            success: function(res) {
                const st = window.__apiKeysPageState;
                st.loading = false;
                if (!res || res.status !== "success" || !Array.isArray(res.data)) {
                    st.rows = [];
                } else {
                    st.rows = res.data.map(function(item) {
                        return {
                            name: item.name || "",
                            provider: item.provider || "",
                            api_address: item.api_address || "",
                            api_key: item.api_key || ""
                        };
                    });
                }
                $("#apiKeysTbody").html(renderApiKeysRows(st.rows));
            },
            error: function() {
                const st = window.__apiKeysPageState;
                st.loading = false;
                st.rows = [];
                $("#apiKeysTbody").html('<tr><td colspan="5" style="padding:24px;text-align:center;color:#9ca3af;">加载失败</td></tr>');
            }
        });
    }

    function bindApiKeysEvents() {
        $("#apiKeysCreateBtn").on("click", function() {
            resetAddDialog();
            $("#apiKeyEditModelName").val("");
            $("#apiKeyEditInput").val("");
            $("#apiKeyEditArea").hide();
            $("#apiKeyAddArea").show();
            $("#apiKeyCreateConfirmBtn").show();
            $("#apiKeyEditConfirmBtn").hide();
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
        $("#apiKeyAddProviderSelect").on("change", function() {
            toggleAddDialogFields();
        });
        $("#apiKeyCreateConfirmBtn").on("click", function() {
            addApiKey();
        });
        $("#apiKeyEditConfirmBtn").on("click", function() {
            saveApiKey();
        });
        $("#api-keys-container").on("click", ".api-key-delete-link", function() {
            const name = String($(this).data("name") || "");
            deleteApiKeyByName(name);
        });
        $("#api-keys-container").on("click", ".api-key-edit-link", function() {
            const name = String($(this).data("name") || "");
            openEditDialogByName(name);
        });
    }

    function openEditDialogByName(name) {
        const st = window.__apiKeysPageState;
        const row = (st.rows || []).find(function(item) { return item.name === name; });
        if (!row) return;
        $("#apiKeyAddArea").hide();
        $("#apiKeyEditArea").show();
        $("#apiKeyCreateConfirmBtn").hide();
        $("#apiKeyEditConfirmBtn").show();
        $("#apiKeyEditModelName").val(row.name || "");
        $("#apiKeyEditInput").val(row.api_key || "");
        $("#apiKeysCreateMask").css("display", "flex");
    }

    function getApiKeysUserId() {
        var fromCookie = (typeof getCookie === "function" ? getCookie("userId") : "") || "";
        if (fromCookie) return fromCookie;
        try {
            return localStorage.getItem("userId") || "";
        } catch (e) {
            return "";
        }
    }

    function addApiKey() {
        const provider = ($("#apiKeyAddProviderSelect").val() || "").trim();
        const apiKey = ($("#apiKeyAddApiKeyInput").val() || "").trim();
        if (!provider) {
            alert(tTextOpenRouter("请选择 provider"));
            return;
        }
        if (provider !== LANDING && !apiKey) {
            alert(tTextOpenRouter("请输入 API Key"));
            return;
        }
        const payload = {
            provider: provider,
            apiKey: apiKey
        };
        if (provider === LANDING) {
            const userId = getApiKeysUserId();
            if (!userId) {
                alert(tTextOpenRouter("Landing 配置需要登录后获取用户 ID，请先登录"));
                return;
            }
            payload.userId = userId;
        }
        if (provider === OPENAI_COMPATIBLE) {
            const name = ($("#apiKeyAddNameInput").val() || "").trim();
            const model = ($("#apiKeyAddModelInput").val() || "").trim();
            const apiAddress = ($("#apiKeyAddApiAddressInput").val() || "").trim();
            if (!name) {
                alert(tTextOpenRouter("OpenAICompatible 类型需要 name"));
                return;
            }
            if (!model) {
                alert(tTextOpenRouter("OpenAICompatible 类型需要 model"));
                return;
            }
            payload.name = name;
            payload.model = model;
            payload.apiAddress = apiAddress;
        }
        $.ajax({
            type: "POST",
            url: "/apiKey/add",
            contentType: "application/json;charset=utf-8",
            data: JSON.stringify(payload),
            success: function(res) {
                if (res && res.status === "success") {
                    $("#apiKeysCreateMask").hide();
                    loadApiKeys();
                } else {
                    alert((res && res.msg) || tTextOpenRouter("新增失败"));
                }
            },
            error: function() {
                alert(tTextOpenRouter("新增失败"));
            }
        });
    }

    function saveApiKey() {
        const modelName = ($("#apiKeyEditModelName").val() || "").trim();
        const apiKey = ($("#apiKeyEditInput").val() || "").trim();
        if (!modelName) {
            alert(tTextOpenRouter("请输入模型名称"));
            return;
        }
        if (!apiKey) {
            alert(tTextOpenRouter("请输入 API Key"));
            return;
        }
        const payload = { modelName: modelName, apiKey: apiKey };
        $.ajax({
            type: "POST",
            url: "/apiKey/update",
            contentType: "application/json;charset=utf-8",
            data: JSON.stringify(payload),
            success: function(res) {
                if (res && res.status === "success") {
                    $("#apiKeysCreateMask").hide();
                    loadApiKeys();
                } else {
                    alert((res && res.msg) || tTextOpenRouter("保存失败"));
                }
            },
            error: function() {
                alert(tTextOpenRouter("保存失败"));
            }
        });
    }

    function deleteApiKeyByName(name) {
        if (!name) return;
        confirm(tTextOpenRouter("确认删除该模型的 API Key 吗？")).then(function(ok) {
            if (!ok) return;
            $.ajax({
                type: "POST",
                url: "/apiKey/delete",
                contentType: "application/json;charset=utf-8",
                data: JSON.stringify({ modelName: name }),
                success: function(res) {
                    if (res && res.status === "success") {
                        loadApiKeys();
                    } else {
                        alert((res && res.msg) || tTextOpenRouter("删除失败"));
                    }
                },
                error: function() {
                    alert(tTextOpenRouter("删除失败"));
                }
            });
        });
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
                        <div style="margin-top:6px;color:#6b7280;font-size:13px;">管理 lagi.yml 中模型的 API Key</div>
                    </div>
                    <button type="button" id="apiKeysCreateBtn" style="padding:10px 18px;border:none;border-radius:8px;background:#6366f1;color:#fff;font-size:14px;cursor:pointer;">新增配置</button>
                </div>
                <div style="color:#6b7280;font-size:13px;margin-bottom:12px;">字段来源：name / provider / api_key / api_address</div>
                <div style="border:1px solid #e5e7eb;border-radius:10px;background:#fff;overflow:hidden;">
                    <div style="overflow-x:auto;">
                        <table style="width:100%;border-collapse:collapse;font-size:13px;">
                            <thead><tr style="background:#fff;"><th style="${openRouterThStyle()}">name</th><th style="${openRouterThStyle()}">provider</th><th style="${openRouterThStyle()}">api_key</th><th style="${openRouterThStyle()}">api_address</th><th style="${openRouterThStyle()}">操作</th></tr></thead>
                            <tbody id="apiKeysTbody">${renderApiKeysRows(st.rows)}</tbody>
                        </table>
                    </div>
                </div>
            </div>
            <div id="apiKeysCreateMask" style="display:none;position:fixed;left:0;top:0;right:0;bottom:0;background:rgba(0,0,0,0.32);z-index:1200;align-items:center;justify-content:center;">
                <div style="width:min(560px,92vw);background:#fff;border:1px solid #e5e7eb;border-radius:12px;padding:16px;box-shadow:0 10px 30px rgba(15,23,42,.18);">
                    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px;">
                        <div style="font-size:20px;font-weight:600;">API Key 配置</div>
                        <button type="button" id="apiKeysCloseBtn" style="border:none;background:transparent;font-size:20px;cursor:pointer;color:#6b7280;">×</button>
                    </div>
                    <div id="apiKeyAddArea" style="display:grid;gap:10px;">
                        <label style="font-size:13px;color:#374151;">provider
                            <select id="apiKeyAddProviderSelect" style="margin-top:6px;width:100%;padding:8px;border:1px solid #d1d5db;border-radius:6px;">
                                <option value="">${tTextOpenRouter("请选择 provider")}</option>
                                ${renderProviderOptions(st.types)}
                            </select>
                        </label>
                        <div id="apiKeyAddOpenAIFields" style="display:none;gap:10px;">
                            <label style="font-size:13px;color:#374151;">name<input id="apiKeyAddNameInput" type="text" placeholder="例如：custom2" style="margin-top:6px;width:100%;padding:8px;border:1px solid #d1d5db;border-radius:6px;" /></label>
                            <label style="font-size:13px;color:#374151;">model<input id="apiKeyAddModelInput" type="text" placeholder="例如：gpt-4o-mini" style="margin-top:6px;width:100%;padding:8px;border:1px solid #d1d5db;border-radius:6px;" /></label>
                        </div>
                        <label id="apiKeyAddApiAddressWrap" style="display:none;font-size:13px;color:#374151;">api_address（可选）<input id="apiKeyAddApiAddressInput" type="text" placeholder="例如：https://xxx/v1/chat/completions" style="margin-top:6px;width:100%;padding:8px;border:1px solid #d1d5db;border-radius:6px;" /></label>
                        <div id="apiKeyLandingHint" style="display:none;padding:10px 12px;border-radius:8px;background:#f0f9ff;border:1px solid #bae6fd;color:#0369a1;font-size:13px;line-height:1.5;">${tTextOpenRouter("无需填写 API Key：密钥将由服务端自动生成，并写入配置文件 lagi.yml。")}</div>
                        <div id="apiKeyAddStandardKeyBlock">
                            <label style="font-size:13px;color:#374151;">API Key<input id="apiKeyAddApiKeyInput" type="text" placeholder="请输入 API Key" style="margin-top:6px;width:100%;padding:8px;border:1px solid #d1d5db;border-radius:6px;" /></label>
                        </div>
                    </div>
                    <div id="apiKeyEditArea" style="display:none;gap:10px;">
                        <label style="font-size:13px;color:#374151;">模型名称<input id="apiKeyEditModelName" type="text" disabled style="margin-top:6px;width:100%;padding:8px;border:1px solid #d1d5db;border-radius:6px;background:#f9fafb;" /></label>
                        <label style="font-size:13px;color:#374151;">API Key<input id="apiKeyEditInput" type="text" placeholder="请输入 API Key" style="margin-top:6px;width:100%;padding:8px;border:1px solid #d1d5db;border-radius:6px;" /></label>
                    </div>
                    <button type="button" id="apiKeyCreateConfirmBtn" style="margin-top:14px;width:100%;padding:10px;border:none;border-radius:8px;background:#6366f1;color:#fff;cursor:pointer;">新增</button>
                    <button type="button" id="apiKeyEditConfirmBtn" style="display:none;margin-top:10px;width:100%;padding:10px;border:none;border-radius:8px;background:#4f46e5;color:#fff;cursor:pointer;">保存修改</button>
                </div>
            </div>
        `;
        $("#item-content").html(tHtmlOpenRouter(html));
        bindApiKeysEvents();
        loadProviders(function(types) {
            const optionHtml = '<option value="">' + tTextOpenRouter("请选择 provider") + "</option>" + renderProviderOptions(types);
            $("#apiKeyAddProviderSelect").html(optionHtml);
            toggleAddDialogFields();
        });
        loadApiKeys();
    };
})();
