let tokenUsageChart = null;
const tTextToken = window.tText || ((s) => s);
const tHtmlToken = window.tHtml || ((s) => s);

function loadTokenUsagePage() {
    $('#queryBox').hide();
    $('#footer-info').hide();
    $('#introduces').hide();
    $('#topTitle').hide();
    $('#item-content').show();
    $('#item-content').css('height', 'calc(100vh - 60px)');
    $('#item-content').css('overflow-y', 'auto');
    hideBallDiv();

    const today = formatTokenDateInput(new Date());
    const html = `
        <div id="token-usage-container" style="padding: 20px; min-height: 100%; background: #f6f8fb;">
            <div style="margin-bottom: 14px;">
                <h2 style="margin: 0; font-size: 24px;">使用情况</h2>
                <div style="margin-top: 6px; color: #6b7280; font-size: 13px;">查看本地会话的 Tokens 估算与趋势（基于问题+回答字符统计）。</div>
            </div>

            <div style="background: #fff; border: 1px solid #e5e7eb; border-radius: 10px; padding: 14px; margin-bottom: 14px;">
                <div style="display: flex; flex-wrap: wrap; gap: 8px; align-items: center;">
                    <div style="font-size: 13px; color: #374151; margin-right: 6px;">时间范围</div>
                    <button class="token-range-btn" data-range="today" style="${tokenRangeBtnStyle(true)}">今天</button>
                    <button class="token-range-btn" data-range="7d" style="${tokenRangeBtnStyle(false)}">7d</button>
                    <button class="token-range-btn" data-range="30d" style="${tokenRangeBtnStyle(false)}">30d</button>
                    <input id="tokenDateStart" type="date" value="${today}" style="padding: 6px 8px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 12px;" />
                    <span style="color: #9ca3af;">至</span>
                    <input id="tokenDateEnd" type="date" value="${today}" style="padding: 6px 8px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 12px;" />
                    <select id="tokenMetricSelect" style="padding: 6px 8px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 12px;">
                        <option value="tokens">Tokens</option>
                        <option value="cost">费用</option>
                    </select>
                    <button id="tokenRefreshBtn" style="padding: 6px 12px; border: none; border-radius: 999px; background: #1296db; color: #fff; font-size: 12px; cursor: pointer;">刷新</button>
                </div>
                <div style="margin-top: 8px; color: #9ca3af; font-size: 12px;">
                    说明：费用为估算值（默认按 0.002 RMB / 1K Tokens）。
                </div>
            </div>

            <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 12px; margin-bottom: 14px;">
                ${tokenStatCardHtml('总 Tokens', 'tokenTotalTokens', '0')}
                ${tokenStatCardHtml('总会话数', 'tokenTotalSessions', '0')}
                ${tokenStatCardHtml('日均 Tokens', 'tokenAvgDailyTokens', '0')}
                ${tokenStatCardHtml('估算费用 (RMB)', 'tokenEstimatedCost', '0')}
            </div>

            <div style="background: #fff; border: 1px solid #e5e7eb; border-radius: 10px; padding: 14px; margin-bottom: 14px;">
                <div style="font-size: 16px; font-weight: 600; margin-bottom: 10px;">按时间趋势</div>
                <div id="tokenUsageChart" style="height: 260px;"></div>
                <div id="tokenUsageNoData" style="display: none; text-align: center; color: #9ca3af; padding: 50px 0;">暂无趋势数据</div>
            </div>

            <div style="background: #fff; border: 1px solid #e5e7eb; border-radius: 10px; padding: 14px;">
                <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px;">
                    <div style="font-size: 16px; font-weight: 600;">会话明细</div>
                    <div id="tokenSessionCountText" style="font-size: 12px; color: #6b7280;">范围内 0 条会话</div>
                </div>
                <div style="overflow-x: auto;">
                    <table style="width: 100%; border-collapse: collapse; font-size: 13px;">
                        <thead>
                            <tr style="background: #f9fafb;">
                                <th style="${tokenThStyle()}">标题</th>
                                <th style="${tokenThStyle()}">日期</th>
                                <th style="${tokenThStyle()}">轮次</th>
                                <th style="${tokenThStyle()}">原始 Tokens</th>
                                <th style="${tokenThStyle()}">当前 Tokens</th>
                                <th style="${tokenThStyle()}">费用</th>
                            </tr>
                        </thead>
                        <tbody id="tokenSessionTbody"></tbody>
                    </table>
                </div>
            </div>
        </div>
    `;
    $('#item-content').html(tHtmlToken(html));
    bindTokenUsageEvents();
    refreshTokenUsageData();
}

