(function() {
    const tTextOpenRouter = window.tText || ((s) => s);
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

    function formatCreditsMoney(v) {
        return Number(v || 0).toFixed(2);
    }

    function ensureCreditsState() {
        if (!window.__creditsPageState) {
            window.__creditsPageState = {
                account: "个人账户: 未登录",
                balance: 10.0,
                autoTopUp: false,
                purchaseAmount: 10,
                serviceFee: 0.8,
                transactions: [
                    { id: "txn_1", date: "2026-03-23 19:19", amount: 10.0, status: "成功" }
                ]
            };
        }
    }

    function renderCreditsTxnRows(transactions) {
        if (!transactions || !transactions.length) {
            return `<tr><td colspan="4" style="padding:24px;text-align:center;color:#9ca3af;">暂无交易记录</td></tr>`;
        }
        return transactions.map(function(tx) {
            return `
                <tr>
                    <td style="${openRouterTdStyle()}">${tx.date || "—"}</td>
                    <td style="${openRouterTdStyle()}">¥ ${formatCreditsMoney(Number(tx.amount || 0))}</td>
                    <td style="${openRouterTdStyle()}">${tx.status || "—"}</td>
                    <td style="${openRouterTdStyle()}"><a href="javascript:void(0)" style="color:#4f46e5;">查看详情</a></td>
                </tr>
            `;
        }).join("");
    }

    function bindCreditsEvents() {
        $("#creditsAddBtn").on("click", function() {
            $("#creditsPurchaseMask").css("display", "flex");
        });
        $("#creditsClosePurchase").on("click", function() {
            $("#creditsPurchaseMask").hide();
        });
        $("#creditsPurchaseMask").on("click", function(e) {
            if (e.target && e.target.id === "creditsPurchaseMask") $("#creditsPurchaseMask").hide();
        });
        $("#creditsAutoTopUpSwitch").on("change", function() {
            const st = window.__creditsPageState;
            st.autoTopUp = $(this).is(":checked");
        });
        $("#creditsAmountInput").on("input", function() {
            const st = window.__creditsPageState;
            let amount = Number($(this).val() || 0);
            if (amount < 1) amount = 1;
            st.purchaseAmount = amount;
            $("#creditsTotalDue").text("¥ " + formatCreditsMoney(st.purchaseAmount + st.serviceFee));
        });
        $("#creditsPurchaseConfirm").on("click", function() {
            applyCreditsPurchase();
        });
        $("#creditsUsageLink").on("click", function() {
            const target = document.getElementById("creditsTxnTbody");
            if (target) target.scrollIntoView({ behavior: "smooth", block: "start" });
        });
    }

    function applyCreditsPurchase() {
        const st = window.__creditsPageState;
        const amount = Number(st.purchaseAmount || 0);
        if (amount <= 0) return;
        st.balance = Number(st.balance || 0) + amount;
        const now = new Date();
        const dateText = now.getFullYear() + "-" + String(now.getMonth() + 1).padStart(2, "0") + "-" + String(now.getDate()).padStart(2, "0")
            + " " + String(now.getHours()).padStart(2, "0") + ":" + String(now.getMinutes()).padStart(2, "0");
        st.transactions.unshift({ id: "txn_" + Date.now(), date: dateText, amount: amount, status: "成功" });
        $("#creditsPurchaseMask").hide();
        window.loadCreditsPage();
    }

    window.loadCreditsPage = function loadCreditsPage() {
        hideChatPartsForStandalonePage();
        ensureCreditsState();
        const st = window.__creditsPageState;
        const html = `
            <div id="credits-container" style="padding:20px;min-height:100%;background:#fff;">
                <div style="margin-bottom:14px;">
                    <h2 style="margin:0;font-size:28px;font-weight:700;">额度中心</h2>
                    <div style="margin-top:6px;color:#6b7280;font-size:13px;">${st.account}</div>
                </div>
                <div style="background:#f3f4f6;border:1px solid #e5e7eb;border-radius:10px;padding:14px;margin-bottom:14px;">
                    <div style="font-size:40px;font-weight:700;letter-spacing:0.5px;">¥ ${formatCreditsMoney(st.balance)}</div>
                </div>
                <div style="display:grid;grid-template-columns:1fr 1fr;gap:14px;margin-bottom:14px;">
                    <div style="border:1px solid #e5e7eb;border-radius:10px;padding:14px;background:#fff;">
                        <div style="display:flex;justify-content:space-between;align-items:center;">
                            <div style="font-size:18px;font-weight:600;">充值额度</div>
                            <div style="font-size:13px;color:#6b7280;">一次性</div>
                        </div>
                        <button type="button" id="creditsAddBtn" style="margin-top:16px;width:100%;padding:10px;border:none;border-radius:8px;background:#6366f1;color:#fff;font-size:14px;cursor:pointer;">立即充值</button>
                        <div style="margin-top:10px;font-size:12px;">
                            <a href="javascript:void(0)" id="creditsUsageLink" style="color:#374151;text-decoration:underline;">查看消费记录</a>
                        </div>
                    </div>
                    <div style="border:1px solid #e5e7eb;border-radius:10px;padding:14px;background:#fff;">
                        <div style="display:flex;justify-content:space-between;align-items:center;">
                            <div style="font-size:18px;font-weight:600;">自动充值</div>
                            <label style="display:inline-flex;align-items:center;gap:6px;cursor:pointer;font-size:13px;color:#6b7280;">
                                <input id="creditsAutoTopUpSwitch" type="checkbox" ${st.autoTopUp ? "checked" : ""} />
                                启用
                            </label>
                        </div>
                        <div style="margin-top:12px;color:#6b7280;font-size:13px;line-height:1.6;">
                            当余额低于阈值时自动发起充值。<br/>你可以在此页面随时开关此能力。
                        </div>
                    </div>
                </div>
                <div style="border:1px solid #e5e7eb;border-radius:10px;padding:14px;background:#fff;">
                    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:10px;">
                        <div style="font-size:18px;font-weight:600;">最近交易</div>
                        <div style="font-size:12px;color:#6b7280;">共 ${st.transactions.length} 条</div>
                    </div>
                    <div style="overflow-x:auto;">
                        <table style="width:100%;border-collapse:collapse;font-size:13px;">
                            <thead><tr style="background:#fff;"><th style="${openRouterThStyle()}">日期</th><th style="${openRouterThStyle()}">金额</th><th style="${openRouterThStyle()}">状态</th><th style="${openRouterThStyle()}">操作</th></tr></thead>
                            <tbody id="creditsTxnTbody">${renderCreditsTxnRows(st.transactions)}</tbody>
                        </table>
                    </div>
                </div>
            </div>
            <div id="creditsPurchaseMask" style="display:none;position:fixed;left:0;top:0;right:0;bottom:0;background:rgba(0,0,0,0.35);z-index:1200;align-items:center;justify-content:center;">
                <div style="width:min(520px,92vw);background:#fff;border-radius:12px;border:1px solid #e5e7eb;box-shadow:0 10px 30px rgba(15,23,42,.18);padding:16px;">
                    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:10px;">
                        <div style="font-size:20px;font-weight:600;">充值额度</div>
                        <button type="button" id="creditsClosePurchase" style="border:none;background:transparent;font-size:20px;cursor:pointer;color:#6b7280;">×</button>
                    </div>
                    <div style="display:grid;grid-template-columns:1fr auto;align-items:center;gap:10px;margin-bottom:10px;">
                        <label style="font-size:14px;color:#374151;">充值金额</label>
                        <input id="creditsAmountInput" type="number" min="1" step="1" value="${st.purchaseAmount}" style="width:120px;padding:8px;border:1px solid #d1d5db;border-radius:6px;text-align:right;" />
                    </div>
                    <div style="border-top:1px solid #e5e7eb;padding-top:10px;margin-top:10px;font-size:14px;line-height:1.8;">
                        <div style="display:flex;justify-content:space-between;"><span>服务费</span><span id="creditsServiceFee">¥ ${formatCreditsMoney(st.serviceFee)}</span></div>
                        <div style="display:flex;justify-content:space-between;font-weight:700;"><span>应付总额</span><span id="creditsTotalDue">¥ ${formatCreditsMoney(st.purchaseAmount + st.serviceFee)}</span></div>
                    </div>
                    <button type="button" id="creditsPurchaseConfirm" style="margin-top:12px;width:100%;padding:10px;border:none;border-radius:8px;background:#6366f1;color:#fff;font-size:14px;cursor:pointer;">确认充值</button>
                    <div style="margin-top:8px;color:#6b7280;font-size:12px;">充值成功后将新增交易记录，并更新当前余额。</div>
                </div>
            </div>
        `;
        $("#item-content").html(tHtmlOpenRouter(html));
        bindCreditsEvents();
    };
})();