function tokenRangeBtnStyle(active) {
    if (active) {
        return 'padding:6px 10px;border-radius:6px;border:1px solid #1296db;background:#1296db;color:#fff;font-size:12px;cursor:pointer;';
    }
    return 'padding:6px 10px;border-radius:6px;border:1px solid #e5e7eb;background:#fff;color:#374151;font-size:12px;cursor:pointer;';
}

function tokenStatCardHtml(title, valueId, value) {
    return `
        <div style="background:#fff;border:1px solid #e5e7eb;border-radius:10px;padding:14px;">
            <div style="font-size:12px;color:#6b7280;">${title}</div>
            <div id="${valueId}" style="margin-top:8px;font-size:26px;font-weight:700;color:#111827;">${value}</div>
        </div>
    `;
}

function tokenThStyle() {
    return 'padding:10px;border-bottom:1px solid #e5e7eb;text-align:left;color:#6b7280;font-weight:600;';
}

function bindTokenUsageEvents() {
    $('.token-range-btn').on('click', function() {
        $('.token-range-btn').each(function() {
            $(this).attr('style', tokenRangeBtnStyle(false));
        });
        $(this).attr('style', tokenRangeBtnStyle(true));
        applyTokenRange($(this).data('range'));
        refreshTokenUsageData();
    });

    $('#tokenDateStart').on('change', function() {
        clearTokenQuickRangeSelection();
        refreshTokenUsageData();
    });
    $('#tokenDateEnd').on('change', function() {
        clearTokenQuickRangeSelection();
        refreshTokenUsageData();
    });
    $('#tokenMetricSelect').on('change', function() {
        refreshTokenUsageData();
    });
    $('#tokenRefreshBtn').on('click', function() {
        refreshTokenUsageData();
    });
}

function clearTokenQuickRangeSelection() {
    $('.token-range-btn').each(function() {
        $(this).attr('style', tokenRangeBtnStyle(false));
    });
}

function applyTokenRange(range) {
    const now = new Date();
    const end = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    let start = new Date(end);
    if (range === '7d') {
        start.setDate(end.getDate() - 6);
    } else if (range === '30d') {
        start.setDate(end.getDate() - 29);
    }
    $('#tokenDateStart').val(formatTokenDateInput(start));
    $('#tokenDateEnd').val(formatTokenDateInput(end));
}

function formatTokenDateInput(date) {
    const y = date.getFullYear();
    const m = String(date.getMonth() + 1).padStart(2, '0');
    const d = String(date.getDate()).padStart(2, '0');
    return `${y}-${m}-${d}`;
}

function getTokenRangeTime() {
    const startStr = $('#tokenDateStart').val();
    const endStr = $('#tokenDateEnd').val();
    const start = startStr ? new Date(`${startStr}T00:00:00`) : new Date(0);
    const end = endStr ? new Date(`${endStr}T23:59:59`) : new Date();
    if (start > end) {
        return { start: end, end: start };
    }
    return { start, end };
}

function estimateTokensByText(text) {
    if (!text) {
        return 0;
    }
    // 使用简化估算：1 token ~= 4 chars
    return Math.ceil(String(text).length / 4);
}

function getConversationUsageRows() {
    const convs = loadConvs();
    if (!Array.isArray(convs)) {
        return [];
    }
    const rows = [];
    for (let i = 0; i < convs.length; i++) {
        const conv = convs[i] || {};
        const turns = Array.isArray(conv.convs) ? conv.convs : [];
        let tokens = 0;
        for (let j = 0; j < turns.length; j++) {
            const turn = turns[j] || {};
            const userQ = turn.user ? turn.user.question : '';
            const robotA = turn.robot ? turn.robot.answer : '';
            tokens += estimateTokensByText(userQ);
            tokens += estimateTokensByText(robotA);
        }
        rows.push({
            title: conv.title || `${tTextToken('会话 ')}${i + 1}`,
            dateTime: conv.dateTime || 0,
            turns: turns.length,
            tokensBefore: Number(conv.tokensBefore) > 0 ? Number(conv.tokensBefore) : tokens,
            tokensAfter: Number(conv.tokensAfter) > 0 ? Number(conv.tokensAfter) : tokens
        });
    }
    return rows;
}

function refreshTokenUsageData() {
    const metric = $('#tokenMetricSelect').val() || 'tokens';
    const rows = getConversationUsageRows();
    const { start, end } = getTokenRangeTime();
    const inRange = rows.filter(row => {
        const ts = Number(row.dateTime || 0);
        if (!ts) {
            return false;
        }
        const dt = new Date(ts);
        return dt >= start && dt <= end;
    }).sort((a, b) => b.dateTime - a.dateTime);

    const totalTokens = inRange.reduce((sum, row) => sum + row.tokensAfter, 0);
    const totalSessions = inRange.length;
    const dayCount = Math.max(1, Math.floor((end.getTime() - start.getTime()) / (24 * 3600 * 1000)) + 1);
    const avgDailyTokens = Math.round(totalTokens / dayCount);
    const estimatedCost = ((totalTokens / 1000) * 0.002).toFixed(4);

    const locale = window.getCurrentLocale ? window.getCurrentLocale() : 'zh-CN';
    $('#tokenTotalTokens').text(totalTokens.toLocaleString(locale));
    $('#tokenTotalSessions').text(totalSessions.toLocaleString(locale));
    $('#tokenAvgDailyTokens').text(avgDailyTokens.toLocaleString(locale));
    $('#tokenEstimatedCost').text(estimatedCost);
    $('#tokenSessionCountText').text(`${tTextToken('范围内 ')}${totalSessions}${tTextToken(' 条会话')}`);

    renderTokenSessionTable(inRange);
    renderTokenUsageChart(inRange, start, end, metric);
}

function renderTokenSessionTable(rows) {
    const tbody = $('#tokenSessionTbody');
    tbody.empty();
    if (!rows.length) {
        tbody.append(`<tr><td colspan="6" style="padding: 24px; text-align: center; color: #9ca3af;">${tTextToken('暂无会话数据')}</td></tr>`);
        return;
    }
    for (let i = 0; i < rows.length; i++) {
        const row = rows[i];
        const dateLabel = new Date(row.dateTime).toLocaleString(locale);
        const cost = ((row.tokensAfter / 1000) * 0.002).toFixed(4);
        const tr = `
            <tr>
                <td style="padding:10px;border-bottom:1px solid #f3f4f6;max-width:260px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;" title="${row.title}">${row.title}</td>
                <td style="padding:10px;border-bottom:1px solid #f3f4f6;">${dateLabel}</td>
                <td style="padding:10px;border-bottom:1px solid #f3f4f6;">${row.turns}</td>
                <td style="padding:10px;border-bottom:1px solid #f3f4f6;">${row.tokensBefore.toLocaleString(locale)}</td>
                <td style="padding:10px;border-bottom:1px solid #f3f4f6;">${row.tokensAfter.toLocaleString(locale)}</td>
                <td style="padding:10px;border-bottom:1px solid #f3f4f6;">${cost}</td>
            </tr>
        `;
        tbody.append(tr);
    }
}

function renderTokenUsageChart(rows, start, end, metric) {
    const chartDom = document.getElementById('tokenUsageChart');
    const noDataDom = document.getElementById('tokenUsageNoData');
    if (!chartDom || typeof echarts === 'undefined') {
        return;
    }

    const points = [];
    const dayMs = 24 * 3600 * 1000;
    const cursor = new Date(start.getFullYear(), start.getMonth(), start.getDate()).getTime();
    const endTs = new Date(end.getFullYear(), end.getMonth(), end.getDate()).getTime();
    for (let ts = cursor; ts <= endTs; ts += dayMs) {
        const d = new Date(ts);
        const label = `${d.getMonth() + 1}/${d.getDate()}`;
        const dayStart = ts;
        const dayEnd = ts + dayMs - 1;
        const dayTokens = rows
            .filter(row => row.dateTime >= dayStart && row.dateTime <= dayEnd)
            .reduce((sum, row) => sum + row.tokensAfter, 0);
        points.push({
            label: label,
            tokens: dayTokens,
            cost: Number(((dayTokens / 1000) * 0.002).toFixed(6))
        });
    }

    const hasData = points.some(item => item.tokens > 0);
    if (!hasData) {
        chartDom.style.display = 'none';
        if (noDataDom) {
            noDataDom.style.display = 'block';
        }
        if (tokenUsageChart) {
            tokenUsageChart.clear();
        }
        return;
    }

    chartDom.style.display = 'block';
    if (noDataDom) {
        noDataDom.style.display = 'none';
    }

    if (!tokenUsageChart) {
        tokenUsageChart = echarts.init(chartDom);
        window.addEventListener('resize', function() {
            if (tokenUsageChart) {
                tokenUsageChart.resize();
            }
        });
    }

    const isCost = metric === 'cost';
    const option = {
        tooltip: { trigger: 'axis' },
        grid: { left: 40, right: 20, top: 20, bottom: 35 },
        xAxis: {
            type: 'category',
            data: points.map(item => item.label),
            axisLine: { lineStyle: { color: '#d1d5db' } }
        },
        yAxis: {
            type: 'value',
            axisLine: { show: false },
            splitLine: { lineStyle: { color: '#eef2f7' } }
        },
        series: [{
            data: points.map(item => isCost ? item.cost : item.tokens),
            type: 'line',
            smooth: true,
            lineStyle: { color: '#ef4444', width: 2 },
            itemStyle: { color: '#ef4444' },
            areaStyle: { color: 'rgba(239,68,68,0.12)' }
        }]
    };
    tokenUsageChart.setOption(option, true);
}

